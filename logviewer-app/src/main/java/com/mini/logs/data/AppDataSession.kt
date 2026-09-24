package com.mini.logs.data

import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/**
 * 全局数据会话。
 *
 * 维护一个数据版本号，当日志源（目录）切换时递增。
 * 所有页面（日志/统计/崩溃）监听此版本号，变化时自动重新加载数据，
 * 实现"切换目录后全应用数据同步刷新"。
 */
object AppDataSession {

    private val _dataVersion = MutableStateFlow(0)
    val dataVersion: StateFlow<Int> = _dataVersion.asStateFlow()

    /** 日志源已变更，通知所有页面刷新数据。 */
    fun notifyDataSourceChanged() {
        _dataVersion.value += 1
    }
}
