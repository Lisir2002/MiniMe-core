package com.mini.me_core.core.util

import android.content.ContentValues
import android.content.Context
import android.net.Uri
import android.os.Build
import android.os.Environment
import android.provider.MediaStore
import android.util.Log
import java.io.File
import java.io.FileOutputStream
import java.io.PrintWriter
import java.io.StringWriter

import java.util.Date
import java.util.Locale
import java.util.concurrent.Executors

/**
 * 日志等级，由低到高。[NONE] 用作阈值时关闭一切输出（没有任何等级 ≥ NONE）。
 *
 * 顺序即严重程度：阈值 [FileLogger.minLevel] 之下的日志（logcat 与落盘）都会被丢弃。
 */
enum class LogLevel {
    VERBOSE, DEBUG, INFO, WARN, ERROR, NONE
}

/**
 * 把日志落盘到 App 的存储，方便在没有连接 adb 的情况下调试。
 *
 * 日志写入**公共外部存储** `Documents/MiniMe-core/logs/`（当 WRITE_EXTERNAL_STORAGE 权限已授予时），
 * 该目录在应用卸载后**仍然保留**，便于卸载后排查问题；权限未授予时回退到外部私有目录
 * `getExternalFilesDir/logs/`（不可用时再回退内部 `filesDir/logs/`），按天分文件
 * （log-yyyy-MM-dd.txt）。公共外部目录可通过文件管理器在
 * `/storage/emulated/0/Documents/MiniMe-core/logs/` 直接查看，无需 root。
 * 所有写入都串行化到单线程后台执行，避免阻塞主线程与多线程交错。同时镜像一份到 [android.util.Log]。
 *
 * 支持按等级过滤：低于 [minLevel] 的日志（含 logcat 镜像与落盘）一律跳过。等级由
 * `LogSettingsRepository` 持久化、`AIEditorApp` 在启动与设置变更时通过 [setMinLevel] 同步。
 * 开发期默认 [LogLevel.VERBOSE]（全量记录）。
 *
 * 使用前需在 [Application.onCreate] 调用一次 [init]；当外部存储权限在运行时被授予后，
 * 调用方（如 MainActivity 权限回调）应调用 [onExternalStorageGranted] 把日志目录切换到公共存储。
 */
object FileLogger {

    private const val TAG = "FileLogger"
    private const val MAX_AGE_DAYS = 7
    private const val MAX_FILE_BYTES = 5 * 1024 * 1024 // 单个日志文件上限 5MB（VERBOSE 下增长较快）

    // 日志子目录名：公共外部存储 Documents/MiniMe-core/logs 与私有兜底 logs 共用（解析见 LogDirectoryResolver）
    private const val LOG_SUBDIR = "logs"

    private val ioExecutor = Executors.newSingleThreadExecutor { r ->
        Thread(r, "file-logger").apply { isDaemon = true }
    }
    private val fileNameFormat = java.time.format.DateTimeFormatter.ofPattern("yyyy-MM-dd").withZone(java.time.ZoneId.systemDefault())
    private val timestampFormat = java.time.format.DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss.SSS").withZone(java.time.ZoneId.systemDefault())

    @Volatile
    private var logDir: File? = null

    /** (c) 写入失败计数；连续失败≥10 由设置页黄条提示。成功一次清零。 */
    @Volatile
    private var consecutiveErrorCount: Int = 0

    val writeErrorCount: Int get() = consecutiveErrorCount

    private fun recordWriteSuccess() { consecutiveErrorCount = 0 }
    private fun recordWriteFailure() { consecutiveErrorCount++ }

    /** 当前最低记录等级；低于它的日志一律跳过。默认 VERBOSE（开发期全量）。 */
    @Volatile
    var minLevel: LogLevel = LogLevel.VERBOSE
        private set

    /** 设置最低记录等级（线程安全）。由设置项/启动同步调用。 */
    fun setMinLevel(level: LogLevel) {
        if (minLevel != level) {
            minLevel = level
            // 等级变更本身用 logcat 记录，避免被新阈值过滤掉
            Log.i(TAG, "日志等级切换为 $level")
        }
    }

    private fun shouldLog(level: LogLevel): Boolean = level.ordinal >= minLevel.ordinal

    /** (g) 隐私模式：开启后 minLevel=NONE 直接不落盘（仍 logcat 镜像）；关闭恢复默认 VERBOSE。 */
    @Volatile
    var privacyMode: Boolean = false
        private set

    /** (g/h) 开关持久化（SharedPreferences），启动读回。 */
    private var prefs: android.content.SharedPreferences? = null

