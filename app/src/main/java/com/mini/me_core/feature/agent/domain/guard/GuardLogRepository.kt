package com.mini.me_core.feature.agent.domain.guard

import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import javax.inject.Inject
import javax.inject.Singleton

/** 单条护栏拦截记录。 */
data class GuardLogEntry(
    val timestamp: Long,
    val toolName: String,
    val guardId: String,
    val code: String,
    val message: String
)

/**
 * 护栏拦截日志（P2）：内存环形缓冲，最近 [MAX_ENTRIES] 条。
 * 新记录入队首，超出容量移除队尾。通过 [logsFlow] 暴露给 UI。
 *
 * 另维护累计「拦截」计数 [blockCount]（仅统计 Block 级真正拦截，不含 Advisory 提醒），
 * 供设置页仪表盘展示；环形缓冲截断不影响该累计值。[clear] 同时清空日志与计数。
 */
@Singleton
class GuardLogRepository @Inject constructor() {

    private val _logs = MutableStateFlow<List<GuardLogEntry>>(emptyList())
    val logsFlow: StateFlow<List<GuardLogEntry>> = _logs.asStateFlow()

    private val _blockCount = MutableStateFlow(0)
    /** 累计真正被护栏拦截（Block）的次数；Advisory 提醒不计入。 */
    val blockCount: StateFlow<Int> = _blockCount.asStateFlow()

    /**
     * 追加一条护栏记录。
     * @param isBlock true 表示真正拦截（Block，计入 [blockCount]）；false 仅 Advisory 提醒。
     */
    fun log(entry: GuardLogEntry, isBlock: Boolean = false) {
        val newList = buildList {
            add(entry)
            addAll(_logs.value)
        }.take(MAX_ENTRIES)
        _logs.value = newList
        if (isBlock) {
            _blockCount.value = (_blockCount.value) + 1
        }
    }

    /** 清空日志与累计拦截计数（仪表盘「重置统计」用）。 */
    fun clear() {
        _logs.value = emptyList()
        _blockCount.value = 0
    }

    fun recent(limit: Int = 20): List<GuardLogEntry> = _logs.value.take(limit)

    private companion object {
        const val MAX_ENTRIES = 50
    }
}
