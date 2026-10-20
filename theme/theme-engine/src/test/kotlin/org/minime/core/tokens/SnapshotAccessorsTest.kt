package org.minime.core.tokens

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNotNull
import org.junit.jupiter.api.Test
import org.minime.core.compiler.ThemeCompiler
import org.minime.core.schema.DimensionValue
import org.minime.core.schema.DurationValue
import org.minime.core.schema.NumberValue
import org.minime.core.schema.StringValue

/**
 * 十子系统强类型访问器端到端（§6.2）：默认主题包 light 上下文编译后的快照，
 * 逐子系统走访问器取值——组件消费面只认这些访问器，不接受 raw 路径字符串。
 */
class SnapshotAccessorsTest {

    private val json: String = SnapshotAccessorsTest::class.java
        .getResourceAsStream("/default-theme.json")!!.use { it.bufferedReader(Charsets.UTF_8).readText() }

    private val snapshot = ThemeCompiler().compileJson(json).snapshot

    // ---------- 1. 配色 ----------

    @Test
    fun `color accessors`() {
        val color = snapshot.color
        assertEquals("#007AFFFF", color.accent.hex)
        assertEquals("#FFFFFFFF", color.bgCanvas.hex)
        assertEquals("#F2F2F7FF", color.bgGrouped.hex)
        assertEquals("#FFFFFFFF", color.bgSurface.hex)
        assertEquals("#000000FF", color.textPrimary.hex)
        assertEquals("#3C3C4399", color.textSecondary.hex)
        assertEquals("#FF3B30FF", color.stateError.hex)
        assertEquals("#34C759FF", color.stateSuccess.hex)
        // 原始层按名查询（主题作者改值入口）
        assertEquals("#007AFFFF", color.primitiveOrNull("systemBlue")?.hex)
        assertEquals(null, color.primitiveOrNull("notAColor"))
    }

    // ---------- 2. 排版 ----------

    @Test
    fun `typography accessors`() {
        val type = snapshot.typography
        assertEquals(StringValue("sans-serif"), type.fontFamilySans)
        assertEquals(StringValue("monospace"), type.fontFamilyMono)
        assertEquals(DimensionValue(34f, "sp"), type.largeTitle.fontSize)
        assertEquals(DimensionValue(22f, "sp"), type.body.lineHeight)
        assertEquals("600", type.headline.fontWeight)
        // monospace 走 mono 字体族别名
        assertEquals("monospace", type.monospace.fontFamily)
        assertEquals(DimensionValue(13f, "sp"), type.monospace.fontSize)
    }

    // ---------- 3. 圆角 ----------

    @Test
    fun `shape accessors`() {
        val shape = snapshot.shape
        assertEquals(DimensionValue(12f, "dp"), shape.radiusButton)
        assertEquals(DimensionValue(12f, "dp"), shape.radiusCard)
        assertEquals(DimensionValue(16f, "dp"), shape.radiusCardLarge)
        assertEquals(DimensionValue(14f, "dp"), shape.radiusDialog)
        assertEquals(DimensionValue(25f, "dp"), shape.radiusPill)
        assertEquals(DimensionValue(12f, "dp"), shape.radiusSheetTop)
    }

    // ---------- 4. 尺寸 ----------

    @Test
    fun `size accessors`() {
        val size = snapshot.size
        assertEquals(DimensionValue(44f, "dp"), size.touchTarget)
        assertEquals(DimensionValue(50f, "dp"), size.buttonHeight)
        assertEquals(DimensionValue(44f, "dp"), size.inputHeight)
        assertEquals(DimensionValue(44f, "dp"), size.listRowHeight)
        assertEquals(DimensionValue(44f, "dp"), size.navBarHeight)
        assertEquals(DimensionValue(49f, "dp"), size.tabBarHeight)
        assertEquals(DimensionValue(36f, "dp"), size.grabberWidth)
        assertEquals(DimensionValue(5f, "dp"), size.grabberHeight)
        assertEquals(DimensionValue(24f, "dp"), size.icon)
        assertEquals(DimensionValue(56f, "dp"), size.fab)
        assertEquals(DimensionValue(51f, "dp"), size.switchWidth)
        assertEquals(DimensionValue(31f, "dp"), size.switchHeight)
        assertEquals(DimensionValue(2f, "dp"), size.focusRing)
    }

    // ---------- 5. 间距 ----------