    fun setPrivacyMode(enabled: Boolean) {
        privacyMode = enabled
        minLevel = if (enabled) LogLevel.NONE else LogLevel.VERBOSE
        prefs?.edit()?.putBoolean("privacy_mode", enabled)?.apply()
        Log.i(TAG, "隐私模式 ${"开启".takeIf { enabled } ?: "关闭"}")
    }

    fun setJsonLines(enabled: Boolean) {
        jsonLines = enabled
        prefs?.edit()?.putBoolean("json_lines", enabled)?.apply()
    }

    /** (h) 日志格式：true=JSON Lines（每行 {ts,level,tag,message,thread}），false=人类可读。 */
    @Volatile
    var jsonLines: Boolean = false
        private set

    fun setCustomTerms(terms: List<String>) {
        RedactionPipeline.setCustomTerms(terms)
        // (j) 持久化敏感词列表（换行拼接）。
        prefs?.edit()?.putString("custom_terms", terms.joinToString("\n"))?.apply()
    }

    /** (d) 清空所有日志：删私有目录 logs 与公共 Documents/MiniMe-core/logs、ai-logs。 */
    fun clearAllLogs(context: Context) {
        ioExecutor.execute {
            runCatching {
                logDir?.listFiles()?.forEach { runCatching { it.delete() } }
                LogDirectoryResolver.resolveLogDir(context, LOG_SUBDIR).listFiles()?.forEach { runCatching { it.delete() } }
                // 公共外部存储
                val docs = android.os.Environment.getExternalStoragePublicDirectory(android.os.Environment.DIRECTORY_DOCUMENTS)
                File(docs, "MiniMe-core/logs").listFiles()?.forEach { runCatching { it.delete() } }
                File(docs, "MiniMe-core/ai-logs").listFiles()?.forEach { runCatching { it.delete() } }
            }
        }
    }

    /** 初始化日志目录。重复调用安全。 */
    fun init(context: Context) {
        if (logDir != null) return
        val dir = LogDirectoryResolver.resolveLogDir(context, LOG_SUBDIR)
        logDir = dir
        // (g/h) 启动读回开关持久化。
        prefs = context.getSharedPreferences("file_logger_prefs", Context.MODE_PRIVATE)
        runCatching {
            setPrivacyMode(prefs?.getBoolean("privacy_mode", false) ?: false)
            setJsonLines(prefs?.getBoolean("json_lines", false) ?: false)
            prefs?.getString("custom_terms", null)?.takeIf { it.isNotBlank() }
                ?.let { RedactionPipeline.setCustomTerms(it.split("\n").filter(String::isNotBlank)) }
        }
        ioExecutor.execute { cleanupOldLogs(dir) }
        i(TAG, "FileLogger 初始化完成，日志目录: ${dir.absolutePath}")
    }

    /**
     * 外部存储权限在运行时被授予后调用，把日志目录切换到公共外部存储（卸载后仍保留）。
     * [init] 通常发生在 Application.onCreate（早于权限授予），因此需要在此处重新解析目录。
     */
    fun onExternalStorageGranted(context: Context) {
        val newDir = LogDirectoryResolver.resolveAfterPermissionGrant(context, LOG_SUBDIR, logDir) ?: return
        logDir = newDir
        ioExecutor.execute { cleanupOldLogs(newDir) }
        i(TAG, "外部存储权限已授予，日志目录切换为: ${newDir.absolutePath}")
    }

    fun v(tag: String, message: String) {
        if (!shouldLog(LogLevel.VERBOSE)) return
        Log.v(tag, message)
        write("VERBOSE", tag, message, null)
    }

    fun d(tag: String, message: String) {
        if (!shouldLog(LogLevel.DEBUG)) return
        Log.d(tag, message)
        write("DEBUG", tag, message, null)
    }

    fun i(tag: String, message: String) {
        if (!shouldLog(LogLevel.INFO)) return
        Log.i(tag, message)
        write("INFO", tag, message, null)
    }

    fun w(tag: String, message: String, throwable: Throwable? = null) {
        if (!shouldLog(LogLevel.WARN)) return
        Log.w(tag, message, throwable)
        write("WARN", tag, message, throwable)
    }

    fun e(tag: String, message: String, throwable: Throwable? = null) {
        if (!shouldLog(LogLevel.ERROR)) return
        Log.e(tag, message, throwable)
        write("ERROR", tag, message, throwable)
    }

    /** 返回当前日志目录，供日志查看器使用。 */
    fun getLogDir(): File? = logDir

    // ── 导出到公共外部存储（解决 Android/data 私有目录在文件管理器不可见的问题） ──

