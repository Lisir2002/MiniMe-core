package org.minime.core.tokens

import org.minime.core.api.Immutable
import org.minime.core.api.CompileErrorCode
import org.minime.core.api.MiniMeEngine
import org.minime.core.api.ThemeCompileException
import org.minime.core.schema.BooleanValue
import org.minime.core.schema.ColorValue
import org.minime.core.schema.DimensionValue
import org.minime.core.schema.DurationValue
import org.minime.core.schema.GlassValue
import org.minime.core.schema.NumberValue
import org.minime.core.schema.SpringValue
import org.minime.core.schema.StringValue
import org.minime.core.schema.TokenValue
import org.minime.core.schema.TypographyValue

/**
 * 主题快照元信息（§5.1 manifest + 激活的修饰符上下文）。
 * 注意与 App 版本、主题包版本共同构成三条独立版本线（分批实施计划 §1）。
 */
@Immutable
data class ThemeMeta(
    val packName: String,
    val packVersion: String,
    val engineVersionRange: String,
    val activeModifiers: List<String>,
)

/**
 * 十大数据子系统（§6.1.2/§6.2）。
 * 各子系统持本层子树（路径相对子系统根），强类型访问器面向组件消费；
 * [raw] 是全量有效令牌表（fallback 基座 + 编译快照，R4 覆写层优先级链的第一二级）。
 */
@Immutable
data class ThemeSnapshot(
    val meta: ThemeMeta,
    val color: ColorTokens,
    val typography: TypographyTokens,
    val shape: ShapeTokens,
    val size: SizeTokens,
    val spacing: SpacingTokens,
    val motion: MotionTokens,
    val interaction: InteractionTokens,
    val elevation: ElevationTokens,
    val opacity: OpacityTokens,
    val glass: GlassTokens,
    val raw: Map<String, TokenValue>,
)

/**
 * 快照构建器：把「有效令牌表」装配为 [ThemeSnapshot]。
 *
 * 优先级链（与 R4 运行时覆写层同一模型）：**fallback 基座 < 编译快照**——
 * 调用方传入的 [effective] 已是「fallback 值 + 主题包编译值」的合并结果，
 * 因此任何被接受的包（含最小包）都能产出完整快照，词汇表缺项回填 fallback。
 */
object SnapshotBuilder {

    fun build(effective: Map<String, TokenValue>, meta: ThemeMeta): ThemeSnapshot {
        fun subtree(prefix: String): Map<String, TokenValue> =
            effective.filterKeys { it.startsWith("$prefix.") }.mapKeys { (k, _) -> k.removePrefix("$prefix.") }

        fun group(name: String): Map<String, TokenValue> =
            effective.filterKeys { it.startsWith("$name.") }.mapKeys { (k, _) -> k.removePrefix("$name.") }

        val colorSubtree = subtree("color")
        val color = ColorTokens(
            primitive = colorSubtree.filterKeys { it.startsWith("primitive.") }
                .mapKeys { (k, _) -> k.removePrefix("primitive.") }
                .mapValues { (k, v) -> typed<ColorValue>("color.primitive.$k", v) },
            semantic = colorSubtree.filterKeys { it.startsWith("semantic.") }
                .mapKeys { (k, _) -> k.removePrefix("semantic.") }
                .mapValues { (k, v) -> typed<ColorValue>("color.semantic.$k", v) },
            editor = group("editor"),
            terminal = group("terminal"),
            chart = group("chart"),
        )

        val opacitySubtree = subtree("opacity")
        val opacity = OpacityTokens(
            primitive = opacitySubtree.filterKeys { it.startsWith("primitive.") }
                .mapKeys { (k, _) -> k.removePrefix("primitive.") }
                .mapValues { (k, v) -> typed<NumberValue>("opacity.primitive.$k", v) },
            semantic = opacitySubtree.filterKeys { !it.startsWith("primitive.") }
                .mapValues { (k, v) -> typed<NumberValue>("opacity.$k", v) },
        )

        val motionSubtree = subtree("motion")
        val motion = MotionTokens(
            spatial = motionSubtree.filterKeys { it.startsWith("spatial.") }
                .mapKeys { (k, _) -> k.removePrefix("spatial.") }
                .mapValues { (k, v) -> typed<SpringValue>("motion.spatial.$k", v) },
            effects = motionSubtree.filterKeys { it.startsWith("effects.") }
                .mapKeys { (k, _) -> k.removePrefix("effects.") }
                .mapValues { (k, v) -> typed<SpringValue>("motion.effects.$k", v) },
            duration = motionSubtree.filterKeys { it.startsWith("duration.") }
                .mapKeys { (k, _) -> k.removePrefix("duration.") }
                .mapValues { (k, v) -> typed<DurationValue>("motion.duration.$k", v) },
        )

        return ThemeSnapshot(
            meta = meta,
            color = color,
            typography = TypographyTokens(subtree("typography")),
            shape = ShapeTokens(subtree("shape")),
            size = SizeTokens(subtree("size")),
            spacing = SpacingTokens(subtree("spacing")),
            motion = motion,
            interaction = InteractionTokens(subtree("interaction")),
            elevation = ElevationTokens(subtree("elevation")),
            opacity = opacity,
            glass = GlassTokens(subtree("glass")),
            raw = effective.toMap(),
        )
    }

