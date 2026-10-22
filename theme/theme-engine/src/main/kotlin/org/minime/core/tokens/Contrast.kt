package org.minime.core.tokens

import org.minime.core.schema.ColorValue
import kotlin.math.pow

/**
 * WCAG 2.x 对比度计算（工坊实时读数 §10.7；文本五档 × 背景五档矩阵 + accent × surface）。
 *
 * 单一真源放在引擎（JVM 可测、无 Android 依赖）——UI 侧只消费 [WcagLevel] 与比值，
 * 禁在面板里重写亮度公式（防两套实现漂移）。
 *
     * 半透明语义（§6.8.5 包络法）：前景（可半透明）与背景（可半透明）依次按
     * source-over 合成到**极端背景**（白 / 黑）上，取两种极端下更差的比值——
     * 只按当前背景直接合成会低估「该色叠在别的内容上」时的最坏情况。
     * 观感链：最终像素 = fg ∘ bg ∘ extreme；对比 = 前景观感 vs 背景观感。
 */
object Contrast {

    /** WCAG 等级阈值：AAA ≥ 7、AA ≥ 4.5、大字 ≥ 3、其余 Fail */
    enum class WcagLevel { AAA, AA, LARGE_ONLY, FAIL }

    /**
     * 相对亮度（WCAG 2.x 定义）：sRGB 分量 → 线性化 → 0.2126R + 0.7152G + 0.0722B。
     * alpha 通道不参与（合成在调用方处理）。
     */
    fun relativeLuminance(color: ColorValue): Double {
        fun lin(channel: Int): Double {
            val c = channel / 255.0
            return if (c <= 0.03928) c / 12.92 else ((c + 0.055) / 1.055).pow(2.4)
        }
        return 0.2126 * lin(color.red) + 0.7152 * lin(color.green) + 0.0722 * lin(color.blue)
    }

    /** 对比度比值 (L1 + 0.05) / (L2 + 0.05)，与顺序无关（自动交换深浅） */
    fun contrastRatio(foreground: ColorValue, background: ColorValue): Double {
        val l1 = relativeLuminance(foreground)
        val l2 = relativeLuminance(background)
        val (hi, lo) = if (l1 >= l2) l1 to l2 else l2 to l1
        return (hi + 0.05) / (lo + 0.05)
    }

    /**
     * 包络法比值（§6.8.5）：[foreground]（可半透明）先按 source-over 合成到
     * [background] 上得到实际观感色，再取白 / 黑两种极端背景下的更差结果。
     *
     * 全不透明前景等价于 [contrastRatio]（合成无变化，两极背景取到的就是自身）。
     */
    fun envelopeRatio(foreground: ColorValue, background: ColorValue): Double {
        // 极端背景下的观感链：fg ∘ bg ∘ extreme（两端都可半透明，逐层合成）
        val whiteBg = background.over(ColorValue.WHITE)
        val blackBg = background.over(ColorValue.BLACK)
        val onWhite = contrastRatio(foreground.over(whiteBg), whiteBg)
        val onBlack = contrastRatio(foreground.over(blackBg), blackBg)
        return minOf(onWhite, onBlack)
    }

    /** 比值 → 等级（AAA / AA / 大字可用 / 不达标） */
    fun levelOf(ratio: Double): WcagLevel = when {
        ratio >= 7.0 -> WcagLevel.AAA
        ratio >= 4.5 -> WcagLevel.AA
        ratio >= 3.0 -> WcagLevel.LARGE_ONLY
        else -> WcagLevel.FAIL
    }
}
