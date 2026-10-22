package org.minime.core.tokens

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test
import org.minime.core.schema.ColorValue

/**
 * WCAG 对比度（工坊实时读数的计算核）。
 *
 * 对拍值全部可由 WCAG 2.x 公式手算复核（sRGB 线性化 + 0.2126/0.7152/0.0722 权重），
 * 不用「网络流传边界值」——公式自洽优先。
 */
class ContrastTest {

    private fun hex(s: String) = ColorValue.fromHex(s)

    @Test
    fun `black on white is exactly 21`() {
        assertEquals(21.0, Contrast.contrastRatio(hex("#000000"), hex("#FFFFFF")), 1e-9)
    }

    @Test
    fun `same color ratio is 1`() {
        assertEquals(1.0, Contrast.contrastRatio(hex("#123456"), hex("#123456")), 1e-9)
    }

    @Test
    fun `mid gray on white is 3_95`() {
        // #808080：128/255 线性化 → L ≈ 0.2157 → 1.05 / 0.2657 ≈ 3.951
        assertEquals(3.95, Contrast.contrastRatio(hex("#808080"), hex("#FFFFFF")), 0.01)
    }

    @Test
    fun `767676 on white sits right at AA edge`() {
        // 经典「达标边缘」灰：公式自洽值 ≈ 4.54（刚好压过 4.5 线）
        val ratio = Contrast.contrastRatio(hex("#767676"), hex("#FFFFFF"))
        assertEquals(4.54, ratio, 0.01)
        assertEquals(Contrast.WcagLevel.AA, Contrast.levelOf(ratio))
    }

    @Test
    fun `envelope with opaque foreground equals plain ratio`() {
        val plain = Contrast.contrastRatio(hex("#000000"), hex("#FFFFFF"))
        assertEquals(plain, Contrast.envelopeRatio(hex("#000000"), hex("#FFFFFF")), 1e-9)
    }

    @Test
    fun `envelope takes the worse extreme background`() {
        // 半透灰（#808080 × 50%）叠白底：观感 #BFBFBF → ≈1.84（白极端更差，取它）
        val ratio = Contrast.envelopeRatio(hex("#80808080"), hex("#FFFFFFFF"))
        assertEquals(1.84, ratio, 0.01)
        assertEquals(Contrast.WcagLevel.FAIL, Contrast.levelOf(ratio))
    }

    @Test
    fun `envelope composites both translucent layers`() {
        // 前景与背景均半透明：白极端 → bg 合成白(#FFF)、fg 叠后 #7F7F7F → 4.00；
        // 黑极端 → bg 合成 #808080、fg 叠后 #404040 → 2.63（更差，包络取它）
        val ratio = Contrast.envelopeRatio(hex("#00000080"), hex("#FFFFFF80"))
        assertEquals(2.63, ratio, 0.01)
    }

    @Test
    fun `level thresholds`() {
        assertEquals(Contrast.WcagLevel.FAIL, Contrast.levelOf(2.99))
        assertEquals(Contrast.WcagLevel.LARGE_ONLY, Contrast.levelOf(3.0))
        assertEquals(Contrast.WcagLevel.LARGE_ONLY, Contrast.levelOf(4.49))
        assertEquals(Contrast.WcagLevel.AA, Contrast.levelOf(4.5))
        assertEquals(Contrast.WcagLevel.AA, Contrast.levelOf(6.99))
        assertEquals(Contrast.WcagLevel.AAA, Contrast.levelOf(7.0))
    }
}
