package com.mini.me_core.core.performance

import android.content.Context
import android.os.Build
import com.mini.me_core.BuildConfig
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
 */
object CrashReporter {

    private const val DIR_NAME = "crash_reports"
    private const val MAX_REPORTS = 10

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
            sb.appendLine("All threads:")
            for ((name, stack) in Thread.getAllStackTraces()) {
                sb.appendLine("  Thread: $name")
                for (e in stack.take(8)) sb.appendLine("    at $e")
            }
            file.writeText(sb.toString())
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
        val reports = listReports()
        if (reports.size > MAX_REPORTS) {
            reports.drop(MAX_REPORTS).forEach { runCatching { it.delete() } }
        }
    }

    /**
     * 检测并消费「上次异常退出」标记：返回最近一条崩溃报告内容，同时清除该标记。
     * MainActivity 首帧后调用，用于弹窗提示。
     */
    fun consumePendingCrash(): String? {
        val latest = listReports().firstOrNull() ?: return null
        // 用一个标记文件记录「本次启动是否已提示过最近崩溃」。
        val marker = File(appContext!!.filesDir, "crash_reports/.consumed")
        val lastConsumed = marker.takeIf { it.exists() }?.readText()?.trim().orEmpty()
        if (lastConsumed == latest.name) return null
        marker.writeText(latest.name)
        return runCatching { latest.readText() }.getOrNull()
    }

    /** 读取指定崩溃报告全文（开发者选项查看详情用）。 */
    fun readReport(file: File): String? = runCatching { file.readText() }.getOrNull()
}
