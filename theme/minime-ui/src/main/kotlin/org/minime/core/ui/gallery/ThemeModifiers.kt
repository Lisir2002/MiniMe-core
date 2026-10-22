package org.minime.core.ui.gallery

import org.minime.core.schema.ColorValue
import org.minime.core.ui.m3.toComposeColor
import org.minime.core.tokens.ColorTokens

/**
 * 修饰符常量（默认主题包 resolutionOrder：dark / highContrast / reducedMotion /
 * reducedTransparency）。名称与主题包 manifest 一致——拼错即静默不生效，
 * 故集中一处，Gallery 顶栏与调试面板共用。
 */
object ThemeModifiers {
    const val DARK = "dark"
    const val HIGH_CONTRAST = "highContrast"
    const val REDUCED_MOTION = "reducedMotion"
    const val REDUCED_TRANSPARENCY = "reducedTransparency"

    val ALL = listOf(DARK, HIGH_CONTRAST, REDUCED_MOTION, REDUCED_TRANSPARENCY)
}

/** 引擎颜色 → Compose Color（复用 M3 映射层同款转换，禁第二套） */
fun ColorValue.compose(): androidx.compose.ui.graphics.Color = toComposeColor()

/** 便捷：语义色直接取 */
val ColorTokens.accentColor: androidx.compose.ui.graphics.Color get() = accent.compose()
val ColorTokens.canvasColor: androidx.compose.ui.graphics.Color get() = bgCanvas.compose()
val ColorTokens.groupedColor: androidx.compose.ui.graphics.Color get() = bgGrouped.compose()
val ColorTokens.surfaceColor: androidx.compose.ui.graphics.Color get() = bgSurface.compose()
val ColorTokens.textPrimaryColor: androidx.compose.ui.graphics.Color get() = textPrimary.compose()
val ColorTokens.textSecondaryColor: androidx.compose.ui.graphics.Color get() = textSecondary.compose()
val ColorTokens.separatorColor: androidx.compose.ui.graphics.Color get() = borderSeparator.compose()
val ColorTokens.fillPrimaryColor: androidx.compose.ui.graphics.Color get() = fillPrimary.compose()

/** 状态三色（§3.4 语义状态面；工坊错误条 / WCAG 徽标 / 预览色板消费） */
val ColorTokens.stateErrorColor: androidx.compose.ui.graphics.Color get() = stateError.compose()
val ColorTokens.stateSuccessColor: androidx.compose.ui.graphics.Color get() = stateSuccess.compose()
val ColorTokens.stateWarningColor: androidx.compose.ui.graphics.Color get() = stateWarning.compose()

/** 第三级文本色（工坊 diff 原值 / 弱化读数） */
val ColorTokens.textTertiaryColor: androidx.compose.ui.graphics.Color get() = textTertiary.compose()
