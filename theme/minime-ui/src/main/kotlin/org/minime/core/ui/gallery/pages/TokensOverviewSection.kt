package org.minime.core.ui.gallery.pages

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import org.minime.core.schema.BooleanValue
import org.minime.core.schema.ColorValue
import org.minime.core.schema.DimensionValue
import org.minime.core.schema.DurationValue
import org.minime.core.schema.GlassValue
import org.minime.core.schema.NumberValue
import org.minime.core.schema.ShadowValue
import org.minime.core.schema.SpringValue
import org.minime.core.schema.StringValue
import org.minime.core.schema.TokenType
import org.minime.core.schema.TokenValue
import org.minime.core.schema.TypographyValue
import org.minime.core.ui.LocalThemeSnapshot
import org.minime.core.ui.MiniMeTokens
import org.minime.core.ui.draw.ContinuousCornerShape
import org.minime.core.ui.draw.drawHairline
import org.minime.core.ui.draw.HairlineEdge
import org.minime.core.ui.m3.toComposeColor
import org.minime.core.ui.m3.toFontFamily
import org.minime.core.ui.m3.toTextStyle
import org.minime.core.ui.gallery.accentColor
import org.minime.core.ui.gallery.fillPrimaryColor
import org.minime.core.ui.gallery.groupedColor
import org.minime.core.ui.gallery.separatorColor
import org.minime.core.ui.gallery.surfaceColor
import org.minime.core.ui.gallery.textPrimaryColor
import org.minime.core.ui.gallery.textSecondaryColor
import org.minime.core.tokens.ThemeSnapshot

/**
 * 分区一：令牌总览（白皮书 §10.3）。
 *
 * 十子系统逐行展示，全部值实时读自当前生效快照 `snapshot.raw`（硬纪律 2：禁硬编码值）；
 * 展示清单 [TokenOverviewSpec] 与词汇表 172 条的双向集合相等由
 * `TokenCoverageTest` 机器核对（缺项即失败）。
 *
 * 每行 = 语义名（强调）+ 令牌路径（等宽，工程标识）+ 按类型的实样——
 * 对齐 §10.3 的「双列标注」与各子系统展示形态表。
 */
@Composable
fun TokensOverviewSection(modifier: Modifier = Modifier) {
    val snapshot = LocalThemeSnapshot.current

    LazyColumn(modifier = modifier.fillMaxSize()) {
        item { OverviewIntro() }

        TokenOverviewSpec.ORDER.forEach { subsystem ->
            val paths = TokenOverviewSpec.PATHS.getValue(subsystem)
            item(key = "header-$subsystem") {
                SubsystemHeader(
                    subsystem = subsystem,
                    count = paths.size,
                    snapshot = snapshot,
                )
            }

            when (subsystem) {
                // opacity 九档阶梯合并为一行梯子（每档仍带路径标注）
                "opacity" -> {
                    item(key = "opacity-ladder") { OpacityLadderRow() }
                    items(
                        items = paths.filterNot { it.startsWith("opacity.primitive.") },
                        key = { it },
                    ) { path -> TokenRow(path, snapshot) }
                }
                // glass 四级材质各一张配方卡
                "glass" -> {
                    items(items = paths, key = { it }) { path -> TokenRow(path, snapshot) }
                }
                else -> {
                    items(items = paths, key = { it }) { path -> TokenRow(path, snapshot) }
                }
            }
        }

        item { OverviewFooter() }
    }
}

// ---------- 区头区尾 ----------

@Composable
private fun OverviewIntro() {
    val color = MiniMeTokens.LocalColor.current
    val typography = MiniMeTokens.LocalTypography.current
    val spacing = MiniMeTokens.LocalSpacing.current

    Column(modifier = Modifier.padding(horizontal = spacing.gutter.value.dp, vertical = spacing.s16.value.dp)) {
        Text(
            text = "令牌总览",
            style = typography.largeTitle.toTextStyle(),
            color = color.textPrimaryColor,
        )
        Spacer(Modifier.height(spacing.s4.value.dp))
        Text(
            text = "全部值实时读自当前生效快照（ThemeSnapshot.raw）——本页即设计系统的活文档。" +
                "展示清单与引擎词汇表 172 条逐行机器核对（TokenCoverageTest）。",
            style = typography.footnote.toTextStyle(),
            color = color.textSecondaryColor,
        )
    }
}

