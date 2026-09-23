package com.mini.me_core.core.util

import android.Manifest
import android.content.ContentValues
import android.content.Context
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import android.os.Environment
import android.provider.MediaStore
import android.util.Log
import androidx.core.content.ContextCompat
import java.io.BufferedWriter
import java.io.File
import java.io.FileInputStream
import java.io.FileOutputStream
import java.io.OutputStream
import java.io.OutputStreamWriter
import java.io.PrintWriter
import java.io.StringWriter
import java.time.Instant
import java.util.concurrent.CountDownLatch
import java.util.concurrent.Executors
import java.util.concurrent.TimeUnit

/**
 * 把日志落盘到 App 的存储，方便在没有连接 adb 的情况下调试。实现 [Logger] 接口。
 *
 * 日志写入**公共外部存储** `Documents/MiniMe-core/logs/`（当 WRITE_EXTERNAL_STORAGE 权限已授予时），
 * 该目录在应用卸载后**仍然保留**，便于卸载后排查问题；权限未授予时回退到外部私有目录
 * `getExternalFilesDir/logs/`（不可用时再回退内部 `filesDir/logs/`），按天分文件
 * （log-yyyy-MM-dd.txt）。公共外部目录可通过文件管理器在
 * `/storage/emulated/0/Documents/MiniMe-core/logs/` 直接查看，无需 root。
 *
 * 写入管线（重构后）：
 *  - 调用线程构建「一行」（时间戳 / 等级 / Tag / **调用线程名** / 脱敏后的消息 + 堆栈），
 *    累加 [LogStats]，再投递到内存队列；
 *  - 单线程 ioExecutor 消费队列，常驻 [BufferedWriter] 批量写入；累积到
 *    [LogConfig.bufferSizeBytes] 或定时 [LogConfig.flushIntervalMs] 触发 flush，避免每行一次 open/close；
 *  - 单文件超过 [LogConfig.maxFileBytes] 时**非破坏性滚动**：log-yyyy-MM-dd.txt → .1 → .2 ...；
 *  - 每个新文件写格式头（格式版本 / app 版本 / pid），便于附属日志应用做版本联动。
 *
 * 所有写入镜像一份到 [android.util.Log]。支持按等级过滤：低于 [minLevel] 的日志一律跳过。
 * 使用前需在 [Application.onCreate] 调用一次 [init]；外部存储权限运行时授予后调用
 * [onExternalStorageGranted] 把目录切到公共存储。
 */
object FileLogger : Logger {

    private const val TAG = "FileLogger"

    private val ioExecutor = Executors.newSingleThreadExecutor { r ->
        Thread(r, "file-logger").apply { isDaemon = true }
    }
    private val flushScheduler = Executors.newSingleThreadScheduledExecutor { r ->
        Thread(r, "file-logger-flush").apply { isDaemon = true }
    }

    private val fileNameFormat = java.time.format.DateTimeFormatter
        .ofPattern("yyyy-MM-dd").withZone(java.time.ZoneId.systemDefault())
    private val timestampFormat = java.time.format.DateTimeFormatter
        .ofPattern("yyyy-MM-dd HH:mm:ss.SSS").withZone(java.time.ZoneId.systemDefault())

    init {
        // 定时把常驻 BufferedWriter flush 到磁盘，避免进程被杀时内存缓冲丢太多行。
        flushScheduler.scheduleWithFixedDelay({
            ioExecutor.execute { runCatching { flushWriter() } }
        }, LogConfig.flushIntervalMs, LogConfig.flushIntervalMs, TimeUnit.MILLISECONDS)
    }

    // ── 写入管线状态（仅在 ioExecutor 线程内读写；currentWriter/currentFile 用 @Volatile 供 flush() 读） ──
    private val pending = java.util.concurrent.ConcurrentLinkedQueue<String>()

    @Volatile
    private var logDir: File? = null

    @Volatile
    private var currentWriter: BufferedWriter? = null

    @Volatile
    private var currentFile: File? = null

    /** 自上次 flush 以来已写入 BufferedWriter 的字节数（仅 ioExecutor 线程访问）。 */
    private var bufferedBytes: Long = 0L

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

