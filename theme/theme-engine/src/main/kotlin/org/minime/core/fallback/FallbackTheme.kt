package org.minime.core.fallback

import org.minime.core.api.Immutable
import org.minime.core.schema.BooleanValue
import org.minime.core.schema.ColorValue
import org.minime.core.schema.DimensionValue
import org.minime.core.schema.DurationValue
import org.minime.core.schema.GlassValue
import org.minime.core.schema.NumberValue
import org.minime.core.schema.ShadowValue
import org.minime.core.schema.SpringValue
import org.minime.core.schema.StringValue
import org.minime.core.schema.TokenValue
import org.minime.core.schema.TypographyValue
import org.minime.core.tokens.FALLBACK_META
import org.minime.core.tokens.SnapshotBuilder
import org.minime.core.tokens.ThemeSnapshot

/**
 * 内置 fallback 主题（G1/R11：包不存在 / 包损坏 / 编译异常时的降级真源）。
 *
 * 数据来源：`default-theme.json` 的 core set（light 上下文）+ 扩展组，
 * 经别名解析后固化为常量——**与默认主题包 light 上下文逐值一致**
 * （由 `FallbackConsistencyTest` 逐一比对守卫，防漂移）。
 *
 * 优先级链（R4 起完整形态）：fallback 基座 < 编译快照 < 运行时覆写。
 * 本表是基座：任何被接受的包（含最小包）都在其上层叠加，词汇表缺项由此回填。
 */
object FallbackTheme {

