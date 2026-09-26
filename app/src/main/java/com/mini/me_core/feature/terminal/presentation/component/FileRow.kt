package com.mini.me_core.feature.terminal.presentation.component

import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/** 文件大小格式化。 */
internal fun formatSize(bytes: Long): String = when {
    bytes >= 1024 * 1024 -> "%.1f MB".format(bytes / (1024.0 * 1024.0))
    bytes >= 1024 -> "%.1f KB".format(bytes / 1024.0)
    bytes > 0 -> "$bytes B"
    else -> "-"
}

/** 智能时间格式：今天 HH:mm，今年 MM-dd，往年 yyyy-MM-dd。 */
internal fun formatSmartTime(epochSec: Long): String {
    if (epochSec <= 0) return "-"
    val ms = epochSec * 1000
    val now = Date()
    val target = Date(ms)
    val sdfYMD = SimpleDateFormat("yyyy-MM-dd", Locale.getDefault())
    val sdfMD = SimpleDateFormat("MM-dd", Locale.getDefault())
    val sdfHM = SimpleDateFormat("HH:mm", Locale.getDefault())
    return when {
        sdfYMD.format(now) == sdfYMD.format(target) -> sdfHM.format(target)
        target.year == now.year -> sdfMD.format(target)
        else -> sdfYMD.format(target)
    }
}
