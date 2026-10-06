package com.minime.template

import android.content.Context
import android.os.Process
import java.io.File
import java.io.FileWriter
import java.io.PrintWriter
import java.io.StringWriter
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/**
 * 崩溃日志捕获器
 *
 * 捕获未处理异常，将崩溃堆栈写入应用私有目录，便于后续排查。
 * 日志文件：/data/data/com.minime.template/crash/crash_yyyyMMdd_HHmmss.log
 */
class CrashHandler private constructor(
    private val context: Context
) : Thread.UncaughtExceptionHandler {

    private val defaultHandler = Thread.getDefaultUncaughtExceptionHandler()
    private val crashDir: File = File(context.filesDir.parentFile, "crash").apply { mkdirs() }

    override fun uncaughtException(thread: Thread, throwable: Throwable) {
        try {
            writeCrashLog(thread, throwable)
        } catch (_: Exception) {
            // 日志写入失败时静默，避免二次崩溃
        }

        // 交回系统默认处理（弹出崩溃对话框 / 退出进程）
        defaultHandler?.uncaughtException(thread, throwable)
    }

    private fun writeCrashLog(thread: Thread, throwable: Throwable) {
        val timestamp = SimpleDateFormat("yyyyMMdd_HHmmss", Locale.getDefault()).format(Date())
        val logFile = File(crashDir, "crash_${timestamp}.log")

        val stringWriter = StringWriter()
        val printWriter = PrintWriter(stringWriter)
        throwable.printStackTrace(printWriter)
        var cause = throwable.cause
        while (cause != null) {
            cause.printStackTrace(printWriter)
            cause = cause.cause
        }
        printWriter.close()

        val logContent = buildString {
            appendLine("===== MiniMe Template Crash Log =====")
            appendLine("时间: ${SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.getDefault()).format(Date())}")
            appendLine("进程ID: ${Process.myPid()}")
            appendLine("线程: ${thread.name} (id=${thread.id})")
            appendLine("应用版本: ${BuildConfig.VERSION_NAME} (${BuildConfig.VERSION_CODE})")
            appendLine("设备: ${android.os.Build.MANUFACTURER} ${android.os.Build.MODEL}")
            appendLine("系统: Android ${android.os.Build.VERSION.RELEASE} (API ${android.os.Build.VERSION.SDK_INT})")
            appendLine()
            appendLine("----- 崩溃堆栈 -----")
            appendLine(stringWriter.toString())
            appendLine("===== End =====")
        }

        FileWriter(logFile).use { it.write(logContent) }

        // 限制最多保留 10 个崩溃日志文件，避免占用过多空间
        val logs = crashDir.listFiles { _, name -> name.startsWith("crash_") && name.endsWith(".log") }
            ?.sortedBy { it.lastModified() } ?: return
        if (logs.size > MAX_LOG_FILES) {
            logs.take(logs.size - MAX_LOG_FILES).forEach { it.delete() }
        }
    }

    companion object {
        private const val MAX_LOG_FILES = 10

        fun init(context: Context) {
            Thread.setDefaultUncaughtExceptionHandler(CrashHandler(context.applicationContext))
        }

        /**
         * 获取所有崩溃日志文件（供调试或上报使用）
         */
        fun getCrashLogs(context: Context): List<File> {
            val crashDir = File(context.filesDir.parentFile, "crash")
            return crashDir.listFiles { _, name -> name.startsWith("crash_") && name.endsWith(".log") }
                ?.sortedByDescending { it.lastModified() } ?: emptyList()
        }
    }
}
