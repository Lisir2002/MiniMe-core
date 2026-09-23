package com.mini.logs.data

import android.content.Context
import androidx.documentfile.provider.DocumentFile
import java.io.File

/**
 * 日志文件引用。统一包装两种来源：
 * - [FileRef]：直接文件路径（公共目录/私有目录）
 * - [UriRef]：SAF 内容 URI（用户手动选择的目录）
 *
 * 文件名作为统一标识，用于去重和 UI 显示。
 */
sealed class LogFileRef {
    /** 文件名（不含路径），如 log-2026-09-24.txt。 */
    abstract val fileName: String

    /** 读取文件全部行（或最后 maxLines 行）。 */
    abstract fun readLines(context: Context, maxLines: Int): List<String>

    /** 直接文件路径引用。 */
    data class FileRef(val file: File) : LogFileRef() {
        override val fileName: String get() = file.name

        override fun readLines(context: Context, maxLines: Int): List<String> {
            if (!file.exists()) return emptyList()
            // 使用 LogRepository 的流式读取逻辑
            return LogRepository(context).readLastLines(file, maxLines)
        }
    }

    /** SAF 内容 URI 引用。 */
    data class UriRef(val uri: android.net.Uri, private val displayName: String) : LogFileRef() {
        override val fileName: String get() = displayName

        override fun readLines(context: Context, maxLines: Int): List<String> {
            return SafDirectoryManager(context).readLines(uri, maxLines)
        }
    }
}
