package org.minime.core.schema

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertThrows
import org.junit.jupiter.api.Test

/** 令牌值类型（颜色 hex 形态/合成、尺寸单位、复合型装配辅助） */
class TokenValueTest {

    // ---------- 颜色 ----------

    @Test
    fun `hex forms normalize to RRGGBBAA`() {
        assertEquals("#FFFFFFFF", ColorValue.fromHex("#FFF").hex)
        assertEquals("#FFFFFFFF", ColorValue.fromHex("#FFFFFF").hex)
        assertEquals("#FFFFFFFF", ColorValue.fromHex("#FFFFFFFF").hex)
        assertEquals("#000000FF", ColorValue.fromHex("#000").hex)
        assertEquals("#00000000", ColorValue.fromHex("#0000").hex) // #ARGB
    }

    @Test
    fun `8-digit hex is RRGGBBAA not AARRGGBB`() {
        // #RRGGBBAA：alpha 在末两位（曾因按 ARGB 解析导致 alpha=0 的全链路错误）
        val c = ColorValue.fromHex("#007AFF80")
        assertEquals(0x00, c.red)
        assertEquals(0x7A, c.green)
        assertEquals(0xFF, c.blue)
        assertEquals(0x80, c.argb ushr 24 and 0xFF)
        assertEquals(128f / 255f, c.alpha)
        // #000000FF = 纯黑不透明（alpha 必须为 1，argb = FF000000）
        assertEquals(1f, ColorValue.fromHex("#000000FF").alpha)
        assertEquals(0xFF000000.toInt(), ColorValue.fromHex("#000000FF").argb)
    }

    @Test
    fun `alpha channel and withAlpha`() {
        val c = ColorValue.fromHex("#007AFFFF")
        assertEquals(1f, c.alpha)
        assertEquals("#007AFF80", c.withAlpha(0.5f).hex)
    }

    @Test
    fun `argb roundtrip`() {
        val c = ColorValue.fromHex("#3C3C4399")
        assertEquals(c.hex, ColorValue.argbToHex(c.red, c.green, c.blue, (c.alpha * 255).toInt()))
    }

    @Test
    fun `over composites source-over`() {
        val white = ColorValue.WHITE
        // 60% 黑叠白 ≈ #666666（iOS secondaryLabel 形态）
        val half = ColorValue.fromHex("#00000099")
        val over = half.over(white)
        assertEquals(0x66, over.red, "红色通道应≈0x66（实际 ${over.red}）")
        assertEquals(0x66, over.green)
        assertEquals(0x66, over.blue)
        assertEquals(1f, over.alpha)
        // 不透明前景原样返回
        assertEquals(white, ColorValue.fromHex("#FFFFFFFF").over(ColorValue.BLACK))
        // 全透明前景 → 背景
        assertEquals(white, ColorValue.fromHex("#00000000").over(white))
    }

    @Test
    fun `object form sRGB`() {
        val c = ColorValue.fromObject("srgb", listOf(1.0, 0.0, 0.0))
        assertEquals("#FF0000FF", c.hex)
        val c2 = ColorValue.fromObject("SRGB", listOf(0.0, 0.0, 0.0, 0.5))
        assertEquals(0.5f, c2.alpha, 0.01f)
        assertThrows(IllegalArgumentException::class.java) {
            ColorValue.fromObject("display-p3", listOf(1.0, 0.0, 0.0))
        }
    }

    @Test
    fun `invalid hex rejected`() {
        assertThrows(IllegalArgumentException::class.java) { ColorValue.fromHex("#GGG") }
        assertThrows(IllegalArgumentException::class.java) { ColorValue.fromHex("#12345") }
        assertThrows(IllegalArgumentException::class.java) { ColorValue("red") }
    }

    // ---------- 尺寸 ----------

    @Test
    fun `dimension units`() {
        val d = DimensionValue(16f, "dp")
        assertEquals(16f, d.dp)
        assertThrows(IllegalStateException::class.java) { d.sp }
        assertThrows(IllegalArgumentException::class.java) { DimensionValue(1f, "rem") }
        assertEquals("12.5dp", DimensionValue(12.5f, "dp").toString())
    }

    // ---------- 复合型 schema ----------

    @Test
    fun `composite schema required fields`() {
        assertEquals(setOf("stiffness", "damping", "initialVelocity"), CompositeSchemas.REQUIRED_FIELDS[TokenType.SPRING])
        assertEquals(setOf("fontSize"), CompositeSchemas.REQUIRED_FIELDS[TokenType.TYPOGRAPHY])
        assertEquals(setOf("color"), CompositeSchemas.REQUIRED_FIELDS[TokenType.SHADOW])
        assertEquals(CompositeSchemas.GLASS.keys, CompositeSchemas.REQUIRED_FIELDS[TokenType.GLASS])
    }

    @Test
    fun `token type raw names`() {
        assertEquals(TokenType.FONT_FAMILY, TokenType.fromRaw("fontFamily"))
        assertEquals(TokenType.FONT_FAMILY, TokenType.fromRaw("fontfamily"))
        assertEquals(TokenType.CUBIC_BEZIER, TokenType.fromRaw("cubicBezier"))
        assertEquals(TokenType.SPRING, TokenType.fromRaw("spring"))
        assertEquals(TokenType.GLASS, TokenType.fromRaw("glass"))
        assertEquals(null, TokenType.fromRaw("gradient")) // 未纳入白名单
    }
}
