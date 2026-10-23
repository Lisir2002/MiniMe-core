package org.minime.core.ui.gallery.pages

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.gestures.drag
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsFocusedAsState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.testTag
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import org.minime.core.schema.BooleanValue
import org.minime.core.schema.ColorValue
import org.minime.core.schema.DimensionValue
import org.minime.core.schema.DurationValue
import org.minime.core.schema.GlassValue
import org.minime.core.schema.NumberValue
import org.minime.core.schema.SpringValue
import org.minime.core.schema.StringValue
import org.minime.core.schema.TokenType
import org.minime.core.schema.TokenValue
import org.minime.core.schema.TypographyValue
import org.minime.core.ui.LocalThemeSnapshot
import org.minime.core.ui.MiniMeTokens
import org.minime.core.ui.draw.ContinuousCornerShape
import org.minime.core.ui.m3.toComposeColor
import org.minime.core.ui.m3.toFontFamily
import org.minime.core.ui.m3.toTextStyle
import org.minime.core.ui.runtime.ThemeManager
import org.minime.core.ui.gallery.accentColor
import org.minime.core.ui.gallery.fillPrimaryColor
import org.minime.core.ui.gallery.stateErrorColor
import org.minime.core.ui.gallery.stateSuccessColor
import org.minime.core.ui.gallery.stateWarningColor
import org.minime.core.ui.gallery.surfaceColor
import org.minime.core.ui.gallery.textPrimaryColor
import org.minime.core.ui.gallery.textSecondaryColor
import org.minime.core.ui.gallery.textTertiaryColor
import kotlin.math.roundToInt

/**
 * 工坊编辑器卡（charter §4「编辑器」：按类型分发）。
 *
 * 结构：令牌头（语义名 + 路径）→ **当前值 vs 主题包原值**（diff 高亮：被覆写时
 * 新值 accent + 原值删除线）→ 类型分发编辑器 → 「还原此条」（仅已覆写时出现）。
 *
 * 所有编辑动作 = `manager.applyOverrides(mapOf(path to 新值))`（单条合并语义；
 * R4.1 运行时的后台编译 / last-wins / 失败回滚不在本页重复实现）。
 */
/**
 * 编辑器内容（无框版——R5.5 手风琴内嵌）。
 *
 * 外框由调用方提供：树的手风琴展开态给「accent 竖线 + surfaceColor 底 + 左缩进」
 * （[WorkshopTree] 内），独立卡片场景可自套圆角底。令牌头 / diff 行 / 类型分发 /
 * 还原此条四段不变。
 */