@Composable
private fun OverviewFooter() {
    val color = MiniMeTokens.LocalColor.current
    val typography = MiniMeTokens.LocalTypography.current
    val spacing = MiniMeTokens.LocalSpacing.current

    Column(modifier = Modifier.padding(horizontal = spacing.gutter.value.dp, vertical = spacing.s24.value.dp)) {
        Text(
            text = "以上 172 条 = 引擎词汇表全量（§6.1.4）。主题包可覆盖其中任意值，" +
                "覆写后本页实时反映（R4 工坊落地）。",
            style = typography.caption1.toTextStyle(),
            color = color.textSecondaryColor,
        )
    }
}

@Composable
private fun SubsystemHeader(subsystem: String, count: Int, snapshot: ThemeSnapshot) {
    val color = MiniMeTokens.LocalColor.current
    val typography = MiniMeTokens.LocalTypography.current
    val spacing = MiniMeTokens.LocalSpacing.current

    val caption = when (subsystem) {
        "color" -> "primitive 原始层 + semantic 语义层 + 扩展组（editor / terminal / chart，随包提供）"
        "typography" -> "iOS 字阶 11 级 + 代码等宽 + 行高字距（实样 + 解析值）"
        "shape" -> "圆角阶梯；连续圆角（squircle）与几何圆角并排 A/B"
        "size" -> "条形按令牌值等比；44dp 触达下限为硬约束（§3.2）"
        "spacing" -> "4–32 阶梯 + 页面语义间距"
        "motion" -> "六槽 spring + 七档时长（点播曲线 R5 落地）"
        "interaction" -> "手势阈值 / 按压系数 / 抖动 / 时序 / 触觉（行为参数）"
        "elevation" -> "发丝线 + 浮层极轻投影（模糊档在 glass，§6.2 边界）"
        "opacity" -> "九档阶梯 + 六语义；只改叠加结果，不改色值（§6.8.2）"
        "glass" -> "四级材质配方 + 开关；默认零玻璃，拟态近似 = tint + 饱和度 + 发丝线"
        else -> ""
    }

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(
                start = spacing.gutter.value.dp,
                end = spacing.gutter.value.dp,
                top = spacing.s24.value.dp,
                bottom = spacing.s8.value.dp,
            ),
    ) {
        Row(verticalAlignment = Alignment.Bottom) {
            Text(
                text = subsystem,
                style = typography.title2.toTextStyle(),
                color = color.textPrimaryColor,
            )
            Spacer(Modifier.width(spacing.s8.value.dp))
            Text(
                text = "$count 项",
                style = typography.footnote.toTextStyle(),
                color = color.accentColor,
                modifier = Modifier.padding(bottom = 3.dp),
            )
        }
        if (caption.isNotEmpty()) {
            Text(
                text = caption,
                style = typography.caption2.toTextStyle(),
                color = color.textSecondaryColor,
            )
        }
        Spacer(Modifier.height(spacing.s8.value.dp))
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .height(1.dp)
                .background(color.separatorColor)
        )
    }
}

// ---------- 行级调度 ----------

