package com.mini.me_core.feature.qqbot.domain

import java.util.concurrent.ConcurrentHashMap
import javax.inject.Inject
import javax.inject.Singleton

/**
 * A single turn of conversation context stored for one QQ chat session.
 *
 * @property role    speaker role; either [ROLE_USER] or [ROLE_ASSISTANT].
 * @property content text content of the message.
 * @property timestamp epoch milliseconds when the message was appended.
 */
data class QBotContextMessage(
    val role: String,
    val content: String,
    val timestamp: Long
) {
    companion object {
        const val ROLE_USER = "user"
        const val ROLE_ASSISTANT = "assistant"
    }
}

/**
 * In-memory conversation context for QQ bot sessions.
 *
 * Each chat party owns an isolated session whose key follows:
 *  - private chat: `qq_{user_id}`
 *  - group chat:    `group_{group_id}_{user_id}`
 *
 * Every session retains at most [QBotConstants.DEFAULT_CONTEXT_LENGTH] most recent
 * messages (oldest evicted on overflow) and is evicted automatically after
 * [QBotConstants.SESSION_TIMEOUT_MS] of inactivity. All access is thread-safe.
 *
 * Two access idioms are supported:
 *  - role-tagged writes via [appendMessage] (raw [QBotContextMessage]);
 *  - plain-string writes via [addMessage] and string reads via [getContext], used by
 *    the message pipeline that talks to the Agent.
 */
@Singleton
class QBotSessionManager @Inject constructor(
    private val logManager: QBotLogManager
) {

    private companion object {
        const val TAG = "QBotSessionManager"
    }

    /** Mutable session state held per key. */
    private class SessionContext(
        val messages: ArrayDeque<QBotContextMessage> = ArrayDeque(),
        var lastAccess: Long = System.currentTimeMillis()
    )

    private val sessions = ConcurrentHashMap<String, SessionContext>()

    /** Build the session key for a private chat with [userId]. */
    fun privateKey(userId: Long): String = "qq_$userId"

    /** Build the session key for [userId] speaking inside group [groupId]. */
    fun groupKey(groupId: Long, userId: Long): String = "group_${groupId}_$userId"

    /**
     * Return the existing session's messages or create an empty one.
     * Touching the session refreshes its last-access time and may evict expired sessions.
     */
    fun getOrCreateSession(key: String): List<QBotContextMessage> {
        sweepExpired()
        val ctx = sessions.getOrPut(key) { SessionContext() }
        ctx.lastAccess = System.currentTimeMillis()
        return synchronized(ctx.messages) { ctx.messages.toList() }
    }

    /**
     * Append a role-tagged message to the session identified by [key], creating it if needed.
     * Excess messages beyond [QBotConstants.DEFAULT_CONTEXT_LENGTH] are dropped from the front.
     *
     * @param role    one of [QBotContextMessage.ROLE_USER] / [QBotContextMessage.ROLE_ASSISTANT].
     * @param content message text; blank entries are ignored.
     */
    fun appendMessage(key: String, role: String, content: String) {
        if (content.isBlank()) return
        sweepExpired()
        val ctx = sessions.getOrPut(key) { SessionContext() }
        synchronized(ctx.messages) {
            ctx.messages.addLast(
                QBotContextMessage(role = role, content = content, timestamp = System.currentTimeMillis())
            )
            while (ctx.messages.size > QBotConstants.DEFAULT_CONTEXT_LENGTH) {
                ctx.messages.removeFirst()
            }
        }
        ctx.lastAccess = System.currentTimeMillis()
    }

    /**
     * Append a raw line to the session, as used by the message pipeline.
     *
     * The role is inferred from conventional prefixes (`User:` / `Bot:`); anything
     * else is treated as a user turn. Blank entries are ignored.
     */
    fun addMessage(key: String, message: String) {
        if (message.isBlank()) return
        val role = when {
            message.startsWith("User:", ignoreCase = true) -> QBotContextMessage.ROLE_USER
            message.startsWith("Bot:", ignoreCase = true) -> QBotContextMessage.ROLE_ASSISTANT
            else -> QBotContextMessage.ROLE_USER
        }
        appendMessage(key, role, message)
    }

    /**
     * Return the retained context for [key] as a list of plain content strings,
     * oldest first; empty if absent. This is the contract used by the Agent caller.
     */
    fun getContext(key: String): List<String> {
        sweepExpired()
        val ctx = sessions[key] ?: return emptyList()
        ctx.lastAccess = System.currentTimeMillis()
        return synchronized(ctx.messages) { ctx.messages.map { it.content } }
    }

    /** Drop the session identified by [key], if it exists. */
    fun resetSession(key: String) {
        sessions.remove(key)
        logManager.debug(TAG, "session reset: $key")
    }

    /** Drop every session. */
    fun clearAll() {
        sessions.clear()
        logManager.debug(TAG, "all sessions cleared")
    }

    /**
     * Evict sessions whose last access is older than [QBotConstants.SESSION_TIMEOUT_MS].
     * Invoked lazily on every read/write; may also be called explicitly by a watchdog.
     */
    fun sweepExpired() {
        val now = System.currentTimeMillis()
        val iterator = sessions.entries.iterator()
        var evicted = 0
        while (iterator.hasNext()) {
            val entry = iterator.next()
            if (now - entry.value.lastAccess > QBotConstants.SESSION_TIMEOUT_MS) {
                iterator.remove()
                evicted++
            }
        }
        if (evicted > 0) logManager.debug(TAG, "swept $evicted expired session(s)")
    }
}
