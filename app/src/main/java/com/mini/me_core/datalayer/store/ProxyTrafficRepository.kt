package com.mini.me_core.datalayer.store

import com.mini.mecore.datalayer.sqldelight.InfraDb
import java.util.Calendar
import javax.inject.Inject
import javax.inject.Singleton

/** 流量用量汇总（字节）。 */
data class TrafficUsage(
    val upBytes: Long,
    val downBytes: Long,
) {
    val totalBytes: Long get() = upBytes + downBytes
}

/**
 * P1-8：代理流量采样持久化（INFRA 库 proxy_traffic 表）。
 *
 * 采样行由 [com.mini.me_core.feature.proxy.domain.ProxyTrafficSampler] 写入；
 * 这里提供今日 / 本周 / 累计用量聚合查询，全部同步（SQLDelight 连接池内执行）。
 */
@Singleton
class ProxyTrafficRepository @Inject constructor(
    private val db: InfraDb,
) {
    private val q get() = db.proxy_trafficQueries

    /** 写入一条 60s 采样（累加差值后的增量字节）。 */
    fun insertSample(timestamp: Long, upBytes: Long, downBytes: Long, periodMs: Long) {
        q.insertSample(timestamp, upBytes, downBytes, periodMs)
    }

    private fun usageSince(since: Long): TrafficUsage =
        TrafficUsage(
            upBytes = q.sumUpSince(since).executeAsOne(),
            downBytes = q.sumDownSince(since).executeAsOne(),
        )

    /** 今日 0 点至今。 */
    fun todayUsage(): TrafficUsage = usageSince(startOfToday())

    /** 本周一 0 点至今。 */
    fun weekUsage(): TrafficUsage = usageSince(startOfWeek())

    /** 累计总用量。 */
    fun totalUsage(): TrafficUsage = TrafficUsage(
        upBytes = q.sumUpAll().executeAsOne(),
        downBytes = q.sumDownAll().executeAsOne(),
    )

    /** 清理 90 天前的采样，控制表体积。 */
    fun purgeOlderThan90Days() {
        val threshold = System.currentTimeMillis() - 90L * 24 * 3600_000L
        q.deleteOlderThan(threshold)
    }

    private fun startOfToday(): Long {
        val cal = Calendar.getInstance()
        cal.set(Calendar.HOUR_OF_DAY, 0); cal.set(Calendar.MINUTE, 0)
        cal.set(Calendar.SECOND, 0); cal.set(Calendar.MILLISECOND, 0)
        return cal.timeInMillis
    }

    private fun startOfWeek(): Long {
        val cal = Calendar.getInstance()
        cal.firstDayOfWeek = Calendar.MONDAY
        cal.set(Calendar.DAY_OF_WEEK, Calendar.MONDAY)
        cal.set(Calendar.HOUR_OF_DAY, 0); cal.set(Calendar.MINUTE, 0)
        cal.set(Calendar.SECOND, 0); cal.set(Calendar.MILLISECOND, 0)
        return cal.timeInMillis
    }
}
