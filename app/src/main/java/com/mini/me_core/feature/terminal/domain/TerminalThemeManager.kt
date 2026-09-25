package com.mini.me_core.feature.terminal.domain

import androidx.compose.ui.graphics.Color
import com.mini.me_core.feature.terminal.data.repository.TerminalTheme

/**
 * F3.6 终端主题管理：把 [TerminalTheme] 枚举映射为 Compose 颜色（fg/bg/cursor）。
 */
object TerminalThemeManager {
    data class ThemeColors(
        val background: Color,
        val foreground: Color,
        val cursor: Color,
    )

    fun colorsFor(theme: TerminalTheme, appDark: Boolean): ThemeColors = when (theme) {
        TerminalTheme.FOLLOW_APP -> if (appDark) darkDefault() else lightDefault()
        TerminalTheme.PURE_BLACK -> darkDefault()
        TerminalTheme.PURE_WHITE -> lightDefault()
        TerminalTheme.DRACULA -> ThemeColors(Color(0xFF282A36), Color(0xFFF8F8F2), Color(0xFFBD93F9))
        TerminalTheme.SOLARIZED_DARK -> ThemeColors(Color(0xFF002B36), Color(0xFF93A1A1), Color(0xFF93A1A1))
        TerminalTheme.SOLARIZED_LIGHT -> ThemeColors(Color(0xFFFDF6E3), Color(0xFF657B83), Color(0xFF657B83))
        TerminalTheme.MONOKAI -> ThemeColors(Color(0xFF272822), Color(0xFFF8F8F2), Color(0xFFF92672))
        TerminalTheme.GITHUB_DARK -> ThemeColors(Color(0xFF0D1117), Color(0xFFC9D1D9), Color(0xFF58A6FF))
    }

    private fun darkDefault() = ThemeColors(Color(0xFF000000), Color(0xFFFFFFFF), Color(0xFFFFFFFF))
    private fun lightDefault() = ThemeColors(Color(0xFFFFFFFF), Color(0xFF000000), Color(0xFF000000))

    val allThemes = listOf(
        TerminalTheme.FOLLOW_APP,
        TerminalTheme.PURE_BLACK,
        TerminalTheme.PURE_WHITE,
        TerminalTheme.DRACULA,
        TerminalTheme.SOLARIZED_DARK,
        TerminalTheme.SOLARIZED_LIGHT,
        TerminalTheme.MONOKAI,
        TerminalTheme.GITHUB_DARK,
    )
}
