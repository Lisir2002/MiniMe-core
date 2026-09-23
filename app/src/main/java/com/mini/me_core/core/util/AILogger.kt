package com.mini.me_core.core.util

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import android.os.Environment
import android.util.Log
import androidx.core.content.ContextCompat
import com.google.gson.GsonBuilder
import java.io.BufferedWriter
import java.io.File
import java.io.FileOutputStream
import java.io.OutputStreamWriter

import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.Executors
import java.util.concurrent.atomic.AtomicInteger

/**
 * AI 提供商「完整请求 / 响应」日志：**每个会话(sessionId)一个文件**，逐次详细落盘每一次
 * 调用的 URL、请求体(body) 与响应(response)，便于在没有抓包工具时离线诊断模型交互问题。
 *
 * 与 [FileLogger]（按天分文件的通用应用日志）相互独立：本类按「会话」维度归档，体量更大、
 * 内容更全（含完整对话历史、工具定义、原始 SSE 流），因此单独成文件、单独清理。
 *
 * 文件写入**公共外部存储** `Documents/MiniMe-core/ai-logs/session-<id>.log`（当 WRITE_EXTERNAL_STORAGE
 * 权限已授予时），该目录在应用卸载后**仍然保留**；权限未授予时回退外部私有目录
 * `getExternalFilesDir/ai-logs/`（不可用时再回退内部 `filesDir/ai-logs/`）。
 * 所有写入串行化到**独立**的单线程后台执行（与 [FileLogger] 互不阻塞）。
 *
 * 安全：写入前统一走 [LogSanitizer] 扫描——既打码 JSON/URL/内联中的 apiKey/token/password 等
 * 凭据，也把 base64 媒体大数据截断为占位符（原 [redactLargeMedia] 逻辑已并入 Sanitizer），
 * 不再依赖「请求体不含密钥」的口头假设。
 *
 * 使用前需在 [android.app.Application.onCreate] 调用一次 [init]；当外部存储权限在运行时被授予后，
 * 调用方（如 MainActivity 权限回调）应调用 [onExternalStorageGranted] 把日志目录切换到公共存储。
 */
object AILogger {

    private const val TAG = "AILogger"

    private val ioExecutor = Executors.newSingleThreadExecutor { r ->
        Thread(r, "ai-logger").apply { isDaemon = true }
    }
    private val timestampFormat = java.time.format.DateTimeFormatter
        .ofPattern("yyyy-MM-dd HH:mm:ss.SSS").withZone(java.time.ZoneId.systemDefault())
    // 与 Retrofit 的 GsonConverter 行为对齐（默认字段名、忽略 null），额外开启缩进便于阅读，
    // 关掉 HTML 转义避免把 prompt 里的 < > & 转成实体、影响可读性。
    private val gson = GsonBuilder().setPrettyPrinting().disableHtmlEscaping().create()

    @Volatile
    private var logDir: File? = null

    /** 每会话的调用计数：用于把同一次交互的 REQUEST / RESPONSE 配上同一序号。 */
    private val counters = ConcurrentHashMap<String, AtomicInteger>()

    /** 每会话常驻的 BufferedWriter（避免每次 open/close；活动会话数通常很少）。 */
    private val sessionWriters = ConcurrentHashMap<String, BufferedWriter>()

