import java.io.FileInputStream
import java.io.File as _File
import java.security.KeyStore
import java.security.MessageDigest
import java.util.Properties

plugins {
    id("com.android.application")
    kotlin("android")
    kotlin("plugin.compose")
    kotlin("plugin.serialization") version "2.4.21"
}

// ── P1-04 签名口令治理：复用主应用官方密钥（app/keystore.properties + app/minime.jks）──
// 不再在本文件硬编码 storePassword/keyPassword。配置期不读密钥内容、不失败；
// 缺密钥/指纹不符的 fail-closed 判断推迟到文件底部 gradle.taskGraph.whenReady 守卫。
// 各字段可由 keystore.properties 提供，也可由环境变量覆盖
// （KEYSTORE_STORE_FILE/KEYSTORE_STORE_PASSWORD/KEYSTORE_KEY_ALIAS/KEYSTORE_KEY_PASSWORD/EXPECTED_CERT_SHA256）。
val keystorePropertiesFile = rootProject.file("app/keystore.properties")
val keystoreProperties = Properties()
if (keystorePropertiesFile.exists()) {
    keystoreProperties.load(FileInputStream(keystorePropertiesFile))
}

fun ksProp(key: String, env: String): String? {
    val fromFile = keystoreProperties.getProperty(key)
    if (!fromFile.isNullOrBlank()) return fromFile.trim()
    val fromEnv = System.getenv(env)
    return fromEnv?.trim()?.takeIf { it.isNotEmpty() }
}

val releaseStoreFileName: String = ksProp("storeFile", "KEYSTORE_STORE_FILE") ?: "minime.jks"
val releaseJksFile: _File = rootProject.file("app/$releaseStoreFileName")
val releaseStorePassword: String? = ksProp("storePassword", "KEYSTORE_STORE_PASSWORD")
val releaseKeyAlias: String? = ksProp("keyAlias", "KEYSTORE_KEY_ALIAS") ?: "minime"
val releaseKeyPassword: String? = ksProp("keyPassword", "KEYSTORE_KEY_PASSWORD")
val releaseExpectedCertSha256: String? = ksProp("expectedCertSha256", "EXPECTED_CERT_SHA256")

// ── 引用主应用源码：构建时将需要的文件暂存到 generated 目录 ──
// 主应用文件只读、零改动；每次构建自动同步最新版本。
val referencedSrcDir = layout.buildDirectory.dir("generated/source/referenced/main")

val stageReferencedSources by tasks.registering(Copy::class) {
    from("../app/src/main/java")
    include(
        // 日志核心（11 个文件）
        "com/mini/me_core/core/util/FileLogger.kt",
        "com/mini/me_core/core/util/AILogger.kt",
        "com/mini/me_core/core/util/LogLineParser.kt",
        "com/mini/me_core/core/util/LogLevel.kt",
        "com/mini/me_core/core/util/LogConfig.kt",
        "com/mini/me_core/core/util/LogSanitizer.kt",
        "com/mini/me_core/core/util/LogStats.kt",
        "com/mini/me_core/core/util/Logger.kt",
        // 日志基础设施（日志层协调能力审计后新增，FileLogger/AILogger 依赖）：
        //   LogFiles        —— 文件命名 / 日期解析 / 排序 的单一真源
        //   LogLevelController —— 含 LogDirResolver（目录解析与权限口径的共用实现）
        //   DiagnosticCleanup  —— 过期日志 / 崩溃现场清理规则的共用实现
        "com/mini/me_core/core/util/LogFiles.kt",
        "com/mini/me_core/core/util/LogLevelController.kt",
        "com/mini/me_core/core/util/DiagnosticCleanup.kt",
        // 主题令牌（10 个文件）
        "com/mini/me_core/core/theme/tokens/PrimitiveColors.kt",
        "com/mini/me_core/core/theme/tokens/SemanticColors.kt",
        "com/mini/me_core/core/theme/tokens/ComponentTokens.kt",
        "com/mini/me_core/core/theme/tokens/PrimitiveSpacing.kt",
        "com/mini/me_core/core/theme/tokens/PrimitiveRadius.kt",
        "com/mini/me_core/core/theme/tokens/PrimitiveElevation.kt",
        "com/mini/me_core/core/theme/tokens/PrimitiveAlpha.kt",
        "com/mini/me_core/core/theme/tokens/CornerScale.kt",
        "com/mini/me_core/core/theme/tokens/AppThemeState.kt",
        "com/mini/me_core/core/theme/tokens/ThemeSettings.kt",
        // 主题组件（9 个文件）
        "com/mini/me_core/core/theme/components/AppTopAppBar.kt",
        "com/mini/me_core/core/theme/components/AppCard.kt",
        "com/mini/me_core/core/theme/components/AppChip.kt",
        "com/mini/me_core/core/theme/components/AppListItem.kt",
        "com/mini/me_core/core/theme/components/AppButton.kt",
        "com/mini/me_core/core/theme/components/AppSectionHeader.kt",
        "com/mini/me_core/core/theme/components/AppBadge.kt",
        "com/mini/me_core/core/theme/components/AppDivider.kt",
        "com/mini/me_core/core/theme/components/AppEmptyState.kt",
        // 主题入口（含 Spacing 定义）
        "com/mini/me_core/core/theme/MiniMeTheme.kt",
        // 动画缩放工具（AppBadge 依赖）
        "com/mini/me_core/core/theme/ScaledAnimation.kt",
    )
    into(referencedSrcDir)
}

