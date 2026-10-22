package org.minime.core.ui.gallery.pages

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.testTag
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import org.minime.core.schema.ColorValue
import org.minime.core.tokens.Contrast
import org.minime.core.tokens.Contrast.WcagLevel
import org.minime.core.ui.LocalThemeSnapshot
import org.minime.core.ui.MiniMeTokens
import org.minime.core.ui.draw.ContinuousCornerShape
import org.minime.core.ui.m3.toComposeColor
import org.minime.core.ui.m3.toTextStyle
import org.minime.core.ui.gallery.accentColor
import org.minime.core.ui.gallery.stateErrorColor
import org.minime.core.ui.gallery.stateSuccessColor
import org.minime.core.ui.gallery.stateWarningColor
import org.minime.core.ui.gallery.surfaceColor
import org.minime.core.ui.gallery.textPrimaryColor
import org.minime.core.ui.gallery.textSecondaryColor
import kotlin.math.roundToInt

/**
 * 工坊实时读数：WCAG 对比度矩阵（charter §4「实时读数」）。
 *
 * 文本五档 × 背景五档 + accent × surface 共 26 格，每格 = 「Aa」实样（前景色画在
 * 背景色上）+ 精确比值 + 等级徽标（AAA / AA / Large only / Fail）。
 *
 * 算法为引擎 [Contrast]（§6.8.5 包络法：半透明色对白 / 黑极端背景取更差）——
 * 面板只消费结果，禁本地重写亮度公式。随每次覆写重组重算（26 格 < 0.5ms）。
 */
@Composable
fun WorkshopWcagMatrix(modifier: Modifier = Modifier) {
    val snapshot = LocalThemeSnapshot.current
    val color = MiniMeTokens.LocalColor.current
    val typography = MiniMeTokens.LocalTypography.current
    val spacing = MiniMeTokens.LocalSpacing.current
    val shape = MiniMeTokens.LocalShape.current

    val textPaths = listOf(
        "text.primary" to "primary",
        "text.secondary" to "secondary",
        "text.tertiary" to "tertiary",
        "text.quaternary" to "quaternary",
        "text.placeholder" to "placeholder",
    )
    val bgPaths = listOf(
        "bg.canvas" to "canvas",
        "bg.grouped" to "grouped",
        "bg.surface" to "surface",
        "bg.surfaceSecondary" to "surf2",
        "bg.surfaceTertiary" to "surf3",
    )

    fun colorAt(path: String): ColorValue =
        snapshot.raw["color.semantic.$path"] as? ColorValue ?: snapshot.color.textSecondary

    Column(
        modifier = modifier
            .fillMaxWidth()
            .verticalScroll(rememberScrollState())
            .semantics { testTag = "workshop-wcag" },
    ) {
        Text(
            text = "WCAG 对比度矩阵",
            style = typography.headline.toTextStyle(),
            color = color.textPrimaryColor,
        )
        Text(
            text = "§6.8.5 包络法（半透明对白/黑极端背景取更差）· AAA ≥ 7 · AA ≥ 4.5 · 大字 ≥ 3 · 随覆写实时重算",
            style = typography.caption2.toTextStyle(),
            color = color.textSecondaryColor,
        )
        Spacer(Modifier.height(spacing.s8.value.dp))

        // ---- 表头（背景五档） ----
        Row(verticalAlignment = Alignment.CenterVertically) {
            Box(modifier = Modifier.width(72.dp)) // 角标（行名列宽）
            bgPaths.forEach { (_, short) ->
                Text(
                    text = short,
                    style = typography.caption2.toTextStyle(),
                    color = color.textSecondaryColor,
                    textAlign = TextAlign.Center,
                    modifier = Modifier.weight(1f),
                )
            }
        }
        Spacer(Modifier.height(spacing.s4.value.dp))

        // ---- 文本五档 × 背景五档 ----
        textPaths.forEach { (textPath, short) ->
            val fg = colorAt(textPath)
            Row(
                modifier = Modifier.padding(vertical = spacing.s4.value.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(
                    text = short,
                    style = typography.caption2.toTextStyle(),
                    color = color.textSecondaryColor,
                    modifier = Modifier.width(72.dp),
                )
                bgPaths.forEach { (bgPath, _) ->
                    WcagCell(
                        fg = fg,
                        bg = colorAt(bgPath),
                        modifier = Modifier.weight(1f),
                    )
                }
            }
        }

        // ---- accent × surface（分隔 + 单独行） ----
        Spacer(Modifier.height(spacing.s8.value.dp))
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(
                text = "accent",
                style = typography.caption2.toTextStyle(),
                color = color.textSecondaryColor,
                modifier = Modifier.width(72.dp),
            )
            Text(
                text = "on surface（按钮文字场景）",
                style = typography.caption2.toTextStyle(),
                color = color.textSecondaryColor,
                modifier = Modifier.weight(1f),
            )
            WcagCell(
                fg = snapshot.color.accent,
                bg = snapshot.color.bgSurface,
                modifier = Modifier.width(64.dp),
            )
        }
    }
}

/** 单格：「Aa」实样 + 比值 + 等级徽标 */
@Composable
private fun WcagCell(fg: ColorValue, bg: ColorValue, modifier: Modifier = Modifier) {
    val color = MiniMeTokens.LocalColor.current
    val typography = MiniMeTokens.LocalTypography.current
    val shape = MiniMeTokens.LocalShape.current

    val ratio = Contrast.envelopeRatio(fg, bg)
    val level = Contrast.levelOf(ratio)

    Column(
        modifier = modifier.padding(horizontal = 2.dp).semantics { testTag = "workshop-wcag-cell" },
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Box(
            modifier = Modifier
                .size(width = 44.dp, height = 32.dp)
                .clip(ContinuousCornerShape(shape.radiusButton.value.dp))
                .background(bg.toComposeColorSafe()),
            contentAlignment = Alignment.Center,
        ) {
            Text(
                text = "Aa",
                style = typography.callout.toTextStyle(),
                color = fg.toComposeColorSafe(),
            )
        }
        Text(
            text = ratio.format2(),
            style = typography.caption2.toTextStyle(),
            color = color.textSecondaryColor,
        )
        LevelBadge(level)
    }
}

/** 等级徽标（语义状态色：success / accent / warning / error——自举，禁硬编码） */
@Composable
private fun LevelBadge(level: WcagLevel) {
    val color = MiniMeTokens.LocalColor.current
    val typography = MiniMeTokens.LocalTypography.current
    val (text, badge) = when (level) {
        WcagLevel.AAA -> "AAA" to color.stateSuccessColor
        WcagLevel.AA -> "AA" to color.accentColor
        WcagLevel.LARGE_ONLY -> "大字" to color.stateWarningColor
        WcagLevel.FAIL -> "Fail" to color.stateErrorColor
    }
    Box(
        modifier = Modifier
            .padding(top = 2.dp)
            .clip(RoundedCornerShape(percent = 50))
            .background(badge)
            .padding(horizontal = 8.dp, vertical = 2.dp),
    ) {
        Text(
            text = text,
            style = typography.caption2.toTextStyle(),
            color = color.surfaceColor,
        )
    }
}

/** 比值紧凑格式（两位小数） */
private fun Double.format2(): String {
    val r = (this * 100).roundToInt() / 100.0
    return if (r == r.toInt().toDouble()) "${r.toInt()}" else "%.2f".format(r)
}

/** Compose Color 转换（复用 M3 映射层，禁第二套） */
private fun ColorValue.toComposeColorSafe(): androidx.compose.ui.graphics.Color =
    this.toComposeColor()
