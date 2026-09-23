package com.mini.me_core

/**
 * 主应用 BuildConfig 的兼容存根。
 *
 * 被引用的 FileLogger.kt 通过 `com.mini.me_core.BuildConfig.VERSION_NAME` 读取主应用版本号。
 * 附属应用独立编译时没有主应用的 BuildConfig，故在此提供同签名存根。
 * 运行时 FileLogger 用 runCatching 包裹，读不到时回退 "unknown"，不影响功能。
 */
object BuildConfig {
    const val VERSION_NAME: String = "1.0.0"
    const val DEBUG: Boolean = false
}