    // 公共导出目录：Download/MiniMe-core/logs/（卸载后仍保留）
    private const val EXPORT_ROOT_DIR = "MiniMe-core"
    private const val EXPORT_LOG_SUBDIR = "logs"
    private const val EXPORT_RELATIVE_PATH = "Download/MiniMe-core/logs"

    /**
     * 把所有日志文件导出到公共外部存储 `Download/MiniMe-core/logs/`：
     *   - API 29+：MediaStore.Downloads 集合（免权限，文件管理器可见）；
     *   - API <29：legacy 公共 Download 目录（需 WRITE_EXTERNAL_STORAGE）。
     * [extraFile] 可附带一个额外文件（如崩溃快照 summary），名称形如 `crash-xxx.log`。
     * 返回导出成功的文件名列表；无日志且无额外文件时返回空列表。调用方负责捕获异常。
     */
    fun exportLogsToDownloads(
        context: Context,
        extraFile: Pair<String, String>? = null
    ): List<String> {
        val exported = mutableListOf<String>()
        val files = listLogFiles()
        for (file in files) {
            val content = runCatching { file.readText() }.getOrNull() ?: continue
            if (writeToPublic(context, file.name, content)) exported.add(file.name)
        }
        if (extraFile != null) {
            if (writeToPublic(context, extraFile.first, extraFile.second)) {
                exported.add(extraFile.first)
            }
        }
        return exported
    }