@Composable
internal fun WorkshopEditorContent(
    path: String,
    manager: ThemeManager,
    overridable: Boolean,
    modifier: Modifier = Modifier,
) {
    val snapshot = LocalThemeSnapshot.current
    val base by manager.baseSnapshot.collectAsState()
    val overlays by manager.overlays.collectAsState()
    val color = MiniMeTokens.LocalColor.current
    val typography = MiniMeTokens.LocalTypography.current
    val spacing = MiniMeTokens.LocalSpacing.current
    val shape = MiniMeTokens.LocalShape.current

    val value = snapshot.raw[path]
    val original = base?.raw?.get(path)
    val overridden = path in overlays

    Column(
        modifier = modifier
            .fillMaxWidth()
            .background(color.surfaceColor)
            .padding(horizontal = spacing.s16.value.dp, vertical = spacing.s12.value.dp),
    ) {
        // ---- 令牌头 ----
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(
                text = WorkshopSpec.displayName(path),
                style = typography.headline.toTextStyle(),
                color = color.textPrimaryColor,
            )
            Spacer(Modifier.width(spacing.s8.value.dp))
            Text(
                text = WorkshopSpec.typeOf(path)?.name?.lowercase() ?: "—",
                style = typography.caption2.toTextStyle(),
                color = color.textSecondaryColor,
            )
        }
        Text(
            text = path,
            style = typography.caption2.toTextStyle().copy(fontFamily = "monospace".toFontFamily()),
            color = color.textSecondaryColor,
        )

        Spacer(Modifier.height(spacing.s12.value.dp))

        // ---- diff 行：当前值 vs 原值 ----
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(
                text = "当前值",
                style = typography.caption1.toTextStyle(),
                color = color.textSecondaryColor,
                modifier = Modifier.width(56.dp),
            )
            Text(
                text = value?.let(::editorValueText) ?: "—",
                style = typography.monospace.toTextStyle().copy(fontFamily = "monospace".toFontFamily()),
                color = if (overridden) color.accentColor else color.textPrimaryColor,
            )
            if (overridden && original != null) {
                Spacer(Modifier.width(spacing.s12.value.dp))
                Text(
                    text = "原值 ${editorValueText(original)}",
                    style = typography.caption1.toTextStyle().copy(fontFamily = "monospace".toFontFamily()),
                    color = color.textSecondaryColor,
                    textDecoration = TextDecoration.LineThrough,
                )
            }
        }

        Spacer(Modifier.height(spacing.s12.value.dp))

        // ---- 类型分发编辑器 ----
        val type = WorkshopSpec.typeOf(path)
        val apply: (TokenValue) -> Unit = { manager.applyOverrides(mapOf(path to it)) }
        Box(modifier = Modifier.alpha(if (overridable) 1f else 0.4f)) {
            when {
                value == null -> Text(
                    text = "快照缺少该令牌（内部缺陷）",
                    style = typography.callout.toTextStyle(),
                    color = color.textSecondaryColor,
                )

                type == TokenType.COLOR ->
                    ColorEditor(value as ColorValue, overridable, apply)

                type == TokenType.DIMENSION ->
                    DimensionEditor(path, value as DimensionValue, overridable, apply)

                type == TokenType.DURATION ->
                    DurationEditor(path, value as DurationValue, overridable, apply)

                type == TokenType.NUMBER ->
                    NumberEditor(path, value as NumberValue, overridable, apply)

                type == TokenType.BOOLEAN ->
                    BooleanEditor(value as BooleanValue, overridable, apply)

                type == TokenType.SPRING ->
                    SpringEditor(value as SpringValue, overridable, apply)

                type == TokenType.GLASS ->
                    GlassEditor(value as GlassValue, overridable, apply)

                type == TokenType.TYPOGRAPHY ->
                    TypographyEditor(value as TypographyValue, overridable, apply)

                type == TokenType.FONT_FAMILY ->
                    FontFamilyEditor(value as StringValue, overridable, apply)

                else -> Text(
                    text = "该类型暂无可视编辑器（${value::class.simpleName}）",
                    style = typography.callout.toTextStyle(),
                    color = color.textSecondaryColor,
                )
            }
        }

        // ---- 还原此条 ----
        if (overridden) {
            Spacer(Modifier.height(spacing.s12.value.dp))
            Text(
                text = "还原此条",
                style = typography.callout.toTextStyle(),
                color = color.accentColor,
                modifier = Modifier
                    .clip(ContinuousCornerShape(shape.radiusButton.value.dp))
                    .clickable { manager.resetOverrides(listOf(path)) }
                    .padding(horizontal = spacing.s12.value.dp, vertical = spacing.s8.value.dp),
            )
        }
    }
}

/** 编辑器内的完整值文本（diff 行与读数共用） */
private fun editorValueText(value: TokenValue): String = when (value) {
    is ColorValue -> "#${value.hex.removePrefix("#")}"
    is DimensionValue -> "${value.value.wsFmt()}${value.unit}"
    is DurationValue -> "${value.ms.wsFmt()}ms"
    is NumberValue -> value.value.wsFmt()
    is BooleanValue -> if (value.value) "true" else "false"
    is StringValue -> value.value
    is SpringValue -> "k ${value.stiffness.wsFmt()} · c ${value.damping.wsFmt()} · v0 ${value.initialVelocity.wsFmt()}"
    is GlassValue -> "blur ${value.blur.value.wsFmt()}${value.blur.unit} · sat ${value.saturation.wsFmt()} · " +
        "tint ${value.tint.hex.removePrefix("#")} · α ${value.tintAlpha.wsFmt()} · 发丝线 ${if (value.hairline) "有" else "无"}"
    is TypographyValue -> buildString {
        append("${value.fontSize.value.wsFmt()}${value.fontSize.unit}")
        value.lineHeight?.let { append(" · 行高 ${it.value.wsFmt()}${it.unit}") }
        value.fontWeight?.let { append(" · 字重 $it") }
        value.letterSpacing?.let { append(" · 字距 ${it.value.wsFmt()}${it.unit}") }
        value.fontFamily?.let { append(" · $it") }
    }

    else -> value.toString()
}

