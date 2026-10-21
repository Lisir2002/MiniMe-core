pluginManagement {
    repositories {
        google()
        mavenCentral()
        gradlePluginPortal()
        // 注：原此处声明了 https://jitpack.io，但项目无任何 jitpack（com.github.*）插件 /
        // 依赖使用它，属未用的第三方仓库（扩大供应链面）。按安全审计建议移除；
        // 如将来确需 jitpack 产物再恢复。
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
        // 注：原此处声明了 https://jitpack.io，但全项目无 com.github.* 依赖，属未用仓库，
        // 按安全审计建议移除。如将来确需再恢复。
    }
}
rootProject.name = "app"

include(":app")
include(":terminal-emulator")
include(":terminal-view")
include(":logviewer-app")
include(":lint-checks")
include(":app-template")

// 主题引擎（theme/ 子树仓库：R1–R3.5 已交付，R4+ 滚动迭代；白皮书 §4.2 / §10）。
// 三模块均为 library，:app 暂不依赖——引擎按接入期排期进入主程序 UI（存量迁移属接入期）。
// 模块路径前缀 theme:（如 :theme:theme-ui）；构建配置统一走根版本目录与本 wrapper（Gradle 8.14）。
include(":theme:theme-engine")
include(":theme:theme-ui")
include(":theme:minime-ui")
