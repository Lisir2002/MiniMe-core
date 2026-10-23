package org.minime.core.motion

import org.minime.core.schema.DurationValue
import org.minime.core.schema.NumberValue
import org.minime.core.schema.SpringValue
import org.minime.core.tokens.InteractionTokens
import org.minime.core.tokens.MotionTokens

/**
 * 槽位 / 时长 / 参数的**唯一解析入口**（§6.3 数据面 → §6.4 规格表的桥）。
 *
 * 存在理由：动效消费方（组件层、theme-ui 工厂、M3 回喂适配层）都需要
 * 「槽位 → spring 三参数」「时长令牌 → 毫秒」「参数令牌 → 数值」这三类查找。
 * 若各消费方自行遍历子树，会出现 N 份解析逻辑（N 份漂移源）——本文件收敛为一份。
 *
 * **不改数据面**：本文件只读，不产生新真源；令牌路径是唯一入口。
 */

/** 槽位 → spring 三参数（[MotionSlot.NONE] 无动画，返回 null） */
fun MotionSlot.springOf(motion: MotionTokens): SpringValue? = when (this) {
    MotionSlot.SPATIAL_FAST -> motion.spatialFast
    MotionSlot.SPATIAL_DEFAULT -> motion.spatialDefault
    MotionSlot.SPATIAL_SLOW -> motion.spatialSlow
    MotionSlot.EFFECTS_FAST -> motion.effectsFast
    MotionSlot.EFFECTS_DEFAULT -> motion.effectsDefault
    MotionSlot.EFFECTS_SLOW -> motion.effectsSlow
    MotionSlot.NONE -> null
}

/** 时长令牌 → 毫秒 */
fun DurationToken.durationOf(motion: MotionTokens): DurationValue = when (this) {
    DurationToken.INSTANT -> motion.durationInstant
    DurationToken.FAST -> motion.durationFast
    DurationToken.DEFAULT -> motion.durationDefault
    DurationToken.SLOW -> motion.durationSlow
    DurationToken.MAX -> motion.durationMax
    DurationToken.SPINNER -> motion.durationSpinner
    DurationToken.SHIMMER -> motion.durationShimmer
}

/**
 * 令牌路径 → 交互子树数值。
 *
 * 路径形如 `interaction.shakeDamping`；子树键为去掉 `interaction.` 前缀后的末段。
 * 找不到即抛错——静默回退会让目录里的覆写令牌名变成谎言（铁律 8/9）。
 */
fun InteractionTokens.numberAt(path: String): NumberValue {
    val key = path.removePrefix("interaction.")
    require(path.startsWith("interaction.") && key != path) {
        "交互参数令牌路径必须形如 interaction.xxx，实际: $path"
    }
    val value = subtree[key] as? NumberValue
        ?: error("交互子树缺少数值令牌: $path")
    return value
}

/**
 * 模式的**有效阻尼比**：槽位默认阻尼，若目录标注了阻尼覆写令牌则取覆写值。
 *
 * 覆写令牌名来自目录（[MotionPattern.dampingOverrideToken]），
 * 消费方不得按模式 id 特判——否则目录失去唯一真源地位。
 */
fun MotionPattern.effectiveDamping(motion: MotionTokens, interaction: InteractionTokens): Float {
    val slotDamping = slot.springOf(motion)?.damping
        ?: error("模式 $id 的槽位 $slot 不承载动画，无法解析阻尼")
    val overrideToken = dampingOverrideToken ?: return slotDamping
    return interaction.numberAt(overrideToken).value
}
