package com.mini.me_core.datalayer.store

import app.cash.sqldelight.db.QueryResult
import app.cash.sqldelight.db.SqlDriver
import com.mini.me_core.core.util.FileLogger
import com.mini.me_core.datalayer.exception.DataLayerErrorCode
import com.mini.me_core.datalayer.exception.DataLayerException
import com.mini.mecore.datalayer.sqldelight.InfraDb

/**
 * 一等 DocumentStore（设计 §6.2）：JSON 文档存储，首版即上 FTS5 全文检索。
 *
 * FTS5 虚表 doc_fts + trigger 在初始化时经 driver.execute 建立（设计备注：
 * 避免 SQLDelight 对虚拟表/trigger 的解析问题；基表 doc_store 由 SQLDelight schema 管理）。
 * 写入时 trigger 自动同步索引；检索走 FTS5 MATCH。
 */
data class DocEntry(
    val id: Long,
    val collection: String,
    val key: String,
    val docJson: String,
    val version: Long,
    val updatedAt: Long,
)

class DocumentStore(private val db: InfraDb, private val driver: SqlDriver) {

    private val queries get() = db.docQueries

    init {
        ensureFts()
    }

    private fun ensureFts() {
        driver.execute(
            null,
            """
            CREATE VIRTUAL TABLE IF NOT EXISTS doc_fts USING fts5(
              title, body, content='doc_store', content_rowid='id'
            );
            """.trimIndent(),
            0,
        )
        driver.execute(null, """
            CREATE TRIGGER IF NOT EXISTS doc_ai AFTER INSERT ON doc_store BEGIN
              INSERT INTO doc_fts(rowid, title, body) VALUES (new.id, new.collection || '/' || new.key, new.doc_json);
            END;
        """.trimIndent(), 0)
        driver.execute(null, """
            CREATE TRIGGER IF NOT EXISTS doc_ad AFTER DELETE ON doc_store BEGIN
              INSERT INTO doc_fts(doc_fts, rowid, title, body) VALUES('delete', old.id, old.collection || '/' || old.key, old.doc_json);
            END;
        """.trimIndent(), 0)
        driver.execute(null, """
            CREATE TRIGGER IF NOT EXISTS doc_au AFTER UPDATE ON doc_store BEGIN
              INSERT INTO doc_fts(doc_fts, rowid, title, body) VALUES('delete', old.id, old.collection || '/' || old.key, old.doc_json);
              INSERT INTO doc_fts(rowid, title, body) VALUES (new.id, new.collection || '/' || new.key, new.doc_json);
            END;
        """.trimIndent(), 0)
    }

    fun put(collection: String, key: String, docJson: String, version: Long = 1) {
        queries.upsertDoc(collection, key, docJson, version, System.currentTimeMillis())
    }

    fun get(collection: String, key: String): DocEntry? =
        queries.selectDoc(collection, key).executeAsOneOrNull()?.toEntry()

    fun getAll(collection: String): List<DocEntry> =
        queries.selectDocByCollection(collection).executeAsList().map { it.toEntry() }

    fun delete(collection: String, key: String) =
        queries.tombstoneDoc(System.currentTimeMillis(), collection, key)

    /**
     * FTS5 全文检索：跨 collection 匹配标题/正文（collection/key 作为 title）。
     * P1 修复：原查询无 LIMIT，FTS 匹配大量文档时一次性加载全部到内存导致 OOM。
     * 加 LIMIT 100 限制单次返回条数。
     *
     * M8 安全加固：用户输入直接喂给 `doc_fts MATCH` 会触发 FTS5 语法错误
     * （含 `-` / `"` / `*` / `NEAR` / 括号等），旧实现**不捕获**，错误直接抛出到 UI 层。
     * 现在：① 把整段输入用双引号包裹成「短语查询」，内部双引号按 FTS5 规则转义，
     * 既杜绝语法错误，又把用户的自由文本当作词组而非运算符；② 任何异常 catch 后
     * 转 [DataLayerException]([DataLayerErrorCode.FTS_QUERY_ERROR])，由上层统一处理。
     */
    fun search(match: String): List<DocEntry> {
        // 引号包裹 + 内部引号转义：把自由文本固化成 FTS5 短语，避免把用户输入当语法。
        val safeMatch = "\"${match.replace("\"", "\"\"")}\""
        val sql = """
            SELECT d.id, d.collection, d.key, d.doc_json, d.version, d.updated_at
            FROM doc_store d JOIN doc_fts f ON d.id = f.rowid
            WHERE doc_fts MATCH ? AND d.deleted = 0
            ORDER BY rank
            LIMIT 100
        """.trimIndent()
        return try {
            driver.executeQuery(null, sql, { cursor ->
                val out = mutableListOf<DocEntry>()
                while (cursor.next().value) {
                    out.add(
                        DocEntry(
                            id = cursor.getLong(0) ?: 0,
                            collection = cursor.getString(1) ?: "",
                            key = cursor.getString(2) ?: "",
                            docJson = cursor.getString(3) ?: "",
                            version = cursor.getLong(4) ?: 1,
                            updatedAt = cursor.getLong(5) ?: 0,
                        ),
                    )
                }
                QueryResult.Value(out)
            }, 1) { bindString(0, safeMatch) }.value
        } catch (e: Exception) {
            FileLogger.e(TAG, "FTS 检索失败（输入已转义为短语）: match=${match.take(80)}", e)
            throw DataLayerException(
                "全文检索失败（输入: ${match.take(40)}）: ${e.message}",
                DataLayerErrorCode.FTS_QUERY_ERROR,
                e,
            )
        }
    }

    private companion object {
        const val TAG = "DocumentStore"
    }

    private fun com.mini.mecore.datalayer.sqldelight.infra.Doc_store.toEntry() = DocEntry(
        id = id,
        collection = collection,
        key = key,
        docJson = doc_json,
        version = version.toLong(),
        updatedAt = updated_at,
    )
}