// ---------- 色值（hex） ----------

@Composable
private fun ColorEditor(value: ColorValue, enabled: Boolean, onApply: (TokenValue) -> Unit) {
    val color = MiniMeTokens.LocalColor.current
    val typography = MiniMeTokens.LocalTypography.current
    val spacing = MiniMeTokens.LocalSpacing.current
    val shape = MiniMeTokens.LocalShape.current

    Column {
        Row(verticalAlignment = Alignment.CenterVertically) {
            // 色板实样（squircle 裁切；半透明加描边才可见）
            Box(
                modifier = Modifier
                    .size(44.dp)
                    .clip(ContinuousCornerShape(shape.radiusButton.value.dp))
                    .background(value.toComposeColorSafe())
                    .then(
                        if (value.alpha < 1f) {
                            Modifier.border(1.dp, color.textSecondaryColor, ContinuousCornerShape(shape.radiusButton.value.dp))
                        } else {
                            Modifier
                        },
                    ),
            )
            Spacer(Modifier.width(spacing.s12.value.dp))
            WorkshopHexField(
                hex = value.hex,
                enabled = enabled,
                onCommit = { onApply(it) },
                modifier = Modifier.weight(1f),
            )
        }
        Spacer(Modifier.height(spacing.s4.value.dp))
        Text(
            text = "支持 #RGB / #RRGGBB / #RRGGBBAA（末两位 = alpha）",
            style = typography.caption2.toTextStyle(),
            color = color.textSecondaryColor,
        )
    }
}

/** Compose Color 转换（复用 M3 映射层同款，禁第二套） */
private fun ColorValue.toComposeColorSafe(): androidx.compose.ui.graphics.Color =
    this.toComposeColor()

// ---------- dimension / duration（滑杆） ----------

@Composable
private fun DimensionEditor(path: String, value: DimensionValue, enabled: Boolean, onApply: (TokenValue) -> Unit) {
    val range = WorkshopSpec.sliderRange(path) ?: 0f..100f
    val step = WorkshopSpec.sliderStep(path)
    EditorSliderRow(
        label = "取值（${value.unit}）",
        readout = "${value.value.wsFmt()}${value.unit}",
        value = value.value,
        range = range,
        step = step,
        enabled = enabled,
        onChange = { onApply(DimensionValue(it, value.unit)) },
    )
}

@Composable
private fun DurationEditor(path: String, value: DurationValue, enabled: Boolean, onApply: (TokenValue) -> Unit) {
    EditorSliderRow(
        label = "时长（ms）",
        readout = "${value.ms.wsFmt()}ms",
        value = value.ms,
        range = 0f..1500f,
        step = 10f,
        enabled = enabled,
        onChange = { onApply(DurationValue(it)) },
    )
}

// ---------- number（步进） ----------

@Composable
private fun NumberEditor(path: String, value: NumberValue, enabled: Boolean, onApply: (TokenValue) -> Unit) {
    val range = WorkshopSpec.numberRange(path) ?: 0f..1f
    EditorStepperRow(
        label = "取值",
        readout = value.value.wsFmt(),
        value = value.value,
        range = range,
        step = WorkshopSpec.numberStep(path),
        enabled = enabled,
        onChange = { onApply(NumberValue(it)) },
    )
}

// ---------- boolean（开关） ----------

@Composable
private fun BooleanEditor(value: BooleanValue, enabled: Boolean, onApply: (TokenValue) -> Unit) {
    val color = MiniMeTokens.LocalColor.current
    val typography = MiniMeTokens.LocalTypography.current

    Row(verticalAlignment = Alignment.CenterVertically) {
        Text(
            text = if (value.value) "true" else "false",
            style = typography.monospace.toTextStyle().copy(fontFamily = "monospace".toFontFamily()),
            color = color.textPrimaryColor,
        )
        Spacer(Modifier.width(MiniMeTokens.LocalSpacing.current.s12.value.dp))
        WorkshopSwitch(
            checked = value.value,
            enabled = enabled,
            onCheckedChange = { onApply(BooleanValue(it)) },
        )
    }
}