@Composable
private fun TokenRow(path: String, snapshot: ThemeSnapshot) {
    val value = snapshot.raw[path] ?: return // 缺失由 TokenCoverageTest 兜底（端到端可读断言）
    val type = typeOf(path)
    val spacing = MiniMeTokens.LocalSpacing.current

    Box(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = spacing.gutter.value.dp)
            .drawHairline(
                HairlineEdge.Bottom,
                MiniMeTokens.LocalColor.current.separatorColor,
                MiniMeTokens.LocalElevation.current.hairline.px,
            ),
    ) {
        when {
            type == TokenType.COLOR -> ColorRow(path, value as ColorValue)
            type == TokenType.TYPOGRAPHY -> TypographyRow(path, value as TypographyValue)
            type == TokenType.FONT_FAMILY -> FamilyRow(path, value as StringValue)
            type == TokenType.DIMENSION && path.startsWith("shape.") ->
                ShapeAbRow(path, value as DimensionValue)
            type == TokenType.DIMENSION && path.startsWith("size.") ->
                SizeRow(path, value as DimensionValue)
            type == TokenType.DIMENSION && path.startsWith("spacing.") ->
                SpacingRow(path, value as DimensionValue)
            type == TokenType.DIMENSION && path == "elevation.hairline" ->
                HairlineRow(path, value as DimensionValue)
            type == TokenType.DIMENSION -> ScalarRow(path, value) // interaction.shakeDistance 等
            type == TokenType.SHADOW -> ShadowRow(path, value as ShadowValue)
            type == TokenType.SPRING -> SpringRow(path, value as SpringValue)
            type == TokenType.GLASS -> GlassRow(path, value as GlassValue)
            type == TokenType.DURATION -> DurationRow(path, value as DurationValue)
            type == TokenType.NUMBER -> ScalarRow(path, value as NumberValue)
            type == TokenType.BOOLEAN -> BooleanRow(path, value as BooleanValue)
            else -> ScalarRow(path, value)
        }
    }
}

/** 词汇表类型查询（渲染器分发依据） */
private fun typeOf(path: String): TokenType? =
    org.minime.core.tokens.TokenVocabulary.typeOf(path)

/** 行容器：语义名 + 令牌路径 + 右侧实样（§10.3 双列标注） */
@Composable
private fun RowShell(
    path: String,
    visual: @Composable () -> Unit,
) {
    val color = MiniMeTokens.LocalColor.current
    val typography = MiniMeTokens.LocalTypography.current
    val spacing = MiniMeTokens.LocalSpacing.current

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = spacing.s16.value.dp, vertical = spacing.s8.value.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = path.substringAfterLast('.'),
                style = typography.callout.toTextStyle(),
                color = color.textPrimaryColor,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            Text(
                text = path,
                style = typography.monospace.toTextStyle().copy(fontFamily = "monospace".toFontFamily()),
                color = color.textSecondaryColor,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        }
        Spacer(Modifier.width(spacing.s12.value.dp))
        visual()
    }
}

// ---------- color ----------

@Composable
private fun ColorRow(path: String, value: ColorValue) {
    val color = MiniMeTokens.LocalColor.current
    val typography = MiniMeTokens.LocalTypography.current
    val spacing = MiniMeTokens.LocalSpacing.current
    val shape = MiniMeTokens.LocalShape.current

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = spacing.s16.value.dp, vertical = spacing.s8.value.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        // 色板（squircle 裁切——形状层能力的第一消费方）
        Box(
            modifier = Modifier
                .size(44.dp)
                .clip(ContinuousCornerShape(shape.radiusButton.value.dp))
                .background(value.toComposeColor())
                .drawBehind {
                    // 半透明色加描边，否则在画布上看不见（iOS 色板惯例）
                    if (value.alpha < 1f) {
                        drawRoundRect(
                            color = color.borderOpaque.toComposeColor(),
                            cornerRadius = CornerRadius(6.dp.toPx()),
                        )
                    }
                },
        )
        Spacer(Modifier.width(spacing.s12.value.dp))
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = path.substringAfterLast('.'),
                style = typography.callout.toTextStyle(),
                color = color.textPrimaryColor,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            Text(
                text = path,
                style = typography.monospace.toTextStyle().copy(fontFamily = "monospace".toFontFamily()),
                color = color.textSecondaryColor,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        }
        Text(
            text = value.hex.removePrefix("#"),
            style = typography.monospace.toTextStyle().copy(fontFamily = "monospace".toFontFamily()),
            color = color.textSecondaryColor,
        )
    }
}

// ---------- typography ----------

