package com.mini.me_core.feature.settings.presentation

import com.mini.me_core.core.util.LogLevel

/**
 * P2-17 行为保持搬迁：以下状态类型原定义于 SettingsViewModel.kt 顶部，
 * 包名保持 com.mini.me_core.feature.settings.presentation 不变，
 * 故 component 子包既有 import 全部无需改动。
 */

sealed class FetchState {
    object Idle : FetchState()
    object Loading : FetchState()
    data class Success(val models: List<String>) : FetchState()
    data class Error(val message: String) : FetchState()
}

/** 供应商级连通性测试状态（点击「测试连接」时通过 GET /v1/models 验证 Key + 端点）。 */
sealed class ConnectionTestState {
    object Idle : ConnectionTestState()
    object Loading : ConnectionTestState()
    data class Success(val message: String) : ConnectionTestState()
    data class Error(val message: String) : ConnectionTestState()
}

/**
 * 日志查看器的日期范围模式。
 *
 * 单一数据源：文件下拉与高级筛选底部弹窗都从 [LogViewerUiState.dateRangeMode] 读取，
 * 保证两处状态严格同步（见设计文档 §7.4）。
 *
 * - [TODAY] / [YESTERDAY] / [LAST_3_DAYS] / [LAST_7_DAYS] / [ALL]：快捷范围
 * - [SINGLE_FILE]：单文件精确查看（由 [LogViewerUiState.selectedFileName] 指定）
 * - [CUSTOM]：自定义起止日期（由 [LogViewerUiState.customDateStart]/[customDateEnd] 指定）
 */
enum class DateRangeMode {
    TODAY, YESTERDAY, LAST_3_DAYS, LAST_7_DAYS, ALL, SINGLE_FILE, CUSTOM
}

data class LogViewerUiState(
    val files: List<String> = emptyList(),
    val selectedFileName: String? = null,
    val filterServerName: String? = null,
    val content: String = "",
    val totalLines: Int = 0,
    val shownLines: Int = 0,
    val loading: Boolean = false,
    val error: String? = null,

    // 筛选面板：默认收起，只通过点击筛选图标打开，通过 X 关闭
    val filterPanelExpanded: Boolean = false,
    val selectedDates: Set<String> = emptySet(),
    val selectedLevels: Set<LogLevel> = emptySet(),
    val selectedTags: Set<String> = emptySet(),
    val allAvailableTags: List<String> = emptyList(),

    // 日期范围：快捷范围由 [dateRangeMode] 驱动，自定义范围由 customDateStart/End 指定
    val dateRangeMode: DateRangeMode = DateRangeMode.TODAY,
    val customDateStart: String? = null,
    val customDateEnd: String? = null,

    // 搜索
    val searchQuery: String = "",

    // 实时尾随
    val liveTailEnabled: Boolean = false,
    val hasNewLogs: Boolean = false,

    // ── 新增字段（运行日志查看器 UI 重构）──
    /** 各等级行数统计（文件/日期作用域内、等级/Tag/搜索过滤前），供筛选栏数量徽章使用。 */
    val levelCounts: Map<LogLevel, Int> = emptyMap(),
    /** 当前最低记录等级（写入阈值），与显示筛选区分。 */
    val recordLevel: LogLevel = LogLevel.VERBOSE,
    /** 顶栏搜索是否展开。 */
    val searchExpanded: Boolean = false,
    /** 搜索结果当前匹配下标（1-based 展示给用户）与总匹配数。 */
    val currentMatchIndex: Int = 0,
    val totalMatches: Int = 0,
    /** 被折叠的等级集合（再次点击筛选 chip 时该等级整组折叠为一行）。 */
    val collapsedLevels: Set<LogLevel> = emptySet(),
    /** 是否自动贴底滚动（用户上滑后置 false，显示「返回最新」）。 */
    val isAutoScrolling: Boolean = true
)
