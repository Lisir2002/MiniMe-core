package com.mini.me_core.feature.agent.domain.container

import com.mini.me_core.feature.agent.domain.container.bridge.RcbBridge

/**
 * RCB 桥环境变量/令牌/注入装配（行为保持搬迁 / 签名保持）。
 *
 * 只负责「如何把桥端点与本会话一次性令牌装配进容器进程环境」以及会话结束令令牌失效；
 * [RcbBridge] 本身的安全逻辑（accept 循环、 presented-token 校验、剪贴板按次门控 open 默认拒绝）
 * 一字未动，仍在 RcbBridge 内。本类不读取/改写剪贴板门控，仅搬运 env 注入装配。
 */
internal class RcbBridgeInjector(
    private val rcbBridge: RcbBridge,
) {
    /** 桥接令牌是否已为本容器会话启动；会话结束后由 [endSession] 复位。 */
    @Volatile
    private var bridgeSessionActive: Boolean = false

    /**
     * 确保本桥会话已启动并返回 (RCB_BRIDGE_ADDR, RCB_BRIDGE_TOKEN)。
     *
     * 逐字搬迁自原 `LinuxContainerEngine.buildContainerEnv` 的桥接段：本容器会话首个 proot
     * 进程生成一次性随机令牌，同会话后续进程复用；分支条件与 else 分支副作用完全一致。
     */
    fun ensureSession(): Pair<String, String> {
        // 桥接会话：本容器会话首个 proot 进程时生成一次性随机令牌；同会话后续进程复用。
        val bridgeAddr = rcbBridge.address()
        val bridgeToken = if (bridgeSessionActive && rcbBridge.authToken().isNotEmpty()) {
            rcbBridge.authToken()
        } else {
            bridgeSessionActive = true
            rcbBridge.startBridgeSession()
        }
        return bridgeAddr to bridgeToken
    }

    /**
     * 容器会话结束：使本会话的一次性桥接令牌立即失效（下次会话重新生成）。
     * 由终端/AI 会话销毁路径经 [LinuxContainerEngine.onContainerSessionEnd] 调用。
     */
    fun endSession() {
        rcbBridge.endBridgeSession()
        bridgeSessionActive = false
    }
}
