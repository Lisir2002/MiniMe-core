package com.mini.logs.export

import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Environment
import androidx.core.content.FileProvider
import com.mini.logs.data.LogEntry
import com.mini.logs.util.FormatUtils
import java.io.File
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/**
 * 日志导出器。支持 TXT / Markdown 格式，含设备信息。
 */
object LogExporter {

    private val timestampFormat = SimpleDateFormat("yyyyMMdd_HHmmss", Locale.getDefault())

    /**
     * 导出选项。
     */
    data class ExportOptions(
        val format: ExportFormat = ExportFormat.TXT,
        val includeDeviceInfo: Boolean = true,
        val includeStats: Boolean = true,
        val includeMarks: Boolean = true,
        val includeFullStacktrace: Boolean = false,
        val scope: ExportScope = ExportScope.CURRENT_VIEW,
    )

    enum class ExportFormat { TXT, MARKDOWN }
    enum class ExportScope { CURRENT_VIEW, CURRENT_FILE, ALL_FILES }

    /**
     * 导出日志到 Downloads 目录。
     * @return 导出的文件，失败返回 null。
     */
    fun export(
        context: Context,
        entries: List<LogEntry>,
        options: ExportOptions = ExportOptions(),
        filterDescription: String = "全部",
    ): File? {
        val content = buildContent(entries, options, filterDescription)
        val fileName = "minime-logs-${timestampFormat.format(Date())}.${options.format.extension}"
        val downloadsDir = Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOWNLOADS)
        if (!downloadsDir.exists()) downloadsDir.mkdirs()
        val file = File(downloadsDir, fileName)
        return try {
            file.writeText(content)
            file
        } catch (e: Exception) {
            null
        }
    }

    /**
     * 系统分享导出的日志。
     */
    fun share(context: Context, file: File) {
        val uri: Uri = try {
            FileProvider.getUriForFile(context, "${context.packageName}.fileprovider", file)
        } catch (e: Exception) {
            Uri.fromFile(file)
        }
        val intent = Intent(Intent.ACTION_SEND).apply {
            type = "text/plain"
            putExtra(Intent.EXTRA_STREAM, uri)
            putExtra(Intent.EXTRA_SUBJECT, "MiniMe Logs 导出")
            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
        }
        context.startActivity(Intent.createChooser(intent, "分享日志").addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
    }

    /**
     * 构建导出内容。
     */
    fun buildContent(
        entries: List<LogEntry>,
        options: ExportOptions,
        filterDescription: String,
    ): String = when (options.format) {
        ExportFormat.TXT -> buildTxt(entries, options, filterDescription)
        ExportFormat.MARKDOWN -> buildMarkdown(entries, options, filterDescription)
    }

    private fun buildTxt(entries: List<LogEntry>, options: ExportOptions, filterDescription: String): String {
        return buildString {
            appendLine("=== MiniMe Logs 导出 ===")
            appendLine("导出时间: ${FormatUtils.formatDateTime(System.currentTimeMillis())}")
            if (options.includeDeviceInfo) {
                append(FormatUtils.deviceInfo())
            }
            appendLine("筛选: $filterDescription")
            appendLine("共 ${entries.size} 行")
            appendLine("========================")
            appendLine()
            for (entry in entries) {
                if (options.includeMarks && entry.isHighlighted) {
                    append("[MARK] ")
                }
                appendLine(entry.rawLine)
                if (entry.bookmarkNote != null && options.includeMarks) {
                    appendLine("  [书签] ${entry.bookmarkNote}")
                }
            }
        }
    }

    private fun buildMarkdown(entries: List<LogEntry>, options: ExportOptions, filterDescription: String): String {
        return buildString {
            appendLine("# MiniMe Logs 报告")
            appendLine()
            appendLine("**导出时间**: ${FormatUtils.formatDateTime(System.currentTimeMillis())}  ")
            if (options.includeDeviceInfo) {
                appendLine("**设备**: ${android.os.Build.MANUFACTURER} ${android.os.Build.MODEL} / Android ${android.os.Build.VERSION.RELEASE}  ")
            }
            appendLine("**筛选**: $filterDescription  ")
            appendLine("**共 ${entries.size} 行**  ")
            appendLine()

            if (options.includeStats) {
                val levelCounts = entries.groupBy { it.level?.name ?: "STACK" }
                    .mapValues { it.value.size }
                    .toSortedMap()
                appendLine("## 统计")
                appendLine()
                appendLine("| 等级 | 数量 |")
                appendLine("|------|------|")
                for ((level, count) in levelCounts) {
                    appendLine("| $level | $count |")
                }
                appendLine()
            }

            appendLine("## 日志详情")
            appendLine()
            appendLine("```")
            for (entry in entries) {
                if (options.includeMarks && entry.isHighlighted) {
                    append("[MARK] ")
                }
                appendLine(entry.rawLine)
                if (entry.bookmarkNote != null && options.includeMarks) {
                    appendLine("  [书签] ${entry.bookmarkNote}")
                }
            }
            appendLine("```")
        }
    }

    private val ExportFormat.extension: String
        get() = when (this) {
            ExportFormat.TXT -> "txt"
            ExportFormat.MARKDOWN -> "md"
        }
}
