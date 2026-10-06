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
 * 捕获未处理异常，将崩溃堆栈写入外部存储公共目录，用户可直接通过文件管理器查看。
 * 主路径：/sdcard/Android/data/com.minime.template/files/crash/crash_yyyyMMdd_HHmmss.log
 * 备选路径（外部存储不可用时）：/data/data/com.minime.template/crash/
 */
class CrashHandler private constructor(
    private val context: Context
) : Thread.UncaughtExceptionHandler {

    private val defaultHandler = Thread.getDefaultUncaughtExceptionHandler()

    // 优先使用外部存储公共目录（用户可直接访问），不可用时回退到内部私有目录
    private val crashDir: File by lazy {
        val externalDir = context.getExternalFilesDir("crash")
        if (externalDir != null && (externalDir.exists() || externalDir.mkdirs())) {
            externalDir
        } else {
            File(context.filesDir.parentFile, "crash").apply { mkdirs() }
        }
    }

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
         * 优先读取外部存储公共目录，其次读取内部私有目录
         */
        fun getCrashLogs(context: Context): List<File> {
            val dirs = mutableListOf<File>()
            context.getExternalFilesDir("crash")?.let { dirs.add(it) }
            dirs.add(File(context.filesDir.parentFile, "crash"))

            return dirs.flatMap { dir ->
                dir.listFiles { _, name -> name.startsWith("crash_") && name.endsWith(".log") }?.toList() ?: emptyList()
            }.sortedByDescending { it.lastModified() }
        }
    }
}