    /** 初始化日志目录。重复调用安全。 */
    fun init(context: Context) {
        if (logDir != null) return
        val dir = resolveLogDir(context)
        logDir = dir
        ioExecutor.execute { cleanupOldLogs(dir) }
        FileLogger.i(TAG, "AILogger 初始化完成，AI 会话日志目录: ${dir.absolutePath}")
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
                closeAllWriters()
                cleanupOldLogs(newDir)
            }
            FileLogger.i(TAG, "外部存储权限已授予，AI 会话日志目录切换为: ${newDir.absolutePath}")
        }
    }

    /**
     * 解析日志目录：优先公共外部存储 `Documents/MiniMe-core/ai-logs`（卸载后保留，需 WRITE_EXTERNAL_STORAGE
     * 权限，targetSdk=28 下可写）；权限未授予时回退外部私有目录，再回退内部存储。
     */
    @Suppress("DEPRECATION") // targetSdk=28 下 getExternalStoragePublicDirectory 仍可用且不受分区存储限制
    private fun resolveLogDir(context: Context): File {
        if (hasExternalStorageWrite(context)) {
            val base = Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOCUMENTS)
            val dir = File(File(base, LogConfig.publicRootDir), LogConfig.aiLogSubdir)
            if (dir.exists() || dir.mkdirs()) return dir
        }
        // 回退：外部私有目录（卸载时清除，但无需权限）；再回退内部存储。
        val base = context.getExternalFilesDir(null) ?: context.filesDir
        return File(base, LogConfig.aiLogSubdir).apply { mkdirs() }
    }

    private fun hasExternalStorageWrite(context: Context): Boolean =
        Environment.getExternalStorageState() == Environment.MEDIA_MOUNTED &&
            ContextCompat.checkSelfPermission(context, Manifest.permission.WRITE_EXTERNAL_STORAGE) ==
            PackageManager.PERMISSION_GRANTED

    /**
     * 记录一次请求的 URL 与请求体，并把本会话计数 +1（作为本次交互的序号）。
     * [body] 传请求对象（用 Gson 序列化为与上送一致的 JSON）或已序列化好的字符串。
     */
    fun logRequest(sessionId: String?, provider: String, model: String, method: String, url: String, body: Any?) {
        val n = counter(sessionId).incrementAndGet()
        val text = buildString {
            append('\n').append("=".repeat(78)).append('\n')
            append(now()).append("  REQUEST #").append(n)
            append("   [").append(provider).append(" / ").append(model).append("]\n")
            append(method).append(' ').append(url).append('\n')
            append("--- request body ---\n")
            append(stringify(body)).append('\n')
        }
        write(sessionId, text)
    }

    /** 记录一次非流式响应对象（用 Gson 序列化为 JSON）。 */
    fun logResponse(sessionId: String?, provider: String, body: Any?) {
        val text = buildString {
            append(now()).append("  RESPONSE #").append(counter(sessionId).get())
            append("   [").append(provider).append("]\n")
            append("--- response body ---\n")
            append(stringify(body)).append('\n')
        }
        write(sessionId, text)
    }

    /** 记录一次流式响应的原始 SSE 文本（由调用方按行累积后整体传入）。 */
    fun logResponseStream(sessionId: String?, provider: String, raw: String) {
        val text = buildString {
            append(now()).append("  RESPONSE #").append(counter(sessionId).get())
            append("   [").append(provider).append(" / stream]\n")
            append("--- raw SSE ---\n")
            append(raw.ifBlank { "(空响应)" })
            if (!raw.endsWith("\n")) append('\n')
        }
        write(sessionId, text)
    }

    /** 记录一次请求失败（取消不算失败，不应走到这里）。 */
    fun logError(sessionId: String?, provider: String, throwable: Throwable) {
        val text = buildString {
            append(now()).append("  ERROR #").append(counter(sessionId).get())
            append("   [").append(provider).append("]\n")
            append(throwable.javaClass.name).append(": ").append(throwable.message ?: "").append('\n')
        }
        write(sessionId, text)
    }

    private fun counter(sessionId: String?): AtomicInteger =
        counters.getOrPut(sessionId ?: "unknown") { AtomicInteger(0) }

    private fun now(): String = timestampFormat.format(java.time.Instant.now())

    private fun stringify(body: Any?): String = when (body) {
        null -> "null"
        is String -> body
        else -> runCatching { gson.toJson(body) }.getOrElse { body.toString() }
    }

    private fun sanitize(text: String): String =
        if (LogConfig.enableSanitizer) LogSanitizer.sanitize(text) else text

    private fun write(sessionId: String?, text: String) {
        val dir = logDir ?: return // 未初始化则直接丢弃，避免在无目录时报错刷屏
        val safeId = (sessionId ?: "unknown").replace(Regex("[^A-Za-z0-9_-]"), "_")
        val safeText = sanitize(text)
        ioExecutor.execute {
            runCatching {
                val file = File(dir, "session-$safeId.log")
                // 即将超限 → 非破坏性滚动（关闭旧 writer，rename .1/.2...）。
                if (file.exists() && file.length() + safeText.length > LogConfig.maxAiFileBytes) {
                    sessionWriters.remove(safeId)?.let { runCatching { it.flush(); it.close() } }
                    rotateSessionFile(dir, safeId)
                }
                val writer = sessionWriters.getOrPut(safeId) {
                    BufferedWriter(
                        OutputStreamWriter(FileOutputStream(file, true), Charsets.UTF_8),
                        8192
                    ).also { w ->
                        if (!file.exists() || file.length() == 0L) w.write(aiHeaderText())
                    }
                }
                writer.write(safeText)
                writer.flush() // AI 日志块较大且重要，逐块 flush 避免大块滞留内存。
            }.onFailure { Log.e(TAG, "写入 AI 会话日志失败", it) }
        }
    }

    /** session-<id>.log → .1，已有 .1 顺延 .2 ...（非破坏性）。 */
    private fun rotateSessionFile(dir: File, safeId: String) {
        var maxIdx = 0
        while (File(dir, "session-$safeId.${maxIdx + 1}.log").exists()) maxIdx++
        for (i in maxIdx downTo 1) {
            File(dir, "session-$safeId.$i.log")
                .renameTo(File(dir, "session-$safeId.${i + 1}.log"))
        }
        File(dir, "session-$safeId.log")
            .renameTo(File(dir, "session-$safeId.1.log"))
    }

    private fun aiHeaderText(): String =
        "# MiniMe AI Log Format v${LogConfig.formatVersion}\n"

    private fun closeAllWriters() {
        sessionWriters.values.forEach { runCatching { it.flush(); it.close() } }
        sessionWriters.clear()
    }

    /** 删除超过 [LogConfig.maxAgeDays] 天未更新的会话日志文件（含滚动文件）。 */
    private fun cleanupOldLogs(dir: File) {
        val cutoff = System.currentTimeMillis() - LogConfig.maxAgeDays * 24L * 60 * 60 * 1000
        dir.listFiles { f -> f.isFile && f.name.startsWith("session-") && f.name.endsWith(".log") }?.forEach { file ->
            if (file.lastModified() < cutoff) runCatching { file.delete() }
        }
    }
}
