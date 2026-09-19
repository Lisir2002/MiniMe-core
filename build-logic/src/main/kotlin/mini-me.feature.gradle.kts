/**
 * Feature 模块约定（:feature:agent / :feature:browser）。
 *
 * 架构规则 #1：feature 模块**只能**依赖 :core:*，禁止 feature → feature 直接依赖。
 * Hilt 仍在 :app 聚合，本模块不拆 Hilt 图（不在这里 apply hilt plugin；DI 由 :app 装配）。
 */
plugins {
    id("com.android.library")
    kotlin("android")
    kotlin("plugin.compose")
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

    buildFeatures {
        compose = true
    }

    lint {
        checkReleaseBuilds = false
        abortOnError = false
    }
}

kotlin {
    compilerOptions {
        jvmTarget.set(org.jetbrains.kotlin.gradle.dsl.JvmTarget.JVM_17)
        freeCompilerArgs.addAll(
            "-opt-in=androidx.compose.material3.ExperimentalMaterial3Api",
            "-opt-in=androidx.compose.foundation.ExperimentalFoundationApi",
            "-opt-in=androidx.compose.animation.ExperimentalAnimationApi",
        )
    }
}

dependencies {
    implementation("org.jetbrains.kotlinx:kotlinx-coroutines-android:1.10.2")
    implementation("org.jetbrains.kotlinx:kotlinx-coroutines-core:1.10.2")
    // @Inject/@Singleton/@ApplicationContext 注解 API（Hilt 图仍在 :app 聚合，不跑 ksp）。
    implementation("javax.inject:javax.inject:1")
    implementation("com.google.dagger:hilt-android:2.56.1")
    // Compose 运行时（本约定 apply compose compiler，类路径必须有 Compose Runtime）。
    val composeBom = platform("androidx.compose:compose-bom:2025.12.01")
    implementation(composeBom)
    implementation("androidx.compose.ui:ui")
    implementation("androidx.compose.material3:material3")
}
