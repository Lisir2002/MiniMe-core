package com.mini.logs.data

import com.mini.me_core.core.util.LogLevel

/**
 * 崩溃类型。
 */
enum class CrashKind {
    JAVA_EXCEPTION,
    ANR,
    NATIVE,
}

/**
 * 崩溃聚合组。
 *
 * @param key 聚合 key = 异常类型 + 消息前 50 字符
 * @param exceptionType 异常类型（如 java.net.ConnectException）
 * @param message 异常消息
 * @param stackTraceFirstLine 堆栈第一行（类名+方法名+行号）
 * @param stackTraceLocations 所有不同的堆栈首行（去重，用于展示发生位置多样性）
 * @param fullStackTrace 完整堆栈
 * @param crashKind 崩溃种类（Java 异常 / ANR / Native）
 * @param occurrences 发生次数
 * @param firstOccurrence 首次发生时间
 * @param lastOccurrence 最近发生时间
 * @param tags 涉及的 Tag 集合
 * @param entries 对应的日志条目列表
 * @param isFixed 是否标记为已修复
 * @param similarGroupKeys 同 exceptionType 但不同 message 的相似组 key
 */
data class CrashGroup(
    val key: String,
    val exceptionType: String,
    val message: String,
    val stackTraceFirstLine: String,
    val stackTraceLocations: Set<String> = emptySet(),
    val fullStackTrace: String,
    val crashKind: CrashKind = CrashKind.JAVA_EXCEPTION,
    val occurrences: Int,
    val firstOccurrence: Long,
    val lastOccurrence: Long,
    val tags: Set<String>,
    val entries: List<LogEntry>,
    val isFixed: Boolean = false,
    val similarGroupKeys: List<String> = emptyList(),
)

/**
 * 崩溃聚合器。从日志条目中识别 ERROR/FATAL 行并按异常类型+消息前 50 字符聚合。
 * 同时识别 ANR 与 Native Crash。
 */
object CrashAggregator {

    private val EXCEPTION_PATTERN = Regex(
        """([a-zA-Z_][a-zA-Z0-9_.]*(?:Exception|Error|Throwable))(?::\s*(.*))?"""
    )

    /** ANR 特征：消息中包含 ANR in / Input dispatching timed out。 */
    private val ANR_PATTERN = Regex("ANR in|Input dispatching timed out", RegexOption.IGNORE_CASE)

    /** Native Crash 特征：signal N (SIGxxx)。 */
    private val NATIVE_PATTERN = Regex("signal\\s+\\d+\\s*\\(SIG\\w+\\)", RegexOption.IGNORE_CASE)

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
        val groupList = groups.map { (key, instances) ->
            val first = instances.first()
            CrashGroup(
                key = key,
                exceptionType = first.exceptionType,
                message = first.message,
                stackTraceFirstLine = first.stackTraceFirstLine,
                stackTraceLocations = instances.mapNotNull { it.stackTraceFirstLine }
                    .filter { it.isNotBlank() }.toSet(),
                fullStackTrace = first.fullStackTrace,
                crashKind = first.crashKind,
                occurrences = instances.size,
                firstOccurrence = instances.minOf { it.timestamp },
                lastOccurrence = instances.maxOf { it.timestamp },
                tags = instances.map { it.tag }.toSet(),
                entries = instances.flatMap { it.entries },
            )
        }

        // 相似崩溃：同 exceptionType 但不同 message 的组互标
        val byType = groupList.groupBy { it.exceptionType }
        val withSimilar = groupList.map { g ->
            val siblings = byType[g.exceptionType].orEmpty().filter { it.key != g.key }
            g.copy(similarGroupKeys = siblings.map { it.key })
        }

        return withSimilar.sortedByDescending { it.occurrences }
    }

    private fun parseCrash(entry: LogEntry, stackLines: List<String>): CrashInstance? {
        val rawMessage = entry.message

        // 先判定 ANR / Native
        val kind = when {
            NATIVE_PATTERN.containsMatchIn(rawMessage) -> CrashKind.NATIVE
            ANR_PATTERN.containsMatchIn(rawMessage) -> CrashKind.ANR
            else -> CrashKind.JAVA_EXCEPTION
        }

        val exceptionType: String
        val exceptionMessage: String
        if (kind == CrashKind.ANR) {
            exceptionType = "ANR"
            exceptionMessage = rawMessage
        } else if (kind == CrashKind.NATIVE) {
            exceptionType = "Native Crash"
            exceptionMessage = rawMessage
        } else {
            val match = EXCEPTION_PATTERN.find(rawMessage)
            exceptionType = match?.groupValues?.getOrNull(1) ?: "UnknownError"
            exceptionMessage = match?.groupValues?.getOrNull(2) ?: rawMessage
        }

        val stackTraceFirstLine = stackLines.firstOrNull { it.trim().startsWith("at ") }
            ?.trim()?.removePrefix("at ") ?: ""

        val fullStackTrace = buildString {
            append(rawMessage)
            if (stackLines.isNotEmpty()) {
                append('\n')
                append(stackLines.joinToString("\n"))
            }
        }

        // 聚合 key：异常类型 + 消息前 50 字符（不再含行号）
        val key = "$exceptionType|${exceptionMessage.take(50)}"

        return CrashInstance(
            key = key,
            exceptionType = exceptionType,
            message = exceptionMessage,
            stackTraceFirstLine = stackTraceFirstLine,
            fullStackTrace = fullStackTrace,
            crashKind = kind,
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
        val crashKind: CrashKind,
        val timestamp: Long,
        val tag: String,
        val entries: List<LogEntry>,
    )
}
