package com.mini.me_core.feature.proxy.domain

/**
 * 拆分产物：运行时端口共享持有对象。
 *
 * 由 [ClashProxyManager] 在构造时创建并传给各协作者。所有实例方法（合成配置 / controller 请求 /
 * env 注入 / 预热换端口 / 提升预热实例）一律读写这里的两个字段，与拆分前读写
 * `runtimeMixedPort` / `runtimeControllerPort` 字段语义完全一致。
 *
 * 拆出此类仅是为了让 [ClashConfigSynthesizer] / [ClashControlClient] / [MihomoProcessManager]
 * 三方共享同一份可变端口状态；不新增任何业务判定。
 */
internal class ClashProxyPorts {
    /** 运行时实际使用的 mixed 端口（默认 = [ClashProxyManager.MIXED_PORT]）。 */
    @Volatile
    var mixed: Int = ClashProxyManager.MIXED_PORT

    /** 运行时实际使用的 controller 端口（默认 = [ClashProxyManager.CONTROLLER_PORT]）。 */
    @Volatile
    var controller: Int = ClashProxyManager.CONTROLLER_PORT

    /** 是否因端口冲突避让过默认 mixed 端口（供 UI 提示）。 */
    @Volatile
    var mixedWasAdjusted: Boolean = false
}
