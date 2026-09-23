package com.mini.logs.ui.stats

import java.io.File
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/**
 * 统计页时间范围。
 *
 * @param label 顶栏下拉展示文字
 * @param days 当前范围覆盖天数（含今天）
 */
enum class StatsRange(val label: String, val days: Int) {
    TODAY("今天", 1),
    LAST_3_DAYS("近3天", 3),
    LAST_7_DAYS("近7天", 7);

    companion object {
        val options = entries.toList()
    }
}

private val fileDateFormat = SimpleDateFormat("yyyy-MM-dd", Locale.US)
private const val DAY_MILLIS = 86_400_000L

/**
 * 从日志文件名 `log-yyyy-MM-dd.txt` 解析出当天 00:00 的时间戳；失败返回 null。
 */
internal fun parseLogFileDateMillis(name: String): Long? {
    if (!name.startsWith("log-") || !name.endsWith(".txt")) return null
    val datePart = name.removePrefix("log-").substringBeforeLast('.')
    return runCatching { fileDateFormat.parse(datePart)?.time }.getOrNull()
}

/**
 * 根据时间范围挑选日志文件：返回 (当前范围文件, 上一个等长范围文件)。
 *
 * 例如近7天：当前范围 = 今天往前 6 天；上一个范围 = 再往前 7 天（用于环比）。
 */
internal fun selectFilesForRange(
    files: List<File>,
    range: StatsRange,
): Pair<List<File>, List<File>> {
    val cal = java.util.Calendar.getInstance()
    cal.set(java.util.Calendar.HOUR_OF_DAY, 0)
    cal.set(java.util.Calendar.MINUTE, 0)
    cal.set(java.util.Calendar.SECOND, 0)
    cal.set(java.util.Calendar.MILLISECOND, 0)
    val todayStart = cal.timeInMillis

    val days = range.days
    val currentStart = todayStart - (days - 1) * DAY_MILLIS
    val prevEnd = currentStart - 1_000L
    val prevStart = todayStart - (2 * days - 1) * DAY_MILLIS

    val current = mutableListOf<File>()
    val prev = mutableListOf<File>()
    for (f in files) {
        val millis = parseLogFileDateMillis(f.name) ?: continue
        when {
            millis in currentStart..todayStart -> current.add(f)
            millis in prevStart..prevEnd -> prev.add(f)
        }
    }
    return current to prev
}

/** 仅供调试/展示：把时间戳格式化为 yyyy-MM-dd。 */
internal fun formatDay(millis: Long): String =
    if (millis > 0) fileDateFormat.format(Date(millis)) else ""
