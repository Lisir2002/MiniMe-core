package com.mini.logs.ui.logs

import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import com.mini.me_core.core.theme.tokens.LocalAppTheme
import com.mini.me_core.core.util.LogLevel

/**
 * 日志等级 → 颜色映射。
 * ERROR=红, WARN=橙, INFO=蓝, DEBUG=紫, VERBOSE=灰, FATAL=深红。
 */
object LogLevelColors {

    @Composable
    fun colorFor(level: LogLevel?): Color {
        val c = LocalAppTheme.current.colors
        return when (level) {
            LogLevel.ERROR -> c.error
            LogLevel.FATAL -> c.error
            LogLevel.WARN -> c.warning
            LogLevel.INFO -> c.info
            LogLevel.DEBUG -> c.accentReasoning
            LogLevel.VERBOSE -> c.textTertiary
            LogLevel.NONE -> c.textTertiary
            null -> c.textSecondary
        }
    }

    @Composable
    fun containerColorFor(level: LogLevel?): Color {
        val c = LocalAppTheme.current.colors
        return when (level) {
            LogLevel.ERROR -> c.errorContainer
            LogLevel.FATAL -> c.errorContainer
            LogLevel.WARN -> c.warningContainer
            LogLevel.INFO -> c.infoContainer
            LogLevel.DEBUG -> c.accentReasoning.copy(alpha = 0.15f)
            LogLevel.VERBOSE -> c.surfaceSunken
            LogLevel.NONE -> c.surfaceSunken
            null -> c.surfaceSunken
        }
    }

    fun labelFor(level: LogLevel): String = when (level) {
        LogLevel.VERBOSE -> "V"
        LogLevel.DEBUG -> "D"
        LogLevel.INFO -> "I"
        LogLevel.WARN -> "W"
        LogLevel.ERROR -> "E"
        LogLevel.FATAL -> "F"
        LogLevel.NONE -> "?"
    }

    fun displayName(level: LogLevel): String = when (level) {
        LogLevel.VERBOSE -> "Verbose"
        LogLevel.DEBUG -> "Debug"
        LogLevel.INFO -> "Info"
        LogLevel.WARN -> "Warn"
        LogLevel.ERROR -> "Error"
        LogLevel.FATAL -> "Fatal"
        LogLevel.NONE -> "None"
    }

    /** 等级筛选栏展示顺序（从高严重到低严重）。 */
    val filterOrder: List<LogLevel> = listOf(
        LogLevel.ERROR,
        LogLevel.WARN,
        LogLevel.INFO,
        LogLevel.DEBUG,
        LogLevel.VERBOSE,
    )
}
