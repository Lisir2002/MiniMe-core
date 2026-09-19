package com.mini.me_core.feature.browser.domain

/**
 * :feature:browser 端口（架构规则 #1：feature 只依赖 :core:*，禁止 feature→feature 直连）。
 *
 * 反转自原对 feature.proxy.ClashProxyManager / feature.workspace.WorkspacePathMapper 的直接依赖；
 * 实现由 :app 侧适配器注入（见 feature/browser 的 DI 模块）。
 */
interface BrowserProxyGateway {
    /** 代理是否启用。 */
    fun isEnabled(): Boolean
    /** 本地混合代理端口。 */
    val mixedPort: Int
    /** 代理状态流（Boolean，true=启用）。 */
    val state: kotlinx.coroutines.flow.Flow<Boolean>
}

interface BrowserWorkspaceGateway {
    /** 容器相对路径 → 宿主机文件（与原 WorkspacePathMapper.toHostFile 同签名）。 */
    fun toHostFile(containerPath: String): java.io.File
    /** 宿主机路径 → 容器相对路径。 */
    fun toContainerPath(hostPath: String): String
}
