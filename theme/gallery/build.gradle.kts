plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.kotlin.android)
    alias(libs.plugins.kotlin.compose)
}

// R3：Design Gallery——项目的第一个可运行界面（白皮书 §10）。
// 三重身份：设计系统活文档 / 每批 RC 验收面 / 终端用户主题工坊。
// 常驻可见（v1.9 决策 D10）：release 也包含本模块，非 debug 彩蛋。
//
// 模块边界（R3 细化，偏离记录见 CHANGELOG）：
// - gallery 只做「摆放 + 选择器」——一律 import :theme-ui 的组件与绘制原语，
//   禁第二套实现（§10.1 硬纪律 1）；
// - squircle / 发丝线等绘制原语在 :theme-ui（组件族 R6–R9 复用），不在本模块。
android {
    namespace = "org.minime.gallery"
    compileSdk = 35

    defaultConfig {
        applicationId = "org.minime.gallery"
        minSdk = 26
        targetSdk = 35
        versionCode = 1
        // 软件发版版本走项目四段式规范；当前未发版，此处仅为 APK 标识
        versionName = "0.0.0.dev"
        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }

    buildFeatures {
        compose = true
    }

    buildTypes {
        release {
            isMinifyEnabled = false
            // R10 再开混淆 + keep 规则（§10.9 体积与混淆要求）
            proguardFiles(getDefaultProguardFile("proguard-android-optimize.txt"))
        }
    }

    // Gallery 是产品页面（D10 常驻可见），自身须过 a11y：Lint 不当配置放过
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

dependencies {
    implementation(project(":theme-ui"))
    implementation(project(":theme-engine"))

    implementation(platform(libs.androidx.compose.bom))
    implementation(libs.androidx.compose.runtime)
    implementation(libs.androidx.compose.ui)
    implementation(libs.androidx.compose.foundation)
    implementation(libs.androidx.material3)
    implementation(libs.androidx.core.ktx)
    implementation(libs.androidx.activity.compose)

    implementation(libs.kotlinx.coroutines.core)
    implementation(libs.kotlinx.coroutines.android)

    // 纯 JVM 单元测试（无需设备）：squircle 数学 / 令牌覆盖机器核对 / 导航模型
    testImplementation(libs.junit.jupiter)
    testImplementation(libs.org.json)
    testImplementation(libs.kotlinx.coroutines.test)
    testRuntimeOnly(libs.junit.platform.launcher)

    // 仪器测试（R3 验收：侧边栏可达、A/B 并排、令牌页滚动稳定——需真机）
    androidTestImplementation(platform(libs.androidx.compose.bom))
    androidTestImplementation(libs.androidx.compose.ui.test.junit4)
    androidTestImplementation(libs.androidx.test.ext.junit)
    androidTestImplementation(libs.androidx.test.runner)
    androidTestImplementation(libs.kotlinx.coroutines.test)
}

tasks.withType<Test>().configureEach {
    useJUnitPlatform()
}
