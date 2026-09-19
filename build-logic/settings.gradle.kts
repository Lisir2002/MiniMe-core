// build-logic 是一个 included build（约定插件 + 架构守卫）。
// 它本身只解析插件与库，不承载业务源码。
rootProject.name = "build-logic"

dependencyResolutionManagement {
    repositories {
        google()
        mavenCentral()
        gradlePluginPortal()
    }
}
