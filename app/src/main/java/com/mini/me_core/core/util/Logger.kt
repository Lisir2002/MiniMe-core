package com.mini.me_core.core.util

/**
 * 日志器抽象接口。
 *
 * [FileLogger]（落盘 + logcat 镜像）是其默认实现。抽象出接口的目的：
 *  - 便于在单元测试中替换为内存收集的 fake（见 [TestLogger]），不依赖 Android 文件系统；
 *  - 未来可扩展输出目标（网络上报 / 内存环形缓冲）而不改动调用方。
 *
 * 约定：实现类必须线程安全；低于实现自身阈值的日志可静默丢弃。
 */
interface Logger {

    fun v(tag: String, message: String)

    fun d(tag: String, message: String)

    fun i(tag: String, message: String)

    fun w(tag: String, message: String, throwable: Throwable? = null)

    fun e(tag: String, message: String, throwable: Throwable? = null)

    /** 最高严重等级（崩溃 / 致命错误）。默认实现可降级为 [e]。 */
    fun fatal(tag: String, message: String, throwable: Throwable? = null)
}

/**
 * 测试用内存日志器：把每条日志收集到 [entries]，不断 IO、不打 logcat。
 * 供需要断言「某 tag 是否记录了某消息」的单元测试使用。
 */
class TestLogger : Logger {

    data class Entry(
        val level: LogLevel,
        val tag: String,
        val message: String,
        val throwable: Throwable?,
    )

    val entries = mutableListOf<Entry>()

    override fun v(tag: String, message: String) { entries.add(Entry(LogLevel.VERBOSE, tag, message, null)) }
    override fun d(tag: String, message: String) { entries.add(Entry(LogLevel.DEBUG, tag, message, null)) }
    override fun i(tag: String, message: String) { entries.add(Entry(LogLevel.INFO, tag, message, null)) }
    override fun w(tag: String, message: String, throwable: Throwable?) {
        entries.add(Entry(LogLevel.WARN, tag, message, throwable))
    }
    override fun e(tag: String, message: String, throwable: Throwable?) {
        entries.add(Entry(LogLevel.ERROR, tag, message, throwable))
    }
    override fun fatal(tag: String, message: String, throwable: Throwable?) {
        entries.add(Entry(LogLevel.FATAL, tag, message, throwable))
    }

    fun clear() = entries.clear()
}
