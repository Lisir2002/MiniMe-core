package com.mini.me_core.core.container.api

import kotlinx.coroutines.flow.StateFlow
import net.schmizz.sshj.connection.channel.direct.Session

/**
 * 远程 SSH 连接端口：terminal / workspace 域消费到的 RemoteSshConnection 能力面。
 *
 * 安全敏感区（TOFU / fail-closed / 指纹常量时间比较）全部留在 agent 域的具体实现内，
 * 本端口只暴露行为面，适配器纯委托，不改任何认证 / 重试 / keepalive 时序。
 */
interface SshConnectionPort {
    /** 连接状态 StateFlow（DISCONNECTED / CONNECTING / CONNECTED / FAILED）。 */
    val connectionState: StateFlow<ConnectionState>

    /** 当前生效的连接配置（未连接 / 未配置时为 null）。 */
    val config: RemoteConnectionConfig?

    /** 远程服务器上的真实 $HOME（连接成功后探测一次），未连接为 null。 */
    val remoteHome: String?

    /** 当前是否已建立 SSH 连接。 */
    fun isConnected(): Boolean

    /** 用给定配置建立 / 重连 SSH（含 TOFU 主机指纹校验，fail-closed）。 */
    suspend fun connect(config: RemoteConnectionConfig)

    /** 开一个交互式 shell channel（调用方再 allocateDefaultPTY + startShell）。 */
    suspend fun startShellSession(): Session

    /** 开一个 exec channel 跑单条命令；调用方负责消费 stdout/stderr/exitStatus 并 close。 */
    suspend fun startExecSession(command: String): Session.Command

    /** 在远程 $HOME 下创建指向当前工作区的 workspace 符号链接。 */
    suspend fun updateWorkspaceSymlink(workspacePath: String)

    /** 注册「断线重连成功」回调；用于自动重建交互终端 tab。 */
    fun registerOnReconnectedListener(listener: suspend () -> Unit)
}
