package com.mini.me_core.feature.agent.domain.container.ports

import com.mini.me_core.core.container.api.AggregateProgressState
import com.mini.me_core.core.container.api.ContainerEnginePort
import com.mini.me_core.core.container.api.ContainerInitState
import com.mini.me_core.core.container.api.ProotInvocation
import com.mini.me_core.feature.agent.domain.container.LinuxContainerEngine
import com.mini.me_core.feature.terminal.data.bundle.TerminalBundleId
import kotlinx.coroutines.flow.StateFlow
import javax.inject.Inject

/**
 * [ContainerEnginePort] 的薄适配器：纯委托到 @Singleton 的 [LinuxContainerEngine]，
 * 方法体一行转发，不改任何线程 / 调度 / Flow 初值 / 时序。
 *
 * 仅服务于 terminal / workspace 域的解耦（P3-12）；agent 域内部仍直接使用具体类。
 */
class LinuxContainerEngineAdapter @Inject constructor(
    private val delegate: LinuxContainerEngine,
) : ContainerEnginePort {

    override val initProgress: StateFlow<ContainerInitState> get() = delegate.initProgress

    override val aggregateProgress: StateFlow<AggregateProgressState> get() = delegate.progressAggregator.state

    override fun isContainerInstalled(): Boolean = delegate.isContainerInstalled()

    override fun defaultShell(): String = delegate.defaultShell()

    override suspend fun ensureInstalled() = delegate.ensureInstalled()

    override fun isStorageShareEnabled(): Boolean = delegate.isStorageShareEnabled()

    override fun buildProotInvocation(command: String, projectPath: String?): ProotInvocation =
        delegate.buildProotInvocation(command, projectPath)

    override suspend fun runCommandSync(command: String, projectPath: String?, timeoutMs: Long): String =
        delegate.runCommandSync(command, projectPath, timeoutMs)

    override suspend fun resetContainer() = delegate.resetContainer()

    override suspend fun setApkMirrorAndUpdate(mirror: String): Boolean =
        delegate.setApkMirrorAndUpdate(mirror)

    override suspend fun restartContainerKeepData() = delegate.restartContainerKeepData()

    override suspend fun installBundle(id: TerminalBundleId) = delegate.installBundle(id)

    override suspend fun uninstallBundle(id: TerminalBundleId) = delegate.uninstallBundle(id)

    override suspend fun installBundlesOrdered(ids: Iterable<TerminalBundleId>) =
        delegate.installBundlesOrdered(ids)

    override suspend fun installCustomPackages(pkgs: List<String>): List<String> =
        delegate.installCustomPackages(pkgs)

    override suspend fun uninstallCustomPackage(pkg: String): Boolean =
        delegate.uninstallCustomPackage(pkg)

    override suspend fun refreshBundleStatesFromApk() = delegate.refreshBundleStatesFromApk()

    override suspend fun refreshCustomPackagesSnapshot() = delegate.refreshCustomPackagesSnapshot()
}
