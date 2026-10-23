package org.minime.core.ui.motion

import androidx.compose.animation.core.AnimationSpec
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.FiniteAnimationSpec
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.runtime.ProvidableCompositionLocal
import androidx.compose.runtime.staticCompositionLocalOf
import org.minime.core.motion.EffectiveMotion
import org.minime.core.motion.MotionCatalog
import org.minime.core.motion.MotionPattern
import org.minime.core.motion.MotionScale
import org.minime.core.motion.ReducedMotionPolicy
import org.minime.core.motion.durationOf
import org.minime.core.motion.effectiveDamping
import org.minime.core.motion.springOf
import org.minime.core.tokens.InteractionTokens
import org.minime.core.tokens.MotionTokens

/**
 * 动效规格工厂（§6.4 目录 → Compose `AnimationSpec`）。
 *
 * **唯一转换点**：组件层不直接读令牌拼 `AnimationSpec`，一律经本工厂——
 * 否则每个组件各写一份 spring 参数拼装，就会出现 N 份真源（与「目录即契约」冲突）。
 *
 * 三条铁律在此落地：
 * - **铁律 2 禁 linear**：除循环动画（spinner/shimmer 的匀速周期）外一律 spring；
 * - **铁律 6 全 spring 可中断**：spring 的初速度单独随规格下发（手势速度交接）；
 * - **铁律 7 降级**：reducedMotion 生效时按目录降级表换 spec（短 tween 减半 / 直切 / 静态）。
 *
 * ## 时长令牌与 spring 物理的关系（重要边界）
 *
 * Compose 的 `spring` **没有 duration 参数**——spring 的收敛时长由刚度/阻尼决定，
 * 物理上不可硬约束。白皮书的「时长覆写令牌」在本层的语义因此被明确为：
 *
 * 1. **标称时长**（`durationMs`）——编排与时序基准（错峰 stagger、串行编排的等待量）；
 * 2. **降级基数**——reducedMotion 生效时短 tween 的时长 = 标称时长 ÷ 2；
 * 3. **循环周期**——spinner/shimmer 的匀速周期。
 *
 * 而「<100ms 回弹」这类**观感上限**由槽位刚度逼近（`spatial.fast` stiffness=800），
 * 不由 tween 硬切——tween 会牺牲铁律 6 的可中断性。此边界在此显式记录，
 * 避免后续误以为「时长令牌 = spring 的 duration」。
 */
class MotionSpecs(
    private val motion: MotionTokens,
    private val interaction: InteractionTokens,
    /** 系统级无障碍动画缩放（0 = 关闭）；PARTIAL 尊重用户中间档，不叠加降级 */
    private val scale: MotionScale = MotionScale.NORMAL,
) {

    /**
     * 取模式规格。
     *
     * 返回 `null` 表示**该模式在当前降级状态下不应产生动画**，消费方须直切/静态呈现：
     * - 无动画模式（`haptic.sync`）；
     * - 降级为「直切」（视差/容器变换）或「静态」（spinner/shimmer）的模式。
     *
     * 目录外 id 直接抛错（`MotionCatalog.byId`）——不静默回退到默认槽位。
     */
    fun of(patternId: String): MotionSpec<Float>? {
        val pattern = MotionCatalog.byId(patternId)
        val effective = ReducedMotionPolicy.resolve(scale, pattern)
        return when (effective) {
            EffectiveMotion.FULL -> fullSpec(pattern)
            // 降级为短 tween：无回弹、时长减半（铁律 7）
            EffectiveMotion.SHORT_TWEEN_HALF -> MotionSpec(
                patternId = pattern.id,
                spec = tween(
                    durationMillis = durationMsOf(pattern) / 2,
                    easing = FastOutSlowInEasing,
                ),
                durationMs = durationMsOf(pattern) / 2,
                initialVelocity = 0f,
                effective = effective,
            )
            // 直切 / 静态：消费方不播动画（返回 null 即契约）
            EffectiveMotion.STATIC, EffectiveMotion.DIRECT -> null
        }
    }

    /** 目录外 id 抛错版本：强制消费方显式处理目录契约 */
    fun require(patternId: String): MotionSpec<Float> =
        of(patternId) ?: error(
            "模式 $patternId 在当前 reducedMotion 状态下不应产生动画（直切/静态），请显式处理",
        )

    /** 模式标称时长（ms）——编排时序基准；不含降级减半 */
    fun durationMsOf(pattern: MotionPattern): Int {
        val token = pattern.durationToken
        val ms = token.durationOf(motion).ms
        require(ms >= 0f) { "模式 ${pattern.id} 的时长令牌 ${token.path} 非法: ${ms}ms" }
        return ms.toInt()
    }

    /** 全规格态的 spec 构造 */
    private fun fullSpec(pattern: MotionPattern): MotionSpec<Float>? {
        // 无动画模式（触觉同帧）：不产生 spec
        if (!pattern.slot.isAnimated) return null

        val nominal = durationMsOf(pattern)
        val slotSpring = pattern.slot.springOf(motion)
            ?: error("模式 ${pattern.id} 的槽位 ${pattern.slot} 不承载动画")

        // 循环动画（spinner/shimmer）：匀速周期是铁律 2 唯一（且必须）的 linear 例外
        if (pattern.durationToken.isCyclic) {
            return MotionSpec(
                patternId = pattern.id,
                spec = tween(durationMillis = nominal, easing = LinearEasing),
                durationMs = nominal,
                initialVelocity = 0f,
                effective = EffectiveMotion.FULL,
            )
        }

        // 阻尼可被目录标注的覆写令牌改写（action.error 的低阻尼自然衰减）
        val damping = pattern.effectiveDamping(motion, interaction)
        return MotionSpec(
            patternId = pattern.id,
            spec = spring(dampingRatio = damping, stiffness = slotSpring.stiffness),
            durationMs = nominal,
            // 初速度单独下发：Compose 的 spring() 不接受初速度，由 animateTo(initialVelocity=) 交接
            initialVelocity = slotSpring.initialVelocity,
            effective = EffectiveMotion.FULL,
        )
    }
}

/**
 * 一份动效规格（模式 + Compose spec + 时序信息）。
 *
 * @param patternId 目录中的模式 id（§6.4）
 * @param spec Compose 动画规格（spring / tween）
 * @param durationMs 标称时长（ms）；降级态为减半后的值
 * @param initialVelocity 初始速度（手势速度交接用；Compose spring 不接受，须由消费方传给 `animateTo`）
 * @param effective 生效的降级档位（全规格 / 短 tween）
 */
data class MotionSpec<T>(
    val patternId: String,
    val spec: FiniteAnimationSpec<T>,
    val durationMs: Int,
    val initialVelocity: Float,
    val effective: EffectiveMotion,
) {
    /** 便捷访问：直接拿到 Compose spec */
    val animationSpec: AnimationSpec<T> get() = spec
}

/**
 * 动效规格工厂的提供层（组件唯一消费面）。
 *
 * 与十子系统 local 同纪律：组件**不自己 new** `MotionSpecs`——
 * 统一从本 local 取，避免各处自行传入令牌快照导致规格漂移。
 */
val LocalMotionSpecs: ProvidableCompositionLocal<MotionSpecs> =
    staticCompositionLocalOf {
        // 未包裹 MiniMeTheme 时的兜底：内置 fallback 快照 + 全规格
        MotionSpecs(
            motion = org.minime.core.fallback.FallbackTheme.snapshot.motion,
            interaction = org.minime.core.fallback.FallbackTheme.snapshot.interaction,
            scale = MotionScale.NORMAL,
        )
    }