// ---------- spring（三滑杆） ----------

@Composable
private fun SpringEditor(value: SpringValue, enabled: Boolean, onApply: (TokenValue) -> Unit) {
    Column {
        EditorSliderRow(
            label = "stiffness 刚度",
            readout = value.stiffness.wsFmt(),
            value = value.stiffness,
            range = WorkshopSpec.fieldRange(TokenType.SPRING, "stiffness")!!,
            step = WorkshopSpec.fieldStep(TokenType.SPRING, "stiffness"),
            enabled = enabled,
            onChange = { onApply(value.copy(stiffness = it)) },
        )
        EditorSliderRow(
            label = "damping 阻尼比",
            readout = value.damping.wsFmt(),
            value = value.damping,
            range = WorkshopSpec.fieldRange(TokenType.SPRING, "damping")!!,
            step = WorkshopSpec.fieldStep(TokenType.SPRING, "damping"),
            enabled = enabled,
            onChange = { onApply(value.copy(damping = it)) },
        )
        EditorSliderRow(
            label = "initialVelocity 初速",
            readout = value.initialVelocity.wsFmt(),
            value = value.initialVelocity,
            range = WorkshopSpec.fieldRange(TokenType.SPRING, "initialVelocity")!!,
            step = WorkshopSpec.fieldStep(TokenType.SPRING, "initialVelocity"),
            enabled = enabled,
            onChange = { onApply(value.copy(initialVelocity = it)) },
        )
        Text(
            text = "damping ≥ 1 无回弹（临界/过阻尼）；v0 单位 = 进度/秒",
            style = MiniMeTokens.LocalTypography.current.caption2.toTextStyle(),
            color = MiniMeTokens.LocalColor.current.textSecondaryColor,
        )
    }
}

// ---------- glass（逐字段） ----------

@Composable
private fun GlassEditor(value: GlassValue, enabled: Boolean, onApply: (TokenValue) -> Unit) {
    Column {
        EditorSliderRow(
            label = "blur 模糊",
            readout = "${value.blur.value.wsFmt()}${value.blur.unit}",
            value = value.blur.value,
            range = WorkshopSpec.fieldRange(TokenType.GLASS, "blur")!!,
            step = WorkshopSpec.fieldStep(TokenType.GLASS, "blur"),
            enabled = enabled,
            onChange = { onApply(value.copy(blur = DimensionValue(it, value.blur.unit))) },
        )
        EditorSliderRow(
            label = "saturation 饱和度",
            readout = value.saturation.wsFmt(),
            value = value.saturation,
            range = WorkshopSpec.fieldRange(TokenType.GLASS, "saturation")!!,
            step = WorkshopSpec.fieldStep(TokenType.GLASS, "saturation"),
            enabled = enabled,
            onChange = { onApply(value.copy(saturation = it)) },
        )
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(
                text = "tint 着色",
                style = MiniMeTokens.LocalTypography.current.caption1.toTextStyle(),
                color = MiniMeTokens.LocalColor.current.textSecondaryColor,
                modifier = Modifier.width(96.dp),
            )
            WorkshopHexField(
                hex = value.tint.hex,
                enabled = enabled,
                onCommit = { onApply(value.copy(tint = it)) },
                modifier = Modifier.weight(1f),
            )
        }
        EditorSliderRow(
            label = "tintAlpha 着色深度",
            readout = value.tintAlpha.wsFmt(),
            value = value.tintAlpha,
            range = WorkshopSpec.fieldRange(TokenType.GLASS, "tintAlpha")!!,
            step = WorkshopSpec.fieldStep(TokenType.GLASS, "tintAlpha"),
            enabled = enabled,
            onChange = { onApply(value.copy(tintAlpha = it)) },
        )
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(
                text = "hairline 发丝线",
                style = MiniMeTokens.LocalTypography.current.caption1.toTextStyle(),
                color = MiniMeTokens.LocalColor.current.textSecondaryColor,
                modifier = Modifier.width(96.dp),
            )
            WorkshopSwitch(
                checked = value.hairline,
                enabled = enabled,
                onCheckedChange = { onApply(value.copy(hairline = it)) },
            )
        }
    }
}

