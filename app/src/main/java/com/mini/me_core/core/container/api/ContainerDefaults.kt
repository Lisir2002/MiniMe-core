package com.mini.me_core.core.container.api

/**
 * 默认 Alpine apk 镜像源（阿里云）。
 *
 * 历史上定义在 ContainerInstaller.ALPINE_MIRROR；为解除 terminal/workspace 对
 * agent.container 具体类的直耦，上提到中立端口包。原 ContainerInstaller.ALPINE_MIRROR
 * 保留为别名，值逐字不变。
 */
const val DEFAULT_APK_MIRROR = "http://mirrors.aliyun.com/alpine"
