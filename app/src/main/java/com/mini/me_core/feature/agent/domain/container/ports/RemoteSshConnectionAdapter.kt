package com.mini.me_core.feature.agent.domain.container.ports

import com.mini.me_core.core.container.api.ConnectionState
import com.mini.me_core.core.container.api.RemoteConnectionConfig
import com.mini.me_core.core.container.api.SshConnectionPort
import com.mini.me_core.feature.agent.domain.container.RemoteSshConnection
import kotlinx.coroutines.flow.StateFlow
import net.schmizz.sshj.connection.channel.direct.Session
import javax.inject.Inject

/**
 * [SshConnectionPort] 的薄适配器：纯委托到 @Singleton 的 [RemoteSshConnection]。
 *
 * TOFU / fail-closed / 指纹常量时间比较 / keepalive 线程全部留在具体类内，
 * 本适配器不触碰任何安全行为，仅做接口面转发。
 */
class RemoteSshConnectionAdapter @Inject constructor(
    private val delegate: RemoteSshConnection,
) : SshConnectionPort {

    override val connectionState: StateFlow<ConnectionState> get() = delegate.connectionState

    override val config: RemoteConnectionConfig? get() = delegate.config

    override val remoteHome: String? get() = delegate.remoteHome

    override fun isConnected(): Boolean = delegate.isConnected()

    override suspend fun connect(config: RemoteConnectionConfig) = delegate.connect(config)

    override suspend fun startShellSession(): Session = delegate.startShellSession()

    override suspend fun startExecSession(command: String): Session.Command =
        delegate.startExecSession(command)

    override suspend fun updateWorkspaceSymlink(workspacePath: String) =
        delegate.updateWorkspaceSymlink(workspacePath)

    override fun registerOnReconnectedListener(listener: suspend () -> Unit) =
        delegate.registerOnReconnectedListener(listener)
}
