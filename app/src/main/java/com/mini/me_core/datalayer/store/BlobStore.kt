package com.mini.me_core.datalayer.store

import com.mini.mecore.datalayer.sqldelight.InfraDb

/**
 * 一等 BlobStore（设计 §6.4）：大二进制存储。
 * 当前字节直接存 DB BLOB 列（事务原子、备份随库走）；实施期可加单条体积上限护栏改走磁盘文件。
 */
data class BlobMeta(val id: Long, val mime: String?, val size: Long, val createdAt: Long)

class BlobStore(private val db: InfraDb) {

    private val q get() = db.blobQueries

    /** P1 修复：BLOB 写入大小护栏，防止超大字节数组导致 OOM。10MB 上限。 */
    private val maxBlobSize = 10L * 1024 * 1024

    /**
     * 存储大二进制。
     * P1 修复：原 INSERT + SELECT last_insert_rowid() 无事务包裹，并发下可能返回错误 id。
     * 同时增加大小护栏，超过 10MB 抛异常拒绝写入，避免 OOM。
     */
    fun put(data: ByteArray, mime: String? = null): Long {
        if (data.size > maxBlobSize) {
            throw IllegalArgumentException("Blob 大小 ${data.size} 超过上限 $maxBlobSize 字节")
        }
        var id = 0L
        db.transaction {
            q.insertBlob(mime, data.size.toLong(), data, System.currentTimeMillis())
            id = q.selectLastInsertId().executeAsOne()
        }
        return id
    }

    fun get(id: Long): ByteArray? = q.selectBlob(id).executeAsOneOrNull()?.data_

    fun meta(id: Long): BlobMeta? =
        q.selectBlobMeta(id).executeAsOneOrNull()?.let { BlobMeta(it.id, it.mime, it.size, it.created_at) }

    fun delete(id: Long) = q.deleteBlob(id)
}
