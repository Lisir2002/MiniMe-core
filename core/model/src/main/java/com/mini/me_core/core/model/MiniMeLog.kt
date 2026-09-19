package com.mini.me_core.core.model

/**
 * 跨模块日志门面（纯 Kotlin，无 Android 依赖）。
 *
 * 背景：FileLogger 依赖 Android Framework（Context / MediaStore），只能住在 :app。
 * 被搬离 :app 的 :core:* 模块（security / agent-workflow / container …）无法直接依赖它。
 * 这里提供一个无操作默认实现的门面：默认 no-op，由 :app 在启动时把 [FileLogger] 注册为 [sink]。
 *
 * 语义保持不变：在 :app 注册 sink 后，所有 core 模块的日志仍落盘到与原来一致的 FileLogger 文件。
 */
interface LogSink {
    /** level 同 [MiniMeLog] 的 V/D/I/W/E。 */
    fun log(level: Int, tag: String, message: String, throwable: Throwable?)
}

object MiniMeLog {
    const val V = 1
    const val D = 2
    const val I = 3
    const val W = 4
    const val E = 5

    @Volatile
    var sink: LogSink? = null

    @JvmStatic fun v(tag: String, message: String) = sink?.log(V, tag, message, null)
    @JvmStatic fun d(tag: String, message: String) = sink?.log(D, tag, message, null)
    @JvmStatic fun i(tag: String, message: String) = sink?.log(I, tag, message, null)
    @JvmStatic fun w(tag: String, message: String, throwable: Throwable? = null) =
        sink?.log(W, tag, message, throwable)
    @JvmStatic fun e(tag: String, message: String, throwable: Throwable? = null) =
        sink?.log(E, tag, message, throwable)

    /** 对齐 FileLogger.flushSync：在错误路径上强制 flush，保证崩溃前日志落盘。 */
    @JvmStatic fun flush(level: String, tag: String, message: String, throwable: Throwable?) {
        val lvl = when (level) {
            "ERROR" -> E
            "WARN" -> W
            "INFO" -> I
            "DEBUG" -> D
            else -> V
        }
        sink?.log(lvl, tag, message, throwable)
    }
}
