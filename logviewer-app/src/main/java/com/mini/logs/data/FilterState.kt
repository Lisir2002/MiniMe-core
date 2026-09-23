package com.mini.logs.data

import com.mini.me_core.core.util.LogLevel

/**
 * 日志筛选状态。
 *
 * @param selectedLevels 选中的等级集合；空集合表示全部
 * @param levelCollapsed 被折叠的等级集合（只显示计数行，不显示具体行）
 * @param selectedTags 选中的 Tag 集合；空集合表示全部
 * @param excludedTags 排除的 Tag 集合
 * @param searchQuery 搜索关键词；空表示不搜索
 * @param isRegexSearch 是否正则搜索
 * @param selectedFileNames 选中的日志文件名集合；空表示全部
 * @param timeRangeStart 时间范围起点（epoch millis）；0 表示不限制
 * @param timeRangeEnd 时间范围终点（epoch millis）；0 表示不限制
 * @param showOnlyHighlighted 是否只显示高亮标记的行
 * @param showOnlyBookmarked 是否只显示书签行
 */
data class FilterState(
    val selectedLevels: Set<LogLevel> = emptySet(),
    val levelCollapsed: Set<LogLevel> = emptySet(),
    val selectedTags: Set<String> = emptySet(),
    val excludedTags: Set<String> = emptySet(),
    val searchQuery: String = "",
    val isRegexSearch: Boolean = false,
    val selectedFileNames: Set<String> = emptySet(),
    val timeRangeStart: Long = 0L,
    val timeRangeEnd: Long = 0L,
    val showOnlyHighlighted: Boolean = false,
    val showOnlyBookmarked: Boolean = false,
) {
    val isFiltering: Boolean
        get() = selectedLevels.isNotEmpty() || selectedTags.isNotEmpty() ||
                excludedTags.isNotEmpty() || searchQuery.isNotEmpty() ||
                selectedFileNames.isNotEmpty() || showOnlyHighlighted || showOnlyBookmarked

    /** 判断一条日志是否通过筛选。 */
    fun matches(entry: LogEntry): Boolean {
        if (entry.isStackTraceLine) return true // 堆栈行始终跟随主行

        // 等级筛选
        if (selectedLevels.isNotEmpty() && entry.level !in selectedLevels) return false

        // Tag 筛选
        if (selectedTags.isNotEmpty() && entry.tag !in selectedTags) return false
        if (entry.tag in excludedTags) return false

        // 文件筛选
        if (selectedFileNames.isNotEmpty() && entry.sourceFile !in selectedFileNames) return false

        // 时间范围
        if (timeRangeStart > 0 && entry.timestamp < timeRangeStart) return false
        if (timeRangeEnd > 0 && entry.timestamp > timeRangeEnd) return false

        // 高亮/书签筛选
        if (showOnlyHighlighted && !entry.isHighlighted) return false
        if (showOnlyBookmarked && entry.bookmarkNote == null) return false

        // 搜索
        if (searchQuery.isNotEmpty()) {
            val haystack = entry.rawLine
            val found = if (isRegexSearch) {
                runCatching { Regex(searchQuery, RegexOption.IGNORE_CASE).containsMatchIn(haystack) }
                    .getOrDefault(false)
            } else {
                haystack.contains(searchQuery, ignoreCase = true)
            }
            if (!found) return false
        }

        return true
    }
}

/** 视图模式：紧凑 / 舒适。 */
enum class ViewMode { COMPACT, COMFORTABLE }

/** 实时尾随状态。 */
data class TailingState(
    val isEnabled: Boolean = false,
    val isPaused: Boolean = false,
    val newLinesCount: Int = 0,
    val linesPerSecond: Int = 0,
)
