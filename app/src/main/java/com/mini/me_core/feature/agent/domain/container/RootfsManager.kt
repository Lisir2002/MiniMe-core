package com.mini.me_core.feature.agent.domain.container

import com.mini.me_core.core.environment.EnvironmentDetector
import com.mini.me_core.core.util.FileLogger

/**
 * rootfs 解压/校验/版本/就绪检查相关的宿主侧文件操作（行为保持搬迁 / 签名保持）。
 *
 * 只搬迁「物理删除 rootfs/proot 全套」「杀掉运行中 proot/qemu 子进程」「x86_64 qemu 转译器
 * 部署与缺失校验」这类不触达生命周期 StateFlow 的纯文件操作；解压编排（installRootfsIfNeed
 * 进度回调）、initJob/initMutex 编排仍由 [LinuxContainerEngine] 门面持有。
 */
@Suppress("ClassOrdering") // 协作者分组，行为保持（primary constructor 语法上在类头，无法满足 detekt 属性→构造的逻辑顺序）
internal class RootfsManager(
    private val installer: ContainerInstaller,
) {
    private companion object {
        const val TAG = "LinuxContainerEngine"
    }

    /**
     * 物理删除 rootfs + proot 全套 + qemu（按当前 profile 架构）。高风险操作。
     * 由 [LinuxContainerEngine.resetContainer] 在已复位 bundleRepo 状态后调用。
     */
    fun deleteContainerFiles(profile: ContainerProfile) {
        runCatching {
            installer.rootfsDirFor(profile).deleteRecursively()
        }
        // 清理宿主侧可执行（跨 profile 共用的 proot 全套都清；x86_64 原生 proot 与 x86_64 专用 qemu
        // 只在当前或 x86_64 架构下清）
        runCatching { installer.prootBin.delete() }
        runCatching { installer.prootLoader.delete() }
        runCatching { installer.prootLoader32.delete() }
        runCatching { installer.prootX86Bin.delete() }
        runCatching { installer.prootX86Loader.delete() }
        runCatching { installer.prootX86Loader32.delete() }
        runCatching { installer.prootX86LibDir.deleteRecursively() }
        if (profile.arch == ContainerArch.X86_64) {
            runCatching { installer.rootfsX86Dir.deleteRecursively() }
            runCatching { installer.qemuX86Bin.delete() }
            runCatching {
                installer.qemuX86Bin.parentFile?.listFiles { f -> f.name.startsWith("qemu-") }?.forEach { it.delete() }
            }
        }
    }

    /**
     * 杀掉本 app 进程树下的 proot / qemu 子进程（终端会话 / AI 命令进程 / MCP 进程）。
     * 宿主 shell 下用 pkill 按可执行文件名匹配；忽略一切失败（无权限 / 无匹配进程都是正常情况）。
     */
    fun killRunningProotProcesses() {
        runCatching {
            val prootNames = buildList {
                add(installer.prootBin.name)
                installer.prootX86Bin.name.let { if (it.isNotBlank()) add(it) }
                installer.qemuX86Bin.name.let { if (it.isNotBlank()) add(it) }
            }.distinct()
            val killScript = prootNames.joinToString(" ") { "pkill -9 -x '$it' 2>/dev/null || true" }
            val pb = ProcessBuilder("/system/bin/sh", "-c", killScript)
                .redirectErrorStream(true)
            val p = pb.start()
            runCatching { p.inputStream.readBytes() }
            runCatching { p.waitFor() }
        }.onFailure { FileLogger.w(TAG, "杀掉 proot 进程失败（不阻塞重启）", it) }
    }

    /**
     * x86_64 容器：无论 rootfs 是否已安装，都要补一遍 qemu 转译器部署（幂等）。
     * 仅 arm64 宿主跑 x86_64 容器（qemu 转译）才需要；x86_64 宿主走 x86_64 原生 proot，无需 qemu。
     * copyAsset 内部有原子 rename + ETXTBSY 捕获，不会崩。若仍缺失则打告警（与
     * ProotInvocationBuilder.buildBaseProotArgv 齐提醒）。
     */
    fun ensureQemuTranslationDeployed(profile: ContainerProfile) {
        if (profile.arch == ContainerArch.X86_64 && !EnvironmentDetector.hostIsX86_64) {
            runCatching { installer.deployQemuX86() }
                .onFailure { t -> FileLogger.w(TAG, "doInit: deployQemuX86 失败 (不抛): ${t.message}") }
            if (!installer.qemuX86Bin.exists()) {
                FileLogger.w(TAG, "doInit: x86_64 profile 但 qemu 仍缺失（${installer.qemuX86Bin.absolutePath}），请再次点击初始化或重启 App")
            }
        }
    }
}
