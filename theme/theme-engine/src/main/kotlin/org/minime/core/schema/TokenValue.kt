package org.minime.core.schema

import org.minime.core.api.Immutable
import kotlin.math.roundToInt

/** 令牌值统一接口（标量 + 复合型） */
sealed interface TokenValue

/** 复合型判断（别名目标/复合字段引用的类型校验用） */
fun TokenValue.isCompositeValue(): Boolean =
    this is TypographyValue || this is ShadowValue || this is SpringValue || this is GlassValue

/**
 * 颜色值。内部恒为规范化 `#RRGGBBAA` 大写形态（8 位，缺省 alpha = FF）。
 * 支持 DTCG 字符串形态（#RGB/#ARGB/#RRGGBB/#RRGGBBAA）与 2025.10 草案对象形态
 * （{"colorSpace":"srgb","components":[r,g,b(,a)]}，分量 0–1）。
 */
@Immutable
data class ColorValue(val hex: String) : TokenValue {
    init {
        require(hex.matches(HEX8)) { "非法颜色值: $hex（须为规范化 #RRGGBBAA）" }
    }

    val argb: Int = parseHexArgb(hex)

    val alpha: Float get() = ((argb ushr 24) and 0xFF) / 255f
    val red: Int get() = (argb shr 16) and 0xFF
    val green: Int get() = (argb shr 8) and 0xFF
    val blue: Int get() = argb and 0xFF

    /** 替换 alpha 通道（0–1） */
    fun withAlpha(a: Float): ColorValue =
        ColorValue(argbToHex(red, green, blue, (a.coerceIn(0f, 1f) * 255f).roundToInt()))

    /**
     * 前景（可半透明）按 source-over 合成到 [background] 上。
     * §6.8.5 等效底色包络校验：tint × tintAlpha 叠在 #FFFFFF/#000000 两种极端背景取更差结果。
     */
    fun over(background: ColorValue): ColorValue {
        val fa = alpha
        if (fa >= 1f) return this
        val ba = background.alpha
        val oa = fa + ba * (1f - fa)
        if (oa <= 0f) return ColorValue("#00000000")
        fun ch(f: Int, b: Int): Int = ((f * fa + b * ba * (1f - fa)) / oa).roundToInt()
        return ColorValue(
            argbToHex(
                ch(red, background.red),
                ch(green, background.green),
                ch(blue, background.blue),
                (oa * 255f).roundToInt(),
            )
        )
    }

