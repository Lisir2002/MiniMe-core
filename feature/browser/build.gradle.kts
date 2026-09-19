plugins {
    // Feature 模块：内置浏览器（BrowserController + 书签/历史/凭证/登录弹窗）。
    // 架构规则 #1：只依赖 :core:* 与 :newui；禁止 feature→feature 直接依赖
    // （对 feature.proxy / feature.workspace 的耦合已反转为端口，由 :app 注入）。
    id("mini-me.feature")
}

android {
    namespace = "com.mini.me_core.feature.browser"
}

dependencies {
    implementation(project(":core:model"))
    implementation(project(":core:security"))
    implementation(project(":newui"))
    // BrowserController 依赖：WebView 代理（WebViewFeature/ProxyController）、OkHttp、JSON。
    implementation("androidx.webkit:webkit:1.13.0")
    implementation("com.squareup.okhttp3:okhttp:4.12.0")
    implementation("org.jetbrains.kotlinx:kotlinx-serialization-json:1.8.1")
}
