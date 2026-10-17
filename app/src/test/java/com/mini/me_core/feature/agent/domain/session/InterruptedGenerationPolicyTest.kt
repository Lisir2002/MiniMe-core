package com.mini.me_core.feature.agent.domain.session

import com.mini.me_core.feature.agent.domain.session.InterruptedGenerationPolicy.LastMessage
import com.mini.me_core.feature.agent.presentation.MessageRole
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

/**
 * 冷启动「流式生成被中断」恢复判定回归（任务 B）。
 *
 * 覆盖四类关键分支：同进程不误判、进程死亡+助手消息补记、
 * 最后一条非助手消息跳过（TOOL 由工具行清理负责）、幂等不重复追加。
 */
class InterruptedGenerationPolicyTest {

    private val notice = "生成被中断（应用在后台被系统回收，回复内容可能不完整）"

    private fun lastMessage(
        id: String = "m1",
        role: MessageRole = MessageRole.ASSISTANT,
        content: String = "半截回复",
    ) = LastMessage(id = id, role = role, content = content)

    @Test
    fun sameProcess_markerWrittenByCurrentProcess_returnsNull() {
        // 同进程：Activity/ViewModel 重建、用户停止后 Job 取消都会走到这里，
        // 标记是本进程写的，绝不能误判为进程死亡。
        val target = InterruptedGenerationPolicy.resolveTargetMessageId(
            markerPid = 1234,
            currentPid = 1234,
            lastMessage = lastMessage(),
            notice = notice,
        )

        assertNull(target)
    }

    @Test
    fun deadProcess_lastMessageIsAssistant_returnsTargetId() {
        val target = InterruptedGenerationPolicy.resolveTargetMessageId(
            markerPid = 1234,
            currentPid = 5678,
            lastMessage = lastMessage(id = "assistant-1"),
            notice = notice,
        )

        assertEquals("assistant-1", target)
    }

    @Test
    fun deadProcess_lastMessageIsTool_returnsNull() {
        // 最后一条是 TOOL 占位行：由 initColdStartCleanup 的「执行被中断」负责，
        // 这里跳过，避免同一中断补两条提示。
        val target = InterruptedGenerationPolicy.resolveTargetMessageId(
            markerPid = 1234,
            currentPid = 5678,
            lastMessage = lastMessage(role = MessageRole.TOOL),
            notice = notice,
        )

        assertNull(target)
    }

    @Test
    fun deadProcess_lastMessageIsUser_returnsNull() {
        // 生成尚未落任何助手内容（半截都在内存里随进程消亡）：最后一条是用户消息，
        // 没有可补记的正文，跳过（不能把中断提示写到用户自己头上）。
        val target = InterruptedGenerationPolicy.resolveTargetMessageId(
            markerPid = 1234,
            currentPid = 5678,
            lastMessage = lastMessage(role = MessageRole.USER),
            notice = notice,
        )

        assertNull(target)
    }

    @Test
    fun deadProcess_emptySession_returnsNull() {
        val target = InterruptedGenerationPolicy.resolveTargetMessageId(
            markerPid = 1234,
            currentPid = 5678,
            lastMessage = null,
            notice = notice,
        )

        assertNull(target)
    }

    @Test
    fun deadProcess_noticeAlreadyAppended_returnsNull() {
        // 幂等：标记清除前又触发一次恢复（或历史残留），已含提示不重复追加。
        val target = InterruptedGenerationPolicy.resolveTargetMessageId(
            markerPid = 1234,
            currentPid = 5678,
            lastMessage = lastMessage(content = "半截回复\n\n$notice"),
            notice = notice,
        )

        assertNull(target)
    }

    @Test
    fun deadProcess_similarButDifferentNotice_appends() {
        // 含相似字样但非完整提示文案（如用户自己的话里提到「中断」），仍判定需要补记。
        val target = InterruptedGenerationPolicy.resolveTargetMessageId(
            markerPid = 1234,
            currentPid = 5678,
            lastMessage = lastMessage(content = "刚才那次生成中断了"),
            notice = notice,
        )

        assertEquals("m1", target)
    }

    @Test
    fun deadProcess_emptyAssistantContent_stillAppends() {
        // 助手消息内容为空串（定期落库恰落在空快照）也要补记：空正文+提示
        // 好过裸一个无主提示，且 role 已是 ASSISTANT，id 稳定可复用。
        val target = InterruptedGenerationPolicy.resolveTargetMessageId(
            markerPid = 1234,
            currentPid = 5678,
            lastMessage = lastMessage(content = ""),
            notice = notice,
        )

        assertEquals("m1", target)
    }
}
