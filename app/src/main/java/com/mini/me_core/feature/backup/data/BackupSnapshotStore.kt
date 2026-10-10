package com.mini.me_core.feature.backup.data

import android.content.Context
import com.mini.me_core.feature.backup.domain.BackupCrypto
import kotlinx.serialization.json.Json
import org.apache.commons.compress.archivers.tar.TarArchiveEntry
import org.apache.commons.compress.archivers.tar.TarArchiveInputStream
import org.apache.commons.compress.archivers.tar.TarArchiveOutputStream
import org.apache.commons.compress.compressors.gzip.GzipCompressorInputStream
import java.io.BufferedInputStream
import java.io.ByteArrayOutputStream
import java.io.File
import java.io.FileInputStream
import java.io.FileOutputStream
import java.io.InputStream
import java.io.OutputStream

/**
 * 共享的备份 [Json] 配置。原样取自原 BackupManagerImpl 中的单例配置：
 * ignoreUnknownKeys / encodeDefaults / prettyPrint=false，导出与恢复两侧共用同一实例。
 */
internal val backupJson = Json {
    ignoreUnknownKeys = true
    encodeDefaults = true
    prettyPrint = false
}

// ── 归档格式契约常量（由原 BackupManagerImpl 私有 companion 原样搬迁，供导出/恢复两侧共用）──
internal const val AGENT_SCHEMA_VERSION = 1
internal const val LEGACY_AGENT_SCHEMA_VERSION = 49
internal const val TAG = "BackupManagerImpl"
internal const val PAGE_SIZE = 500
internal const val FILE_METADATA = "metadata.json"
internal const val FILE_SESSIONS = "chatSessions.jsonl"
internal const val FILE_MESSAGES = "messages.jsonl"
internal const val FILE_TODOS = "todoItems.jsonl"
internal const val FILE_LEGACY_SNAPSHOT = "snapshot.json"
internal const val FILE_REGISTRY = "registry.tar"

/** 已由 jsonl 流式导出的大表（注册表导出时排除，避免备份体积翻倍）。 */
internal val JSONL_STREAMED_TABLES = setOf("chat_sessions", "agent_messages", "todo_items")

/**
 * 备份「快照读写 / 临时文件」低层管道层。
 *
 * 职责：
 * - 临时文件的创建与在归档流关闭时的清理（崩溃/异常路径下由 finally 或包装流 close 兜底删除）；
 * - 打开一个（可能先流式解密再解 gzip 的）tar 输入流；
 * - 低层 tar 条目写入与 jsonl 经临时文件落盘后流式拷入 tar。
 *
 * 由 [BackupManagerImpl] 装配，导出方 [BackupExporter] 与恢复方 [BackupRestorer] 共用。
 * 本类不含任何业务判定/范围/加密策略，仅做文件与流搬运。
 */
internal class BackupSnapshotStore(private val context: Context) {

    fun createTempFile(): File = File.createTempFile("backup", ".tmp", context.cacheDir)

    /**
     * 打开一个 tar 输入流：若 [pw] 非空先流式解密到临时文件再打开 GZIP+TAR；否则直接包装。
     * 返回的 [TarArchiveInputStream] 由调用方 use() 关闭；内部临时文件在流关闭后清理。
     */
    fun openTarStream(input: InputStream, pw: CharArray?): TarArchiveInputStream {
        val buffered = BufferedInputStream(input)
        if (pw == null) {
            return TarArchiveInputStream(GzipCompressorInputStream(buffered))
        }
        val temp = createTempFile()
        FileOutputStream(temp).use { dst -> BackupCrypto.decryptStream(buffered, dst, pw) }
        val fis = FileInputStream(temp)
        // 用一个会同时删除临时文件的包装流：TarArchiveInputStream close 时会关闭底层 gz -> fis。
        val gz = GzipCompressorInputStream(fis)
        return object : TarArchiveInputStream(gz) {
            override fun close() {
                runCatching { super.close() }
                temp.delete()
            }
        }
    }

    fun writeTarEntry(tar: TarArchiveOutputStream, name: String, content: ByteArray) {
        val entry = TarArchiveEntry(name).apply { size = content.size.toLong() }
        tar.putArchiveEntry(entry)
        tar.write(content)
        tar.closeArchiveEntry()
    }

    /**
     * 先将 jsonl 写入一个临时文件，获取确切的 [File.length] 设置 TarArchiveEntry.size，
     * 然后流式拷入 TarArchiveOutputStream，避免在 Header 中 size 设为 0 导致写入越界异常。
     */
    suspend fun writeJsonlFileEntry(
        tar: TarArchiveOutputStream,
        entryName: String,
        block: suspend (JsonlWriter) -> Unit
    ) {
        val tmp = createTempFile()
        try {
            FileOutputStream(tmp).use { fos ->
                val writer = JsonlWriter(fos)
                block(writer)
                writer.flush()
            }
            val entry = TarArchiveEntry(entryName).apply { size = tmp.length() }
            tar.putArchiveEntry(entry)
            FileInputStream(tmp).use { fis -> fis.copyTo(tar) }
            tar.closeArchiveEntry()
        } finally {
            tmp.delete()
        }
    }

    /** 带内部缓冲的 jsonl 写入器：行缓冲满 64KB 时刷入 tar 流，避免逐行写系统调用。 */
    internal class JsonlWriter(private val out: OutputStream) {
        private val buffer = ByteArrayOutputStream(64 * 1024)

        fun writeLine(line: String) {
            buffer.write(line.toByteArray(Charsets.UTF_8))
            buffer.write('\n'.code)
            if (buffer.size() >= 64 * 1024) flush()
        }

        fun flush() {
            buffer.writeTo(out)
            buffer.reset()
        }
    }
}
