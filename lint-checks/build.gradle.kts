// 自定义 Lint 检查模块（custom_lint 框架）。
// 该模块作为 lintChecks 依赖挂到 :app，:app:lintRelease 运行时自动加载本模块注册的所有 Issue。
//
// 依赖版本必须与 AGP 内置 lint 版本一致（当前为 31.9.3，见本地 gradle 缓存 / AGP 版本）。
// 升级 AGP 时需同步此处版本，否则 lint 加载期会因 API 不匹配报错。
plugins {
    id("java-library")
}

java {
    sourceCompatibility = JavaVersion.VERSION_17
    targetCompatibility = JavaVersion.VERSION_17
}

dependencies {
    compileOnly("com.android.tools.lint:lint-api:31.9.3")
    compileOnly("com.android.tools.lint:lint-checks:31.9.3")
    // UAST / PSI 由 lint-api 传递提供，无需额外声明。
}