// ---------- typography（逐字段；可空字段带「置空」） ----------

@Composable
private fun TypographyEditor(value: TypographyValue, enabled: Boolean, onApply: (TokenValue) -> Unit) {
    val typography = MiniMeTokens.LocalTypography.current
    val color = MiniMeTokens.LocalColor.current

    Column {
        EditorSliderRow(
            label = "fontSize 字号",
            readout = "${value.fontSize.value.wsFmt()}${value.fontSize.unit}",
            value = value.fontSize.value,
            range = WorkshopSpec.fieldRange(TokenType.TYPOGRAPHY, "fontSize")!!,
            step = WorkshopSpec.fieldStep(TokenType.TYPOGRAPHY, "fontSize"),
            enabled = enabled,
            onChange = { onApply(value.copy(fontSize = DimensionValue(it, value.fontSize.unit))) },
        )
        NullableSliderRow(
            label = "lineHeight 行高",
            readout = value.lineHeight?.let { "${it.value.wsFmt()}${it.unit}" } ?: "无",
            current = value.lineHeight?.value,
            range = WorkshopSpec.fieldRange(TokenType.TYPOGRAPHY, "lineHeight")!!,
            step = WorkshopSpec.fieldStep(TokenType.TYPOGRAPHY, "lineHeight"),
            enabled = enabled,
            onChange = { onApply(value.copy(lineHeight = DimensionValue(it, "sp"))) },
            onNull = { onApply(value.copy(lineHeight = null)) },
        )
        NullableSegmentRow(
            label = "fontWeight 字重",
            options = listOf("400", "500", "600", "700"),
            selected = value.fontWeight,
            enabled = enabled,
            onSelect = { onApply(value.copy(fontWeight = it)) },
            onNull = { onApply(value.copy(fontWeight = null)) },
        )
        NullableSliderRow(
            label = "letterSpacing 字距",
            readout = value.letterSpacing?.let { "${it.value.wsFmt()}${it.unit}" } ?: "无",
            current = value.letterSpacing?.value,
            range = WorkshopSpec.fieldRange(TokenType.TYPOGRAPHY, "letterSpacing")!!,
            step = WorkshopSpec.fieldStep(TokenType.TYPOGRAPHY, "letterSpacing"),
            enabled = enabled,
            onChange = { onApply(value.copy(letterSpacing = DimensionValue(it, "sp"))) },
            onNull = { onApply(value.copy(letterSpacing = null)) },
        )
        NullableSegmentRow(
            label = "fontFamily 字体族",
            options = listOf("sans-serif", "monospace"),
            selected = value.fontFamily,
            enabled = enabled,
            onSelect = { onApply(value.copy(fontFamily = it)) },
            onNull = { onApply(value.copy(fontFamily = null)) },
        )
        Text(
            text = "字阶实样见左侧实时预览；置空 = 回到系统默认（该字阶原初可能即无此字段）",
            style = typography.caption2.toTextStyle(),
            color = color.textSecondaryColor,
        )
    }
}

@Composable
private fun FontFamilyEditor(value: StringValue, enabled: Boolean, onApply: (TokenValue) -> Unit) {
    WorkshopSegmented(
        options = listOf("sans-serif", "monospace"),
        selectedIndex = listOf("sans-serif", "monospace").indexOf(value.value).takeIf { it >= 0 },
        enabled = enabled,
        onSelect = { onApply(StringValue(listOf("sans-serif", "monospace")[it])) },
    )
}

// ---------- 自绘控件（滑杆 / 步进 / 开关 / hex / 分段；组件族随 R6 回收） ----------

