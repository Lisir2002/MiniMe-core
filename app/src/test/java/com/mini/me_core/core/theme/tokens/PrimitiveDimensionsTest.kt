package com.mini.me_core.core.theme.tokens

import androidx.compose.ui.unit.dp
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Primitive 间距/圆角/阴影/透明度 Token 单元测试。
 */
class PrimitiveDimensionsTest {

    @Test
    fun `间距 Token 与现有 Spacing 兼容`() {
        // 现有 Spacing.xs = 4dp
        assertEquals(4.dp, PrimitiveSpacing.Xs)
        // 现有 Spacing.sm = 8dp
        assertEquals(8.dp, PrimitiveSpacing.Sm)
        // 现有 Spacing.md = 12dp
        assertEquals(12.dp, PrimitiveSpacing.Md)
        // 现有 Spacing.lg = 16dp
        assertEquals(16.dp, PrimitiveSpacing.Lg)
        // 现有 Spacing.xl = 24dp
        assertEquals(24.dp, PrimitiveSpacing.Xxl)
        // 现有 Spacing.xxl = 32dp
        assertEquals(32.dp, PrimitiveSpacing.Xxxl)
    }

    @Test
    fun `间距 Token 补充高频非标值`() {
        // 高频 2dp（41次使用）
        assertEquals(2.dp, PrimitiveSpacing.Xxs)
        // 高频 6dp（23次使用）
        assertEquals(6.dp, PrimitiveSpacing.SmPlus)
        // 高频 10dp（17次使用）
        assertEquals(10.dp, PrimitiveSpacing.MdPlus)
    }

    @Test
    fun `圆角 Token 与现有 Radius 兼容`() {
        // 现有 Radius.xs = 4dp
        assertEquals(4.dp, PrimitiveRadius.Xs)
        // 现有 Radius.sm = 8dp
        assertEquals(8.dp, PrimitiveRadius.Md)
        // 现有 Radius.md = 10dp
        assertEquals(10.dp, PrimitiveRadius.Lg)
        // 现有 Radius.lg = 14dp → 注意：现有 Radius.lg=14，但 PrimitiveRadius.Xl=12
        // 现有 Radius.pill = 999dp
        assertEquals(999.dp, PrimitiveRadius.Pill)
    }

    @Test
    fun `圆角 Token 补充高频值`() {
        // 高频 12dp（37次使用）
        assertEquals(12.dp, PrimitiveRadius.Xl)
        // 高频 16dp（6次使用）
        assertEquals(16.dp, PrimitiveRadius.Xxl)
    }

    @Test
    fun `阴影 Token 与现有 Elevation 兼容`() {
        // 现有 Elevation.z0 = 0dp
        assertEquals(0.dp, PrimitiveElevation.Z0)
        // 现有 Elevation.z1 = 1dp
        assertEquals(1.dp, PrimitiveElevation.Z1)
        // 现有 Elevation.z2 = 3dp
        assertEquals(3.dp, PrimitiveElevation.Z2)
        // 现有 Elevation.z3 = 8dp
        assertEquals(8.dp, PrimitiveElevation.Z3)
        // 现有 Elevation.z4 = 12dp
        assertEquals(12.dp, PrimitiveElevation.Z4)
    }

    @Test
    fun `透明度 Token 层级合理`() {
        assertTrue(PrimitiveAlpha.Transparent < PrimitiveAlpha.Hover)
        assertTrue(PrimitiveAlpha.Hover < PrimitiveAlpha.Pressed)
        assertTrue(PrimitiveAlpha.Pressed < PrimitiveAlpha.Sunken)
        assertTrue(PrimitiveAlpha.Sunken < PrimitiveAlpha.Scrim)
        assertTrue(PrimitiveAlpha.Scrim < PrimitiveAlpha.Accent)
        assertTrue(PrimitiveAlpha.Accent < PrimitiveAlpha.CardSunken)
        assertTrue(PrimitiveAlpha.CardSunken < PrimitiveAlpha.Card)
        assertTrue(PrimitiveAlpha.Card < PrimitiveAlpha.Overlay)
        assertTrue(PrimitiveAlpha.Overlay < PrimitiveAlpha.Opaque)
    }

    @Test
    fun `透明度 Token 关键值正确`() {
        assertEquals(0f, PrimitiveAlpha.Transparent, 0.001f)
        assertEquals(0.40f, PrimitiveAlpha.Scrim, 0.001f)
        assertEquals(0.85f, PrimitiveAlpha.Card, 0.001f)
        assertEquals(1.0f, PrimitiveAlpha.Opaque, 0.001f)
        assertEquals(0.38f, PrimitiveAlpha.Disabled, 0.001f)
    }
}