@Composable
private fun TypographyRow(path: String, value: TypographyValue) {
    val color = MiniMeTokens.LocalColor.current
    val typography = MiniMeTokens.LocalTypography.current
    val spacing = MiniMeTokens.LocalSpacing.current

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = spacing.s16.value.dp, vertical = spacing.s8.value.dp),
    ) {
        Row(verticalAlignment = Alignment.Bottom) {
            Text(
                text = path.substringAfterLast('.'),
                style = typography.callout.toTextStyle(),
                color = color.textPrimaryColor,
            )
            Spacer(Modifier.width(spacing.s8.value.dp))
            Text(
                text = path,
                style = typography.caption2.toTextStyle().copy(fontFamily = "monospace".toFontFamily()),
                color = color.textSecondaryColor,
            )
        }
        // 实样（按令牌渲染自身——活文档的体现）
        Text(
            text = "主题引擎 MiniMe Aa",
            style = value.toTextStyle(),
            color = color.textPrimaryColor,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )
        Text(
            text = buildString {
                append("${value.fontSize.value}${value.fontSize.unit}")
                value.lineHeight?.let { append(" · 行高 ${it.value}${it.unit}") }
                value.fontWeight?.let { append(" · 字重 $it") }
                value.letterSpacing?.let { append(" · 字距 ${it.value}${it.unit}") }
            },
            style = typography.caption2.toTextStyle(),
            color = color.textSecondaryColor,
        )
    }
}

@Composable
private fun FamilyRow(path: String, value: StringValue) {
    RowShell(path = path) {
        val color = MiniMeTokens.LocalColor.current
        val typography = MiniMeTokens.LocalTypography.current
        Text(
            text = value.value,
            style = typography.footnote.toTextStyle().copy(fontFamily = value.value.toFontFamily()),
            color = color.textPrimaryColor,
        )
    }
}

// ---------- shape：squircle A/B ----------

@Composable
private fun ShapeAbRow(path: String, value: DimensionValue) {
    val color = MiniMeTokens.LocalColor.current
    val typography = MiniMeTokens.LocalTypography.current
    val spacing = MiniMeTokens.LocalSpacing.current

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = spacing.s16.value.dp, vertical = spacing.s8.value.dp),
    ) {
        Row(verticalAlignment = Alignment.Bottom) {
            Text(
                text = path.substringAfterLast('.'),
                style = typography.callout.toTextStyle(),
                color = color.textPrimaryColor,
            )
            Spacer(Modifier.width(spacing.s8.value.dp))
            Text(
                text = "$path · ${value.value}${value.unit}",
                style = typography.caption2.toTextStyle().copy(fontFamily = "monospace".toFontFamily()),
                color = color.textSecondaryColor,
            )
        }
        Spacer(Modifier.height(spacing.s8.value.dp))
        // A/B 并排（R3 验收项 / R6 观感命门）：左 squircle，右几何圆角
        Row(horizontalArrangement = Arrangement.spacedBy(spacing.s12.value.dp)) {
            AbSample(
                label = "连续圆角",
                modifier = Modifier
                    .size(64.dp)
                    .clip(ContinuousCornerShape(value.value.dp))
                    .background(color.fillPrimaryColor),
            )
            AbSample(
                label = "几何圆角",
                modifier = Modifier
                    .size(64.dp)
                    .clip(RoundedCornerShape(value.value.dp))
                    .background(color.fillPrimaryColor),
            )
        }
    }
}

@Composable
private fun AbSample(label: String, modifier: Modifier) {
    val color = MiniMeTokens.LocalColor.current
    val typography = MiniMeTokens.LocalTypography.current
    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        Box(modifier)
        Spacer(Modifier.height(4.dp))
        Text(
            text = label,
            style = typography.caption2.toTextStyle(),
            color = color.textSecondaryColor,
        )
    }
}

// ---------- size ----------

@Composable
private fun SizeRow(path: String, value: DimensionValue) {
    val color = MiniMeTokens.LocalColor.current
    val typography = MiniMeTokens.LocalTypography.current
    val spacing = MiniMeTokens.LocalSpacing.current
    val shape = MiniMeTokens.LocalShape.current

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = spacing.s16.value.dp, vertical = spacing.s8.value.dp),
    ) {
        RowShell(path = path) {
            Text(
                text = "${value.value}${value.unit}",
                style = typography.monospace.toTextStyle().copy(fontFamily = "monospace".toFontFamily()),
                color = color.textPrimaryColor,
            )
        }
        // 等比条（上限 96dp）
        Box(
            modifier = Modifier
                .padding(top = spacing.s4.value.dp)
                .width(value.value.coerceIn(2f, 96f).dp)
                .height(10.dp)
                .clip(ContinuousCornerShape(shape.radiusPill.value.dp))
                .background(color.accentColor)
        )
    }
}