    /** 全量 fallback 令牌表（172 标准词汇 + 24 扩展路径，共 196 条） */
    val rawValues: Map<String, TokenValue> = linkedMapOf(
        // ---------- color ----------
        "color.primitive.label" to ColorValue("#000000FF"),
        "color.primitive.opaqueSeparator" to ColorValue("#C6C6C8FF"),
        "color.primitive.placeholderText" to ColorValue("#3C3C434C"),
        "color.primitive.quaternaryLabel" to ColorValue("#3C3C432D"),
        "color.primitive.secondaryLabel" to ColorValue("#3C3C4399"),
        "color.primitive.secondarySystemBackground" to ColorValue("#F2F2F7FF"),
        "color.primitive.secondarySystemGroupedBackground" to ColorValue("#FFFFFFFF"),
        "color.primitive.separator" to ColorValue("#3C3C4349"),
        "color.primitive.systemBackground" to ColorValue("#FFFFFFFF"),
        "color.primitive.systemBlue" to ColorValue("#007AFFFF"),
        "color.primitive.systemFill" to ColorValue("#78788033"),
        "color.primitive.systemFillQuaternary" to ColorValue("#74748014"),
        "color.primitive.systemFillSecondary" to ColorValue("#78788028"),
        "color.primitive.systemFillTertiary" to ColorValue("#7676801E"),
        "color.primitive.systemGray" to ColorValue("#8E8E93FF"),
        "color.primitive.systemGray2" to ColorValue("#AEAEB2FF"),
        "color.primitive.systemGray3" to ColorValue("#C7C7CCFF"),
        "color.primitive.systemGray4" to ColorValue("#D1D1D6FF"),
        "color.primitive.systemGray5" to ColorValue("#E5E5EAFF"),
        "color.primitive.systemGray6" to ColorValue("#F2F2F7FF"),
        "color.primitive.systemGreen" to ColorValue("#34C759FF"),
        "color.primitive.systemGroupedBackground" to ColorValue("#F2F2F7FF"),
        "color.primitive.systemIndigo" to ColorValue("#5856D6FF"),
        "color.primitive.systemOrange" to ColorValue("#FF9500FF"),
        "color.primitive.systemPink" to ColorValue("#FF2D55FF"),
        "color.primitive.systemPurple" to ColorValue("#AF52DEFF"),
        "color.primitive.systemRed" to ColorValue("#FF3B30FF"),
        "color.primitive.systemTeal" to ColorValue("#5AC8FAFF"),
        "color.primitive.systemYellow" to ColorValue("#FFCC00FF"),
        "color.primitive.tertiaryLabel" to ColorValue("#3C3C434C"),
        "color.primitive.tertiarySystemBackground" to ColorValue("#FFFFFFFF"),
        "color.semantic.accent" to ColorValue("#007AFFFF"),
        "color.semantic.bg.canvas" to ColorValue("#FFFFFFFF"),
        "color.semantic.bg.grouped" to ColorValue("#F2F2F7FF"),
        "color.semantic.bg.surface" to ColorValue("#FFFFFFFF"),
        "color.semantic.bg.surfaceSecondary" to ColorValue("#F2F2F7FF"),
        "color.semantic.bg.surfaceTertiary" to ColorValue("#FFFFFFFF"),
        "color.semantic.border.opaque" to ColorValue("#C6C6C8FF"),
        "color.semantic.border.separator" to ColorValue("#3C3C4349"),
        "color.semantic.fill.primary" to ColorValue("#78788033"),
        "color.semantic.fill.quaternary" to ColorValue("#74748014"),
        "color.semantic.fill.secondary" to ColorValue("#78788028"),
        "color.semantic.fill.tertiary" to ColorValue("#7676801E"),
        "color.semantic.gray1" to ColorValue("#8E8E93FF"),
        "color.semantic.gray2" to ColorValue("#AEAEB2FF"),
        "color.semantic.gray3" to ColorValue("#C7C7CCFF"),
        "color.semantic.gray4" to ColorValue("#D1D1D6FF"),
        "color.semantic.gray5" to ColorValue("#E5E5EAFF"),
        "color.semantic.gray6" to ColorValue("#F2F2F7FF"),
        "color.semantic.indigo" to ColorValue("#5856D6FF"),
        "color.semantic.pink" to ColorValue("#FF2D55FF"),
        "color.semantic.purple" to ColorValue("#AF52DEFF"),
        "color.semantic.state.error" to ColorValue("#FF3B30FF"),
        "color.semantic.state.success" to ColorValue("#34C759FF"),
        "color.semantic.state.warning" to ColorValue("#FF9500FF"),
        "color.semantic.teal" to ColorValue("#5AC8FAFF"),
        "color.semantic.text.placeholder" to ColorValue("#3C3C434C"),
        "color.semantic.text.primary" to ColorValue("#000000FF"),
        "color.semantic.text.quaternary" to ColorValue("#3C3C432D"),
        "color.semantic.text.secondary" to ColorValue("#3C3C4399"),
        "color.semantic.text.tertiary" to ColorValue("#3C3C434C"),
        "color.semantic.yellow" to ColorValue("#FFCC00FF"),
        // ---------- typography ----------
        "typography.body" to TypographyValue(fontSize = DimensionValue(17f, "sp"), lineHeight = DimensionValue(22f, "sp"), fontWeight = "400", fontFamily = StringValue("sans-serif").value),
        "typography.callout" to TypographyValue(fontSize = DimensionValue(16f, "sp"), lineHeight = DimensionValue(21f, "sp"), fontWeight = "400", fontFamily = StringValue("sans-serif").value),
        "typography.caption1" to TypographyValue(fontSize = DimensionValue(12f, "sp"), lineHeight = DimensionValue(16f, "sp"), fontWeight = "400", fontFamily = StringValue("sans-serif").value),
        "typography.caption2" to TypographyValue(fontSize = DimensionValue(11f, "sp"), lineHeight = DimensionValue(13f, "sp"), fontWeight = "400", fontFamily = StringValue("sans-serif").value),
        "typography.fontFamily.mono" to StringValue("monospace"),
        "typography.fontFamily.sans" to StringValue("sans-serif"),
        "typography.footnote" to TypographyValue(fontSize = DimensionValue(13f, "sp"), lineHeight = DimensionValue(18f, "sp"), fontWeight = "400", fontFamily = StringValue("sans-serif").value),
        "typography.headline" to TypographyValue(fontSize = DimensionValue(17f, "sp"), lineHeight = DimensionValue(22f, "sp"), fontWeight = "600", fontFamily = StringValue("sans-serif").value),
        "typography.largeTitle" to TypographyValue(fontSize = DimensionValue(34f, "sp"), lineHeight = DimensionValue(41f, "sp"), fontWeight = "700", fontFamily = StringValue("sans-serif").value),
        "typography.monospace" to TypographyValue(fontSize = DimensionValue(13f, "sp"), lineHeight = DimensionValue(18f, "sp"), fontWeight = "400", fontFamily = StringValue("monospace").value),
        "typography.subheadline" to TypographyValue(fontSize = DimensionValue(15f, "sp"), lineHeight = DimensionValue(20f, "sp"), fontWeight = "400", fontFamily = StringValue("sans-serif").value),
        "typography.title1" to TypographyValue(fontSize = DimensionValue(28f, "sp"), lineHeight = DimensionValue(34f, "sp"), fontWeight = "700", fontFamily = StringValue("sans-serif").value),
        "typography.title2" to TypographyValue(fontSize = DimensionValue(22f, "sp"), lineHeight = DimensionValue(28f, "sp"), fontWeight = "700", fontFamily = StringValue("sans-serif").value),
        "typography.title3" to TypographyValue(fontSize = DimensionValue(20f, "sp"), lineHeight = DimensionValue(25f, "sp"), fontWeight = "600", fontFamily = StringValue("sans-serif").value),
        // ---------- shape ----------
        "shape.radius.button" to DimensionValue(12f, "dp"),
        "shape.radius.card" to DimensionValue(12f, "dp"),
        "shape.radius.cardLarge" to DimensionValue(16f, "dp"),
        "shape.radius.dialog" to DimensionValue(14f, "dp"),
        "shape.radius.pill" to DimensionValue(25f, "dp"),
        "shape.radius.sheetTop" to DimensionValue(12f, "dp"),
        // ---------- size ----------
        "size.button.height" to DimensionValue(50f, "dp"),
        "size.fab" to DimensionValue(56f, "dp"),
        "size.focusRing" to DimensionValue(2f, "dp"),
        "size.grabber.height" to DimensionValue(5f, "dp"),
        "size.grabber.width" to DimensionValue(36f, "dp"),
        "size.icon" to DimensionValue(24f, "dp"),
        "size.input.height" to DimensionValue(44f, "dp"),
        "size.list.rowHeight" to DimensionValue(44f, "dp"),
        "size.navBar.height" to DimensionValue(44f, "dp"),
        "size.switch.height" to DimensionValue(31f, "dp"),
        "size.switch.width" to DimensionValue(51f, "dp"),
        "size.tabBar.height" to DimensionValue(49f, "dp"),
        "size.touchTarget" to DimensionValue(44f, "dp"),
        // ---------- spacing ----------
        "spacing.12" to DimensionValue(12f, "dp"),
        "spacing.16" to DimensionValue(16f, "dp"),
        "spacing.20" to DimensionValue(20f, "dp"),
        "spacing.24" to DimensionValue(24f, "dp"),
        "spacing.32" to DimensionValue(32f, "dp"),
        "spacing.4" to DimensionValue(4f, "dp"),
        "spacing.8" to DimensionValue(8f, "dp"),
        "spacing.dividerInset" to DimensionValue(16f, "dp"),
        "spacing.gutter" to DimensionValue(16f, "dp"),
        "spacing.listInset" to DimensionValue(16f, "dp"),
        "spacing.section" to DimensionValue(24f, "dp"),
        // ---------- motion ----------
        "motion.duration.default" to DurationValue(250.0f),
        "motion.duration.fast" to DurationValue(150.0f),
        "motion.duration.instant" to DurationValue(100.0f),
        "motion.duration.max" to DurationValue(400.0f),
        "motion.duration.shimmer" to DurationValue(1200.0f),
        "motion.duration.slow" to DurationValue(300.0f),
        "motion.duration.spinner" to DurationValue(1000.0f),
        "motion.effects.default" to SpringValue(600.0f, 1.0f, 0.0f),
        "motion.effects.fast" to SpringValue(1200.0f, 1.0f, 0.0f),
        "motion.effects.slow" to SpringValue(300.0f, 1.0f, 0.0f),
        "motion.spatial.default" to SpringValue(380.0f, 0.86f, 0.0f),
        "motion.spatial.fast" to SpringValue(800.0f, 0.9f, 0.0f),
        "motion.spatial.slow" to SpringValue(200.0f, 0.85f, 0.0f),
        // ---------- interaction ----------
        "interaction.backDismissRatio" to NumberValue(0.3f),
        "interaction.badgePopScale" to NumberValue(1.1f),
        "interaction.bannerAutoDismiss" to DurationValue(0.0f),
        "interaction.cardPressScale" to NumberValue(0.98f),
        "interaction.chevronRotation" to NumberValue(180.0f),
        "interaction.dragDismissRatio" to NumberValue(0.5f),
        "interaction.flickDismissRatio" to NumberValue(0.05f),
        "interaction.flickVelocity" to NumberValue(800.0f),
        "interaction.haptic.heavy" to NumberValue(3.0f),
        "interaction.haptic.light" to NumberValue(1.0f),
        "interaction.haptic.medium" to NumberValue(2.0f),
        "interaction.itemEnterOffset" to DimensionValue(8f, "dp"),
        "interaction.longPressTimeout" to DurationValue(400.0f),
        "interaction.maxStaggerItems" to NumberValue(10.0f),
        "interaction.overlayScale" to NumberValue(0.96f),
        "interaction.parallaxFactor" to NumberValue(0.5f),
        "interaction.pressScale" to NumberValue(0.96f),
        "interaction.rubberBandDamping" to NumberValue(0.5f),
        "interaction.selectSheetThreshold" to NumberValue(7.0f),
        "interaction.shakeCycles" to NumberValue(2.0f),
        "interaction.shakeDistance" to DimensionValue(4f, "dp"),
        "interaction.stagger.list" to DurationValue(30.0f),
        "interaction.stagger.menu" to DurationValue(20.0f),
        "interaction.streamFloodThreshold" to NumberValue(10.0f),
        "interaction.swipeActionRatio" to NumberValue(0.6f),
        "interaction.swipeRevealRatio" to NumberValue(0.3f),
        // ---------- elevation ----------
        "elevation.hairline" to DimensionValue(1f, "px"),
        "elevation.overlay" to ShadowValue(color = ColorValue("#0000001A"), blur = DimensionValue(8f, "dp"), offsetY = DimensionValue(2f, "dp")),
        // ---------- opacity ----------
        "opacity.dimmed" to NumberValue(0.8f),
        "opacity.disabled" to NumberValue(0.5f),
        "opacity.pressHighlight" to NumberValue(0.08f),
        "opacity.primitive.0" to NumberValue(0.0f),
        "opacity.primitive.04" to NumberValue(0.04f),
        "opacity.primitive.08" to NumberValue(0.08f),
        "opacity.primitive.16" to NumberValue(0.16f),
        "opacity.primitive.32" to NumberValue(0.32f),
        "opacity.primitive.50" to NumberValue(0.5f),
        "opacity.primitive.64" to NumberValue(0.64f),
        "opacity.primitive.80" to NumberValue(0.8f),
        "opacity.primitive.full" to NumberValue(1.0f),
        "opacity.scrim" to NumberValue(0.32f),
        "opacity.scrimBright" to NumberValue(0.35f),
        "opacity.veil" to NumberValue(0.64f),
        // ---------- glass ----------
        "glass.enabled" to BooleanValue(false),
        "glass.reducedTransparency" to BooleanValue(false),
        "glass.regular" to GlassValue(blur = DimensionValue(20f, "dp"), saturation = 1.8f, tint = ColorValue("#FFFFFFFF"), tintAlpha = NumberValue(0.72f).value, hairline = true),
        "glass.regular.tintAlpha" to NumberValue(0.72f),
        "glass.thick" to GlassValue(blur = DimensionValue(32f, "dp"), saturation = 1.9f, tint = ColorValue("#FFFFFFFF"), tintAlpha = NumberValue(0.82f).value, hairline = true),
        "glass.thick.tintAlpha" to NumberValue(0.82f),
        "glass.thin" to GlassValue(blur = DimensionValue(12f, "dp"), saturation = 1.7f, tint = ColorValue("#FFFFFFFF"), tintAlpha = NumberValue(0.65f).value, hairline = true),
        "glass.thin.tintAlpha" to NumberValue(0.65f),
        "glass.ultraThin" to GlassValue(blur = DimensionValue(8f, "dp"), saturation = 1.6f, tint = ColorValue("#FFFFFFFF"), tintAlpha = NumberValue(0.55f).value, hairline = false),
        "glass.ultraThin.tintAlpha" to NumberValue(0.55f),
        // ---------- 扩展组 $editor ----------
        "editor.syntax.keyword" to ColorValue("#C7254EFF"),
        "editor.syntax.string" to ColorValue("#183691FF"),
        // ---------- 扩展组 $terminal ----------
        "terminal.ansi.black" to ColorValue("#000000FF"),
        "terminal.ansi.blue" to ColorValue("#0A84FFFF"),
        "terminal.ansi.brightBlack" to ColorValue("#8E8E93FF"),
        "terminal.ansi.brightBlue" to ColorValue("#007AFFFF"),
        "terminal.ansi.brightCyan" to ColorValue("#70D7FFFF"),
        "terminal.ansi.brightGreen" to ColorValue("#32D74BFF"),
        "terminal.ansi.brightMagenta" to ColorValue("#DA8FFFFF"),
        "terminal.ansi.brightRed" to ColorValue("#FF6961FF"),
        "terminal.ansi.brightWhite" to ColorValue("#FFFFFFFF"),
        "terminal.ansi.brightYellow" to ColorValue("#FFCC00FF"),
        "terminal.ansi.cyan" to ColorValue("#64D2FFFF"),
        "terminal.ansi.green" to ColorValue("#30D158FF"),
        "terminal.ansi.magenta" to ColorValue("#BF5AF2FF"),
        "terminal.ansi.red" to ColorValue("#FF453AFF"),
        "terminal.ansi.white" to ColorValue("#E5E5EAFF"),
        "terminal.ansi.yellow" to ColorValue("#FFD60AFF"),
        // ---------- 扩展组 $chart ----------
        "chart.c1" to ColorValue("#007AFFFF"),
        "chart.c2" to ColorValue("#AF52DEFF"),
        "chart.c3" to ColorValue("#5AC8FAFF"),
        "chart.c4" to ColorValue("#FFCC00FF"),
        "chart.c5" to ColorValue("#FF2D55FF"),
        "chart.c6" to ColorValue("#5856D6FF"),
    )

    /** 内置 fallback 快照（降级路径的交付物） */
    val snapshot: ThemeSnapshot by lazy { SnapshotBuilder.build(rawValues, FALLBACK_META) }
}
