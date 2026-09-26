package com.mini.me_core.feature.settings.presentation.components

import android.content.ContentValues
import android.content.Context
import android.os.Build
import android.os.Environment
import android.provider.MediaStore
import com.mini.me_core.core.util.FileLogger
import com.mini.me_core.feature.workspace.data.local.entity.RemoteAuditLogEntity
import dagger.hilt.android.qualifiers.ApplicationContext
import java.io.File
import java.io.FileOutputStream
import java.io.OutputStream
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import javax.inject.Inject
import javax.inject.Singleton

/**
 * 操作审计 CSV 导出器（MiniMe）。
 *
 * Android 10+ 走 MediaStore.Downloads；低版本写公共 Download 目录。
 * CSV 列：时间, 分类, 动作, 连接名, 主机, 成功, 消息。
 */
@Singleton
class AuditCsvExporter @Inject constructor(
    @param:ApplicationContext private val context: Context,
) {
    private companion object {
        const val TAG = "AuditCsvExporter"
        const val SUB_DIR = "MiniMe-core"
        const val MIME = "text/csv"
    }

    sealed interface Result {
        data object Success : Result
        data class Failure(val message: String) : Result
    }

    /** 导出日志列表为 CSV，返回 [Result]。 */
    fun export(logs: List<RemoteAuditLogEntity>): Result = runCatching {
        val timestamp = SimpleDateFormat("yyyyMMdd-HHmmss", Locale.US).format(Date())
        val name = "audit-$timestamp.csv"
        val content = buildCsv(logs)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            writeViaMediaStore(name, content)
        } else {
            writeViaLegacy(name, content)
        }
        Result.Success
    }.getOrElse {
        FileLogger.w(TAG, "导出审计 CSV 失败", it)
        Result.Failure(it.message ?: "unknown")
    }

    private fun buildCsv(logs: List<RemoteAuditLogEntity>): String {
        val timeFmt = SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.US)
        val sb = StringBuilder()
        sb.append("时间,分类,动作,连接名,主机,成功,消息\n")
        for (log in logs) {
            sb.append(timeFmt.format(Date(log.createdAt))).append(',')
                .append(csvCell(log.category)).append(',')
                .append(csvCell(AuditActionMapper.displayName(log.action))).append(',')
                .append(csvCell(log.connectionName)).append(',')
                .append(csvCell(log.remoteHost)).append(',')
                .append(if (log.success) "1" else "0").append(',')
                .append(csvCell(log.message)).append('\n')
        }
        return sb.toString()
    }

    /** CSV 单元格转义：逗号 / 引号 / 换行。 */
    private fun csvCell(raw: String?): String {
        if (raw.isNullOrEmpty()) return ""
        val needsQuote = raw.any { it == ',' || it == '"' || it == '\n' || it == '\r' }
        val escaped = raw.replace("\"", "\"\"")
        return if (needsQuote) "\"$escaped\"" else escaped
    }

    private fun writeViaMediaStore(name: String, content: String) {
        val resolver = context.contentResolver
        val values = ContentValues().apply {
            put(MediaStore.Downloads.DISPLAY_NAME, name)
            put(MediaStore.Downloads.MIME_TYPE, MIME)
            put(MediaStore.Downloads.RELATIVE_PATH, "Download/$SUB_DIR/")
            put(MediaStore.MediaColumns.IS_PENDING, 1)
        }
        val uri = resolver.insert(MediaStore.Downloads.EXTERNAL_CONTENT_URI, values)
            ?: throw IllegalStateException("MediaStore insert failed")
        try {
            resolver.openOutputStream(uri)?.use { it.write(content.toByteArray(Charsets.UTF_8)) }
                ?: throw IllegalStateException("MediaStore open stream failed")
        } finally {
            values.clear()
            values.put(MediaStore.MediaColumns.IS_PENDING, 0)
            resolver.update(uri, values, null, null)
        }
    }

    @Suppress("DEPRECATION")
    private fun writeViaLegacy(name: String, content: String) {
        val dir = File(
            Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOWNLOADS),
            SUB_DIR,
        )
        if (!dir.exists() && !dir.mkdirs()) throw IllegalStateException("cannot create dir")
        FileOutputStream(File(dir, name)).use {
            it.write(content.toByteArray(Charsets.UTF_8))
        }
    }
}
