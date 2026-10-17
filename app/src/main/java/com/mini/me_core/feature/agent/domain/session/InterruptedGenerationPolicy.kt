package com.mini.me_core.feature.agent.domain.session

import com.mini.me_core.feature.agent.presentation.MessageRole

/**
 * 冷启动「流式生成被中断」恢复的纯判定策略（任务 B）。
 *
 * 背景：应用切后台被系统回收（LMKD/厂商管理）时，正在流式生成的助手消息
 * 内容停在中途且无任何中断标记，用户下次打开看到半截回复，无从判断。
 * 恢复链路：流式开始写 `sessionId → pid` 活跃标记 → 进程死亡 → 冷启动时
 * 对 pid 不匹配的会话，给其最后一条助手消息补记中断提示。
 *
 * 本对象只做判定（无 IO、无 Android 依赖），可完整单测；
 * 实际的标记读写与追加落库在 [SessionUseCase]。
 */
internal object InterruptedGenerationPolicy {

    /**
     * 判定是否需要给某会话补记「生成被中断」提示。
     *
     * @param markerPid 写入生成标记时的进程 pid
     * @param currentPid 当前进程 pid
     * @param lastMessage 该会话按时间序的最后一条消息（分块消息已合并）；null 表示空会话
     * @param notice 中断提示文案（用于幂等比对）
     * @return 需要追加提示的消息 id；null 表示无需处理
     */
    fun resolveTargetMessageId(
        markerPid: Int,
        currentPid: Int,
        lastMessage: LastMessage?,
        notice: String,
    ): String? {
        // 同进程：标记就是本进程写的，绝非进程死亡残留。
        // Activity/ViewModel 重建、用户手动停止后取消 Job 等场景都走这条，不会误伤。
        if (markerPid == currentPid) return null
        // 空会话没有可补记的消息。
        // 最后一条不是助手消息时跳过：若为 TOOL 占位行，由 initColdStartCleanup 的
        // 「执行被中断」逻辑统一负责，避免同一中断补两条提示。
        if (lastMessage == null || lastMessage.role != MessageRole.ASSISTANT) return null
        // 幂等：内容已含提示说明补记过（如标记清除前又触发一次恢复），不重复追加。
        if (lastMessage.content.contains(notice)) return null
        return lastMessage.id
    }

    /** 恢复判定所需的最小消息视图（由 Agent_message 实体映射而来）。 */
    data class LastMessage(
        val id: String,
        val role: MessageRole,
        val content: String,
    )
}