    companion object {
        private val HEX8 = Regex("^#[0-9A-F]{8}$")

        /** 引擎内置极端背景（§6.8.5 包络校验） */
        val WHITE: ColorValue = ColorValue("#FFFFFFFF")
        val BLACK: ColorValue = ColorValue("#000000FF")

        fun argbToHex(r: Int, g: Int, b: Int, a: Int): String =
            "#%02X%02X%02X%02X".format(r.coerceIn(0, 255), g.coerceIn(0, 255), b.coerceIn(0, 255), a.coerceIn(0, 255))

        private fun parseHexArgb(hex: String): Int {
            val h = hex.removePrefix("#")
            return when (h.length) {
                8 -> { // #RRGGBBAA → ARGB 位模式（FFFFFF00 也超 Int 上限，逐字节组装）
                    val r = h.substring(0, 2).toInt(16)
                    val g = h.substring(2, 4).toInt(16)
                    val b = h.substring(4, 6).toInt(16)
                    val a = h.substring(6, 8).toInt(16)
                    (a shl 24) or (r shl 16) or (g shl 8) or b
                }
                6 -> (0xFF shl 24) or h.toInt(16)
                4 -> { // #ARGB（DTCG 4 位简写）
                    val r = h[0].digitToInt(16); val g = h[1].digitToInt(16)
                    val b = h[2].digitToInt(16); val a = h[3].digitToInt(16)
                    (a * 17 shl 24) or (r * 17 shl 16) or (g * 17 shl 8) or (b * 17)
                }
                3 -> { // #RGB
                    val r = h[0].digitToInt(16); val g = h[1].digitToInt(16); val b = h[2].digitToInt(16)
                    (0xFF shl 24) or (r * 17 shl 16) or (g * 17 shl 8) or (b * 17)
                }
                else -> throw IllegalArgumentException("非法颜色 hex: $hex")
            }
        }

        /** 字符串 hex 形态 → 规范化 ColorValue */
        fun fromHex(raw: String): ColorValue = ColorValue(argbToHexFromRaw(raw))

        private fun argbToHexFromRaw(raw: String): String {
            val h = raw.trim().removePrefix("#")
            require(h.isNotEmpty() && h.all { it in '0'..'9' || it in 'a'..'f' || it in 'A'..'F' }) {
                "非法颜色值: $raw"
            }
            val argb = when (h.length) {
                8 -> { // #RRGGBBAA → ARGB 位模式
                    val r = h.substring(0, 2).toInt(16)
                    val g = h.substring(2, 4).toInt(16)
                    val b = h.substring(4, 6).toInt(16)
                    val a = h.substring(6, 8).toInt(16)
                    (a shl 24) or (r shl 16) or (g shl 8) or b
                }
                6 -> (0xFF shl 24) or h.toInt(16)
                4 -> {
                    val r = h[0].digitToInt(16); val g = h[1].digitToInt(16)
                    val b = h[2].digitToInt(16); val a = h[3].digitToInt(16)
                    (a * 17 shl 24) or (r * 17 shl 16) or (g * 17 shl 8) or (b * 17)
                }
                3 -> {
                    val r = h[0].digitToInt(16); val g = h[1].digitToInt(16); val b = h[2].digitToInt(16)
                    (0xFF shl 24) or (r * 17 shl 16) or (g * 17 shl 8) or (b * 17)
                }
                else -> throw IllegalArgumentException("非法颜色值: $raw（支持 #RGB/#ARGB/#RRGGBB/#RRGGBBAA）")
            }
            return argbToHex((argb shr 16) and 0xFF, (argb shr 8) and 0xFF, argb and 0xFF, (argb ushr 24) and 0xFF)
        }

        /** 对象形态（DTCG 2025.10 草案）：{"colorSpace":"srgb","components":[r,g,b(,a)]}，分量 0–1 */
        fun fromObject(colorSpace: String, components: List<Double>): ColorValue {
            require(colorSpace.equals("srgb", ignoreCase = true)) {
                "对象形态颜色仅支持 srgb 色彩空间（当前: $colorSpace）"
            }
            require(components.size == 3 || components.size == 4) {
                "对象形态颜色 components 须为 3（rgb）或 4（rgba）个分量（当前 ${components.size}）"
            }
            fun c(d: Double): Int = (d.coerceIn(0.0, 1.0) * 255.0).roundToInt()
            val a = if (components.size == 4) c(components[3]) else 255
            return ColorValue(argbToHex(c(components[0]), c(components[1]), c(components[2]), a))
        }
    }
}

/** 尺寸值（单位限定 dp/sp/px；typography 的 fontSize/lineHeight 为 sp，elevation.hairline 为 px） */
@Immutable
data class DimensionValue(val value: Float, val unit: String) : TokenValue {
    init {
        require(unit in UNITS) { "非法尺寸单位: $unit（仅支持 dp/sp/px）" }
    }

    val dp: Float get() = value.takeIf { unit == "dp" } ?: error("尺寸单位为 $unit，不能按 dp 读取")
    val sp: Float get() = value.takeIf { unit == "sp" } ?: error("尺寸单位为 $unit，不能按 sp 读取")
    val px: Float get() = value.takeIf { unit == "px" } ?: error("尺寸单位为 $unit，不能按 px 读取")

    override fun toString(): String = "${value}${unit}"

    companion object {
        val UNITS = setOf("dp", "sp", "px")
    }
}

/** 时长值（内部统一毫秒） */
@Immutable
data class DurationValue(val ms: Float) : TokenValue {
    override fun toString(): String = "${ms}ms"
}

