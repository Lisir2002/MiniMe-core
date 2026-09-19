/**
 * 纯 Kotlin JVM 核心模块约定（:core:model / :core:agent-workflow）。
 *
 * 硬约束（架构规则 #2）：此模块类型**禁止**依赖 Android / datalayer / feature。
 * 它只允许依赖 :core:model。对外部世界的耦合必须反转为接口，由 :app / :feature:agent 注入实现。
 *
 * 该约束由 build-logic 的 DependencyGuardTest 在每次 test 时静态扫描源码强制；
 * 任何 `import android.*` / `import com.mini.me_core.feature.*` / `...datalayer.*` 都会让构建失败。
 */
plugins {
    id("org.jetbrains.kotlin.jvm")
}

kotlin {
    jvmToolchain(17)
    compilerOptions {
        freeCompilerArgs.addAll("-opt-in=kotlin.RequiresOptIn")
    }
}

dependencies {
    // 协程：状态机 / LoopGuard / 上下文压缩都跑在协程上。纯 JVM，不引入 android artifact。
    implementation("org.jetbrains.kotlinx:kotlinx-coroutines-core:1.10.2")
    // 跨模块纯领域模型序列化（消息 / 工具调用参数等）。
    implementation("org.jetbrains.kotlinx:kotlinx-serialization-json:1.8.1")

    testImplementation("junit:junit:4.13.2")
    testImplementation("org.jetbrains.kotlinx:kotlinx-coroutines-test:1.10.2")
}
