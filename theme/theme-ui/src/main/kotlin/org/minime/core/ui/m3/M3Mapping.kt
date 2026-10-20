package org.minime.core.ui.m3

import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.ColorScheme
import androidx.compose.material3.Shapes
import androidx.compose.material3.Typography
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.TextUnit
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import org.minime.core.schema.ColorValue
import org.minime.core.schema.DimensionValue
import org.minime.core.schema.TypographyValue
import org.minime.core.tokens.ColorTokens
import org.minime.core.tokens.ShapeTokens
import org.minime.core.tokens.TypographyTokens

/**
 * M3 映射层（§9.1 兼容模型）。
 *
 * **范围声明（v1.7 审计修正，白皮书 §9.1）**：MVP 只映射 **color / typography / shape**
 * 三个子系统——它们在 M3 侧有对应角色可落；motion / interaction / size / spacing /
 * elevation / opacity / glass **不做 M3 映射**（引擎自绘组件直接消费 `ThemeSnapshot`，
 * 双轨期 M3 页面的动效与阈值维持原状，迁移完成即消失，属可接受的过渡态）。
 *
 * 实现约定：一律 `Xxx().copy(...)` 而非全参数构造——M3 的 `ColorScheme`/`Typography`/`Shapes`
 * 字段集会随 BOM 演进，全参数构造升版即断裂；`copy` 只声明我们关心的角色，其余交给 M3 默认值。
 */

// ---------- color → ColorScheme ----------

/**
 * 语义色 → M3 `ColorScheme`。
 *
 * 映射原则：**语义对角色，不按色值猜**。表面组用 `bg.*`、强调色用 `accent`、
 * 状态色用 `state.*`、描边用 `border.*`；M3 中无对应语义的角色（tertiary 系列、
 * inverse 系列等）保留 M3 默认值，不硬凑——目标是让存量 M3 页面「同色」，
 * 不追求角色表完备（完备性由自绘组件侧的 `ColorTokens` 承担）。
 */
fun ColorTokens.toColorScheme(): ColorScheme {
    // 以 M3 的亮/暗基底出发：未映射的角色（tertiary 系列、inverse 系列等）也跟随主题深浅，
    // 而不是一律拿亮色基底——否则暗色主题下存量 M3 页面会出现亮色残留角色。
    val base = if (prefersDark()) darkColorScheme() else lightColorScheme()
    return base.copy(
        primary = accent.toComposeColor(),
        onPrimary = Color.White,                    // 与 iOS filled 按钮一致（accent 为深色系）
        background = bgCanvas.toComposeColor(),
        onBackground = textPrimary.toComposeColor(),
        surface = bgSurface.toComposeColor(),
        onSurface = textPrimary.toComposeColor(),
        surfaceVariant = bgSurfaceSecondary.toComposeColor(),
        onSurfaceVariant = textSecondary.toComposeColor(),
        surfaceTint = accent.toComposeColor(),
        error = stateError.toComposeColor(),
        onError = Color.White,
        errorContainer = stateError.toComposeColor(),
        onErrorContainer = textPrimary.toComposeColor(),
        outline = borderOpaque.toComposeColor(),
        outlineVariant = borderSeparator.toComposeColor(),
        scrim = bgCanvas.toComposeColor(),           // 遮罩底色；透明度由组件层按 opacity.scrim 施加
        surfaceContainerLowest = bgCanvas.toComposeColor(),
        surfaceContainerLow = bgSurface.toComposeColor(),
        surfaceContainer = bgSurfaceSecondary.toComposeColor(),
        surfaceContainerHigh = bgSurfaceTertiary.toComposeColor(),
    )
}

/**
 * 深浅判定：按 `color.semantic.bg.canvas` 的感知亮度（ITU-R BT.601 近似）。
 * 用画布色而非 accent 判断——画布决定整体明度基调。
 */
private fun ColorTokens.prefersDark(): Boolean {
    val luminance = (0.299f * bgCanvas.red + 0.587f * bgCanvas.green + 0.114f * bgCanvas.blue) / 255f
    return luminance < 0.5f
}

