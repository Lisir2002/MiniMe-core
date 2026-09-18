plugins {
    id("com.android.library")
    kotlin("android")
    kotlin("plugin.compose")
}

/**
 * :newui 模块版本独立声明：
 *
 * 本模块是独立设计系统库，其 versionName / versionCode 独立于 :app 模块，
 * 不随 app 的 versionCode 增长而变化。版本号用于未来将 newui 作为独立 AAR /
 *  Maven 产物发布时的版本标识。当前为 0.1.0（experimental），所有公共组件均标注
 *  `@since 0.1.0-experimental`，API 可能在后续 minor 版本中调整。
 */
android {
    namespace = "com.mini.me_core.newui"
    compileSdk = 36
    buildToolsVersion = "36.0.0"

    defaultConfig {
        minSdk = 26
        // Library 模块不设置 versionCode/versionName（仅 Application 模块支持）；
        // 模块版本号通过 @since 0.1.0-experimental 标注在组件 KDoc 中管理，
        // 未来独立发布 AAR 时由 Maven 发布配置指定版本。
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }

    @Suppress("DEPRECATION")
    kotlinOptions {
        jvmTarget = "17"
        freeCompilerArgs += listOf(
            "-opt-in=androidx.compose.material3.ExperimentalMaterial3Api",
            "-opt-in=androidx.compose.foundation.ExperimentalFoundationApi",
            "-opt-in=androidx.compose.animation.ExperimentalAnimationApi",
        )
    }

    buildFeatures {
        compose = true
        buildConfig = false
    }

    // :newui 是独立设计系统库，不走 app 的 lintVital；放行常用 deprecation 检查即可。
    lint {
        checkReleaseBuilds = false
        abortOnError = false
    }
}

dependencies {
    val composeBom = platform("androidx.compose:compose-bom:2025.12.01")
    implementation(composeBom)

    implementation("androidx.compose.ui:ui")
    implementation("androidx.compose.ui:ui-tooling-preview")
    implementation("androidx.compose.foundation:foundation")
    implementation("androidx.compose.material3:material3")
    implementation("androidx.compose.material:material-icons-core")
    implementation("androidx.compose.material:material-icons-extended")
    implementation("androidx.compose.animation:animation")
    implementation("androidx.compose.animation:animation-graphics")

    // 自适应导航（AppAdaptiveNav）：NavigationSuiteScaffold / 五断点
    implementation("androidx.compose.material3:material3-window-size-class")
    implementation("androidx.compose.material3:material3-adaptive-navigation-suite")
    implementation("androidx.activity:activity-compose")

    debugImplementation("androidx.compose.ui:ui-tooling")

    // 组件 UI 测试（Robolectric 上跑 Compose 语义树/手势，不需要真机）：
    // 用途：滑扫组件等手势分子的回归验证（按钮揭示、互斥收起等视觉/状态断言）。
    testImplementation(composeBom)
    testImplementation("androidx.compose.ui:ui-test-junit4")
    testImplementation("androidx.compose.ui:ui-test-manifest")
    testImplementation("org.robolectric:robolectric:4.14.1")

    // 视觉回归截图金标（UI-4b）：只给通用 component 拍金标，业务 composite 不拍。
    // 用法见 component/AppButtonsScreenshotTest.kt；CI 跑 verifyRoborazziDebug 对比金标。
    testImplementation("io.github.takahirom.roborazzi:roborazzi:1.29.0")
    testImplementation("io.github.takahirom.roborazzi:roborazzi-junit-rule:1.29.0")

    testImplementation("junit:junit:4.13.2")
}

android {
    testOptions {
        unitTests {
            // Robolectric + Compose UI 测试必需：让 unit test 能访问 res 与主题资源。
            isIncludeAndroidResources = true
        }
    }
}

/**
 * 令牌生成（Style Dictionary / DTCG）：把 newui/tokens/**/*.tokens.json
 * （W3C DTCG 2025.10 格式）经 style-dictionary build 直接写回已提交的 committed 产物
 * src/main/java/.../designsystem/token/generated/AppTokens.kt（单一事实源，IDE 可直接 import）。
 *
 * UI-5 修缮：不再每次编译硬挂 Node（改令牌频率极低，本地手动跑即可）；任务声明正规
 * inputs/outputs 由 Gradle 自动判断是否重跑；防漂移交给 verifyDesignTokens 在 CI 对账。
 * 依赖 node + npm；断网/无 node 时自动停用（沿用已提交产物，不拉垮整个构建）。
 */
val designTokenOut =
    file("src/main/java/com/mini/me_core/newui/designsystem/token/generated/AppTokens.kt")

/** 优先找 node 可执行路径（node / ~/.nvm/.../bin/node）。 */
fun findNodeBinary(): String? {
    val candidates = listOf(
        System.getenv("NODE_BIN"),
        "node",
        "/usr/local/bin/node",
        "/usr/bin/node",
        "/opt/homebrew/bin/node",
    )
    return candidates.firstOrNull { candidate ->
        candidate != null && runCatching {
            val p = ProcessBuilder(candidate, "--version")
                .redirectErrorStream(false).start()
            p.waitFor() == 0
        }.getOrDefault(false)
    }
}


tasks.register<Exec>("generateDesignTokens") {
    group = "ui"
    description = "用 Style Dictionary 从 tokens/*.json 生成令牌常量（写回 committed AppTokens.kt）"
    workingDir(projectDir)
    inputs.files(fileTree("tokens") { include("**/*.json") })
    outputs.file(designTokenOut)
    val nodeBin = findNodeBinary()
    val styleDictBin = file("node_modules/style-dictionary/bin/style-dictionary.js").absolutePath
    if (nodeBin == null || !file(styleDictBin).exists()) {
        logger.warn("(newui) 未找到 node 或 style-dictionary，跳过令牌生成；使用已提交的 generated/ 产物。")
        enabled = false
        return@register
    }
    // 直接调本地 style-dictionary 二进制；npm exec 会把 --config 误吞成 npm 参数，故不用。
    commandLine(nodeBin, styleDictBin, "build", "--config", "style-dictionary.config.js")
}

/**
 * 令牌对账（CI 用）：重新生成令牌后，若 committed AppTokens.kt 出现未提交 diff 即失败。
 * 本地：改完 tokens/*.json 跑 ./gradlew :newui:generateDesignTokens 后提交产物；
 * CI：跑 ./gradlew :newui:verifyDesignTokens 做门禁。
 */
tasks.register("verifyDesignTokens") {
    group = "ui"
    description = "校验 tokens 源与 committed AppTokens.kt 一致；不一致即失败（CI 对账门禁）"
    dependsOn("generateDesignTokens")
    doLast {
        val rel = designTokenOut.relativeTo(rootProjectDir).path
        val exit = ProcessBuilder("git", "diff", "--exit-code", "--", rel)
            .redirectErrorStream(true).start().apply { waitFor() }.exitValue()
        if (exit != 0) {
            throw GradleException(
                "tokens/*.json 已变更但 AppTokens.kt 未重新生成/提交。" +
                    "请执行 ./gradlew :newui:generateDesignTokens 后提交产物。"
            )
        }
        logger.lifecycle("\u2713 design tokens 与 committed 产物一致。")
    }
}
