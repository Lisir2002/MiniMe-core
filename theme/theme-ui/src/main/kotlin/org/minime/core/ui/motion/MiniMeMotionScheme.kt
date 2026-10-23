package org.minime.core.ui.motion

import androidx.compose.animation.core.FiniteAnimationSpec
import androidx.compose.animation.core.spring
import androidx.compose.runtime.ProvidableCompositionLocal
import androidx.compose.runtime.staticCompositionLocalOf
import org.minime.core.fallback.FallbackTheme
import org.minime.core.tokens.MotionTokens

/**
 * 动效方案（motion scheme）——本项目**稳定版**的六槽动效契约。
 *
 * ## 为什么要自建而不直接用 M3 `MotionScheme`
 *
 * R5 联网核验结论（2026-10-23，material3 **1.4.0 stable**，本项目 BOM 2025.12.01 所带版本）：
 *
 * | 事实 | 证据 |
 * |---|---|
 * | 1.4.0 的 `MotionScheme` **在 Kotlin 层是 `internal`** | 编译实证：`Cannot access 'interface MotionScheme': it is internal in file` |
 * | 1.4.0 的 `MaterialTheme(colorScheme, motionScheme, …)` 重载同样 **internal** | 编译实证：`it is internal in file` |
 * | `MotionScheme` 转为 **public** 是 **1.5.0-beta02**，且带 `@ExperimentalMaterial3ExpressiveApi` | 官方 API 参考页「Added in 1.5.0-beta02」；官方发布页示 1.4.0 为 stable、1.5.0-beta02 为 beta |
 *
 * 因此**在 material3 1.5 stable 之前，官方 `MotionScheme` 不可实现、不可传入**——
 * 「把六槽回喂 M3」这件事在 1.4.0 上没有可编译的实现路径。
 *
 * ## 本接口的角色
 *
 * 本接口是**回喂契约的稳定落点**：
 *
 * 1. **单一真源不变**——六槽 spring 值全部来自 [MotionTokens]，本接口只读不写；
 * 2. **版本解耦**——M3 侧是否提供官方 `MotionScheme` 不影响本项目的动效数据流；
 * 3. **桥接点已预留**——material3 1.5 stable 落地后，只需新增一个
 *    「`MiniMeMotionScheme` → 官方 `MotionScheme`」的桥接适配器（约 40 行），
 *    并把它传给 `MaterialTheme(motionScheme = …)`，本接口与其调用方**无需改动**。
 *
 * 命名刻意与官方接口区分（`MiniMe` 前缀），避免「看起来能用官方 API」的误读。
 */
interface MiniMeMotionScheme {

    /** 快速空间位移（位置/缩放/旋转）——对应 spatial.fast */
    fun fastSpatialSpec(): FiniteAnimationSpec<Float>

    /** 默认空间位移——对应 spatial.default */
    fun defaultSpatialSpec(): FiniteAnimationSpec<Float>

    /** 慢速空间位移——对应 spatial.slow */
    fun slowSpatialSpec(): FiniteAnimationSpec<Float>

    /** 快速效果（颜色/透明度）——对应 effects.fast */
    fun fastEffectsSpec(): FiniteAnimationSpec<Float>

    /** 默认效果——对应 effects.default */
    fun defaultEffectsSpec(): FiniteAnimationSpec<Float>

    /** 慢速效果——对应 effects.slow */
    fun slowEffectsSpec(): FiniteAnimationSpec<Float>
}

/**
 * 六槽 → [MiniMeMotionScheme] 的**只读**适配（唯一定义处）。
 *
 * - 单向：只从 [MotionTokens] 读，自身不持有独立状态；
 * - 逐槽直映：M3 的 speed × type 与本项目六槽同构，**不引入人格映射**
 *   （人格是「消费方对槽位的组合选择」，RC1 决策；此处映射会把双真源引入数据面）；
 * - 初速度不在此表达：Compose 的 `spring()` 不接受初速度，手势速度交接由
 *   [MotionSpecs] 的 `initialVelocity` 单独下发。
 */
fun MotionTokens.toMiniMeMotionScheme(): MiniMeMotionScheme = object : MiniMeMotionScheme {

    override fun fastSpatialSpec(): FiniteAnimationSpec<Float> =
        spring(dampingRatio = spatialFast.damping, stiffness = spatialFast.stiffness)

    override fun defaultSpatialSpec(): FiniteAnimationSpec<Float> =
        spring(dampingRatio = spatialDefault.damping, stiffness = spatialDefault.stiffness)

    override fun slowSpatialSpec(): FiniteAnimationSpec<Float> =
        spring(dampingRatio = spatialSlow.damping, stiffness = spatialSlow.stiffness)

    override fun fastEffectsSpec(): FiniteAnimationSpec<Float> =
        spring(dampingRatio = effectsFast.damping, stiffness = effectsFast.stiffness)

    override fun defaultEffectsSpec(): FiniteAnimationSpec<Float> =
        spring(dampingRatio = effectsDefault.damping, stiffness = effectsDefault.stiffness)

    override fun slowEffectsSpec(): FiniteAnimationSpec<Float> =
        spring(dampingRatio = effectsSlow.damping, stiffness = effectsSlow.stiffness)
}

/**
 * 动效方案的提供层（组件消费面）。
 *
 * 与十子系统 local 同纪律：组件从本 local 取动效方案，不自行构造——
 * 避免各处硬编码 spring 参数。
 */
val LocalMiniMeMotionScheme: ProvidableCompositionLocal<MiniMeMotionScheme> =
    staticCompositionLocalOf { FallbackTheme.snapshot.motion.toMiniMeMotionScheme() }
