package com.mini.me_core.di

import com.mini.me_core.feature.agent.domain.container.AgentExecutionMode
import com.mini.me_core.feature.agent.domain.container.ExecutionModePort
import com.mini.me_core.feature.agent.domain.container.ExecutionModeSnapshot
import com.mini.me_core.feature.settings.data.repository.ExecutionMode
import com.mini.me_core.feature.settings.data.repository.ExecutionModeHolder
import javax.inject.Inject
import javax.inject.Singleton

/**
 * [ExecutionModePort] 的 :app 实现：把 settings 的 ExecutionModeHolder 委派成 agent 域端口模型，
 * 使 agent 域不再直接 import settings.data.repository.ExecutionMode。
 */
@Singleton
class ExecutionModePortAdapter @Inject constructor(
    private val holder: ExecutionModeHolder,
) : ExecutionModePort {

    override fun currentMode(): AgentExecutionMode =
        if (holder.currentMode() == ExecutionMode.REMOTE_SSH) AgentExecutionMode.REMOTE_SSH
        else AgentExecutionMode.LOCAL_PROOT

    override fun currentSnapshot(): ExecutionModeSnapshot =
        ExecutionModeSnapshot(mode = currentMode(), remoteHost = "", remotePath = "/workspace")
}
