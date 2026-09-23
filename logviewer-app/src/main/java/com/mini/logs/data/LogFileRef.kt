package com.mini.logs.data

import android.content.Context
import java.io.File

/**
 * 日志文件引用。统一包装两种来源：
 * - [FileRef]：直接文件路径（公共目录/私有目录）
 * - [UriRef]：SAF 内容 URI（用户手动选择的目录）
 *
 * 文件名作为统一标识，用于去重和 UI 显示。
 * 读取为 suspend 函数，I/O 在 IO 线程执行。
 */
sealed class LogFileRef {
    /** 文件名（不含路径），如 log-2026-09-24.txt。 */
    abstract val fileName: String

    /** 读取文件行（最后 maxLines 行）。 */
    abstract suspend fun readLines(context: Context, maxLines: Int): List<String>

    /** 直接文件路径引用。 */
    data class FileRef(val file: File) : LogFileRef() {
        override val fileName: String get() = file.name

        override suspend fun readLines(context: Context, maxLines: Int): List<String> {
            if (!file.exists()) return emptyList()
            return kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.IO) {
                LogRepository(context).readLastLines(file, maxLines)
            }
        }
    }

    /** SAF 内容 URI 引用。 */
    data class UriRef(val uri: android.net.Uri, private val displayName: String) : LogFileRef() {
        override val fileName: String get() = displayName

        override suspend fun readLines(context: Context, maxLines: Int): List<String> {
            return SafDirectoryManager(context).readLines(uri, maxLines)
        }
    }
}