    /** 初始化日志目录。重复调用安全。 */
    fun init(context: Context) {
        if (logDir != null) return
        val dir = resolveLogDir(context)
        logDir = dir
        ioExecutor.execute { cleanupOldLogs(dir) }
        i(TAG, "FileLogger 初始化完成，日志目录: ${dir.absolutePath}")
    }

    /**
     * 外部存储权限在运行时被授予后调用，把日志目录切换到公共外部存储（卸载后仍保留）。
     * [init] 通常发生在 Application.onCreate（早于权限授予），因此需要在此处重新解析目录。
     */
    fun onExternalStorageGranted(context: Context) {
        val newDir = resolveLogDir(context)
        val current = logDir
        if (current == null || newDir.absolutePath != current.absolutePath) {
            logDir = newDir
            ioExecutor.execute {
                closeWriter()
                cleanupOldLogs(newDir)
            }
            i(TAG, "外部存储权限已授予，日志目录切换为: ${newDir.absolutePath}")
        }
    }

    /**
     * 解析日志目录：优先公共外部存储 `Documents/MiniMe-core/logs`（卸载后保留，需 WRITE_EXTERNAL_STORAGE
     * 权限，targetSdk=28 下可写）；权限未授予时回退外部私有目录，再回退内部存储。
     */
    @Suppress("DEPRECATION") // targetSdk=28 下 getExternalStoragePublicDirectory 仍可用且不受分区存储限制
    private fun resolveLogDir(context: Context): File {
        if (hasExternalStorageWrite(context)) {
            val base = Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOCUMENTS)
            val dir = File(File(base, LogConfig.publicRootDir), LogConfig.logSubdir)
            if (dir.exists() || dir.mkdirs()) return dir
        }
        // 回退：外部私有目录（卸载时清除，但无需权限）；再回退内部存储。
        val base = context.getExternalFilesDir(null) ?: context.filesDir
        return File(base, LogConfig.logSubdir).apply { mkdirs() }
    }

    private fun hasExternalStorageWrite(context: Context): Boolean =
        Environment.getExternalStorageState() == Environment.MEDIA_MOUNTED &&
            ContextCompat.checkSelfPermission(context, Manifest.permission.WRITE_EXTERNAL_STORAGE) ==
            PackageManager.PERMISSION_GRANTED

    // ── Logger 接口实现 ──

    override fun v(tag: String, message: String) {
        if (!shouldLog(LogLevel.VERBOSE)) return
        Log.v(tag, message)
        enqueue(LogLevel.VERBOSE, "VERBOSE", tag, message, null)
    }

    override fun d(tag: String, message: String) {
        if (!shouldLog(LogLevel.DEBUG)) return
        Log.d(tag, message)
        enqueue(LogLevel.DEBUG, "DEBUG", tag, message, null)
    }

    override fun i(tag: String, message: String) {
        if (!shouldLog(LogLevel.INFO)) return
        Log.i(tag, message)
        enqueue(LogLevel.INFO, "INFO", tag, message, null)
    }

    override fun w(tag: String, message: String, throwable: Throwable?) {
        if (!shouldLog(LogLevel.WARN)) return
        Log.w(tag, message, throwable)
        enqueue(LogLevel.WARN, "WARN", tag, message, throwable)
    }

    override fun e(tag: String, message: String, throwable: Throwable?) {
        if (!shouldLog(LogLevel.ERROR)) return
        Log.e(tag, message, throwable)
        enqueue(LogLevel.ERROR, "ERROR", tag, message, throwable)
    }

    override fun fatal(tag: String, message: String, throwable: Throwable?) {
        if (!shouldLog(LogLevel.FATAL)) return
        // logcat 没有 FATAL 等级，用 ERROR 镜像（Android 崩溃本身走 Error 级别）。
        Log.e(tag, message, throwable)
        enqueue(LogLevel.FATAL, "FATAL", tag, message, throwable)
    }

    /**
     * 耗时记录便捷方法：写一条 INFO 级日志 `"<label> 耗时 <ms>ms"`。
     * 调用方自行测量时长后传入；走正常的缓冲/过滤/脱敏管线。
     */
    fun timing(tag: String, label: String, durationMs: Long) {
        i(tag, "$label 耗时 ${durationMs}ms")
    }

    /** 主动把内存缓冲落到磁盘（异步执行并最多等待 2 秒）。退出/导出前调用以保证一致。 */
    fun flush() {
        val latch = CountDownLatch(1)
        ioExecutor.execute {
            runCatching { processQueue(); flushWriter() }
            latch.countDown()
        }
        runCatching { latch.await(2, TimeUnit.SECONDS) }
    }

    /** 返回当前日志目录，供日志查看器使用。 */
    fun getLogDir(): File? = logDir

    /** 暴露日志统计（按等级 / 按 Tag）。 */
    fun getStats(): LogStats = LogStats

    /**
     * 构建设备信息摘要字符串，供崩溃处理器在崩溃快照中附加。
     * 包含厂商/型号、系统版本、应用版本、进程 ID。
     */
    fun buildDeviceInfo(@Suppress("UNUSED_PARAMETER") context: Context): String = buildString {
        append("device=").append(Build.MANUFACTURER).append(' ').append(Build.MODEL).append('\n')
        append("android=").append(Build.VERSION.RELEASE)
            .append(" (sdk ").append(Build.VERSION.SDK_INT).append(")\n")
        append("app-version=").append(appVersionName()).append('\n')
        append("pid=").append(android.os.Process.myPid()).append('\n')
    }

    private fun appVersionName(): String =
        runCatching { com.mini.me_core.BuildConfig.VERSION_NAME }.getOrDefault("unknown")

    // ── 导出到公共外部存储（解决 Android/data 私有目录在文件管理器不可见的问题） ──

    // 公共导出目录：Download/MiniMe-core/logs/（卸载后仍保留）
    private val exportRelativePath: String get() = "Download/${LogConfig.publicRootDir}/${LogConfig.logSubdir}"

    /**
     * 把所有日志文件导出到公共外部存储 `Download/MiniMe-core/logs/`：
     *   - API 29+：MediaStore.Downloads 集合（免权限，文件管理器可见）；
     *   - API <29：legacy 公共 Download 目录（需 WRITE_EXTERNAL_STORAGE）。
     * [extraFile] 可附带一个额外文件（如崩溃快照 summary），名称形如 `crash-xxx.log`。
     * 返回导出成功的文件名列表；无日志且无额外文件时返回空列表。调用方负责捕获异常。
     *
     * 日志文件采用 **8KB 流式复制**，不再 readText 全量读入内存，避免低端设备 OOM。
     */
    fun exportLogsToDownloads(
        context: Context,
        extraFile: Pair<String, String>? = null
    ): List<String> {
        val exported = mutableListOf<String>()
        for (file in listLogFiles()) {
            val ok = publishToPublic(context, file.name) { out ->
                FileInputStream(file).use { it.copyTo(out, 8192) }
            }
            if (ok) exported.add(file.name)
        }
        if (extraFile != null) {
            val ok = publishToPublic(context, extraFile.first) { out ->
                out.write(extraFile.second.toByteArray(Charsets.UTF_8))
            }
            if (ok) exported.add(extraFile.first)
        }
        return exported
    }

    /** 把一个文件发布到公共 Download/MiniMe-core/logs/，[block] 负责往输出流写内容。 */
    private fun publishToPublic(
        context: Context,
        name: String,
        block: (OutputStream) -> Unit
    ): Boolean = runCatching {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            publishViaMediaStore(context, name, block)
        } else {
            publishViaLegacyFile(context, name, block)
        }
        true
    }.onFailure {
        android.util.Log.e(TAG, "导出日志到公共目录失败: $name", it)
    }.getOrDefault(false)

    private fun publishViaMediaStore(context: Context, name: String, block: (OutputStream) -> Unit) {
        val resolver = context.contentResolver
        val values = ContentValues().apply {
            put(MediaStore.Downloads.DISPLAY_NAME, name)
            put(MediaStore.Downloads.MIME_TYPE, "text/plain")
            put(MediaStore.Downloads.RELATIVE_PATH, "$exportRelativePath/")
            put(MediaStore.MediaColumns.IS_PENDING, 1)
        }
        val uri: Uri = resolver.insert(MediaStore.Downloads.EXTERNAL_CONTENT_URI, values)
            ?: throw IllegalStateException("MediaStore 插入失败")
        try {
            resolver.openOutputStream(uri)?.use(block)
                ?: throw IllegalStateException("MediaStore 打开输出流失败")
        } finally {
            values.clear()
            values.put(MediaStore.MediaColumns.IS_PENDING, 0)
            resolver.update(uri, values, null, null)
        }
    }

    @Suppress("DEPRECATION") // targetSdk=28 下 getExternalStoragePublicDirectory 仍可用
    private fun publishViaLegacyFile(context: Context, name: String, block: (OutputStream) -> Unit) {
        if (!hasExternalStorageWrite(context)) throw IllegalStateException("未授予存储权限")
        val base = Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOWNLOADS)
        val dir = File(File(base, LogConfig.publicRootDir), LogConfig.logSubdir)
        if (!dir.exists() && !dir.mkdirs()) throw IllegalStateException("无法创建导出目录")
        FileOutputStream(File(dir, name)).use(block)
    }

    // ── 紧急同步落盘（崩溃兜底） ──

    /**
     * 紧急同步落盘：不进 ioExecutor 队列，阻塞当前线程直接把一行写到今天的日志文件。
     * 仅用于「线程即将崩溃、进程马上被系统杀」这种最后一刻。ioExecutor 的排队任务
     * 会因进程被杀而全部丢失（这正是用户说「闪退拿不到日志」的根因），因此 CrashHandler
     * 必须绕过异步，保证 CRASH 记录在 return 前已经 fsync-ish 落盘。
     * 写入前同样经过脱敏；文件不存在 / 为空时先写格式头。
     */
    fun flushSync(level: String, tag: String, message: String, throwable: Throwable?) {
        val dir = logDir ?: return
        val now = Instant.now()
        val today = fileNameFormat.format(now)
        val threadName = Thread.currentThread().name
        val safeMessage = sanitizeText(message)
        val safeStack = throwable?.let { sanitizeText(stackTraceToString(it)) }
        val line = buildString {
            append(timestampFormat.format(now)).append(' ').append(level)
            append(" [").append(tag).append("]")
            append(" [thread:").append(threadName).append("] ").append(safeMessage)
            if (safeStack != null) append('\n').append(safeStack)
            append('\n')
        }
        runCatching { LogLevel.valueOf(level) }.getOrNull()?.let { LogStats.increment(it, tag) }
        runCatching {
            val file = File(dir, "log-$today.txt")
            FileOutputStream(file, true).use { fos ->
                OutputStreamWriter(fos, Charsets.UTF_8).use { writer ->
                    if (!file.exists() || file.length() == 0L) {
                        writer.write(headerText())
                    }
                    writer.write(line)
                    writer.flush()
                    // 尽力 force 到 OS（不保证 fsync，但对 Java IO 已尽力）。
                    fos.channel?.force(false)
                }
            }
        }.onFailure {
            // 紧急日志本身再失败，就只 logcat——此时 IO 基本挂了，也没法再兜
            android.util.Log.e(TAG, "紧急同步落盘失败", it)
        }
    }

    /** 返回当前所有日志文件（含滚动 .1/.2），按文件名（即日期）排序，供"查看日志"等界面使用。 */
    fun listLogFiles(): List<File> {
        val dir = logDir ?: return emptyList()
        return dir.listFiles { f -> f.isFile && f.name.startsWith("log-") && f.name.endsWith(".txt") }
            ?.sortedBy { it.name }
            ?: emptyList()
    }

    // ── 内存缓冲 + 批量写入（仅 ioExecutor 线程内执行） ──

    /** 调用线程侧：构建一行（含线程名 + 脱敏 + 堆栈），投递到队列并触发一次消费。 */
    private fun enqueue(levelEnum: LogLevel, level: String, tag: String, message: String, throwable: Throwable?) {
        if (logDir == null) return // 未初始化则只走 logcat，不落盘
        val now = Instant.now()
        val threadName = Thread.currentThread().name
        val safeMessage = sanitizeText(message)
        val safeStack = throwable?.let { sanitizeText(stackTraceToString(it)) }
        val line = buildString {
            append(timestampFormat.format(now)).append(' ').append(level)
            append(" [").append(tag).append("]")
            append(" [thread:").append(threadName).append("] ").append(safeMessage)
            if (safeStack != null) append('\n').append(safeStack)
            append('\n')
        }
        LogStats.increment(levelEnum, tag)
        pending.offer(line)
        ioExecutor.execute { processQueue() }
    }

    /** ioExecutor 侧：把队列里累积的行一次性写进常驻 BufferedWriter，按需滚动/定时 flush。 */
    private fun processQueue() {
        val dir = logDir ?: return
        val sb = StringBuilder()
        while (true) {
            val line = pending.poll() ?: break
            sb.append(line)
        }
        if (sb.isEmpty()) return
        runCatching {
            val today = fileNameFormat.format(Instant.now())
            // 目录切换 / 跨天 / writer 缺失 → 重开文件。
            if (currentWriter == null || currentFile == null ||
                currentFile!!.parentFile != dir ||
                currentFile!!.name != "log-$today.txt"
            ) {
                closeWriter()
                openLogFile(dir, today)
            }
            // 写这批会让文件超限 → 先滚动再写。
            if (currentFile!!.length() + sb.length > LogConfig.maxFileBytes) {
                rotateAndReopen(dir, today)
            }
            currentWriter!!.write(sb.toString())
            bufferedBytes += sb.length
            if (bufferedBytes >= LogConfig.bufferSizeBytes) {
                flushWriter()
            }
        }.onFailure {
            Log.e(TAG, "批量写入日志失败", it)
        }
    }

    /** 打开（或追加）当天日志文件；文件不存在 / 为空时先写格式头。仅 ioExecutor 线程调用。 */
    private fun openLogFile(dir: File, today: String) {
        val file = File(dir, "log-$today.txt")
        val needHeader = !file.exists() || file.length() == 0L
        val writer = BufferedWriter(
            OutputStreamWriter(FileOutputStream(file, true), Charsets.UTF_8),
            8192
        )
        if (needHeader) {
            writer.write(headerText())
        }
        writer.flush() // 头信息立即落盘，便于附属应用首行检测格式版本
        currentWriter = writer
        currentFile = file
        bufferedBytes = 0L
    }

    /**
     * 非破坏性滚动：把当前 log-today.txt 改名为 log-today.1.txt，
     * 已有 .1 顺延为 .2、.2 顺延为 .3 ... 然后打开一个新的当天文件（带头）。
     */
    private fun rotateAndReopen(dir: File, today: String) {
        closeWriter()
        // 找到当前已存在的最大滚动序号
        var maxIdx = 0
        while (File(dir, "log-$today.${maxIdx + 1}.txt").exists()) maxIdx++
        // 从高到低顺延：.maxIdx -> .(maxIdx+1) ... .1 -> .2
        for (i in maxIdx downTo 1) {
            File(dir, "log-$today.$i.txt")
                .renameTo(File(dir, "log-$today.${i + 1}.txt"))
        }
        // 当前活动文件 -> .1
        File(dir, "log-$today.txt")
            .renameTo(File(dir, "log-$today.1.txt"))
        openLogFile(dir, today)
    }

    private fun flushWriter() {
        runCatching { currentWriter?.flush() }
        bufferedBytes = 0L
    }

    private fun closeWriter() {
        runCatching { currentWriter?.flush(); currentWriter?.close() }
        currentWriter = null
        currentFile = null
        bufferedBytes = 0L
    }

    private fun headerText(): String = buildString {
        append("# MiniMe Log Format v").append(LogConfig.formatVersion).append('\n')
        append("# app-version: ").append(appVersionName()).append('\n')
        append("# pid: ").append(android.os.Process.myPid()).append('\n')
        append('\n')
    }

    private fun sanitizeText(text: String): String =
        if (LogConfig.enableSanitizer) LogSanitizer.sanitize(text) else text

    private fun stackTraceToString(throwable: Throwable): String {
        val sw = StringWriter()
        throwable.printStackTrace(PrintWriter(sw))
        return sw.toString().trimEnd()
    }

    /** 删除超过 [LogConfig.maxAgeDays] 天的日志文件（含滚动文件）。 */
    private fun cleanupOldLogs(dir: File) {
        val cutoff = System.currentTimeMillis() - LogConfig.maxAgeDays * 24L * 60 * 60 * 1000
        dir.listFiles { f -> f.isFile && f.name.startsWith("log-") && f.name.endsWith(".txt") }?.forEach { file ->
            if (file.lastModified() < cutoff) {
                runCatching { file.delete() }
            }
        }
    }
}