// ---------- spacing ----------

@Composable
private fun SpacingRow(path: String, value: DimensionValue) {
    val color = MiniMeTokens.LocalColor.current
    val typography = MiniMeTokens.LocalTypography.current
    val spacing = MiniMeTokens.LocalSpacing.current
    val shape = MiniMeTokens.LocalShape.current

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = spacing.s16.value.dp, vertical = spacing.s8.value.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        // 阶梯可视化
        Box(
            modifier = Modifier
                .width(value.value.coerceIn(2f, 96f).dp)
                .height(10.dp)
                .clip(ContinuousCornerShape(shape.radiusPill.value.dp))
                .background(color.accentColor)
        )
        Spacer(Modifier.width(spacing.s12.value.dp))
        Text(
            text = path.substringAfterLast('.'),
            style = typography.callout.toTextStyle(),
            color = color.textPrimaryColor,
            modifier = Modifier.weight(1f),
            maxLines = 1,
        )
        Text(
            text = "${value.value}${value.unit}",
            style = typography.monospace.toTextStyle().copy(fontFamily = "monospace".toFontFamily()),
            color = color.textSecondaryColor,
        )
    }
}

// ---------- motion ----------

@Composable
private fun SpringRow(path: String, value: SpringValue) {
    val color = MiniMeTokens.LocalColor.current
    val typography = MiniMeTokens.LocalTypography.current
    val spacing = MiniMeTokens.LocalSpacing.current
    val mono = typography.monospace.toTextStyle().copy(fontFamily = "monospace".toFontFamily())

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = spacing.s16.value.dp, vertical = spacing.s8.value.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = path.removePrefix("motion."),
                style = typography.callout.toTextStyle(),
                color = color.textPrimaryColor,
            )
            Text(
                text = path,
                style = typography.caption2.toTextStyle().copy(fontFamily = "monospace".toFontFamily()),
                color = color.textSecondaryColor,
            )
        }
        Text(
            text = "k ${value.stiffness.fmt()} · c ${value.damping.fmt()} · v0 ${value.initialVelocity.fmt()}",
            style = mono,
            color = color.textPrimaryColor,
        )
    }
}

@Composable
private fun DurationRow(path: String, value: DurationValue) {
    val snapshot = LocalThemeSnapshot.current
    val color = MiniMeTokens.LocalColor.current
    val typography = MiniMeTokens.LocalTypography.current
    val spacing = MiniMeTokens.LocalSpacing.current
    val shape = MiniMeTokens.LocalShape.current
    val mono = typography.monospace.toTextStyle().copy(fontFamily = "monospace".toFontFamily())

    // 相对时长硬上限（duration.max）的等比条——「时长区间 200–350ms」红线（§6.7.3）的可视化
    val maxMs = snapshot.motion.durationMax.ms
    val ratio = (value.ms / maxMs).coerceIn(0f, 1f)

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = spacing.s16.value.dp, vertical = spacing.s8.value.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(
            modifier = Modifier
                .width((ratio * 72f).coerceAtLeast(2f).dp)
                .height(10.dp)
                .clip(ContinuousCornerShape(shape.radiusPill.value.dp))
                .background(color.accentColor)
        )
        Spacer(Modifier.width(spacing.s12.value.dp))
        Text(
            text = path.removePrefix("motion."),
            style = typography.callout.toTextStyle(),
            color = color.textPrimaryColor,
            modifier = Modifier.weight(1f),
            maxLines = 1,
        )
        Text(text = "${value.ms.fmt()}ms", style = mono, color = color.textPrimaryColor)
    }
}

// ---------- interaction / 数值型 ----------

