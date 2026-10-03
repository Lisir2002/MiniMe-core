package com.mini.me_core.feature.browser.domain

import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import javax.inject.Inject
import javax.inject.Singleton

/**
 * 浏览器操作中断控制器（单例）。
 *
 * 职责：管理用户中断信号，供 [BrowserAgentTool] 在操作间隙检查并响应。
 *
 * 工作流程：
 *  1. UI 层点击「中断」按钮 → [requestInterrupt] 设置 [interruptRequested] = true；
 *  2. [BrowserAgentTool] 在 execute() 开始及每个耗时操作前调用 [isInterruptRequested]；
 *  3. 检测到中断后立即停止页面加载并返回 INTERRUPTED 错误；
 *  4. 操作完成或被中断后调用 [resetInterrupt] 清除标志，准备下一轮。
 */
@Singleton
class BrowserOperationController @Inject constructor() {

    private val _interruptRequested = MutableStateFlow(false)
    /** 中断请求信号：UI 设置为 true 请求中断当前操作 */
    val interruptRequested: StateFlow<Boolean> = _interruptRequested.asStateFlow()

    private val _operationActive = MutableStateFlow(false)
    /** 当前是否有浏览器操作正在执行（控制中断按钮显示） */
    val operationActive: StateFlow<Boolean> = _operationActive.asStateFlow()

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
     * 检查是否已请求中断。供 [BrowserAgentTool] 在操作间轮询调用。
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
}
