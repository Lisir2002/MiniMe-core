import org.gradle.api.JavaVersion

// build-logic：编译「预编译脚本插件」(src/main/kotlin/*.gradle.kts) + 架构守卫单测。
// 用 kotlin-dsl 让 src/main/kotlin 下的 *.gradle.kts 成为可被根工程 apply 的约定插件。
plugins {
    `kotlin-dsl`
}

repositories {
    google()
    mavenCentral()
    gradlePluginPortal()
}

// 约定插件在编译期要引用 AGP / Kotlin / KSP 类型。
// 预编译脚本插件要用 `id("com.android.library")` 这种无版本写法解析外部插件，
// 必须把插件 marker 放到 build-logic 的 runtime classpath（implementation，不是 compileOnly）。
// 版本与根 build.gradle.kts 保持一致。
dependencies {
    implementation("com.android.tools.build:gradle:8.9.3")
    implementation("org.jetbrains.kotlin:kotlin-gradle-plugin:2.2.21")
    implementation("org.jetbrains.kotlin:compose-compiler-gradle-plugin:2.2.21")
    implementation("com.google.devtools.ksp:symbol-processing-gradle-plugin:2.2.21-2.0.5")

    // 架构守卫单测（纯 JVM，读源码文本做依赖规则断言，无需 Android SDK）。
    testImplementation("junit:junit:4.13.2")
}

kotlin {
    compilerOptions {
        freeCompilerArgs.addAll("-opt-in=kotlin.RequiresOptIn")
    }
}

java {
    sourceCompatibility = JavaVersion.VERSION_17
    targetCompatibility = JavaVersion.VERSION_17
}
