package com.mini.me_core.feature.agent.domain.container

import android.os.Environment
import com.mini.me_core.core.environment.EnvironmentDetector
import com.mini.me_core.core.util.FileLogger
import java.io.File

/**
 * 存储/工作区/各目录绑定挂载登记与路径（行为保持搬迁 / 签名保持）。
 *
 * 每个方法只把「一组 -b/-w/-q 绑定」追加到调用方传入的 argv；由
 * [ProotInvocationBuilder.buildBaseProotArgv] 按原 [ProotInvocation] 装配顺序调用，
 * 因此绑定先后顺序、源/目标路径、mkdirs 副作用与告警日志均与原实现逐字一致。
 */
@Suppress("ClassOrdering") // 协作者分组，行为保持（primary constructor 语法上在类头，无法满足 detekt 属性→构造的逻辑顺序）
internal class ContainerBindRegistry(
    private val installer: ContainerInstaller,
) {
    private companion object {
        const val TAG = "LinuxContainerEngine"
    }

    /**
     * x86_64 容器 + 非 x86_64 宿主（arm64 宿主）：proot 的 -q 注入静态 qemu 转译器。
     * -q 后的路径是「宿主侧」绝对路径（qemu 本体跑在 proot 之外，需宿主 loader 加载）。
     * x86_64 宿主（x86_64 模拟器）原生执行 x86_64 rootfs，无需 -q。
     */
    fun appendQemuTranslation(argv: MutableList<String>, profile: ContainerProfile) {
        if (profile.arch == ContainerArch.X86_64 && !EnvironmentDetector.hostIsX86_64) {
            val qemu = installer.qemuX86Bin
            if (qemu.exists()) {
                argv.add("-q")
                argv.add(qemu.absolutePath)
            } else {
                FileLogger.w(TAG, "x86_64 profile 已选但 qemu 转译器缺失（${qemu.absolutePath}），按原生 aarch64 启动将失败，请重新初始化容器")
            }
        }
    }

    /**
     * 把当前工作区目录绑定到容器内 ~/workspace（即 /root/workspace），使命令与文件工具作用于同一目录，
     * 并设 -w 为该目录。
     */
    fun appendWorkspace(argv: MutableList<String>, projectPath: String?) {
        if (projectPath != null) {
            argv.add("-b")
            argv.add("$projectPath:/root/workspace")
            argv.add("-w")
            argv.add("/root/workspace")
        }
    }

    /**
     * 把 AI 配置目录绑定到容器内 /root/.minime（读写）：内含 skills/ 与 mcp.json。
     * 宿主物理目录独立于 rootfs，容器升级重装不丢用户数据。proot 的 -b 要求源路径存在，故先确保目录已建。
     */
    fun appendMinime(argv: MutableList<String>) {
        val minimeDir = installer.minimeDir.apply { mkdirs() }
        argv.add("-b")
        argv.add("${minimeDir.absolutePath}:/root/.minime")
    }

    /**
     * 「共享设备存储」：开启时把设备外存绑定到容器内 /root/storage/shared。
     * 走 legacy storage，per-process 视图，新进程即生效。proot 的 -b 只会在「父目录存在」时自动创建
     * 固定目标点；rootfs 里通常没有 /root/storage，若不先建父目录会导致 ls 报 no such。
     */
    fun appendStorageShared(argv: MutableList<String>, rootfs: String, enabled: Boolean) {
        if (enabled) {
            val external = Environment.getExternalStorageDirectory()
            if (external != null && external.exists()) {
                File("$rootfs/root/storage").mkdirs()
                argv.add("-b")
                argv.add("${external.absolutePath}:/root/storage/shared")
            } else {
                FileLogger.w(TAG, "存储共享已开启但外存路径不可用（${external?.absolutePath}），跳过绑定")
            }
        }
    }

    /**
     * 自动挂载宿主 Download 目录到容器 /mnt/shared，实现容器与宿主快速互传。
     * 与存储共享同走 legacy storage；源目录不存在时跳过（与外存共享一致）。
     */
    fun appendDownload(argv: MutableList<String>, rootfs: String) {
        runCatching {
            val download = Environment.getExternalStoragePublicDirectory(
                Environment.DIRECTORY_DOWNLOADS
            )
            if (download != null && download.exists()) {
                File("$rootfs/mnt").mkdirs()
                argv.add("-b")
                argv.add("${download.absolutePath}:/mnt/shared")
            }
        }
    }

    /** 自定义 profile 的额外绑定与参数（内置 profile 这俩为空，此段无操作）。 */
    fun appendProfileExtras(argv: MutableList<String>, profile: ContainerProfile) {
        for (b in profile.extraBindings) {
            argv.add("-b")
            argv.add(b)
        }
        argv.addAll(profile.extraArgs)
    }
}