// ---------- typography → Typography ----------

/**
 * 12 级字阶 → M3 `Typography`。
 *
 * 映射表（iOS 字阶 → M3 角色，按**字号降序**对齐，避免角色错位导致排版跳变）：
 *
 * | M3 角色 | 令牌 | M3 角色 | 令牌 |
 * |---|---|---|---|
 * | displayLarge | largeTitle 34 | titleLarge | title3 20 |
 * | displayMedium | title1 28 | titleMedium | subheadline 15 |
 * | displaySmall | title2 22 | titleSmall | footnote 13 |
 * | headlineLarge | title3 20 | bodyLarge | body 17 |
 * | headlineMedium | headline 17·600 | bodyMedium | callout 16 |
 * | headlineSmall | subheadline 15 | bodySmall | footnote 13 |
 * | labelLarge | subheadline 15 | labelMedium | caption1 12 |
 * | labelSmall | caption2 11 | | |
 *
 * `monospace`(13) 在 M3 无对应角色——由引擎自绘组件直接消费，不映射。
 */
fun TypographyTokens.toTypography(): Typography = Typography().copy(
    displayLarge = largeTitle.toTextStyle(),
    displayMedium = title1.toTextStyle(),
    displaySmall = title2.toTextStyle(),
    headlineLarge = title3.toTextStyle(),
    headlineMedium = headline.toTextStyle(),
    headlineSmall = subheadline.toTextStyle(),
    titleLarge = title3.toTextStyle(),
    titleMedium = subheadline.toTextStyle(),
    titleSmall = footnote.toTextStyle(),
    bodyLarge = body.toTextStyle(),
    bodyMedium = callout.toTextStyle(),
    bodySmall = footnote.toTextStyle(),
    labelLarge = subheadline.toTextStyle(),
    labelMedium = caption1.toTextStyle(),
    labelSmall = caption2.toTextStyle(),
)

// ---------- shape → Shapes ----------

/**
 * 圆角 → M3 `Shapes`。
 *
 * 注：引擎自绘组件用 `ShapeTokens` 原值（含连续圆角 squircle）；
 * 这里仅服务**存量 M3 页面**，形状精度以自绘侧为准。
 */
fun ShapeTokens.toShapes(): Shapes = Shapes().copy(
    extraSmall = RoundedCornerShape(radiusButton.value.dp),
    small = RoundedCornerShape(radiusButton.value.dp),
    medium = RoundedCornerShape(radiusCard.value.dp),
    large = RoundedCornerShape(radiusCardLarge.value.dp),
    extraLarge = RoundedCornerShape(radiusDialog.value.dp),
)

// ---------- 值转换 ----------

/** 引擎颜色 → Compose `Color`（引擎内部已存 ARGB 位模式，零解析零分配） */
fun ColorValue.toComposeColor(): Color = Color(argb)

/** 排版令牌 → `TextStyle`（fontSize / lineHeight 按 sp，字族按引擎语义名映射） */
fun TypographyValue.toTextStyle(): TextStyle = TextStyle(
    fontSize = fontSize.spValue(),
    lineHeight = lineHeight?.spValue() ?: TextUnit.Unspecified,
    fontWeight = fontWeight?.let { FontWeight(it.toIntOrNull() ?: 400) } ?: FontWeight.Normal,
    fontFamily = fontFamily.toFontFamily(),
    letterSpacing = letterSpacing?.spValue() ?: TextUnit.Unspecified,
)

/** 字族名 → `FontFamily`（只认引擎两个语义名，其余回落默认，不猜系统字体） */
fun String?.toFontFamily(): FontFamily = when (this?.lowercase()) {
    "monospace", "mono" -> FontFamily.Monospace
    else -> FontFamily.Default
}

/** 尺寸 → sp 文本单位（单位必须是 sp，与设计不符即快速失败，不静默换算） */
private fun DimensionValue.spValue(): TextUnit {
    check(unit == "sp") { "排版尺寸单位必须为 sp，实际为 $unit" }
    return value.sp
}
