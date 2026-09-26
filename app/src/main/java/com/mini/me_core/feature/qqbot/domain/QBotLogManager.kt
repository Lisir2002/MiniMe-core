package com.mini.me_core.feature.qqbot.domain

import com.mini.me_core.core.util.FileLogger
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import java.util.concurrent.ConcurrentLinkedDeque
import javax.inject.Inject
import javax.inject.Singleton

/**
 * In-memory ring buffer for QQ bot runtime logs.
 *
 * Keeps at most [QBotConstants.LOG_BUFFER_SIZE] most recent [QBotLogEntry]s in a
 * thread-safe deque and exposes them as a [StateFlow] so the UI can observe live
 * logs without holding a long-lived reader. Each entry is additionally mirrored to
 * [FileLogger] for on-disk persistence.
 *
 * The buffer is append-only and evicts the oldest entries once full, which keeps
 * memory bounded regardless of how long the bot runs.
 */
@Singleton
class QBotLogManager @Inject constructor() {

    private companion object {
        const val TAG = "QBotLogManager"
    }

    private val buffer = ConcurrentLinkedDeque<QBotLogEntry>()

    private val _logs = MutableStateFlow<List<QBotLogEntry>>(emptyList())

    /** Observable snapshot of buffered log entries, oldest first. */
    val logs: StateFlow<List<QBotLogEntry>> = _logs.asStateFlow()

    /**
     * Append a log entry and evict the oldest entries beyond the buffer capacity.
     *
     * @param level severity of the entry.
     * @param tag   short component tag, e.g. the originating class name.
     * @param message human-readable description of the event.
     */
    @Synchronized
    fun log(level: QBotLogLevel, tag: String, message: String) {
        val entry = QBotLogEntry(
            timestamp = System.currentTimeMillis(),
            level = level,
            tag = tag,
            message = message
        )
        buffer.addLast(entry)
        while (buffer.size > QBotConstants.LOG_BUFFER_SIZE) {
            buffer.pollFirst()
        }
        _logs.value = buffer.toList()
        mirrorToFile(level, tag, message)
    }

    /**
     * Return the most recent [limit] entries, oldest first.
     *
     * @param limit maximum number of entries to return; defaults to the full buffer size.
     */
    fun getLogs(limit: Int = QBotConstants.LOG_BUFFER_SIZE): List<QBotLogEntry> {
        val all = _logs.value
        if (limit <= 0 || all.isEmpty()) return emptyList()
        return if (limit >= all.size) all else all.subList(all.size - limit, all.size)
    }

    // ── Level-tagged convenience API (delegates to [log]) ──────────────────────────────

    /** Log a debug-level message. */
    fun debug(tag: String, message: String) = log(QBotLogLevel.DEBUG, tag, message)

    /** Log an info-level message. */
    fun info(tag: String, message: String) = log(QBotLogLevel.INFO, tag, message)

    /** Log a warning-level message. */
    fun warn(tag: String, message: String) = log(QBotLogLevel.WARN, tag, message)

    /** Log an error-level message, mirroring [throwable] to [FileLogger] when present. */
    fun error(tag: String, message: String, throwable: Throwable? = null) {
        log(QBotLogLevel.ERROR, tag, message)
        throwable?.let { FileLogger.e(tag, message, it) }
    }

    /** Remove all buffered entries and emit an empty list. */
    @Synchronized
    fun clear() {
        buffer.clear()
        _logs.value = emptyList()
        FileLogger.i(TAG, "log buffer cleared")
    }

    private fun mirrorToFile(level: QBotLogLevel, tag: String, message: String) {
        when (level) {
            QBotLogLevel.DEBUG -> FileLogger.d(tag, message)
            QBotLogLevel.INFO -> FileLogger.i(tag, message)
            QBotLogLevel.WARN -> FileLogger.w(tag, message)
            QBotLogLevel.ERROR -> FileLogger.e(tag, message)
        }
    }
}