/** 数值（阈值/系数/比例/透明度） */
@Immutable
data class NumberValue(val value: Float) : TokenValue {
    override fun toString(): String = value.toString()
}

@Immutable
data class BooleanValue(val value: Boolean) : TokenValue

@Immutable
data class StringValue(val value: String) : TokenValue

/** 字阶（DTCG 原生复合型；fontSize 必填，其余可选） */
@Immutable
data class TypographyValue(
    val fontSize: DimensionValue,
    val lineHeight: DimensionValue? = null,
    val fontWeight: String? = null,
    val fontFamily: String? = null,
    val letterSpacing: DimensionValue? = null,
) : TokenValue

/** 投影（DTCG 原生复合型；color 必填，blur/offsetX/offsetY 可选） */
@Immutable
data class ShadowValue(
    val color: ColorValue,
    val blur: DimensionValue? = null,
    val offsetX: DimensionValue? = null,
    val offsetY: DimensionValue? = null,
) : TokenValue

/** 弹簧三参数（引擎自定义型，§6.3；damping 为阻尼比，≥1 无回弹） */
@Immutable
data class SpringValue(
    val stiffness: Float,
    val damping: Float,
    val initialVelocity: Float,
) : TokenValue

/** 玻璃材质配方（引擎自定义复合型，§6.8.3） */
@Immutable
data class GlassValue(
    val blur: DimensionValue,
    val saturation: Float,
    val tint: ColorValue,
    val tintAlpha: Float,
    val hairline: Boolean,
) : TokenValue

/** 三次贝塞尔（DTCG 原生，兼容 M3/Web 缓动曲线） */
@Immutable
data class CubicBezierValue(
    val x1: Float, val y1: Float, val x2: Float, val y2: Float,
) : TokenValue

/**
 * 引擎自定义复合型的字段 schema（§6.1.3/§6.8.3）。
 * 主题包 `$extensions.minime.schema` 的声明必须与此一致（导入期校验）。
 */
object CompositeSchemas {
    val SPRING: Map<String, TokenType> = linkedMapOf(
        "stiffness" to TokenType.NUMBER,
        "damping" to TokenType.NUMBER,
        "initialVelocity" to TokenType.NUMBER,
    )
    val GLASS: Map<String, TokenType> = linkedMapOf(
        "blur" to TokenType.DIMENSION,
        "saturation" to TokenType.NUMBER,
        "tint" to TokenType.COLOR,
        "tintAlpha" to TokenType.NUMBER,
        "hairline" to TokenType.BOOLEAN,
    )
    val TYPOGRAPHY: Map<String, TokenType> = linkedMapOf(
        "fontFamily" to TokenType.FONT_FAMILY,
        "fontSize" to TokenType.DIMENSION,
        "fontWeight" to TokenType.FONT_WEIGHT,
        "lineHeight" to TokenType.DIMENSION,
        "letterSpacing" to TokenType.DIMENSION,
    )
    val SHADOW: Map<String, TokenType> = linkedMapOf(
        "color" to TokenType.COLOR,
        "blur" to TokenType.DIMENSION,
        "offsetX" to TokenType.DIMENSION,
        "offsetY" to TokenType.DIMENSION,
    )

    /** 各复合型必填字段（spring/glass 全字段必填；typography/shadow 按 DTCG 语义） */
    val REQUIRED_FIELDS: Map<TokenType, Set<String>> = mapOf(
        TokenType.SPRING to SPRING.keys,
        TokenType.GLASS to GLASS.keys,
        TokenType.TYPOGRAPHY to setOf("fontSize"),
        TokenType.SHADOW to setOf("color"),
    )

    fun forType(type: TokenType): Map<String, TokenType>? = when (type) {
        TokenType.SPRING -> SPRING
        TokenType.GLASS -> GLASS
        TokenType.TYPOGRAPHY -> TYPOGRAPHY
        TokenType.SHADOW -> SHADOW
        else -> null
    }
}
