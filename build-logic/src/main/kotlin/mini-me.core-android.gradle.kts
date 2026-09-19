/**
 * Android Library 核心模块约定（:core:security / :core:container / :core:database / :core:network）。
 *
 * 这类模块可以依赖 Android Framework（Keystore / Context / SQLite），但**禁止**反向依赖
 * feature.* / :app。它们只允许依赖 :core:model 与其它明确声明的 :core:* 模块。
 *
 * 与 :newui 保持一致的 SDK / JVM 版本口径（compileSdk=36, minSdk=26, jvmTarget=17）。
 */
plugins {
    id("com.android.library")
    kotlin("android")
}

android {
    namespace = "com.mini.me_core.${project.name.replace('-', '_')}"
    compileSdk = 36
    buildToolsVersion = "36.0.0"

    defaultConfig {
        minSdk = 26
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }

    lint {
        checkReleaseBuilds = false
        abortOnError = false
    }
}

kotlin {
    compilerOptions {
        jvmTarget.set(org.jetbrains.kotlin.gradle.dsl.JvmTarget.JVM_17)
    }
}

dependencies {
    implementation("org.jetbrains.kotlinx:kotlinx-coroutines-android:1.10.2")
    implementation("org.jetbrains.kotlinx:kotlinx-coroutines-core:1.10.2")
    implementation("org.jetbrains.kotlinx:kotlinx-serialization-json:1.8.1")

    // 各 :core:* Android 模块普遍带 @Inject/@Singleton/@ApplicationContext 注解（DI 标注）。
    // Hilt 图仍在 :app 聚合（不跑 ksp）；这里只提供注解 API，避免每模块重复声明。
    implementation("javax.inject:javax.inject:1")
    implementation("com.google.dagger:hilt-android:2.56.1")

    testImplementation("junit:junit:4.13.2")
    testImplementation("org.jetbrains.kotlinx:kotlinx-coroutines-test:1.10.2")
}
