package com.mini.me_core.core.util

import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.atomic.AtomicInteger

/**
 * 内存级日志统计：按等级、按 Tag 线程安全计数。
 *
 * 供日志查看器的「等级筛选数量徽章 / Tag 频次排序」等组件**实时**读取，
 * 无需每次遍历全量日志文件。每条落盘日志在 [FileLogger] 内调用 [increment] 累加。
 *
 * 实现：[ConcurrentHashMap] + [AtomicInteger]，无锁、高并发安全；
 * 读取时返回快照拷贝，外部拿到的 Map 不会被后续写入改动。
 * 纯 Kotlin，可直接单元测试。
 */
object LogStats {

    private val levelCounts = ConcurrentHashMap<LogLevel, AtomicInteger>()
    private val tagCounts = ConcurrentHashMap<String, AtomicInteger>()

    /** 记录一条日志：等级计数与 Tag 计数各 +1。 */
    fun increment(level: LogLevel, tag: String) {
        levelCounts.getOrPut(level) { AtomicInteger(0) }.incrementAndGet()
        if (tag.isNotEmpty()) {
            tagCounts.getOrPut(tag) { AtomicInteger(0) }.incrementAndGet()
        }
    }

    /** 当前各等级计数快照（拷贝，不可变）。从未出现的等级不包含在结果中。 */
    fun getLevelCounts(): Map<LogLevel, Int> =
        levelCounts.entries.associate { (k, v) -> k to v.get() }

    /** 当前各 Tag 计数快照（拷贝，不可变）。 */
    fun getTagCounts(): Map<String, Int> =
        tagCounts.entries.associate { (k, v) -> k to v.get() }

    /** 清空所有计数。单元测试在 @Before / @After 中调用以隔离用例。 */
    fun reset() {
        levelCounts.clear()
        tagCounts.clear()
    }
}