@Composable
private fun ScalarRow(path: String, value: TokenValue) {
    val color = MiniMeTokens.LocalColor.current
    val typography = MiniMeTokens.LocalTypography.current
    val spacing = MiniMeTokens.LocalSpacing.current

    val text = when (value) {
        is NumberValue -> value.value.fmt()
        is DurationValue -> "${value.ms.fmt()}ms"
        is DimensionValue -> "${value.value}${value.unit}"
        is StringValue -> value.value
        else -> value.toString()
    }

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = spacing.s16.value.dp, vertical = spacing.s8.value.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = path.substringAfterLast('.'),
                style = typography.callout.toTextStyle(),
                color = color.textPrimaryColor,
                maxLines = 1,
            )
            Text(
                text = path,
                style = typography.caption2.toTextStyle().copy(fontFamily = "monospace".toFontFamily()),
                color = color.textSecondaryColor,
                maxLines = 1,
            )
        }
        Text(
            text = text,
            style = typography.monospace.toTextStyle().copy(fontFamily = "monospace".toFontFamily()),
            color = color.textPrimaryColor,
        )
    }
}

@Composable
private fun BooleanRow(path: String, value: BooleanValue) {
    val color = MiniMeTokens.LocalColor.current
    val typography = MiniMeTokens.LocalTypography.current
    val spacing = MiniMeTokens.LocalSpacing.current
    val size = MiniMeTokens.LocalSize.current
    val shape = MiniMeTokens.LocalShape.current

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = spacing.s16.value.dp, vertical = spacing.s8.value.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = path.substringAfterLast('.'),
                style = typography.callout.toTextStyle(),
                color = color.textPrimaryColor,
            )
            Text(
                text = path,
                style = typography.caption2.toTextStyle().copy(fontFamily = "monospace".toFontFamily()),
                color = color.textSecondaryColor,
            )
        }
        // iOS 开关实样（尺寸走 size.switch 令牌）
        val w = size.switchWidth.value.dp
        val h = size.switchHeight.value.dp
        val on = value.value
        Box(
            modifier = Modifier
                .width(w)
                .height(h)
                .clip(ContinuousCornerShape(shape.radiusPill.value.dp))
                .background(if (on) color.accentColor else color.fillPrimaryColor),
        ) {
            Box(
                modifier = Modifier
                    .padding(2.dp)
                    .size(h - 4.dp)
                    .clip(ContinuousCornerShape(shape.radiusPill.value.dp))
                    .background(Color.White)
                    .then(
                        if (on) Modifier.padding(start = w - h) else Modifier
                    ),
            )
        }
        Spacer(Modifier.width(spacing.s12.value.dp))
        Text(
            text = if (on) "true" else "false",
            style = typography.monospace.toTextStyle().copy(fontFamily = "monospace".toFontFamily()),
            color = color.textPrimaryColor,
        )
    }
}

// ---------- elevation ----------

@Composable
private fun HairlineRow(path: String, value: DimensionValue) {
    val color = MiniMeTokens.LocalColor.current
    val typography = MiniMeTokens.LocalTypography.current
    val spacing = MiniMeTokens.LocalSpacing.current

    RowShell(path = path) {
        Box(
            modifier = Modifier
                .width(96.dp)
                .height(24.dp)
                .drawHairline(HairlineEdge.Bottom, color.separatorColor, value.px),
        ) {
            Text(
                text = "${value.value}${value.unit}",
                style = typography.caption2.toTextStyle().copy(fontFamily = "monospace".toFontFamily()),
                color = color.textSecondaryColor,
                modifier = Modifier.align(Alignment.CenterStart),
            )
        }
    }
}

@Composable
private fun ShadowRow(path: String, value: ShadowValue) {
    val color = MiniMeTokens.LocalColor.current
    val typography = MiniMeTokens.LocalTypography.current
    val spacing = MiniMeTokens.LocalSpacing.current
    val shape = MiniMeTokens.LocalShape.current

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = spacing.s16.value.dp, vertical = spacing.s8.value.dp),
    ) {
        RowShell(path = path) {
            Box(
                modifier = Modifier
                    .size(48.dp)
                    .padding(4.dp)
                    .clip(ContinuousCornerShape(shape.radiusCard.value.dp))
                    .background(color.surfaceColor),
            )
        }
        Text(
            text = buildString {
                append("blur ${value.blur?.value ?: "—"}${value.blur?.unit ?: ""}")
                append(" · offsetY ${value.offsetY?.value ?: "—"}${value.offsetY?.unit ?: ""}")
                append(" · color ${value.color.hex.removePrefix("#")}")
            },
            style = typography.caption2.toTextStyle().copy(fontFamily = "monospace".toFontFamily()),
            color = color.textSecondaryColor,
        )
    }
}

