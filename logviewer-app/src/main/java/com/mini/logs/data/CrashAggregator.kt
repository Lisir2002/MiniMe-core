package com.mini.logs.data

import com.mini.me_core.core.util.LogLevel

/**
 * 崩溃聚合组。
 *
 * @param key 聚合 key = 异常类型 + 堆栈第一行
 * @param exceptionType 异常类型（如 java.net.ConnectException）
 * @param message 异常消息
 * @param stackTraceFirstLine 堆栈第一行（类名+方法名+行号）
 * @param fullStackTrace 完整堆栈
 * @param occurrences 发生次数
 * @param firstOccurrence 首次发生时间
 * @param lastOccurrence 最近发生时间
 * @param tags 涉及的 Tag 集合
 * @param entries 对应的日志条目列表
 * @param isFixed 是否标记为已修复
 */
data class CrashGroup(
    val key: String,
    val exceptionType: String,
    val message: String,
    val stackTraceFirstLine: String,
    val fullStackTrace: String,
    val occurrences: Int,
    val firstOccurrence: Long,
    val lastOccurrence: Long,
    val tags: Set<String>,
    val entries: List<LogEntry>,
    val isFixed: Boolean = false,
)

/**
 * 崩溃聚合器。从日志条目中识别 ERROR/FATAL 行并按异常类型+堆栈第一行聚合。
 */
object CrashAggregator {

    private val EXCEPTION_PATTERN = Regex(
        """([a-zA-Z_][a-zA-Z0-9_.]*(?:Exception|Error|Throwable))(?::\s*(.*))?"""
    )

    /**
     * 从日志条目中聚合崩溃。
     * 识别策略：ERROR/FATAL 行 + 后续堆栈行（at ... / Caused by: ...）。
     */
    fun aggregate(entries: List<LogEntry>): List<CrashGroup> {
        val crashes = mutableListOf<CrashInstance>()
        var i = 0

        while (i < entries.size) {
            val entry = entries[i]
            if (entry.level == LogLevel.ERROR || entry.level == LogLevel.FATAL) {
                // 收集后续堆栈行
                val stackLines = mutableListOf<String>()
                var j = i + 1
                while (j < entries.size && entries[j].isStackTraceLine) {
                    stackLines.add(entries[j].rawLine)
                    j++
                }

                val crash = parseCrash(entry, stackLines)
                if (crash != null) {
                    crashes.add(crash)
                }
                i = j
            } else {
                i++
            }
        }

        // 按 key 聚合
        val groups = crashes.groupBy { it.key }
        return groups.map { (key, instances) ->
            val first = instances.first()
            CrashGroup(
                key = key,
                exceptionType = first.exceptionType,
                message = first.message,
                stackTraceFirstLine = first.stackTraceFirstLine,
                fullStackTrace = first.fullStackTrace,
                occurrences = instances.size,
                firstOccurrence = instances.minOf { it.timestamp },
                lastOccurrence = instances.maxOf { it.timestamp },
                tags = instances.map { it.tag }.toSet(),
                entries = instances.flatMap { it.entries },
            )
        }.sortedByDescending { it.occurrences }
    }

    private fun parseCrash(entry: LogEntry, stackLines: List<String>): CrashInstance? {
        val message = entry.message
        val match = EXCEPTION_PATTERN.find(message)
        val exceptionType = match?.groupValues?.getOrNull(1) ?: "UnknownError"
        val exceptionMessage = match?.groupValues?.getOrNull(2) ?: message

        val stackTraceFirstLine = stackLines.firstOrNull { it.trim().startsWith("at ") }
            ?.trim()?.removePrefix("at ") ?: ""

        val fullStackTrace = buildString {
            append(message)
            if (stackLines.isNotEmpty()) {
                append('\n')
                append(stackLines.joinToString("\n"))
            }
        }

        val key = "$exceptionType|$stackTraceFirstLine"

        return CrashInstance(
            key = key,
            exceptionType = exceptionType,
            message = exceptionMessage,
            stackTraceFirstLine = stackTraceFirstLine,
            fullStackTrace = fullStackTrace,
            timestamp = entry.timestamp,
            tag = entry.tag,
            entries = listOf(entry),
        )
    }

    private data class CrashInstance(
        val key: String,
        val exceptionType: String,
        val message: String,
        val stackTraceFirstLine: String,
        val fullStackTrace: String,
        val timestamp: Long,
        val tag: String,
        val entries: List<LogEntry>,
    )
}
