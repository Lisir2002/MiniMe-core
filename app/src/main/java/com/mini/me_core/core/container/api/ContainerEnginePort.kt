package com.mini.me_core.core.container.api

import com.mini.me_core.feature.terminal.data.bundle.TerminalBundleId
import kotlinx.coroutines.flow.StateFlow

/**
 * 容器引擎端口：terminal / workspace 域实际消费到的 LinuxContainerEngine 能力面。
 *
 * 仅包含终端与会话/文件/资源/安装入口真正调用的方法与 Flow，不臆造；
 * 由 agent 域的薄适配器纯委托到 @Singleton 的 LinuxContainerEngine，
 * 方法体一行转发，不改任何线程/时序/Flow 初值。
 */
@Suppress("TooManyFunctions") // 端口面按 13 个调用点聚合，行为保持（非新增能力）
interface ContainerEnginePort {
    /** 容器初始化实时进度（rootfs 解压 / proot 部署 / bundle 安装中 / Ready / Failed）。 */
    val initProgress: StateFlow<ContainerInitState>

    /** Bundle 安装聚合进度（每 100ms 一帧的不可变快照 StateFlow）。 */
    val aggregateProgress: StateFlow<AggregateProgressState>

    /** 容器是否物理就绪（rootfs + proot 已落盘）。 */
    fun isContainerInstalled(): Boolean

    /** 当前 profile 默认 shell（/bin/bash 或 /bin/ash）。 */
    fun defaultShell(): String

    /** 确保容器已初始化（同步触发 rootfs 解压 / proot 部署 / 首包 provision）。 */
    suspend fun ensureInstalled()

    /** 「共享设备存储」开关是否开着（仅影响后续新起的 proot 进程）。 */
    fun isStorageShareEnabled(): Boolean

    /**
     * 为给定命令构造一次完整 proot 调用（argv + 环境），供 Termux TerminalSession /
     * ProcessBuilder 直接 execvp。含 RCB 桥令牌注入、proot -b 绑定、QEMU 转译参数。
     */
    fun buildProotInvocation(command: String, projectPath: String?): ProotInvocation

    /** 在容器内同步执行命令并回 stdout；超时强杀并追加超时提示。 */
    suspend fun runCommandSync(command: String, projectPath: String?, timeoutMs: Long): String

    /** 清空 rootfs 重置容器（保留 workspace）。 */
    suspend fun resetContainer()

    /** 切换 apk 镜像源并 `apk update`。 */
    suspend fun setApkMirrorAndUpdate(mirror: String): Boolean

    /** 重启 proot（保留数据与已装包）。 */
    suspend fun restartContainerKeepData()

    /** 安装单个功能包 bundle。 */
    suspend fun installBundle(id: TerminalBundleId)

    /** 卸载单个功能包 bundle。 */
    suspend fun uninstallBundle(id: TerminalBundleId)

    /** 按给定顺序依次安装多个 bundle。 */
    suspend fun installBundlesOrdered(ids: Iterable<TerminalBundleId>)

    /** 在容器 apk 世界安装自定义包；返回安装失败的包名列表（全成功时返回空）。 */
    suspend fun installCustomPackages(pkgs: List<String>): List<String>

    /** 在容器 apk 世界卸载自定义包；返回是否成功。 */
    suspend fun uninstallCustomPackage(pkg: String): Boolean

    /** 从容器真实 apk 世界重新同步 bundle 安装状态标记。 */
    suspend fun refreshBundleStatesFromApk()

    /** 从容器真实 apk 世界刷新「自定义包」列表快照。 */
    suspend fun refreshCustomPackagesSnapshot()
}
