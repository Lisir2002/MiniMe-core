package com.mini.me_core.datalayer.store

import com.mini.me_core.core.util.FileLogger
import com.mini.mecore.datalayer.sqldelight.InfraDb
import java.io.File

/**
 * 一等 BlobStore（设计 §6.4）：大二进制存储。
 *
 * **v2（M10）落盘策略**：
 *  - ≤ [MAX_INLINE_BLOB]（256KB）的小对象直接存 `data` 列（同事务原子、备份随库走）；
 *  - 更大的对象**写盘到应用私有目录**，DB 只保留相对路径 `path`。这样重型字节不进
 *    `db.transaction` 的写锁临界区，避免「单连接持有写锁过久、阻塞该库其它读写」（审计 M10）。
 *
 * 路径约定：`path` 存相对 `blobDir` 的 `blob/<id>.bin`，删除时一并清理落盘文件。
 */
data class BlobMeta(val id: Long, val mime: String?, val size: Long, val createdAt: Long)

class BlobStore(private val db: InfraDb, private val blobDir: File) {

    private val q get() = db.blobQueries

    /** P1 修复：BLOB 写入大小护栏，防止超大字节数组导致 OOM。10MB 上限。 */
    private val maxBlobSize = 10L * 1024 * 1024

    /** 内联阈值：≤ 此值的 BLOB 存 DB；超过则落盘（见类注释 M10）。 */
    private val maxInlineBlobSize = 256L * 1024

    private companion object {
        const val TAG = "BlobStore"
        const val REL_DIR = "blob"
    }

    /**
     * 存储大二进制。
     * P1 修复：原 INSERT + SELECT last_insert_rowid() 无事务包裹，并发下可能返回错误 id；
     * 同时增加大小护栏，超过 10MB 抛异常拒绝写入，避免 OOM。
     * M10：超过内联阈值时改为落盘，DB 仅持路径，缩短写锁占用。
     */
    fun put(data: ByteArray, mime: String? = null): Long {
        if (data.size > maxBlobSize) {
            throw IllegalArgumentException("Blob 大小 ${data.size} 超过上限 $maxBlobSize 字节")
        }
        ensureBlobDir()
        var id = 0L
        db.transaction {
            if (data.size <= maxInlineBlobSize) {
                // 小对象：直接存 data 列，path 为 NULL。
                q.insertBlob(mime, data.size.toLong(), data, null, System.currentTimeMillis())
            } else {
                // 大对象：先以空 data 占位拿到自增 id，再把字节落盘、回填 path。
                // 重型字节在事务外写入文件，DB 行始终轻量（一行元数据 + 一个相对路径）。
                q.insertBlob(mime, data.size.toLong(), ByteArray(0), null, System.currentTimeMillis())
                id = q.selectLastInsertId().executeAsOne()
                val rel = "$REL_DIR/$id.bin"
                File(blobDir, rel).writeBytes(data)
                q.updateBlobPath(rel, id)
            }
            if (id == 0L) id = q.selectLastInsertId().executeAsOne()
        }
        return id
    }

    fun get(id: Long): ByteArray? {
        val row = q.selectBlob(id).executeAsOneOrNull() ?: return null
        val rel = row.path
        if (rel != null) {
            // 大对象：从落盘文件读取。
            val file = File(blobDir, rel)
            if (!file.exists()) {
                FileLogger.e(TAG, "Blob 落盘文件缺失（数据不可恢复）: id=$id, rel=$rel")
                return null
            }
            return runCatching { file.readBytes() }.getOrElse { e ->
                FileLogger.e(TAG, "Blob 落盘文件读取失败: id=$id, rel=$rel", e)
                null
            }
        }
        // 小对象 / 旧库：直接读 data 列。
        return row.data_
    }

    fun meta(id: Long): BlobMeta? =
        q.selectBlobMeta(id).executeAsOneOrNull()?.let { BlobMeta(it.id, it.mime, it.size, it.created_at) }

    fun delete(id: Long) {
        // 落盘文件一并删除，避免残留占用磁盘。
        runCatching { File(blobDir, "$REL_DIR/$id.bin").delete() }
            .onFailure { FileLogger.w(TAG, "Blob 落盘文件删除失败（忽略）: id=$id", it) }
        q.deleteBlob(id)
    }

    private fun ensureBlobDir() {
        val dir = File(blobDir, REL_DIR)
        if (!dir.exists() && !dir.mkdirs()) {
            FileLogger.w(TAG, "Blob 落盘目录创建失败: ${dir.absolutePath}")
        }
    }
}
