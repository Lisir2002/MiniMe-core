package com.mini.me_core.feature.agent.domain.container

/** agent 域执行模式（与 settings ExecutionMode 解耦的端口侧模型）。 */
enum class AgentExecutionMode { LOCAL_PROOT, REMOTE_SSH }

/** 当前执行模式快照（远程连接配置的内存形式）。 */
data class ExecutionModeSnapshot(
    val mode: AgentExecutionMode,
    val remoteHost: String,
    val remotePath: String,
) {
    val isRemote: Boolean get() = mode == AgentExecutionMode.REMOTE_SSH
}

/**
 * 执行模式端口：agent 域只面向此接口，不直接 import settings.data.repository.ExecutionMode。
 * 实现见 :app（ExecutionModePortAdapter 委派 ExecutionModeHolder/ExecutionModeRepository）。
 */
interface ExecutionModePort {
    fun currentMode(): AgentExecutionMode
    fun currentSnapshot(): ExecutionModeSnapshot
}
