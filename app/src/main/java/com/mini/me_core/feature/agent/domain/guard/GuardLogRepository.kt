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
 */
@Singleton
class GuardLogRepository @Inject constructor() {

    private val _logs = MutableStateFlow<List<GuardLogEntry>>(emptyList())
    val logsFlow: StateFlow<List<GuardLogEntry>> = _logs.asStateFlow()

    fun log(entry: GuardLogEntry) {
        val newList = buildList {
            add(entry)
            addAll(_logs.value)
        }.take(MAX_ENTRIES)
        _logs.value = newList
    }

    fun recent(limit: Int = 20): List<GuardLogEntry> = _logs.value.take(limit)

    private companion object {
        const val MAX_ENTRIES = 50
    }
}