/** 滑杆行：标签 + 读数 + 滑杆（44dp 触达，6dp 轨道，24dp 圆点） */
@Composable
private fun EditorSliderRow(
    label: String,
    readout: String,
    value: Float,
    range: ClosedFloatingPointRange<Float>,
    step: Float,
    enabled: Boolean,
    onChange: (Float) -> Unit,
) {
    val color = MiniMeTokens.LocalColor.current
    val typography = MiniMeTokens.LocalTypography.current
    val spacing = MiniMeTokens.LocalSpacing.current

    Column(modifier = Modifier.padding(vertical = spacing.s4.value.dp).semantics { testTag = "workshop-editor-$label" }) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(
                text = label,
                style = typography.caption1.toTextStyle(),
                color = color.textSecondaryColor,
                modifier = Modifier.weight(1f),
            )
            Text(
                text = readout,
                style = typography.monospace.toTextStyle().copy(fontFamily = "monospace".toFontFamily()),
                color = color.textPrimaryColor,
            )
        }
        WorkshopSlider(
            value = value,
            range = range,
            step = step,
            enabled = enabled,
            onChange = onChange,
        )
    }
}

/** 可空滑杆行：滑杆 + 「置空」钮（当前为空时滑杆禁用并显示「无」） */
@Composable
private fun NullableSliderRow(
    label: String,
    readout: String,
    current: Float?,
    range: ClosedFloatingPointRange<Float>,
    step: Float,
    enabled: Boolean,
    onChange: (Float) -> Unit,
    onNull: () -> Unit,
) {
    val color = MiniMeTokens.LocalColor.current
    val typography = MiniMeTokens.LocalTypography.current
    val spacing = MiniMeTokens.LocalSpacing.current

    Column(modifier = Modifier.padding(vertical = spacing.s4.value.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(
                text = label,
                style = typography.caption1.toTextStyle(),
                color = color.textSecondaryColor,
                modifier = Modifier.weight(1f),
            )
            Text(
                text = readout,
                style = typography.monospace.toTextStyle().copy(fontFamily = "monospace".toFontFamily()),
                color = color.textPrimaryColor,
            )
            Spacer(Modifier.width(spacing.s8.value.dp))
            Text(
                text = "置空",
                style = typography.caption1.toTextStyle(),
                color = if (current != null && enabled) color.accentColor else color.textSecondaryColor,
                modifier = Modifier
                    .clickable(enabled = current != null && enabled, onClick = onNull)
                    .padding(4.dp),
            )
        }
        WorkshopSlider(
            value = current ?: range.start,
            range = range,
            step = step,
            enabled = enabled && current != null,
            onChange = onChange,
        )
    }
}

/** 步进行：标签 + 读数 + 「− 值 ＋」 */
@Composable
private fun EditorStepperRow(
    label: String,
    readout: String,
    value: Float,
    range: ClosedFloatingPointRange<Float>,
    step: Float,
    enabled: Boolean,
    onChange: (Float) -> Unit,
) {
    val color = MiniMeTokens.LocalColor.current
    val typography = MiniMeTokens.LocalTypography.current
    val spacing = MiniMeTokens.LocalSpacing.current
    val shape = MiniMeTokens.LocalShape.current

    Row(
        modifier = Modifier.fillMaxWidth().padding(vertical = spacing.s8.value.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            text = label,
            style = typography.caption1.toTextStyle(),
            color = color.textSecondaryColor,
            modifier = Modifier.weight(1f),
        )
        StepperButton(
            text = "−",
            enabled = enabled && value > range.start,
            onClick = { onChange((value - step).coerceAtLeast(range.start)) },
        )
        Text(
            text = readout,
            style = typography.monospace.toTextStyle().copy(fontFamily = "monospace".toFontFamily()),
            color = color.textPrimaryColor,
            modifier = Modifier.padding(horizontal = spacing.s12.value.dp),
        )
        StepperButton(
            text = "＋",
            enabled = enabled && value < range.endInclusive,
            onClick = { onChange((value + step).coerceAtMost(range.endInclusive)) },
        )
    }
}

@Composable
private fun StepperButton(text: String, enabled: Boolean, onClick: () -> Unit) {
    val color = MiniMeTokens.LocalColor.current
    val typography = MiniMeTokens.LocalTypography.current
    val shape = MiniMeTokens.LocalShape.current

    Box(
        modifier = Modifier
            .size(36.dp)
            .clip(CircleShape)
            .background(if (enabled) color.fillPrimaryColor else androidx.compose.ui.graphics.Color.Transparent)
            .clickable(enabled = enabled, onClick = onClick)
            .alpha(if (enabled) 1f else 0.4f),
        contentAlignment = Alignment.Center,
    ) {
        Text(
            text = text,
            style = typography.headline.toTextStyle(),
            color = color.textPrimaryColor,
        )
    }
}