    /** 写一个文件到公共 Download/MiniMe-core/logs/。API 29+ 走 MediaStore；API <29 走 legacy 目录。 */
    private fun writeToPublic(context: Context, name: String, content: String): Boolean = runCatching {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            writeViaMediaStore(context, name, content)
        } else {
            writeViaLegacyFile(context, name, content)
        }
        true
    }.onFailure {
        android.util.Log.e(TAG, "导出日志到公共目录失败: $name", it)
    }.getOrDefault(false)

    private fun writeViaMediaStore(context: Context, name: String, content: String) {
        val resolver = context.contentResolver
        val values = ContentValues().apply {
            put(MediaStore.Downloads.DISPLAY_NAME, name)
            put(MediaStore.Downloads.MIME_TYPE, "text/plain")
            put(MediaStore.Downloads.RELATIVE_PATH, "$EXPORT_RELATIVE_PATH/")
            put(MediaStore.MediaColumns.IS_PENDING, 1)
        }
        val uri: Uri = resolver.insert(MediaStore.Downloads.EXTERNAL_CONTENT_URI, values)
            ?: throw IllegalStateException("MediaStore 插入失败")
        try {
            resolver.openOutputStream(uri)?.use { it.write(content.toByteArray(Charsets.UTF_8)) }
                ?: throw IllegalStateException("MediaStore 打开输出流失败")
        } finally {
            values.clear()
            values.put(MediaStore.MediaColumns.IS_PENDING, 0)
            resolver.update(uri, values, null, null)
        }
    }

    @Suppress("DEPRECATION") // targetSdk=28 下 getExternalStoragePublicDirectory 仍可用
    private fun writeViaLegacyFile(context: Context, name: String, content: String) {
        if (!LogDirectoryResolver.hasExternalStorageWrite(context)) throw IllegalStateException("未授予存储权限")
        val base = Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOWNLOADS)
        val dir = File(File(base, EXPORT_ROOT_DIR), EXPORT_LOG_SUBDIR)
        if (!dir.exists() && !dir.mkdirs()) throw IllegalStateException("无法创建导出目录")
        FileOutputStream(File(dir, name)).use { it.write(content.toByteArray(Charsets.UTF_8)) }
    }

    /**
     * 紧急同步落盘：不进 ioExecutor 队列，阻塞当前线程直接把一行写到今天的日志文件。
     * 仅用于「线程即将崩溃、进程马上被系统杀」这种最后一刻。ioExecutor 的排队任务
     * 会因进程被杀而全部丢失（这正是用户说「闪退拿不到日志」的根因），因此 CrashHandler
     * 必须绕过异步，保证 CRASH 记录在 return 前已经 fsync-ish 落盘。
     */
    fun flushSync(level: String, tag: String, message: String, throwable: Throwable?) {
        val dir = logDir ?: return
        val now = java.time.Instant.now()
        val line = buildString {
            append(timestampFormat.format(now))
            append(" ").append(level)
            append(" [").append(tag).append("] ")
            append(message)
            if (throwable != null) {
                append("\n").append(stackTraceToString(throwable))
            }
            append("\n")
        }
        runCatching {
            val file = File(dir, "log-${fileNameFormat.format(now)}.txt")
            rotateIfNeeded(file)
            file.appendText(line)
            // 再尝试 flush 到 OS（不保证 fsync，但对 Java IO 已尽力），
            // 避免后续立即杀进程导致缓冲行丢失。
            runCatching { java.io.FileOutputStream(file, true).use { it.channel?.force(false) } }
        }.onFailure {
            // 紧急日志本身再失败，就只 logcat——此时 IO 基本挂了，也没法再兜
            android.util.Log.e(TAG, "紧急同步落盘失败", it)
        }
    }

    /** (b) 超 5MB 轮转：当前文件改名 <name>.1，.1->.2，最多保留 .1/.2/.3 三个轮转，再新建空文件。 */
    private fun rotateIfNeeded(file: File) {
        if (file.length() <= MAX_FILE_BYTES) return
        runCatching {
            for (i in 3 downTo 2) {
                val src = File(file.parentFile, file.nameWithoutExtension + ".$i.txt")
                val dst = File(file.parentFile, file.nameWithoutExtension + ".${i + 1}.txt")
                if (dst.exists()) dst.delete()
                if (src.exists()) src.renameTo(dst)
            }
            val one = File(file.parentFile, file.nameWithoutExtension + ".1.txt")
            if (one.exists()) one.delete()
            file.renameTo(one)
        }
    }

    /** 返回当前所有日志文件，按文件名（即日期）排序，供"查看日志"等界面使用。 */
    fun listLogFiles(): List<File> {
        val dir = logDir ?: return emptyList()
        return dir.listFiles { f -> f.isFile && f.name.startsWith("log-") }
            ?.sortedBy { it.name }
            ?: emptyList()
    }

    private fun write(level: String, tag: String, message: String, throwable: Throwable?) {
        val dir = logDir ?: return // 未初始化则只走 logcat，不落盘
        val now = java.time.Instant.now()
        // 落盘前统一过脱敏管线（F1）：消息与堆栈都脱敏，保留上下文。
        val safeMessage = RedactionPipeline.redact(message)
        val line = if (jsonLines) {
            // (h) JSON Lines：每行 {ts,level,tag,message,thread}
            val msg = if (throwable != null) safeMessage + "\n" + RedactionPipeline.redact(stackTraceToString(throwable)) else safeMessage
            "{\"ts\":\"${timestampFormat.format(now)}\",\"level\":\"$level\",\"tag\":\"$tag\",\"message\":\"${msg.replace("\"", "\\\"")}\",\"thread\":\"${Thread.currentThread().name}\"}\n"
        } else {
            buildString {
                append(timestampFormat.format(now))
                append(" ").append(level)
                append(" [").append(tag).append("] ")
                append(safeMessage)
                if (throwable != null) {
                    append("\n").append(RedactionPipeline.redact(stackTraceToString(throwable)))
                }
                append("\n")
            }
        }
        ioExecutor.execute {
            runCatching {
                val file = File(dir, "log-${fileNameFormat.format(now)}.txt")
                rotateIfNeeded(file)
                file.appendText(line)
            }.onSuccess { recordWriteSuccess() }
            .onFailure {
                recordWriteFailure()
                Log.e(TAG, "写入日志失败", it)
            }
        }
    }

    private fun stackTraceToString(throwable: Throwable): String {
        val sw = StringWriter()
        throwable.printStackTrace(PrintWriter(sw))
        return sw.toString().trimEnd()
    }

    /** (i) 超 7 天 gzip 归档为 .gz；.gz 再留 14 天后物理删。 */
    private fun cleanupOldLogs(dir: File) {
        val now = System.currentTimeMillis()
        val archiveCutoff = now - MAX_AGE_DAYS * 24L * 60 * 60 * 1000
        val deleteCutoff = now - (MAX_AGE_DAYS + 14L) * 24L * 60 * 60 * 1000
        dir.listFiles { f -> f.isFile }?.forEach { file ->
            runCatching {
                when {
                    // .gz 超 14 天物理删
                    file.name.endsWith(".gz") && file.lastModified() < deleteCutoff -> file.delete()
                    // 明文日志超 7 天 gzip 归档
                    !file.name.endsWith(".gz") && file.name.startsWith("log-") && file.lastModified() < archiveCutoff -> {
                        val gz = File(dir, file.name + ".gz")
                        java.util.zip.GZIPOutputStream(java.io.FileOutputStream(gz)).use { out ->
                            file.inputStream().use { it.copyTo(out) }
                        }
                        file.delete()
                    }
                }
            }
        }
    }
}
