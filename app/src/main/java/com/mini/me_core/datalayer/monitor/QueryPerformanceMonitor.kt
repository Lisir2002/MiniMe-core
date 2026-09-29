package com.mini.me_core.datalayer.monitor

/** 慢查询记录。 */
data class SlowQueryEntry(
    val dbId: String,
    val queryName: String,
    val durationMs: Long,
    val rowCount: Int,
    val timestamp: Long,
)

/** 按查询名聚合的统计。 */
data class QueryStats(
    val queryName: String,
    val count: Long,
    val totalDurationMs: Long,
    val avgDurationMs: Double,
    val maxDurationMs: Long,
    val lastSlowTimestamp: Long,
)

/** 数据库级性能指标。 */
data class DatabasePerformance(
    val dbId: String,
    val totalQueries: Long,
    val avgDurationMs: Double,
    val slowQueryCount: Long,
    val slowQueryRatio: Double,
    val totalDurationMs: Long,
)

/**
 * 查询性能监控器（单例，线程安全）。
 *
 * - 内存中保留最近 1000 条慢查询（环形缓冲区）
 * - 查询统计永久保留（按 dbId + queryName 聚合），直到 reset() 或进程结束
 * - release 构建可通过 enabled=false 全局禁用，零开销
 */
object QueryPerformanceMonitor {

    @Volatile
    var enabled: Boolean = true

    private const val MAX_SLOW_QUERIES = 1000

    /**
     * 慢查询阈值（ms）。记录入环形缓冲与 [getSlowQueries] 的过滤阈值共用同一常量，
     * 杜绝「记录用 100、查询传 50 却拿不到 50–100ms 记录」的自相矛盾（审计 L5）。
     * 调用方若需更细粒度，须同时把相同阈值传给 [recordQuery] 与 [getSlowQueries]。
     */
    const val SLOW_QUERY_THRESHOLD_MS = 100L

    // 慢查询环形缓冲区
    private val slowQueries = ArrayDeque<SlowQueryEntry>()
    private val slowLock = Any()

    // 查询统计：key = "dbId|queryName"
    private val queryStats = mutableMapOf<String, AggregatedStats>()
    private val statsLock = Any()

    private class AggregatedStats {
        var count: Long = 0
        var totalDurationMs: Long = 0
        var maxDurationMs: Long = 0
        var slowCount: Long = 0
        var lastSlowTimestamp: Long = 0
    }

    /**
     * 记录一次查询执行。
     * @param dbId 数据库 id
     * @param queryName 查询标识（通常是 SQLDelight 生成的方法名或 SQL 摘要）
     * @param durationMs 执行耗时
     * @param rowCount 返回行数
     */
    fun recordQuery(dbId: String, queryName: String, durationMs: Long, rowCount: Int) {
        if (!enabled) return

        // 聚合统计
        val key = "$dbId|$queryName"
        synchronized(statsLock) {
            val agg = queryStats.getOrPut(key) { AggregatedStats() }
            agg.count++
            agg.totalDurationMs += durationMs
            if (durationMs > agg.maxDurationMs) agg.maxDurationMs = durationMs
        }

        // 慢查询单独记录（阈值见 SLOW_QUERY_THRESHOLD_MS）
        if (durationMs >= SLOW_QUERY_THRESHOLD_MS) {
            synchronized(statsLock) {
                val agg = queryStats[key]!!
                agg.slowCount++
                agg.lastSlowTimestamp = System.currentTimeMillis()
            }
            val entry = SlowQueryEntry(dbId, queryName, durationMs, rowCount, System.currentTimeMillis())
            synchronized(slowLock) {
                slowQueries.addLast(entry)
                while (slowQueries.size > MAX_SLOW_QUERIES) {
                    slowQueries.removeFirst()
                }
            }
        }
    }

    /** 获取慢查询列表（最新在前）。[thresholdMs] 应与 [recordQuery] 的慢查询阈值一致。 */
    fun getSlowQueries(thresholdMs: Long = SLOW_QUERY_THRESHOLD_MS): List<SlowQueryEntry> {
        synchronized(slowLock) {
            return slowQueries.filter { it.durationMs >= thresholdMs }.reversed()
        }
    }

    /** 获取查询统计（可按 dbId 过滤）。 */
    fun getQueryStats(dbId: String? = null): List<QueryStats> {
        synchronized(statsLock) {
            return queryStats.mapNotNull { (key, agg) ->
                val parts = key.split("|", limit = 2)
                if (parts.size < 2) return@mapNotNull null
                val entryDbId = parts[0]
                val queryName = parts[1]
                if (dbId != null && entryDbId != dbId) return@mapNotNull null
                QueryStats(
                    queryName = queryName,
                    count = agg.count,
                    totalDurationMs = agg.totalDurationMs,
                    avgDurationMs = if (agg.count > 0) agg.totalDurationMs.toDouble() / agg.count else 0.0,
                    maxDurationMs = agg.maxDurationMs,
                    lastSlowTimestamp = agg.lastSlowTimestamp,
                )
            }.sortedByDescending { it.totalDurationMs }
        }
    }

    /** 获取数据库级性能指标。 */
    fun getDatabasePerformance(dbId: String): DatabasePerformance {
        synchronized(statsLock) {
            var totalQueries = 0L
            var totalDuration = 0L
            var slowCount = 0L
            for ((key, agg) in queryStats) {
                if (!key.startsWith("$dbId|")) continue
                totalQueries += agg.count
                totalDuration += agg.totalDurationMs
                slowCount += agg.slowCount
            }
            return DatabasePerformance(
                dbId = dbId,
                totalQueries = totalQueries,
                avgDurationMs = if (totalQueries > 0) totalDuration.toDouble() / totalQueries else 0.0,
                slowQueryCount = slowCount,
                slowQueryRatio = if (totalQueries > 0) slowCount.toDouble() / totalQueries else 0.0,
                totalDurationMs = totalDuration,
            )
        }
    }

    /** 重置所有统计。 */
    fun reset() {
        synchronized(slowLock) { slowQueries.clear() }
        synchronized(statsLock) { queryStats.clear() }
    }
}
