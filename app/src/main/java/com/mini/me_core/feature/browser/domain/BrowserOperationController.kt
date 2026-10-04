package com.mini.me_core.feature.browser.domain

import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import javax.inject.Inject
import javax.inject.Singleton

/**
 * 浏览器操作控制器（单例）。
 *
 * 职责：
 *  1. 管理用户中断信号，供 BrowserAgentTool 在操作间隙检查并响应。
 *  2. 追踪页面健康状态，检测 WebView 无响应、服务断连等异常。
 *  3. 记录操作历史，为智能重试和错误恢复提供决策依据。
 *
 * 工作流程：
 *  1. UI 层点击「中断」按钮 → requestInterrupt 设置 interruptRequested = true；
 *  2. BrowserAgentTool 在 execute() 开始及每个耗时操作前调用 isInterruptRequested；
 *  3. 检测到中断后立即停止页面加载并返回 INTERRUPTED 错误；
 *  4. 操作完成或被中断后调用 resetInterrupt 清除标志，准备下一轮。
 */
@Singleton
class BrowserOperationController @Inject constructor() {

    // ===== 中断控制 =====

    private val _interruptRequested = MutableStateFlow(false)
    /** 中断请求信号：UI 设置为 true 请求中断当前操作 */
    val interruptRequested: StateFlow<Boolean> = _interruptRequested.asStateFlow()

    private val _operationActive = MutableStateFlow(false)
    /** 当前是否有浏览器操作正在执行（控制中断按钮显示） */
    val operationActive: StateFlow<Boolean> = _operationActive.asStateFlow()

    // ===== 页面健康追踪 =====

    /**
     * 页面健康状态。
     *
     * HEALTHY：正常响应
     * SLOW：响应变慢（连续操作超时）
     * UNRESPONSIVE：无响应（连续失败达到阈值）
     * DISCONNECTED：服务断连（CONNECTION_REFUSED）
     */
    enum class PageHealth { HEALTHY, SLOW, UNRESPONSIVE, DISCONNECTED }

    private val _pageHealth = MutableStateFlow(PageHealth.HEALTHY)
    /** 当前页面健康状态 */
    val pageHealth: StateFlow<PageHealth> = _pageHealth.asStateFlow()

    private val _lastError = MutableStateFlow<String?>(null)
    /** 最近一次错误信息（供 UI 展示和模型参考） */
    val lastError: StateFlow<String?> = _lastError.asStateFlow()

    // ===== 操作历史 =====

    /**
     * 操作记录。
     */
    data class OperationRecord(
        val action: String,
        val success: Boolean,
        val errorCode: String? = null,
        val timestamp: Long = System.currentTimeMillis(),
        val durationMs: Long = 0,
    )

    private val _operationHistory = MutableStateFlow<List<OperationRecord>>(emptyList())
    /** 最近操作历史（最多保留 20 条） */
    val operationHistory: StateFlow<List<OperationRecord>> = _operationHistory.asStateFlow()

    // ===== 统计 =====

    private var consecutiveTimeouts = 0
    private var consecutiveFailures = 0
    private var totalOperations = 0
    private var totalFailures = 0

    /** 请求中断当前正在执行的浏览器操作。 */
    fun requestInterrupt() {
        _interruptRequested.value = true
    }

    /**
     * 重置中断标志。在操作完成或被中断后调用，准备接受下一次请求。
     */
    fun resetInterrupt() {
        _interruptRequested.value = false
    }

    /**
     * 检查是否已请求中断。供 BrowserAgentTool 在操作间轮询调用。
     *
     * @return true 表示用户已请求中断，应立即停止操作
     */
    fun isInterruptRequested(): Boolean = _interruptRequested.value

    /** 标记操作开始，UI 显示中断按钮。 */
    fun operationStarted() {
        _operationActive.value = true
    }

    /** 标记操作结束，UI 隐藏中断按钮。 */
    fun operationFinished() {
        _operationActive.value = false
    }

    /**
     * 记录操作结果，更新健康状态和统计。
     *
     * @param action 操作名称
     * @param success 是否成功
     * @param errorCode 错误码（失败时）
     * @param durationMs 操作耗时
     */
    fun recordOperation(
        action: String,
        success: Boolean,
        errorCode: String? = null,
        durationMs: Long = 0,
    ) {
        totalOperations++

        val record = OperationRecord(
            action = action,
            success = success,
            errorCode = errorCode,
            durationMs = durationMs,
        )

        _operationHistory.value = (_operationHistory.value + record).takeLast(20)

        if (success) {
            consecutiveTimeouts = 0
            consecutiveFailures = 0
            if (_pageHealth.value != PageHealth.HEALTHY) {
                _pageHealth.value = PageHealth.HEALTHY
                _lastError.value = null
            }
        } else {
            totalFailures++
            consecutiveFailures++
            _lastError.value = errorCode

            when (errorCode) {
                "TIMEOUT" -> {
                    consecutiveTimeouts++
                    _pageHealth.value = if (consecutiveTimeouts >= 2) PageHealth.SLOW else PageHealth.HEALTHY
                }
                "PAGE_UNRESPONSIVE" -> {
                    _pageHealth.value = PageHealth.UNRESPONSIVE
                }
                "CONNECTION_REFUSED" -> {
                    _pageHealth.value = PageHealth.DISCONNECTED
                }
                else -> {
                    if (consecutiveFailures >= 3) {
                        _pageHealth.value = PageHealth.UNRESPONSIVE
                    }
                }
            }
        }
    }

    /**
     * 获取健康恢复建议。
     *
     * 根据当前页面健康状态，返回推荐的恢复操作。
     *
     * @return 恢复建议描述
     */
    fun getRecoverySuggestion(): String = when (_pageHealth.value) {
        PageHealth.HEALTHY -> "页面状态正常"
        PageHealth.SLOW -> "页面响应变慢，建议：调用 reload 重新加载页面，或 wait_for_network_idle 等待网络空闲"
        PageHealth.UNRESPONSIVE -> "页面无响应，建议：先调用 reload 重新加载；若仍无响应，navigate 到目标 URL 完全重新加载；严重时可请求用户接管"
        PageHealth.DISCONNECTED -> "目标服务已断开（常见于 localhost 本地开发服务停止），建议：先确认服务是否启动，不要继续 click/type 等操作；服务重启后 navigate 重新访问"
    }

    /**
     * 检查是否应该建议用户接管。
     *
     * 当连续失败次数过多或页面持续无响应时，建议请求用户接管。
     *
     * @return true 表示应该建议用户接管
     */
    fun shouldSuggestTakeover(): Boolean =
        consecutiveFailures >= 5 || _pageHealth.value == PageHealth.UNRESPONSIVE

    /**
     * 重置健康状态（页面成功导航或重新加载后调用）。
     */
    fun resetHealth() {
        _pageHealth.value = PageHealth.HEALTHY
        _lastError.value = null
        consecutiveTimeouts = 0
        consecutiveFailures = 0
    }

    /**
     * 获取操作统计摘要。
     *
     * @return 统计信息字符串
     */
    fun getStatsSummary(): String =
        "操作 $totalOperations 次，失败 $totalFailures 次，连续失败 $consecutiveFailures 次，健康状态：${_pageHealth.value.name}"
}
