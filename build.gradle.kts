plugins {
    id("com.android.application") version "8.9.3" apply false
    id("org.jetbrains.kotlin.android") version "2.2.21" apply false
    id("org.jetbrains.kotlin.plugin.compose") version "2.2.21" apply false
    id("com.google.dagger.hilt.android") version "2.56.1" apply false
    id("com.google.devtools.ksp") version "2.2.21-2.0.5" apply false
    // 新数据层（data-layer-redesign）：SQLDelight 类型安全 SQL 生成
    id("app.cash.sqldelight") version "2.2.1" apply false
    // detekt 静态代码质量检查
    id("io.gitlab.arturbosch.detekt") version "1.23.8" apply false
    // OWASP Dependency-Check：依赖 CVE 扫描（任务在 :app 模块配置，见 .github/workflows/dependency-check.yml）
    // 注意：12.x 起仅发布到 Gradle Plugin Portal（settings.gradle.kts 的 pluginManagement 已含 gradlePluginPortal）
    id("org.owasp.dependencycheck") version "12.1.8" apply false
}
