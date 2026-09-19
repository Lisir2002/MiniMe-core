plugins {
    alias(libs.plugins.android.application) apply false
    alias(libs.plugins.kotlin.android) apply false
    alias(libs.plugins.kotlin.compose) apply false
    alias(libs.plugins.hilt.android) apply false
    alias(libs.plugins.ksp) apply false
    // 新数据层（data-layer-redesign）：SQLDelight 类型安全 SQL 生成
    alias(libs.plugins.sqldelight) apply false
}
