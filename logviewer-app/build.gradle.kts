plugins {
    id("com.android.application")
    kotlin("android")
    kotlin("plugin.compose")
    kotlin("plugin.serialization") version "2.2.21"
}

// ── 引用主应用源码：构建时将需要的文件暂存到 generated 目录 ──
// 主应用文件只读、零改动；每次构建自动同步最新版本。
val referencedSrcDir = layout.buildDirectory.dir("generated/source/referenced/main")

val stageReferencedSources by tasks.registering(Copy::class) {
    from("../app/src/main/java")
    include(
        // 日志核心（8 个文件）
        "com/mini/me_core/core/util/FileLogger.kt",
        "com/mini/me_core/core/util/AILogger.kt",
        "com/mini/me_core/core/util/LogLineParser.kt",
        "com/mini/me_core/core/util/LogLevel.kt",
        "com/mini/me_core/core/util/LogConfig.kt",
        "com/mini/me_core/core/util/LogSanitizer.kt",
        "com/mini/me_core/core/util/LogStats.kt",
        "com/mini/me_core/core/util/Logger.kt",
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
        versionCode = 7
        versionName = "0.0.7"
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

    // 复用主应用官方签名密钥（app/minime.jks）
    signingConfigs {
        create("release") {
            storeFile = rootProject.file("app/minime.jks")
            storePassword = "9d4d4f44c1eaf5bcd57c68c5c4dab893"
            keyAlias = "minime"
            keyPassword = "9d4d4f44c1eaf5bcd57c68c5c4dab893"
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
    implementation("androidx.core:core-ktx:1.13.1")
    implementation("androidx.activity:activity-compose:1.9.3")
    implementation("androidx.documentfile:documentfile:1.0.1")

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
    implementation("androidx.navigation:navigation-compose:2.8.5")

    // Coroutines
    implementation("org.jetbrains.kotlinx:kotlinx-coroutines-android:1.10.2")
    implementation("org.jetbrains.kotlinx:kotlinx-coroutines-core:1.10.2")

    // Kotlin Serialization（ThemeSettings.kt 依赖）
    implementation("org.jetbrains.kotlinx:kotlinx-serialization-json:1.8.1")

    // Gson（AILogger.kt 依赖）
    implementation("com.google.code.gson:gson:2.10.1")

    // Vico 图表
    implementation("com.patrykandpatrick.vico:compose:1.15.0")
    implementation("com.patrykandpatrick.vico:compose-m3:1.15.0")

    // Testing
    testImplementation("junit:junit:4.13.2")
    testImplementation("org.jetbrains.kotlinx:kotlinx-coroutines-test:1.10.2")
}