@Composable
private fun WorkshopSwitch(checked: Boolean, enabled: Boolean, onCheckedChange: (Boolean) -> Unit) {
    val color = MiniMeTokens.LocalColor.current
    val size = MiniMeTokens.LocalSize.current
    val shape = MiniMeTokens.LocalShape.current
    val w = size.switchWidth.value.dp
    val h = size.switchHeight.value.dp

    Box(
        modifier = Modifier
            .width(w)
            .height(h)
            .clip(ContinuousCornerShape(shape.radiusPill.value.dp))
            .background(if (checked) color.accentColor else color.fillPrimaryColor)
            .clickable(enabled = enabled) { onCheckedChange(!checked) },
    ) {
        Box(
            modifier = Modifier
                .padding(2.dp)
                .size(h - 4.dp)
                .clip(ContinuousCornerShape(shape.radiusPill.value.dp))
                .background(androidx.compose.ui.graphics.Color.White)
                .then(if (checked) Modifier.padding(start = w - h) else Modifier),
        )
    }
}

/** hex 输入（#RRGGBBAA；实时提交合法值，非法红边不提交——行内校验） */
@Composable
private fun WorkshopHexField(hex: String, enabled: Boolean, onCommit: (ColorValue) -> Unit, modifier: Modifier = Modifier) {
    val color = MiniMeTokens.LocalColor.current
    val typography = MiniMeTokens.LocalTypography.current
    val spacing = MiniMeTokens.LocalSpacing.current
    val shape = MiniMeTokens.LocalShape.current
    val interaction = remember { MutableInteractionSource() }
    val focused by interaction.collectIsFocusedAsState()
    var text by remember(hex) { mutableStateOf(hex.removePrefix("#")) }
    var invalid by remember(hex) { mutableStateOf(false) }

    Row(
        modifier = modifier
            .height(40.dp)
            .clip(ContinuousCornerShape(shape.radiusButton.value.dp))
            .background(color.fillPrimaryColor)
            .border(
                width = 1.dp,
                color = when {
                    invalid -> color.stateErrorColor
                    focused -> color.accentColor
                    else -> androidx.compose.ui.graphics.Color.Transparent
                },
                shape = ContinuousCornerShape(shape.radiusButton.value.dp),
            )
            .padding(horizontal = spacing.s12.value.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            text = "#",
            style = typography.monospace.toTextStyle().copy(fontFamily = "monospace".toFontFamily()),
            color = color.textSecondaryColor,
        )
        BasicTextField(
            value = text,
            onValueChange = { raw ->
                val filtered = raw.filter { it in '0'..'9' || it in 'a'..'f' || it in 'A'..'F' }.take(8)
                text = filtered
                val parsed = runCatching { ColorValue.fromHex("#$filtered") }.getOrNull()
                invalid = parsed == null
                parsed?.let(onCommit)
            },
            enabled = enabled,
            singleLine = true,
            textStyle = typography.monospace.toTextStyle()
                .copy(fontFamily = "monospace".toFontFamily(), color = color.textPrimaryColor),
            cursorBrush = SolidColor(color.accentColor),
            keyboardOptions = KeyboardOptions(imeAction = ImeAction.Done),
            keyboardActions = KeyboardActions(),
            interactionSource = interaction,
            modifier = Modifier.weight(1f).padding(start = spacing.s4.value.dp),
        )
    }
}

/** 分段选择（fontWeight / fontFamily 用） */
@Composable
private fun WorkshopSegmented(
    options: List<String>,
    selectedIndex: Int?,
    enabled: Boolean,
    onSelect: (Int) -> Unit,
) {
    val color = MiniMeTokens.LocalColor.current
    val typography = MiniMeTokens.LocalTypography.current
    val spacing = MiniMeTokens.LocalSpacing.current

    Row(horizontalArrangement = Arrangement.spacedBy(spacing.s4.value.dp)) {
        options.forEachIndexed { index, option ->
            val selected = index == selectedIndex
            Box(
                modifier = Modifier
                    .clip(androidx.compose.foundation.shape.RoundedCornerShape(percent = 50))
                    .background(if (selected) color.accentColor else color.fillPrimaryColor)
                    .clickable(enabled = enabled) { onSelect(index) }
                    .padding(horizontal = spacing.s16.value.dp, vertical = spacing.s8.value.dp),
            ) {
                Text(
                    text = option,
                    style = typography.caption1.toTextStyle(),
                    color = if (selected) color.surfaceColor else color.textPrimaryColor,
                )
            }
        }
    }
}

