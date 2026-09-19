plugins {
    // 纯 Kotlin JVM 核心模块：跨 feature 的纯领域模型 + 共享端口（日志/审计常量）。
    // 架构规则 #2：此模块不依赖 Android / datalayer / feature，可被任意 :core:* 与 feature 依赖。
    id("mini-me.core-jvm")
}

description = "跨 feature 纯领域模型 + 共享端口（日志 facade / 审计事件常量）。纯 Kotlin，不依赖 Android。"
