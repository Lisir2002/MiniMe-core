plugins {
    alias(libs.plugins.android.library)
    alias(libs.plugins.kotlin.android)
    alias(libs.plugins.kotlin.compose)
}

// R3.5：minime-ui——MiniMe 主程序的 UI 层引擎（白皮书 §10 Design Gallery 的可嵌入形态）。
//
// 定位：**示例页面群 + 可选导航部件**，以 Android library 存在，供接入方主程序依赖后
// 内置使用（要哪个页面嵌哪个）。本仓库不再有可安装 app（用户裁定：不做独立 UI App）。
//
// API 五原则（「按实际项目架构整理」）：
// 1. 页面即单元——pages/ 每个分区是独立 composable 入口，库内无导航状态；
// 2. 状态外置——ThemeManager 由宿主创建并传入（DebugPanelSection(manager)），库不建单例；
// 3. 零 Context 持有——库代码不得引用 Context / Activity / Application；
// 4. 可选聚合部件——nav/ 侧边栏与顶栏是「参考导航」，接入方通常自备导航；
// 5. 禁第二套实现——组件与绘制原语一律 import :theme-ui（§10.1 硬纪律 1）。
android {
    namespace = "org.minime.core.ui.gallery"
    compileSdk = 35

    defaultConfig {
        minSdk = 26
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }

    buildFeatures {
        compose = true
    }

    // 纯 JVM 单元测试（无需设备）——令牌覆盖机器核对 / Robolectric Compose UI 测试
    testOptions {
        unitTests.isReturnDefaultValues = true
        // Robolectric 需要真实合并清单（库模块默认 false 会落到 org.robolectric.default，
        // 导致 ComponentActivity 无法解析）——配合 src/debug/AndroidManifest.xml
        unitTests.isIncludeAndroidResources = true
    }

    // 示例页面自身须过 a11y（Lint 不当配置放过）
    lint {
        abortOnError = true
        warningsAsErrors = false
    }
}

kotlin {
    compilerOptions {
        jvmTarget.set(org.jetbrains.kotlin.gradle.dsl.JvmTarget.JVM_17)
    }
}

// Robolectric Compose UI 测试依赖 debug 源集的 ui-test-manifest（ComponentActivity 载体），
// release 变体无此清单——禁用 release 单测（Compose UI 测试只跑 debug variant）。
androidComponents {
    beforeVariants(selector().withBuildType("release")) { variant ->
        variant.enableUnitTest = false
    }
}

dependencies {
    api(project(":theme-ui"))

    implementation(platform(libs.androidx.compose.bom))
    implementation(libs.androidx.compose.runtime)
    implementation(libs.androidx.compose.ui)
    implementation(libs.androidx.compose.foundation)
    implementation(libs.androidx.material3)
    implementation(libs.androidx.core.ktx)
    implementation(libs.androidx.activity.compose)

    implementation(libs.kotlinx.coroutines.core)
    implementation(libs.kotlinx.coroutines.android)

    // ---- JVM 单元测试（无需设备） ----
    // 1) 令牌覆盖机器核对（§10.3：展示清单 × 词汇表双向集合相等）
    testImplementation(libs.junit.jupiter)
    testImplementation(libs.org.json)
    testImplementation(libs.kotlinx.coroutines.test)
    testRuntimeOnly(libs.junit.platform.launcher)

    // 2) Robolectric + Compose UI 测试：导航 / A/B / 主题切换 / 全量滚动
    //    （原 R3 仪器测试改造——删 app 后无 LAUNCHER 载体，改 JVM 自包含，
    //     真实 ThemeManager + 真实默认包 + 真实 composable 树，覆盖等价且 CI 可跑）
    testImplementation(libs.robolectric)
    testImplementation(platform(libs.androidx.compose.bom))
    testImplementation(libs.androidx.compose.ui.test.junit4)
    testImplementation(libs.androidx.test.ext.junit)
    testRuntimeOnly(libs.junit.vintage.engine) // JUnit Platform 跑 JUnit4 Robolectric 测试
    debugImplementation(libs.androidx.compose.ui.test.manifest) // createComposeRule 的 ComponentActivity 注册
}

tasks.withType<Test>().configureEach {
    useJUnitPlatform()
}
