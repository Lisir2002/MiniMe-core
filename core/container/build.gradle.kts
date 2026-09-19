plugins {
    // Android Library 核心模块：容器生命周期（proot/qemu 进度解析、并发策略、路径/bundle 网关端口）。
    // 依赖规则：只依赖 :core:model；对 feature.terminal / feature.workspace 的耦合由
    // ContainerPorts.kt 端口反转，实现由 :app 注入。
    id("mini-me.core-android")
}

android {
    namespace = "com.mini.me_core.core.container"
}

dependencies {
    implementation(project(":core:model"))
}
