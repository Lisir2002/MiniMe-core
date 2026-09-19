package com.mini.me_core.core.container

/**
 * 容器引擎对外耦合的反向端口（:core:container 不直接依赖 feature.terminal / feature.workspace）。
 *
 * 现状：LinuxContainerEngine 仍在 :app（它与 TerminalBundleRepository / WorkspacePathMapper /
 * RcbBridge / ClashProxyManager 深度耦合，完整下沉需在可编译迭代中分阶段进行）。
 * 这里先固化端口，作为后续把 LinuxContainerEngine 1403 行继续拆入本模块的接缝：
 *  - [WorkspaceHomeGateway] 替代 feature.workspace.domain.WorkspacePathMapper
 *  - [TerminalBundleGateway] 替代 feature.terminal.data.repository.TerminalBundleRepository
 *
 * 实现由 :app 提供（Hilt 注入）。
 */
interface WorkspaceHomeGateway {
    /** 容器内 home 目录映射（替代 workspacePathMapper.containerHome = … 的 setter 语义）。 */
    fun setContainerHome(home: String?)
}

/**
 * bundle 安装状态网关（替代 TerminalBundleRepository 的安装/卸载/状态/根目录更新语义）。
 * key 用 String 透传（原 TerminalBundleId.stableKey），避免本模块依赖 terminal 模块的枚举类型。
 */
interface TerminalBundleGateway {
    suspend fun isInstalledSnapshot(key: String): Boolean
    suspend fun markInstalled(key: String, displayName: String, packages: String)
    suspend fun markUninstalled(key: String)
    suspend fun markFailed(key: String, reason: String)
    suspend fun updateRootfsDir(dir: String)
    suspend fun migrateIfNeededAfterBoot(): Boolean
}
