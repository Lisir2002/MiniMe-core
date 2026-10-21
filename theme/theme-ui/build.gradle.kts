plugins {
    id("com.android.library")
    kotlin("android")
    kotlin("plugin.compose")
}

// R2：运行时 + 提供层 + M3 映射（Android library）。
// 引擎内核（:theme-engine，路径 :theme:theme-engine）保持纯 JVM，可脱离 Android 完整单测；
// 本模块只做「平台接线」。插件无版本声明——版本由主程序根 build.gradle.kts 统一管理
// （AGP 8.9.3 / Kotlin 2.2.21，与 :app 同源）；依赖版本走主程序 gradle/libs.versions.toml。
android {
    namespace = "org.minime.core.ui"
    compileSdk = 35

    defaultConfig {
        minSdk = 26
        // R2 验收需要仪器测试（换肤重组计数、冷启动首帧、导入耗时）
        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }

    buildFeatures {
        compose = true
    }

    // 纯 JVM 单元测试（无需设备）——覆盖 ThemeManager 状态机、覆写层、映射表
    testOptions {
        unitTests.isReturnDefaultValues = true
    }
}

kotlin {
    compilerOptions {
        jvmTarget.set(org.jetbrains.kotlin.gradle.dsl.JvmTarget.JVM_17)
    }
}

dependencies {
    api(project(":theme:theme-engine"))

    implementation(platform(libs.androidx.compose.bom))
    implementation(libs.androidx.compose.runtime)
    implementation(libs.androidx.compose.ui)
    implementation(libs.androidx.compose.foundation)
    implementation(libs.androidx.compose.material3)
    implementation(libs.androidx.core.ktx)

    implementation(libs.kotlinx.coroutines.core)
    implementation(libs.kotlinx.coroutines.android)

    // android.jar 自带 org.json **stub**（方法返回 null），会覆盖真实实现并让解析器拿到 null 的 sets。
    // 生产环境用平台 org.json（Android 自带），单元测试必须显式注入真实实现。
    testImplementation(libs.json)
    testImplementation(libs.junit.jupiter)
    testImplementation(libs.kotlinx.coroutines.test)

    // 仪器测试（R2 验收项：换肤重组计数 / 首帧即目标主题 / 导入耗时，需真机或模拟器）
    androidTestImplementation(platform(libs.androidx.compose.bom))
    androidTestImplementation(libs.androidx.compose.ui.test.junit4)
    androidTestImplementation(libs.androidx.test.ext.junit)
    androidTestImplementation(libs.androidx.test.runner)
    androidTestImplementation(libs.json) // android.jar 的 org.json 是 stub，必须注入真实实现
    androidTestImplementation(libs.kotlinx.coroutines.test)
    testRuntimeOnly(libs.junit.platform.launcher)
}

tasks.withType<Test>().configureEach {
    useJUnitPlatform()
}
