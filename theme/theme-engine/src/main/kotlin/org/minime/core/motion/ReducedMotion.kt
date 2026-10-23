package org.minime.core.motion

/**
 * 系统动效缩放档位（读 `Settings.Global.ANIMATOR_DURATION_SCALE`，由平台侧提供）。
 *
 * 与主题包级 `reducedMotion` 修饰符（R1 已有：编译期把六槽 spring 换成高阻尼）
 * 是**两级分离**的两级——本枚举管系统无障碍级（运行时全局降级）。
 */
enum class MotionScale {
    /** 系统关闭动画（无障碍「移除动画」；animator scale = 0） */
    OFF,

    /** 系统半开（animator scale ∈ (0,1)：用户已自行全局减速，引擎不叠加降级） */
    PARTIAL,

    /** 系统正常（animator scale ≥ 1） */
    NORMAL,
    ;

    companion object {
        /** 平台 animator duration scale → 档位（0=关 / (0,1)=半开 / ≥1=正常） */
        fun fromSystemValue(value: Float): MotionScale = when {
            value <= 0f -> OFF
            value < 1f -> PARTIAL
            else -> NORMAL
        }
    }
}

/** 降级决策的输出（消费侧据此选 AnimationSpec 形态） */
enum class EffectiveMotion {
    /** 正常：按模式槽位 + 时长令牌全规格播放 */
    FULL,

    /** 短 tween 无回弹，时长减半（spatial.* 降级态） */
    SHORT_TWEEN_HALF,

    /** 静态（spinner → 静态/呼吸淡入；shimmer → 静态灰底） */
    STATIC,

    /** 直切（无动效；视差/容器变换/滚动驱动插值降级态） */
    DIRECT,
}

/**
 * reducedMotion 降级决策器（§6.4.6 铁律 7 的系统级执行）。
 *
 * 纯函数、无平台依赖（theme-engine 纯 JVM 可完整单测）——平台侧只负责
 * 读系统 scale 调 [fromSystemValue] 后传入。
 *
 * 叠加策略（charter §4）：**系统级优先于主题包级**——系统关动画时，
 * 即使主题包编译快照已被 reducedMotion 修饰符高阻尼化，仍走本降级表
 * （系统无障碍意志高于皮肤选择）。
 */
object ReducedMotionPolicy {

    /**
     * 决策：系统档位 × 模式降级行为 → 生效动效形态。
     *
     * - [MotionScale.NORMAL] / [MotionScale.PARTIAL] → [EffectiveMotion.FULL]
     *   （PARTIAL 时系统已全局减速，引擎不叠加——尊重用户的中间档选择）；
     * - [MotionScale.OFF] → 按模式行为降级（[ReducedMotion.NONE] 的模式如触觉保留）。
     */
    fun resolve(scale: MotionScale, behavior: ReducedMotion): EffectiveMotion = when (scale) {
        MotionScale.NORMAL, MotionScale.PARTIAL -> EffectiveMotion.FULL
        MotionScale.OFF -> when (behavior) {
            ReducedMotion.SHORT_TWEEN_HALF -> EffectiveMotion.SHORT_TWEEN_HALF
            ReducedMotion.STATIC -> EffectiveMotion.STATIC
            ReducedMotion.DIRECT -> EffectiveMotion.DIRECT
            ReducedMotion.NONE -> EffectiveMotion.FULL
        }
    }

    /** 便捷重载：直接对目录模式决策 */
    fun resolve(scale: MotionScale, pattern: MotionPattern): EffectiveMotion =
        resolve(scale, pattern.reducedMotion)

    /** 降级态时长（铁律 7「时长减半」；非减半类目原样返回） */
    fun degradedDurationMs(specDurationMs: Int, effective: EffectiveMotion): Int = when (effective) {
        EffectiveMotion.SHORT_TWEEN_HALF -> specDurationMs / 2
        else -> specDurationMs
    }
}
