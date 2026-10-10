package com.mini.me_core.core.container.api

import java.io.File

/**
 * 容器安装器端口：terminal / workspace 域消费到的 ContainerInstaller 能力面
 * （rootfs 目录解析、minime 配置目录、自定义 rootfs 重置）。
 */
interface ContainerInstallerPort {
    /** 当前激活 profile 对应的 rootfs 目录（默认内置 arm64）。 */
    val rootfsDir: File

    /** AI 配置目录（独立于 rootfs，~/.minime 绑定到此宿主目录）。 */
    val minimeDir: File

    /** 指定 profile 对应的 rootfs 目录（随 profile 的 arch / id 变化）。 */
    fun rootfsDirFor(profile: ContainerProfile): File

    /** 删除自定义 profile 导入的 rootfs 目录（切 profile / 删 profile 时调用）。 */
    fun deleteCustomRootfs(profile: ContainerProfile)

    /** 重置内置 arm64 rootfs（下次初始化重新解压 + provision）。 */
    fun resetBuiltinRootfs()

    /** 重置内置 x86_64 rootfs（下次初始化重新解压 + QEMU 转译 provision）。 */
    fun resetBuiltinX86Rootfs()
}
