package com.mini.logs.data

import com.mini.me_core.core.util.LogLevel

/**
 * 结构化日志条目。由原始日志行解析而来，附带 UI 状态（高亮/书签/展开）。
 *
 * @param rawLine 原始行文本（含时间戳/等级/Tag/消息）
 * @param timestamp 精确到毫秒的 epoch millis；解析失败为 0
 * @param date 日期字符串 "yyyy-MM-dd"
 * @param time 时间字符串 "HH:mm:ss.SSS"
 * @param level 日志等级；附属行（堆栈/分隔线）为 null
 * @param tag 来源标签，如 "McpManager"；附属行为空串
 * @param message 消息正文；附属行为原始行
 * @param threadName 线程名（如果日志行有 [thread:name] 段）
 * @param sourceFile 来自哪个日志文件名
 * @param lineNumber 在合并列表中的行号（从 1 开始）
 * @param isStackTraceLine 是否为堆栈附属行（不以时间戳开头）
 * @param isHighlighted 是否被用户高亮标记
 * @param highlightColor 高亮颜色索引（0-4，对应 5 色）；未高亮为 -1
 * @param bookmarkNote 书签备注；null 表示无书签
 * @param isExpanded 消息/堆栈是否展开
 * @param isNew 是否为实时尾随新入场的日志（用于入场动画）
 */
data class LogEntry(
    val rawLine: String,
    val timestamp: Long = 0L,
    val date: String = "",
    val time: String = "",
    val level: LogLevel? = null,
    val tag: String = "",
    val message: String = "",
    val threadName: String? = null,
    val sourceFile: String = "",
    val lineNumber: Int = 0,
    val isStackTraceLine: Boolean = false,
    val isHighlighted: Boolean = false,
    val highlightColor: Int = -1,
    val bookmarkNote: String? = null,
    val isExpanded: Boolean = false,
    val isNew: Boolean = false,
) {
    /** 是否为可筛选的主日志行（非堆栈附属行）。 */
    val isMainLine: Boolean get() = level != null && !isStackTraceLine

    /** 等级首字母（用于紧凑视图）。 */
    val levelLetter: String
        get() = when (level) {
            LogLevel.VERBOSE -> "V"
            LogLevel.DEBUG -> "D"
            LogLevel.INFO -> "I"
            LogLevel.WARN -> "W"
            LogLevel.ERROR -> "E"
            LogLevel.FATAL -> "F"
            LogLevel.NONE -> ""
            null -> ""
        }
}

/** 高亮标记的 5 种颜色。 */
enum class HighlightColor(val index: Int, val argb: Long) {
    RED(0, 0xFFFFCDD2),
    YELLOW(1, 0xFFFFF9C4),
    GREEN(2, 0xFFC8E6C9),
    BLUE(3, 0xFFBBDEFB),
    PURPLE(4, 0xFFE1BEE7);

    companion object {
        fun fromIndex(index: Int): HighlightColor? = entries.firstOrNull { it.index == index }
    }
}
