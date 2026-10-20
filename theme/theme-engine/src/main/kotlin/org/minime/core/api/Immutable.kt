package org.minime.core.api

/**
 * 快照稳定性契约标记（白皮书 §6.2：「全部 @Immutable data class，staticCompositionLocalOf 提供」）。
 *
 * 语义与 Jetpack Compose 的 `androidx.compose.runtime.Immutable` 一致：被标注的类型其公开属性
 * 在实例存活期内不变，消费方（Compose 重组 / 多线程读取）可安全缓存与共享。
 * 引擎侧由一致性测试与快照只读构造保证该契约成立（令牌只在换肤时整包替换，不做原地变更）。
 *
 * 依赖边界（R1 纯 JVM 决策）：本注解为引擎自定义标记，不引入 compose-runtime 依赖；
 * R2 起 `:theme-ui`（Compose 模块）接入时，可机械切换为
 * `androidx.compose.runtime.Immutable`（同为标记注解，仅换 import——届时快照类对
 * Compose 编译器的稳定性推断正式生效）。
 */
@Target(AnnotationTarget.CLASS)
@Retention(AnnotationRetention.BINARY)
@MustBeDocumented
annotation class Immutable