/** 可空分段行：分段 + 「置空」钮 */
@Composable
private fun NullableSegmentRow(
    label: String,
    options: List<String>,
    selected: String?,
    enabled: Boolean,
    onSelect: (String) -> Unit,
    onNull: () -> Unit,
) {
    val color = MiniMeTokens.LocalColor.current
    val typography = MiniMeTokens.LocalTypography.current
    val spacing = MiniMeTokens.LocalSpacing.current

    Row(
        modifier = Modifier.fillMaxWidth().padding(vertical = spacing.s4.value.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            text = label,
            style = typography.caption1.toTextStyle(),
            color = color.textSecondaryColor,
            modifier = Modifier.weight(1f),
        )
        WorkshopSegmented(
            options = options,
            selectedIndex = selected?.let { s -> options.indexOf(s).takeIf { it >= 0 } },
            enabled = enabled,
            onSelect = { onSelect(options[it]) },
        )
        Spacer(Modifier.width(spacing.s8.value.dp))
        Text(
            text = "置空",
            style = typography.caption1.toTextStyle(),
            color = if (selected != null && enabled) color.accentColor else color.textSecondaryColor,
            modifier = Modifier
                .clickable(enabled = selected != null && enabled, onClick = onNull)
                .padding(4.dp),
        )
    }
}

// ---------- 滑杆本体 ----------

@Composable
private fun WorkshopSlider(
    value: Float,
    range: ClosedFloatingPointRange<Float>,
    step: Float,
    enabled: Boolean,
    onChange: (Float) -> Unit,
) {
    val color = MiniMeTokens.LocalColor.current
    val shape = MiniMeTokens.LocalShape.current
    val density = LocalDensity.current
    val thumbSize = 24.dp
    val thumbPx = with(density) { thumbSize.toPx() }
    var trackWidth by remember { mutableFloatStateOf(0f) }

    val fraction = ((value - range.start) / (range.endInclusive - range.start)).coerceIn(0f, 1f)

    Box(
        modifier = Modifier
            .fillMaxWidth()
            .height(44.dp) // size.touchTarget 触达下限
            .alpha(if (enabled) 1f else 0.4f)
            .onSizeChanged { trackWidth = it.width.toFloat() }
            .semantics { testTag = "workshop-slider" }
            .pointerInput(enabled, range, step) {
                if (!enabled) return@pointerInput
                awaitEachGesture {
                    val down = awaitFirstDown(requireUnconsumed = false)
                    fun emit(x: Float) {
                        val t = (x / size.width.toFloat()).coerceIn(0f, 1f)
                        val raw = range.start + t * (range.endInclusive - range.start)
                        val snapped = (raw / step).roundToInt() * step
                        onChange(snapped.coerceIn(range.start, range.endInclusive))
                    }
                    emit(down.position.x)
                    drag(down.id) { change ->
                        emit(change.position.x)
                    }
                }
            },
        contentAlignment = Alignment.CenterStart,
    ) {
        // 轨道（底 + 填充）
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .height(6.dp)
                .clip(ContinuousCornerShape(shape.radiusPill.value.dp))
                .background(color.fillPrimaryColor),
        )
        Box(
            modifier = Modifier
                .fillMaxWidth(fraction)
                .height(6.dp)
                .clip(ContinuousCornerShape(shape.radiusPill.value.dp))
                .background(color.accentColor),
        )
        // 圆点
        Box(
            modifier = Modifier
                .offset { IntOffset(((trackWidth - thumbPx) * fraction).roundToInt(), 0) }
                .size(thumbSize)
                .clip(CircleShape)
                .background(androidx.compose.ui.graphics.Color.White)
                .border(2.dp, color.accentColor, CircleShape),
        )
    }
}

