package com.mini.me_core.datalayer.store

import com.mini.mecore.datalayer.sqldelight.InfraDb

/**
 * 一等 Queue（设计 §6.3）：可靠队列，只管持久化数据面（触发/执行交现有 WorkManager/协程）。
 * at-least-once 消费：pending 取后可标记 running/done/failed，失败可重排 next_run。
 */
data class QueueItem(
    val id: Long,
    val topic: String,
    val payload: String,
    val status: String,
    val attempt: Long,
    val nextRun: Long,
    val errorMsg: String?,
    val createdAt: Long,
)

class Queue(private val db: InfraDb) {

    private val q get() = db.queueQueries

    /**
     * 入队。
     * P1 修复：原 INSERT + SELECT last_insert_rowid() 分两步无事务包裹，
     * 并发场景下两条 INSERT 之间可能插入其他记录导致返回错误的 rowid。
     * 改为单事务保证原子性。
     */
    fun enqueue(topic: String, payload: String, nextRun: Long = System.currentTimeMillis()): Long {
        var id = 0L
        db.transaction {
            q.insertQueueItem(topic, payload, "pending", 0, nextRun, null, System.currentTimeMillis())
            id = q.selectLastInsertId().executeAsOne()
        }
        return id
    }

    fun pending(topic: String, now: Long = System.currentTimeMillis()): List<QueueItem> =
        q.selectPending(topic, "pending", now).executeAsList().map { it.toItem() }

    fun mark(status: String, attempt: Long, error: String?, id: Long) =
        q.markStatus(status, attempt, error, id)

    fun delete(id: Long) = q.deleteQueueItem(id)

    fun get(id: Long): QueueItem? = q.selectById(id).executeAsOneOrNull()?.toItem()

    private fun com.mini.mecore.datalayer.sqldelight.infra.Queue_store.toItem() = QueueItem(
        id = id,
        topic = topic,
        payload = payload,
        status = status,
        attempt = attempt,
        nextRun = next_run,
        errorMsg = error_msg,
        createdAt = created_at,
    )
}