    private fun fail(message: String): Nothing =
        throw ThemeCompileException(CompileErrorCode.SNAPSHOT_BUILD_FAILED, "快照构建失败: $message")

    /** 强类型转换（词汇表覆盖 + 导入类型校验保证成立；失败即内部缺陷，快速失败） */
    private inline fun <reified T : TokenValue> typed(path: String, value: TokenValue): T =
        value as? T ?: fail("令牌 $path 值类型不符: 期望 ${T::class.simpleName}，实际 ${value::class.simpleName}")
}

/** fallback 快照元信息（内置兜底主题，R11/G1） */
internal val FALLBACK_META = ThemeMeta(
    packName = "MiniMe Built-in Fallback",
    packVersion = MiniMeEngine.VERSION,
    engineVersionRange = MiniMeEngine.VERSION,
    activeModifiers = emptyList(),
)

// 访问器内部辅助（词汇表覆盖校验保证存在；缺失即引擎 bug，快速失败）
internal fun Map<String, TokenValue>.dimension(path: String): DimensionValue =
    this[path] as? DimensionValue ?: error("快照缺少 dimension 令牌: $path")

internal fun Map<String, TokenValue>.number(path: String): NumberValue =
    this[path] as? NumberValue ?: error("快照缺少 number 令牌: $path")

internal fun Map<String, TokenValue>.boolean(path: String): BooleanValue =
    this[path] as? BooleanValue ?: error("快照缺少 boolean 令牌: $path")

internal fun Map<String, TokenValue>.duration(path: String): DurationValue =
    this[path] as? DurationValue ?: error("快照缺少 duration 令牌: $path")

internal fun Map<String, TokenValue>.spring(path: String): SpringValue =
    this[path] as? SpringValue ?: error("快照缺少 spring 令牌: $path")

internal fun Map<String, TokenValue>.typography(path: String): TypographyValue =
    this[path] as? TypographyValue ?: error("快照缺少 typography 令牌: $path")

internal fun Map<String, TokenValue>.string(path: String): StringValue =
    this[path] as? StringValue ?: error("快照缺少 string 令牌: $path")

internal fun Map<String, TokenValue>.glass(path: String): GlassValue =
    this[path] as? GlassValue ?: error("快照缺少 glass 令牌: $path")

internal fun Map<String, TokenValue>.color(path: String): ColorValue =
    this[path] as? ColorValue ?: error("快照缺少 color 令牌: $path")
