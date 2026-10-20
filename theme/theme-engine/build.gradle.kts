plugins {
    alias(libs.plugins.kotlin.jvm)
}

// R1：纯 JVM 模块（schema/compiler/tokens 三包，零 UI 依赖）。
// §4.2 单模块结构中的 runtime/motion/shape/material/components/page 依赖 Compose，
// 将随 R2+ 以 :theme-ui（Android library）接入；引擎内核保持纯 JVM 可完整单测。
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
    compileOnly(libs.org.json)
    testImplementation(libs.org.json)
    testImplementation(libs.junit.jupiter)
    testRuntimeOnly(libs.junit.platform.launcher)
}

tasks.test {
    useJUnitPlatform()
}
