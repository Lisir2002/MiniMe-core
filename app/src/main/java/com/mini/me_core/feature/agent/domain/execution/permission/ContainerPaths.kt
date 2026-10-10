package com.mini.me_core.feature.agent.domain.execution.permission

/**
 * 容器内路径常量（纯 Kotlin，无 Android 依赖，可单测）。
 *
 * 容器 HOME 由 LinuxContainerEngine 在启动时注入环境变量 `HOME=/root`。
 * 权限/解析层在做路径归一与灾难性目标黑名单比对前，统一把 `$HOME`、`${HOME}`、
 * `~`、`~user` 归一到这里，避免 `rm -rf $HOME` / `rm -rf ~` 因字面量未展开而绕过黑名单。
 */
object ContainerPaths {
    /** 容器内 root 用户家目录（与 LinuxContainerEngine 注入的 HOME 保持一致）。 */
    const val CONTAINER_HOME: String = "/root"

    /** 容器内工作区根（与既有 checkCatastrophicRm 中的 /root/workspace 判定保持一致）。 */
    const val CONTAINER_WORKSPACE: String = "/root/workspace"
}
