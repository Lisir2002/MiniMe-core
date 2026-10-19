package com.mini.me_core.core.splash

import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/**
 * 启动阶段上报：把 MiniMeCore.onCreate 的三个真实启动阶段暴露给启动画面，
 * 取代原来按时间轴硬编码映射的「假进度」。
 *
 * 阶段与 MiniMeCore 的 [启动/1/3]、[启动/2/3]、[启动/3/3] 日志一一对应。
 * ASYNC_WARMUP 阶段的预热任务全部在后台并行，不阻塞首帧。
 */
enum class StartupPhase {
    /** Application.onCreate 尚未开始（理论态，正常启动瞬间即过）。 */
    STARTING,

    /** [启动/1/3] 数据层：迁移、BouncyCastle、通知渠道、AGENT 库预热与自检。 */
    DATA_LAYER,

    /** [启动/2/3] 核心服务：凭据桥接 + MCP（此时 AppInitState.markReady 放行）。 */
    CORE_SERVICES,

    /** [启动/3/3] 异步预热：文档/提示词提取、日志导出（后台并行，不阻塞）。 */
    ASYNC_WARMUP,
}

object StartupProgress {

    private val _phase = MutableStateFlow(StartupPhase.STARTING)

    /** 当前启动阶段。冷启动瞬间从 STARTING 快速推进，之后只增不减。 */
    val phase: StateFlow<StartupPhase> = _phase.asStateFlow()

    /** 上报启动阶段。由 Application.onCreate 在三个阶段边界调用。 */
    fun update(phase: StartupPhase) {
        _phase.value = phase
    }

    /**
     * 阶段 → 进度条目标值（0..1）。
     *
     * 权重按三阶段实际阻塞程度分配：数据层最重（同步 DB + 自检），
     * 核心服务次之，异步预热只是后台收尾。就绪后恒为 1.0。
     */
    fun progressOf(phase: StartupPhase, isReady: Boolean): Float = when {
        isReady -> 1f
        phase == StartupPhase.DATA_LAYER -> 0.25f
        phase == StartupPhase.CORE_SERVICES -> 0.6f
        phase == StartupPhase.ASYNC_WARMUP -> 0.9f
        else -> 0.1f
    }
}
