package com.mini.me_core.di

import com.mini.me_core.core.container.api.ContainerEnginePort
import com.mini.me_core.core.container.api.ContainerInstallerPort
import com.mini.me_core.core.container.api.SshConnectionPort
import com.mini.me_core.feature.agent.domain.container.ports.ContainerInstallerAdapter
import com.mini.me_core.feature.agent.domain.container.ports.LinuxContainerEngineAdapter
import com.mini.me_core.feature.agent.domain.container.ports.RemoteSshConnectionAdapter
import dagger.Binds
import dagger.Module
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import javax.inject.Singleton

/**
 * 把 [ContainerEnginePort] / [ContainerInstallerPort] / [SshConnectionPort]
 * 绑定到 agent 域的薄适配器。适配器内部委托到原 @Singleton 具体单例，
 * 因此 terminal / workspace 通过端口拿到的仍是同一实例、同一作用域。
 *
 * 独立 @Module 以避免与 [AgentModule]（object，不能 @Binds）以及
 * Mcp/Permission/Git/Packager 仓库绑定相互干扰。
 */
@Module
@InstallIn(SingletonComponent::class)
abstract class ContainerPortModule {

    @Binds
    @Singleton
    abstract fun bindContainerEnginePort(impl: LinuxContainerEngineAdapter): ContainerEnginePort

    @Binds
    @Singleton
    abstract fun bindContainerInstallerPort(impl: ContainerInstallerAdapter): ContainerInstallerPort

    @Binds
    @Singleton
    abstract fun bindSshConnectionPort(impl: RemoteSshConnectionAdapter): SshConnectionPort
}