android {
    namespace = "com.mini.logs"
    compileSdk = 36

    defaultConfig {
        applicationId = "com.mini.logs"
        minSdk = 26
        targetSdk = 28
        versionCode = 8
        versionName = "0.0.8"
        vectorDrawables { useSupportLibrary = true }
    }

    sourceSets {
        getByName("main") {
            java.srcDirs("src/main/java", referencedSrcDir)
        }
    }

    buildFeatures {
        compose = true
        buildConfig = true
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }

    kotlinOptions {
        jvmTarget = "17"
        freeCompilerArgs += listOf(
            "-opt-in=androidx.compose.material3.ExperimentalMaterial3Api",
            "-opt-in=androidx.compose.foundation.ExperimentalFoundationApi",
            "-opt-in=androidx.compose.animation.ExperimentalAnimationApi",
        )
    }

    packaging {
        resources {
            excludes += "/META-INF/{AL2.0,LGPL2.1}"
        }
    }

    lint {
        abortOnError = false
        checkReleaseBuilds = false
    }

    // 复用主应用官方签名密钥（app/minime.jks）——懒加载，绝不硬编码口令。
    // 配置期占位、不失败；缺密钥/指纹不符由文件底部 taskGraph.whenReady 守卫 fail-closed，不回退 debug。
    signingConfigs {
        create("release") {
            storeFile = releaseJksFile
            if (releaseStorePassword != null) storePassword = releaseStorePassword
            if (releaseKeyAlias != null) keyAlias = releaseKeyAlias
            if (releaseKeyPassword != null) keyPassword = releaseKeyPassword
            enableV1Signing = true
            enableV2Signing = true
        }
    }

    buildTypes {
        release {
            isMinifyEnabled = false
            signingConfig = signingConfigs.getByName("release")
        }
        debug {
            isMinifyEnabled = false
        }
    }
}

// 确保编译前先暂存引用源码
tasks.withType<org.jetbrains.kotlin.gradle.tasks.KotlinCompile>().configureEach {
    dependsOn(stageReferencedSources)
}

