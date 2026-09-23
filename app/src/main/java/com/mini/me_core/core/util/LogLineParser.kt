package com.mini.me_core.core.util

import java.time.ZoneId
import java.time.format.DateTimeFormatter

/**
 * 日志行解析结果。
 *
 * @param date 日期 "2026-08-08"
 * @param level 日志等级，解析失败为 null
 * @param tag 来源标签，如 "McpManager"
 * @param raw 原始行内容
 * @param message 消息正文（去掉时间戳/等级/Tag/线程头之后的部分）；旧日志行可能解析不完整，默认空串
 * @param timestamp 完整时间戳对应的 epoch millis；解析失败为 0
 * @param threadName 日志行中的 `[thread:name]` 部分；旧格式没有则为 null
 */
data class ParsedLogLine(
    val date: String,
    val level: LogLevel?,
    val tag: String,
    val raw: String,
    val message: String = "",
    val timestamp: Long = 0L,
    val threadName: String? = null,
)

/**
 * 日志行解析工具。
 *
 * 支持两种（兼容）行格式：
 *  - 旧格式：`yyyy-MM-dd HH:mm:ss.SSS LEVEL [TAG] message`
 *  - 新格式：`yyyy-MM-dd HH:mm:ss.SSS LEVEL [TAG] [thread:name] message`
 *    （`[thread:name]` 段可选，新老解析器互相兼容）
 *
 * 不匹配该格式的行（如堆栈跟踪、分隔线、格式头 `# ...`）视为「附属行」，
 * 过滤时始终保留（不单独判定），但不会用于提取 Tag/Level。
 */
object LogLineParser {

    // 捕获组：
    //  1 = date(yyyy-MM-dd)
    //  2 = time(HH:mm:ss.SSS)
    //  3 = LEVEL
    //  4 = TAG
    //  5 = threadName（可选）
    //  6 = message
    private val LOG_PATTERN = Regex(
        """^(\d{4}-\d{2}-\d{2})\s+(\d{2}:\d{2}:\d{2}\.\d{3})\s+""" +
            """(VERBOSE|DEBUG|INFO|WARN|ERROR|FATAL)\s+\[([^\]]+)\]""" +
            """(?:\s+\[thread:([^\]]+)\])?\s+(.*)$"""
    )

    // 格式头首行：# MiniMe Log Format vN
    private val FORMAT_VERSION_PATTERN = Regex("^#\\s*MiniMe Log Format v(\\d+)\\s*$")

    private val timestampParser = DateTimeFormatter
        .ofPattern("yyyy-MM-dd HH:mm:ss.SSS")
        .withZone(ZoneId.systemDefault())

    /** 解析单行日志。若格式不匹配（含格式头/堆栈/分隔线）返回 null。 */
    fun parse(line: String): ParsedLogLine? {
        val match = LOG_PATTERN.matchEntire(line) ?: return null
        val date = match.groupValues[1]
        val time = match.groupValues[2]
        return ParsedLogLine(
            date = date,
            level = runCatching { LogLevel.valueOf(match.groupValues[3]) }.getOrNull(),
            tag = match.groupValues[4],
            raw = line,
            message = match.groupValues[6],
            timestamp = toEpochMillis(date, time),
            threadName = match.groupValues[5].ifEmpty { null },
        )
    }

    /** 从多行日志中提取所有出现过的 Tag（去重、按首现顺序）。 */
    fun extractTags(lines: List<String>): List<String> {
        val seen = LinkedHashSet<String>()
        for (line in lines) {
            val parsed = parse(line)
            if (parsed != null) {
                seen.add(parsed.tag)
            }
        }
        return seen.toList()
    }

    /** 判断一行是否为「格式版本头」（`# MiniMe Log Format vN`）。 */
    fun isFormatHeader(line: String): Boolean = FORMAT_VERSION_PATTERN.containsMatchIn(line)

    /**
     * 从格式头行解析版本号；非格式头行返回 null。
     * 附属日志应用据此检测：若日志文件版本高于自身支持版本，提示用户升级。
     */
    fun parseFormatVersion(line: String): Int? =
        FORMAT_VERSION_PATTERN.find(line)?.groupValues?.getOrNull(1)?.toIntOrNull()

    private fun toEpochMillis(date: String, time: String): Long =
        runCatching {
            timestampParser.parse("$date $time", java.time.Instant::from).toEpochMilli()
        }.getOrDefault(0L)
}
