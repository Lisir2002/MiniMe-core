plugins {
    kotlin("jvm")
}

// R1：纯 JVM 模块（schema/compiler/tokens 三包，零 UI 依赖）。
// §4.2 单模块结构中的 runtime/motion/shape/material/components/page 依赖 Compose，
// 将随 R2+ 以 :theme-ui（Android library）接入；引擎内核保持纯 JVM 可完整单测。
//
// 集成说明（R4 期 subtree 合入主程序）：本模块插件无版本声明——版本由主程序根
// build.gradle.kts 统一管理（kotlin("jvm") 2.2.21，与 kotlin.android 同源）；
// 主程序 settings.gradle.kts 的 pluginManagement.resolutionStrategy 负责将
// org.jetbrains.kotlin.jvm 映射到 kotlin-gradle-plugin 模块。依赖版本统一走
// 主程序 gradle/libs.versions.toml（org.json 用主 catalog 的 libs.json）。
kotlin {
    compilerOptions {
        jvmTarget.set(org.jetbrains.kotlin.gradle.dsl.JvmTarget.JVM_17)
    }
}

java {
    sourceCompatibility = JavaVersion.VERSION_17
    targetCompatibility = JavaVersion.VERSION_17
}

dependencies {
    // §4.2：JSON 解析用框架自带 org.json（Android 侧由平台提供 → compileOnly；
    // 纯 JVM 单测与未来 :theme-ui 的单元测试需要真实实现 → testImplementation）。
    // @Immutable 稳定性契约为引擎自定义标记（org.minime.core.api.Immutable），零外部注解依赖。
    compileOnly(libs.json)
    testImplementation(libs.json)
    testImplementation(libs.junit.jupiter)
    testRuntimeOnly(libs.junit.platform.launcher)
}

tasks.test {
    useJUnitPlatform()
}