dependencies {
    // AndroidX Core
    implementation("androidx.core:core-ktx:1.19.1")
    implementation("androidx.activity:activity-compose:1.9.3")
    implementation("androidx.documentfile:documentfile:1.1.0")

    // Compose BOM
    implementation(platform("androidx.compose:compose-bom:2025.12.01"))
    implementation("androidx.compose.ui:ui")
    implementation("androidx.compose.ui:ui-graphics")
    implementation("androidx.compose.ui:ui-tooling-preview")
    implementation("androidx.compose.material3:material3")
    implementation("androidx.compose.animation:animation")
    implementation("androidx.compose.material:material-icons-core")
    implementation("androidx.compose.material:material-icons-extended")
    debugImplementation("androidx.compose.ui:ui-tooling")

    // Lifecycle + ViewModel
    implementation("androidx.lifecycle:lifecycle-viewmodel-compose:2.8.7")
    implementation("androidx.lifecycle:lifecycle-runtime-compose:2.8.7")

    // Navigation
    implementation("androidx.navigation:navigation-compose:2.10.2")

    // Coroutines
    implementation("org.jetbrains.kotlinx:kotlinx-coroutines-android:1.10.2")
    implementation("org.jetbrains.kotlinx:kotlinx-coroutines-core:1.10.2")

    // Kotlin Serialization（ThemeSettings.kt 依赖）
    implementation("org.jetbrains.kotlinx:kotlinx-serialization-json:1.12.0")

    // Gson（AILogger.kt 依赖）
    implementation("com.google.code.gson:gson:2.14.0")

    // Vico 图表
    implementation("com.patrykandpatrick.vico:compose:1.15.0")
    implementation("com.patrykandpatrick.vico:compose-m3:1.15.0")

    // Testing
    testImplementation("junit:junit:4.13.2")
    testImplementation("org.jetbrains.kotlinx:kotlinx-coroutines-test:1.10.2")
}

// ── P1-04 任务图守卫：release 产出 fail-closed ─────────────────────────────
// 仅当任务图含「:logviewer-app 模块产出正式包」的任务时才校验签名：
// :logviewer-app:assembleRelease / :logviewer-app:bundleRelease。
// 其他模块的中间任务、纯编译（compileReleaseKotlin）、lint、test 均不触发。
gradle.taskGraph.whenReady {
    val hasReleaseArtifactTask = allTasks.any { t ->
        val p = t.path
        p == ":logviewer-app:assembleRelease" || p == ":logviewer-app:bundleRelease"
    }
    if (!hasReleaseArtifactTask) return@whenReady

    fun failRelease(msg: String): Nothing = throw GradleException(
        "[P1-04 release 签名守卫] $msg\n" +
            "  → CI 需从 GitHub Secrets 恢复 app/keystore.properties（含 storePassword/keyAlias/keyPassword/expectedCertSha256）" +
            "与 app/minime.jks；本地出 release 包须从安全渠道获取官方密钥。"
    )

    if (!keystorePropertiesFile.exists()) {
        failRelease("缺少 app/keystore.properties。")
    }
    if (!releaseJksFile.exists()) {
        failRelease("缺少签名密钥文件 ${releaseJksFile.path}（app/minime.jks）。")
    }
    val storePw = releaseStorePassword
        ?: failRelease("缺少 storePassword（keystore.properties 或 KEYSTORE_STORE_PASSWORD）。")
    val keyAliasV = releaseKeyAlias
        ?: failRelease("缺少 keyAlias（keystore.properties 或 KEYSTORE_KEY_ALIAS）。")
    if (releaseKeyPassword == null) {
        failRelease("缺少 keyPassword（keystore.properties 或 KEYSTORE_KEY_PASSWORD）。")
    }
    val expectedRaw = releaseExpectedCertSha256
        ?: failRelease("缺少官方证书 SHA-256 白名单 expectedCertSha256（keystore.properties 或 EXPECTED_CERT_SHA256）。")

    val certEncoded = run {
        val errors = mutableListOf<String>()
        var encoded: ByteArray? = null
        for (type in listOf("PKCS12", "JKS")) {
            try {
                val ks = KeyStore.getInstance(type)
                FileInputStream(releaseJksFile).use { ks.load(it, storePw.toCharArray()) }
                val cert = ks.getCertificate(keyAliasV)
                    ?: error("别名 $keyAliasV 不存在于 $type")
                encoded = cert.encoded
                break
            } catch (e: Exception) {
                errors += "$type: ${e.message}"
            }
        }
        encoded ?: failRelease("无法从 $releaseJksFile 读取别名 $keyAliasV 的证书（${errors.joinToString("; ")}）。")
    }
    val actualSha256 = MessageDigest.getInstance("SHA-256").digest(certEncoded)
        .joinToString("") { "%02x".format(it) }
    val expectedNorm = expectedRaw.replace(":", "").replace(" ", "").trim().lowercase()

    if (!MessageDigest.isEqual(actualSha256.toByteArray(), expectedNorm.toByteArray())) {
        failRelease("签名证书 SHA-256 与官方白名单不匹配。\n    实际: $actualSha256\n    预期: $expectedNorm")
    }
}
