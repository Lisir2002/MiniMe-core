package com.mini.me_core.datalayer.monitor

import app.cash.sqldelight.Query
import app.cash.sqldelight.Transacter
import app.cash.sqldelight.db.QueryResult
import app.cash.sqldelight.db.SqlCursor
import app.cash.sqldelight.db.SqlDriver
import app.cash.sqldelight.db.SqlPreparedStatement

/**
 * 监控驱动包装器。
 *
 * 包装底层 [SqlDriver]，自动测量每次 execute / executeQuery 的耗时，
 * 并上报 [QueryPerformanceMonitor]。
 *
 * 使用方式：在 debug 构建中用此包装器包裹真实 driver；
 * release 构建直接使用原始 driver，零开销。
 *
 * 查询名提取：取 SQL 的前 60 个字符作为标识（足够区分不同查询）。
 */
class MonitoringDriverWrapper(
    private val delegate: SqlDriver,
    private val dbId: String,
) : SqlDriver {

    override fun execute(
        identifier: Int?,
        sql: String,
        parameters: Int,
        binders: (SqlPreparedStatement.() -> Unit)?,
    ): QueryResult<Long> {
        if (!QueryPerformanceMonitor.enabled) {
            return delegate.execute(identifier, sql, parameters, binders)
        }
        val start = System.nanoTime()
        val result = delegate.execute(identifier, sql, parameters, binders)
        val durationMs = (System.nanoTime() - start) / 1_000_000
        QueryPerformanceMonitor.recordQuery(dbId, summarizeSql(sql), durationMs, result.value.toInt())
        return result
    }

    override fun <R> executeQuery(
        identifier: Int?,
        sql: String,
        mapper: (SqlCursor) -> QueryResult<R>,
        parameters: Int,
        binders: (SqlPreparedStatement.() -> Unit)?,
    ): QueryResult<R> {
        if (!QueryPerformanceMonitor.enabled) {
            return delegate.executeQuery(identifier, sql, mapper, parameters, binders)
        }
        val start = System.nanoTime()
        var rowCount = 0
        val countingMapper: (SqlCursor) -> QueryResult<R> = { cursor ->
            val countingCursor = CountingCursor(cursor)
            val result = mapper(countingCursor)
            rowCount = countingCursor.count
            result
        }
        val result = delegate.executeQuery(identifier, sql, countingMapper, parameters, binders)
        val durationMs = (System.nanoTime() - start) / 1_000_000
        QueryPerformanceMonitor.recordQuery(dbId, summarizeSql(sql), durationMs, rowCount)
        return result
    }

    override fun close() {
        delegate.close()
    }

    override fun addListener(vararg queryKeys: String, listener: Query.Listener) {
        delegate.addListener(*queryKeys, listener = listener)
    }

    override fun removeListener(vararg queryKeys: String, listener: Query.Listener) {
        delegate.removeListener(*queryKeys, listener = listener)
    }

    override fun notifyListeners(vararg queryKeys: String) {
        delegate.notifyListeners(*queryKeys)
    }

    override fun newTransaction(): QueryResult<Transacter.Transaction> {
        return delegate.newTransaction()
    }

    override fun currentTransaction(): Transacter.Transaction? {
        return delegate.currentTransaction()
    }

    /** 提取 SQL 摘要作为查询名（前 60 字符，去掉空白）。 */
    private fun summarizeSql(sql: String): String {
        // G3b：正则提为常量——过去每次查询都 `Regex("\\s+")`（构造即 Pattern.compile），
        //      在 SQL 热路径上逐次编译。提到 companion 复用。
        val compact = WHITESPACE_REGEX.replace(sql, " ").trim()
        return if (compact.length <= 60) compact else compact.substring(0, 60) + "..."
    }

    private companion object {
        val WHITESPACE_REGEX = Regex("\\s+")
    }

    /** 代理 Cursor，统计 next() 调用次数。 */
    private class CountingCursor(private val delegate: SqlCursor) : SqlCursor {
        var count = 0
            private set

        override fun next(): QueryResult<Boolean> {
            val result = delegate.next()
            if (result.value) count++
            return result
        }

        override fun getBytes(index: Int): ByteArray? = delegate.getBytes(index)
        override fun getDouble(index: Int): Double? = delegate.getDouble(index)
        override fun getLong(index: Int): Long? = delegate.getLong(index)
        override fun getString(index: Int): String? = delegate.getString(index)
        override fun getBoolean(index: Int): Boolean? = delegate.getBoolean(index)
    }
}