// ---------- opacity ----------

@Composable
private fun OpacityLadderRow() {
    val color = MiniMeTokens.LocalColor.current
    val typography = MiniMeTokens.LocalTypography.current
    val spacing = MiniMeTokens.LocalSpacing.current
    val shape = MiniMeTokens.LocalShape.current
    val opacity = MiniMeTokens.LocalOpacity.current

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = spacing.s16.value.dp, vertical = spacing.s8.value.dp),
    ) {
        Text(
            text = "primitive 九档阶梯（叠在画布色上的实样）",
            style = typography.callout.toTextStyle(),
            color = color.textPrimaryColor,
        )
        Spacer(Modifier.height(spacing.s8.value.dp))
        Row(horizontalArrangement = Arrangement.spacedBy(spacing.s8.value.dp)) {
            val ladder = listOf(
                "0" to opacity.l0, "04" to opacity.l04, "08" to opacity.l08,
                "16" to opacity.l16, "32" to opacity.l32, "50" to opacity.l50,
                "64" to opacity.l64, "80" to opacity.l80, "full" to opacity.full,
            )
            ladder.forEach { (name, token) ->
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Box(
                        modifier = Modifier
                            .size(28.dp)
                            .clip(ContinuousCornerShape(shape.radiusButton.value.dp))
                            .background(color.accentColor.copy(alpha = token.value)),
                    )
                    Spacer(Modifier.height(2.dp))
                    Text(
                        text = name,
                        style = typography.caption2.toTextStyle().copy(fontFamily = "monospace".toFontFamily()),
                        color = color.textSecondaryColor,
                    )
                }
            }
        }
    }
}

// ---------- glass ----------

@Composable
private fun GlassRow(path: String, value: GlassValue) {
    val color = MiniMeTokens.LocalColor.current
    val typography = MiniMeTokens.LocalTypography.current
    val spacing = MiniMeTokens.LocalSpacing.current
    val shape = MiniMeTokens.LocalShape.current

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = spacing.s16.value.dp, vertical = spacing.s8.value.dp),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            // 拟态近似实样（§6.8.3：tint + 饱和度 + 发丝线，零 GPU 成本；
            // 真 blur 与 glass.enabled 开关联动在 R9 落地）
            Box(
                modifier = Modifier
                    .size(56.dp)
                    .clip(ContinuousCornerShape(shape.radiusCard.value.dp))
                    .background(color.groupedColor)
                    .drawBehind {
                        drawRoundRect(
                            color = value.tint.toComposeColor().copy(alpha = value.tintAlpha),
                            cornerRadius = CornerRadius(shape.radiusCard.value.dp.toPx()),
                        )
                        if (value.hairline) {
                            drawRoundRect(
                                color = color.borderOpaque.toComposeColor(),
                                cornerRadius = CornerRadius(shape.radiusCard.value.dp.toPx()),
                                style = androidx.compose.ui.graphics.drawscope.Stroke(width = 1f),
                            )
                        }
                    },
            )
            Spacer(Modifier.width(spacing.s12.value.dp))
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = path.substringAfterLast('.'),
                    style = typography.callout.toTextStyle(),
                    color = color.textPrimaryColor,
                )
                Text(
                    text = path,
                    style = typography.caption2.toTextStyle().copy(fontFamily = "monospace".toFontFamily()),
                    color = color.textSecondaryColor,
                )
            }
        }
        Spacer(Modifier.height(spacing.s4.value.dp))
        Text(
            text = "blur ${value.blur.value}${value.blur.unit} · 饱和度 ${value.saturation.fmt()} · " +
                "tintAlpha ${value.tintAlpha.fmt()} · 发丝线 ${if (value.hairline) "有" else "无"}",
            style = typography.caption2.toTextStyle().copy(fontFamily = "monospace".toFontFamily()),
            color = color.textSecondaryColor,
        )
    }
}

// ---------- 工具 ----------

/** Float 紧凑格式（去尾零，最多两位） */
private fun Float.fmt(): String {
    val r = Math.round(this * 100f) / 100f
    return if (r == r.toInt().toFloat()) r.toInt().toString() else r.toString()
}

