plugins {
    // 纯 Kotlin JVM 模块：Agent 工作流内核——循环级 Guard + 上下文压缩纯逻辑 + 外部耦合端口。
    // 架构规则 #2：只允许依赖 :core:model；禁止 Android / datalayer / feature。
    // 对 feature.settings / core.network / core.util 的耦合全部反转为接口（见 WorkflowPorts.kt）。
    id("mini-me.core-jvm")
}

dependencies {
    implementation(project(":core:model"))
}
