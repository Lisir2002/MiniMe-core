package com.mini.me_core.di

import com.mini.me_core.feature.browser.domain.BrowserProxyGateway
import com.mini.me_core.feature.browser.domain.BrowserWorkspaceGateway
import com.mini.me_core.feature.proxy.domain.ClashProxyManager
import com.mini.me_core.feature.workspace.domain.WorkspacePathMapper
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import javax.inject.Inject
import javax.inject.Singleton

/**
 * :app 侧适配器：把 feature.proxy / feature.workspace 的具体实现接到 :feature:browser 的端口。
 * 架构规则 #1：feature→feature 不直连，依赖在 :app 装配。
 */
@Module
@InstallIn(SingletonComponent::class)
object BrowserPortsModule {

    @Provides
    @Singleton
    fun provideProxyGateway(clash: ClashProxyManager): BrowserProxyGateway =
        object : BrowserProxyGateway {
            override fun isEnabled(): Boolean = clash.isEnabled()
            override val mixedPort: Int = ClashProxyManager.MIXED_PORT
            override val state: Flow<Boolean> = clash.state.map { it is Boolean && it }
        }

    @Provides
    @Singleton
    fun provideWorkspaceGateway(mapper: WorkspacePathMapper): BrowserWorkspaceGateway =
        object : BrowserWorkspaceGateway {
            override fun toHostFile(containerPath: String): java.io.File = mapper.toHostFile(containerPath)
            override fun toContainerPath(hostPath: String): String = mapper.toContainerPath(hostPath)
        }
}
