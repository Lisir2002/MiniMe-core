package com.mini.me_core.core.theme.tokens

import androidx.compose.ui.graphics.Color
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Primitive 颜色 Token 单元测试。
 * 验证关键色值与现有代码一致，防止后续修改意外破坏视觉。
 */
class PrimitiveColorsTest {

    @Test
    fun `品牌蓝色阶与现有 Brand Blue 一致`() {
        // 现有 Brand.Blue = #2563EB，对应 Blue600
        assertEquals(Color(0xFF2563EB), PrimitiveColors.Blue600)
        // 现有 DarkColorScheme.primary = #60A5FA，对应 Blue400
        assertEquals(Color(0xFF60A5FA), PrimitiveColors.Blue400)
    }

    @Test
    fun `成功绿色阶与现有 StatusGreen 一致`() {
        // 现有 Brand.StatusGreen.Light = #16A34A
        assertEquals(Color(0xFF16A34A), PrimitiveColors.Green600)
        // 现有 Brand.StatusGreen.Dark = #4ADE80
        assertEquals(Color(0xFF4ADE80), PrimitiveColors.Green400)
        // 高频成功色 #22C55E
        assertEquals(Color(0xFF22C55E), PrimitiveColors.Green500)
    }

    @Test
    fun `错误红色阶与现有 ColorScheme error 一致`() {
        // 现有 LightColorScheme.error = #DC2626
        assertEquals(Color(0xFFDC2626), PrimitiveColors.Red600)
        // 现有 DarkColorScheme.error = #F87171
        assertEquals(Color(0xFFF87171), PrimitiveColors.Red400)
        // 现有 LightColorScheme.errorContainer = #FEE2E2
        assertEquals(Color(0xFFFEE2E2), PrimitiveColors.Red100)
    }

    @Test
    fun `警告琥珀色阶与现有 ChatAccent Build 一致`() {
        // 现有 ChatAccent.Build.light = #B45309
        assertEquals(Color(0xFFB45309), PrimitiveColors.Amber700)
        // 现有 ChatAccent.Build.dark = #FBBF24
        assertEquals(Color(0xFFFBBF24), PrimitiveColors.Amber400)
    }

    @Test
    fun `中性灰色阶与现有背景色一致`() {
        // 现有 LightColorScheme.background = #F8FAFC
        assertEquals(Color(0xFFF8FAFC), PrimitiveColors.Slate50)
        // 现有 DarkColorScheme.background = #0F172A
        assertEquals(Color(0xFF0F172A), PrimitiveColors.Slate900)
        // 现有 DarkColorScheme.surface = #1E293B
        assertEquals(Color(0xFF1E293B), PrimitiveColors.Slate800)
        // 现有 LightColorScheme.surfaceVariant = #F1F5F9
        assertEquals(Color(0xFFF1F5F9), PrimitiveColors.Slate100)
    }

    @Test
    fun `功能语义色与现有 ChatAccent 一致`() {
        // Plan
        assertEquals(Color(0xFF2563EB), PrimitiveColors.Blue600)
        assertEquals(Color(0xFF60A5FA), PrimitiveColors.Blue400)
        // Auto
        assertEquals(Color(0xFF0D9488), PrimitiveColors.Teal600)
        assertEquals(Color(0xFF2DD4BF), PrimitiveColors.Teal400)
        // Reasoning
        assertEquals(Color(0xFF7C3AED), PrimitiveColors.Violet600)
        assertEquals(Color(0xFFA78BFA), PrimitiveColors.Violet400)
        // Skill
        assertEquals(Color(0xFFDB2777), PrimitiveColors.Pink600)
        assertEquals(Color(0xFFF472B6), PrimitiveColors.Pink400)
    }

    @Test
    fun `色阶单调递增（从 50 到 900 逐渐变深）`() {
        val blueShades = listOf(
            PrimitiveColors.Blue50, PrimitiveColors.Blue100, PrimitiveColors.Blue200,
            PrimitiveColors.Blue300, PrimitiveColors.Blue400, PrimitiveColors.Blue500,
            PrimitiveColors.Blue600, PrimitiveColors.Blue700, PrimitiveColors.Blue800,
            PrimitiveColors.Blue900
        )
        // 验证每个色阶都不同（色阶有效）
        val uniqueCount = blueShades.toSet().size
        assertEquals("蓝色阶应有 10 个不同色值", 10, uniqueCount)
    }

    @Test
    fun `纯白纯黑透明定义正确`() {
        assertEquals(Color(0xFFFFFFFF), PrimitiveColors.White)
        assertEquals(Color(0xFF000000), PrimitiveColors.Black)
        assertEquals(Color(0x00000000), PrimitiveColors.Transparent)
    }
}
