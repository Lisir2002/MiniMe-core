pluginManagement {
    // 立"牙"（Step 0）：build-logic 作为 included build，提供约定插件
    //   - mini-me.core-jvm     纯 Kotlin 核心模块（:core:model / :core:agent-workflow）
    //   - mini-me.core-android Android Library 核心模块（security/container/database/network）
    //   - mini-me.feature      feature 模块（agent/browser）
    // 架构依赖守卫由 build-logic 的 DependencyGuardTest 在 test 阶段强制（违规即失败）。
    includeBuild("build-logic")
    repositories {
        google()
        mavenCentral()
        gradlePluginPortal()
        maven { url = uri("https://jitpack.io") }
    }
    resolutionStrategy {
        eachPlugin {
            when (requested.id.id) {
                "com.android.application",
                "com.android.library",
                "com.android.dynamic-feature",
                "com.android.test" ->
                    useModule("com.android.tools.build:gradle:${requested.version}")
                "com.android.settings" ->
                    useModule("com.android.tools.build:gradle-settings-plugin:${requested.version}")
                "org.jetbrains.kotlin.android",
                "org.jetbrains.kotlin.jvm",
                "org.jetbrains.kotlin.multiplatform" ->
                    useModule("org.jetbrains.kotlin:kotlin-gradle-plugin:${requested.version}")
                "org.jetbrains.kotlin.plugin.compose" ->
                    useModule("org.jetbrains.kotlin:compose-compiler-gradle-plugin:${requested.version}")
                "org.jetbrains.kotlin.plugin.serialization" ->
                    useModule("org.jetbrains.kotlin:kotlin-serialization:${requested.version}")
                "com.google.devtools.ksp" ->
                    useModule("com.google.devtools.ksp:symbol-processing-gradle-plugin:${requested.version}")
                "com.google.dagger.hilt.android" ->
                    useModule("com.google.dagger:hilt-android-gradle-plugin:${requested.version}")
            }
        }
    }
}
dependencyResolutionManagement {
    repositoriesMode.set(RepositoriesMode.FAIL_ON_PROJECT_REPOS)
    repositories {
        google()
        mavenCentral()
        maven { url = uri("https://jitpack.io") }
    }
}
rootProject.name = "app"

include(":app")
include(":terminal-emulator")
include(":terminal-view")
// 独立设计系统 / 全新 UI 层模块（被 :app 依赖，见 app/build.gradle.kts 的 dependencies）
include(":newui")

// ── 增量重构（Step 0-6）抽出的模块 ─────────────────────────────────────
// 纯 Kotlin 共享层：跨 feature 纯领域模型 + 日志 facade + 审计常量。
include(":core:model")
// Android Library：凭据加密（MasterKey→DEK→AES-GCM）+ crypto + 凭据 domain 模型。
include(":core:security")
// 纯 Kotlin：工作流内核（LoopGuard + settings 反向端口）。
include(":core:agent-workflow")
// Android Library：容器生命周期进度解析 + 端口（LinuxContainerEngine 完整下沉为分阶段项）。
include(":core:container")
// Android Library：数据层（SQLDelight 多库 + SQLCipher + 迁移/自愈）下沉落点。
include(":core:database")

// Feature 层（规则 #1：只依赖 :core:* 与 :newui）。
// 内置浏览器（BrowserController 已反转为端口）。
include(":feature:browser")
// Agent 中枢落点（工具抽象/状态机已下沉 :core:agent-workflow；完整下沉分阶段）。
include(":feature:agent")
// 后续步骤追加：:core:agent-workflow / :core:container / :core:database
//              :feature:agent / :feature:browser