    @Test
    fun `spacing accessors`() {
        val spacing = snapshot.spacing
        assertEquals(DimensionValue(4f, "dp"), spacing.s4)
        assertEquals(DimensionValue(8f, "dp"), spacing.s8)
        assertEquals(DimensionValue(16f, "dp"), spacing.s16)
        assertEquals(DimensionValue(24f, "dp"), spacing.s24)
        assertEquals(DimensionValue(32f, "dp"), spacing.s32)
        assertEquals(DimensionValue(16f, "dp"), spacing.gutter)
        assertEquals(DimensionValue(16f, "dp"), spacing.listInset)
        assertEquals(DimensionValue(16f, "dp"), spacing.dividerInset)
        assertEquals(DimensionValue(24f, "dp"), spacing.section)
    }

    // ---------- 6. 动效 ----------

    @Test
    fun `motion accessors`() {
        val motion = snapshot.motion
        assertEquals(800f, motion.spatialFast.stiffness)
        assertEquals(0.9f, motion.spatialFast.damping)
        assertEquals(380f, motion.spatialDefault.stiffness)
        assertEquals(0.86f, motion.spatialDefault.damping)
        assertEquals(1200f, motion.effectsFast.stiffness)
        assertEquals(1f, motion.effectsFast.damping)
        assertEquals(DurationValue(100f), motion.durationInstant)
        assertEquals(DurationValue(150f), motion.durationFast)
        assertEquals(DurationValue(250f), motion.durationDefault)
        assertEquals(DurationValue(400f), motion.durationMax)
        assertEquals(DurationValue(1200f), motion.durationShimmer)
        assertEquals(DurationValue(1000f), motion.durationSpinner)
    }

    // ---------- 7. 交互 ----------

    @Test
    fun `interaction accessors`() {
        val it = snapshot.interaction
        assertEquals(NumberValue(800f), it.flickVelocity)
        assertEquals(NumberValue(0.05f), it.flickDismissRatio)
        assertEquals(NumberValue(0.5f), it.dragDismissRatio)
        assertEquals(NumberValue(0.96f), it.pressScale)
        assertEquals(NumberValue(0.98f), it.cardPressScale)
        assertEquals(DimensionValue(4f, "dp"), it.shakeDistance)
        assertEquals(DurationValue(400f), it.subtree.duration("longPressTimeout"))
        assertNotNull(it.subtree["haptic.light"])
    }

    // ---------- 8. 层级 ----------

    @Test
    fun `elevation accessors`() {
        assertEquals(DimensionValue(1f, "px"), snapshot.elevation.hairline)
        val overlay = snapshot.elevation.overlay
        assertEquals("#0000001A", overlay.color.hex)
        assertEquals(DimensionValue(8f, "dp"), overlay.blur)
        assertEquals(DimensionValue(2f, "dp"), overlay.offsetY)
    }

    // ---------- 9. 透明度 ----------

    @Test
    fun `opacity accessors`() {
        val opacity = snapshot.opacity
        assertEquals(NumberValue(0f), opacity.l0)
        assertEquals(NumberValue(0.5f), opacity.l50)
        assertEquals(NumberValue(1f), opacity.full)
        // 语义层是 primitive 别名
        assertEquals(NumberValue(0.5f), opacity.disabled)
        assertEquals(NumberValue(0.08f), opacity.pressHighlight)
        assertEquals(NumberValue(0.35f), opacity.scrimBright)
    }

    // ---------- 10. 玻璃 ----------

    @Test
    fun `glass accessors`() {
        val glass = snapshot.glass
        assertEquals(false, glass.enabled.value)
        assertEquals(false, glass.reducedTransparency.value)
        assertEquals(12f, glass.thin.blur.value)
        assertEquals(1.7f, glass.thin.saturation)
        assertEquals(NumberValue(0.65f), glass.thinTintAlpha)
        assertEquals(true, glass.thin.hairline)
        assertEquals(NumberValue(0.82f), glass.thickTintAlpha)
        assertEquals(NumberValue(0.55f), glass.ultraThinTintAlpha)
        assertEquals(NumberValue(0.72f), glass.regularTintAlpha)
    }

    // ---------- 元信息 ----------

    @Test
    fun `meta carries pack identity and modifier context`() {
        assertEquals("MiniMe Pure White", snapshot.meta.packName)
        assertEquals("1.0.0", snapshot.meta.packVersion)
        assertEquals(">=1.0 <2.0", snapshot.meta.engineVersionRange)
        assertEquals(emptyList<String>(), snapshot.meta.activeModifiers)
    }
}
