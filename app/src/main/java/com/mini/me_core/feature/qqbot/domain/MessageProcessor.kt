package com.mini.me_core.feature.qqbot.domain

import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Semaphore
import kotlinx.coroutines.sync.withPermit
import java.util.concurrent.ConcurrentHashMap
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Callback invoked to ask the Agent to generate a reply.
 *
 * The host application sets this on [MessageProcessor] to bridge into the
 * core Agent pipeline without creating a direct compile-time dependency.
 */
fun interface AgentCaller {
    /**
     * @param sessionKey the session identifier (e.g. `qq_123456` or `group_789_123456`).
     * @param context recent conversation history, oldest first.
     * @param userMessage the plain-text message sent by the user.
     * @return the generated reply text.
     */
    suspend fun onCall(sessionKey: String, context: List<String>, userMessage: String): String
}

/**
 * Callback invoked after a reply has been generated and sent, so the host
 * application can persist the user message and bot reply to its repository.
 */
fun interface MessagePersister {
    /**
     * @param event the original OneBot message event.
     * @param replyText the reply that was sent to the user.
     */
    suspend fun onPersist(event: OneBotMessageEvent, replyText: String)
}

/**
 * Message processing chain for incoming OneBot 11 events.
 *
 * Pipeline:
 * 1. Check trigger rules (private = always; group = only when bot is @'ed).
 * 2. Enforce per-user rate limit ([QBotConstants.RATE_LIMIT_PER_MINUTE] / minute).
 * 3. Extract plain text from message segments (filters out at segments).
 * 4. Build session key: `qq_{user_id}` for private, `group_{group_id}_{user_id}` for group.
 * 5. Load context from [QBotSessionManager].
 * 6. Call [AgentCaller] under a concurrency limit of [QBotConstants.MAX_CONCURRENT_SESSIONS].
 * 7. Send the reply via [OneBotApiClient].
 * 8. Update session context and invoke [MessagePersister].
 */
@Singleton
class MessageProcessor @Inject constructor(
    private val apiClient: OneBotApiClient,
    private val sessionManager: QBotSessionManager,
    private val logManager: QBotLogManager
) {

    companion object {
        private const val TAG = "MessageProcessor"
        private const val RATE_WINDOW_MS = 60_000L
    }

    /** External Agent bridge. Must be set before messages are processed. */
    @Volatile
    var agentCaller: AgentCaller? = null

    /** External persistence bridge. Optional; if null, messages are not persisted. */
    @Volatile
    var messagePersister: MessagePersister? = null

    /** Bot's own QQ number, used to detect @ mentions in group messages. */
    @Volatile
    var botQq: Long = 0L

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    private val processingSemaphore = Semaphore(QBotConstants.MAX_CONCURRENT_SESSIONS)

    /** userId -> list of message timestamps within the current rate-limit window. */
    private val rateLimitBuckets = ConcurrentHashMap<Long, MutableList<Long>>()

    /**
     * Entry point for processing an incoming [event].
     * Returns immediately; the actual work runs on [Dispatchers.IO].
     */
    fun process(event: OneBotMessageEvent) {
        scope.launch {
            try {
                handleMessage(event)
            } catch (e: Exception) {
                logManager.error(TAG, "Error while processing message", e)
            }
        }
    }

    private suspend fun handleMessage(event: OneBotMessageEvent) {
        // Step 1: trigger rules.
        if (!shouldTrigger(event)) {
            logManager.debug(TAG, "Message does not meet trigger conditions, ignored")
            return
        }

        // Step 2: rate limit.
        if (!checkRateLimit(event.userId)) {
            logManager.debug(TAG, "Rate limit exceeded for user=${event.userId}")
            return
        }

        // Step 3: extract plain text.
        val text = extractPlainText(event)
        if (text.isBlank()) {
            logManager.debug(TAG, "Extracted text is blank, skipping")
            return
        }

        // Step 4: build session key via the existing session manager helpers.
        val sessionKey = when (event.messageType) {
            "private" -> sessionManager.privateKey(event.userId)
            "group" -> sessionManager.groupKey(event.groupId ?: 0L, event.userId)
            else -> "unknown_${event.userId}"
        }
        logManager.debug(TAG, "Processing message sessionKey=$sessionKey textLength=${text.length}")

        // Step 5: load context.
        val context = sessionManager.getContext(sessionKey)

        // Step 6: call Agent under concurrency limit.
        val caller = agentCaller
        if (caller == null) {
            logManager.warn(TAG, "AgentCaller is not set, cannot generate reply")
            return
        }

        val reply = processingSemaphore.withPermit {
            caller.onCall(sessionKey, context, text)
        }

        if (reply.isBlank()) {
            logManager.warn(TAG, "Agent returned empty reply, skip sending")
            return
        }

        // Step 7: send reply.
        when (event.messageType) {
            "private" -> apiClient.sendPrivateMessage(event.userId, reply)
            "group" -> {
                val groupId = event.groupId ?: run {
                    logManager.warn(TAG, "Group message missing groupId, cannot send reply")
                    return
                }
                apiClient.sendGroupMessage(groupId, reply)
            }
            else -> {
                logManager.warn(TAG, "Unknown message_type=${event.messageType}, cannot send reply")
                return
            }
        }

        // Step 8: update context and persist.
        sessionManager.addMessage(sessionKey, "User: $text")
        sessionManager.addMessage(sessionKey, "Bot: $reply")

        try {
            messagePersister?.onPersist(event, reply)
        } catch (e: Exception) {
            logManager.error(TAG, "MessagePersister threw exception", e)
        }
    }

    /**
     * Determine whether [event] should trigger the bot.
     *
     * - Private messages: always trigger.
     * - Group messages: trigger only if a `at` segment mentions [botQq].
     */
    private fun shouldTrigger(event: OneBotMessageEvent): Boolean {
        return when (event.messageType) {
            "private" -> true
            "group" -> event.message.any { segment ->
                segment.type == "at" && segment.data["qq"] == botQq.toString()
            }
            else -> false
        }
    }

    /**
     * Extract concatenated plain text from text segments, excluding at segments.
     */
    private fun extractPlainText(event: OneBotMessageEvent): String {
        return event.message
            .filter { it.type == "text" }
            .map { it.data["text"] ?: "" }
            .joinToString(" ")
            .trim()
    }

    /**
     * Check whether [userId] has exceeded [QBotConstants.RATE_LIMIT_PER_MINUTE]
     * messages within the last [RATE_WINDOW_MS].
     *
     * If allowed, the current timestamp is recorded in the bucket.
     */
    private fun checkRateLimit(userId: Long): Boolean {
        val now = System.currentTimeMillis()
        val windowStart = now - RATE_WINDOW_MS

        val bucket = rateLimitBuckets.getOrPut(userId) { mutableListOf() }
        synchronized(bucket) {
            // Drop timestamps outside the current window.
            while (bucket.isNotEmpty() && bucket.first() < windowStart) {
                bucket.removeAt(0)
            }
            return if (bucket.size < QBotConstants.RATE_LIMIT_PER_MINUTE) {
                bucket.add(now)
                true
            } else {
                false
            }
        }
    }
}
