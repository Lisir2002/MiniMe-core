package com.mini.me_core.core.performance

import android.content.Context
import android.os.Build
import com.mini.me_core.BuildConfig
import com.mini.me_core.core.util.LogSanitizer
import java.io.File
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/**
 * F6.4 应用内崩溃捕获与本地持久化（不上报任何服务器）。
 *
 * - 纯 object，在 attachBaseContext 阶段 [init] （早于 Hilt），与 FileLogger 同生命周期。
 * - 崩溃时由 MiniMeCore.installCrashHandler 调用 [record]，把设备信息 + 版本 + 堆栈写入
 *   app 私有目录 filesDir/crash_reports/，最多保留 [MAX_REPORTS] 条，超出删除最旧。
 * - [consumePendingCrash] 供下次启动 MainActivity 检测「上次异常退出」并弹窗查看。
 *
 * ⚠️ 安全（审计 D1）：崩溃报告是**最敏感的**诊断产物（含完整堆栈 + 设备 + 全部线程），
 * 而它会被同步导出到**公共** Downloads 目录，故落盘前统一过 [LogSanitizer] 脱敏，
 * 与 FileLogger「落盘必脱敏」策略保持同一口径。
 */
object CrashReporter {

    private const val DIR_NAME = "crash_reports"

    /**
     * 崩溃报告保留条数（审计 F2：纳入集中配置，不再散落硬编码）。
     */
    val MAX_REPORTS: Int get() = com.mini.me_core.core.util.LogConfig.maxCrashReports

    /**
     * 采集堆栈时最多列出的线程数（审计 E1）：崩溃处理必须在毫秒级完成，
     * `Thread.getAllStackTraces()` 会 STW 地枚举全部线程，重场景下拖慢杀进程前窗口。
     */
    private const val MAX_THREADS_IN_REPORT = 24

    @Volatile
    private var appContext: Context? = null

    fun init(context: Context) {
        if (appContext == null) {
            appContext = context.applicationContext
        }
    }

    private fun dir(): File? = appContext?.let { File(it.filesDir, DIR_NAME).apply { mkdirs() } }

    /** 记录一次未处理崩溃，返回写入的文件（失败返回 null）。 */
    fun record(thread: Thread, throwable: Throwable): File? {
        val base = dir() ?: return null
        return runCatching {
            val stamp = SimpleDateFormat("yyyyMMdd-HHmmss-SSS", Locale.US).format(Date())
            val file = File(base, "crash-$stamp.txt")
            val sb = StringBuilder()
            sb.appendLine("MiniMe-core Crash Report")
            sb.appendLine("time=${SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.US).format(Date())}")
            sb.appendLine("thread=${thread.name} (id=${thread.id})")
            sb.appendLine("pid=${android.os.Process.myPid()}")
            sb.appendLine("version=${BuildConfig.VERSION_NAME} code=${BuildConfig.VERSION_CODE} debug=${BuildConfig.DEBUG}")
            sb.appendLine("device=${Build.MANUFACTURER} ${Build.MODEL} sdk=${Build.VERSION.SDK_INT} release=${Build.VERSION.RELEASE}")
            val rt = Runtime.getRuntime()
            sb.appendLine("heapUsed=${(rt.totalMemory() - rt.freeMemory()) / (1024 * 1024)}MB heapMax=${rt.maxMemory() / (1024 * 1024)}MB")
            sb.appendLine("exception=${throwable.javaClass.name}: ${throwable.message}")
            sb.appendLine("-".repeat(60))
            val sw = java.io.StringWriter()
            throwable.printStackTrace(java.io.PrintWriter(sw))
            sb.append(sw.toString())
            sb.appendLine("-".repeat(60))
            sb.appendLine("All threads (最多 $MAX_THREADS_IN_REPORT 条):")
            // E1：限制线程数，避免在最需要快速退出的崩溃路径上做全量 STW 遍历。
            val stacks = Thread.getAllStackTraces()
            var printed = 0
            for ((name, stack) in stacks) {
                if (printed >= MAX_THREADS_IN_REPORT) {
                    sb.appendLine("  … 其余 ${stacks.size - printed} 个线程已省略")
                    break
                }
                printed++
                sb.appendLine("  Thread: $name")
                for (e in stack.take(8)) sb.appendLine("    at $e")
            }
            // D1：统一脱敏后再落盘（会被导出到公共目录）。
            file.writeText(LogSanitizer.sanitize(sb.toString()))
            trimOld()
            file
        }.getOrNull()
    }

    /** 列出全部崩溃报告（按时间倒序）。 */
    fun listReports(): List<File> {
        val base = dir() ?: return emptyList()
        return base.listFiles()?.filter { it.isFile && it.name.startsWith("crash-") }
            ?.sortedByDescending { it.name } ?: emptyList()
    }

    /** 删除全部报告。 */
    fun clearAll() {
        dir()?.listFiles()?.forEach { runCatching { it.delete() } }
    }

    private fun trimOld() {
        // 规则收敛于 DiagnosticCleanup（审计 F2），保留条数来自 LogConfig.maxCrashReports。
        com.mini.me_core.core.util.DiagnosticCleanup.trimCrashReports(dir())
    }

    /**
     * 检测并消费「上次异常退出」标记：返回最近一条崩溃报告内容，同时清除该标记。
     * MainActivity 首帧后调用，用于弹窗提示。
     */
    fun consumePendingCrash(): String? {
        val latest = listReports().firstOrNull() ?: return null
        val ctx = appContext ?: return null
        // 用一个标记文件记录「本次启动是否已提示过最近崩溃」。
        val marker = File(ctx.filesDir, "crash_reports/.consumed")
        val lastConsumed = marker.takeIf { it.exists() }?.readText()?.trim().orEmpty()
        if (lastConsumed == latest.name) return null
        marker.writeText(latest.name)
        return runCatching { latest.readText() }.getOrNull()
    }

    /** 读取指定崩溃报告全文（开发者选项查看详情用）。 */
    fun readReport(file: File): String? = runCatching { file.readText() }.getOrNull()
}
