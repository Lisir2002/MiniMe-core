package com.mini.me_core.newui.designsystem.layout

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.items
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import com.mini.me_core.newui.designsystem.primitive.AppTextCaption
import com.mini.me_core.newui.designsystem.component.AppChatBubble
import com.mini.me_core.newui.designsystem.component.AppChatMessageState
import com.mini.me_core.newui.designsystem.component.AppMessageScroller
import com.mini.me_core.newui.designsystem.token.generated.AppSpacing

/**
 * 聊天消息在会话流中的状态（organism 层对外语义，与分子层 [AppChatMessageState] 一一对应）。
 */
enum class ChatMessageStatus {
    /** 已入队待发送。 */
    Pending,

    /** 流式输出中（气泡尾部光标闪烁）。 */
    Streaming,

    /** 渲染完成。 */
    Complete,

    /** 发送失败，可由 [ChatMessageList.onRetry] 触发重试。 */
    Error,
}

/**
 * 一条聊天消息（organism · ChatMessageList 的输入模型）。
 *
 * @param id 消息唯一 id，用于列表 key、重试 / 点击回调定位。
 * @param content 消息正文（Markdown 文本，由 [AppChatBubble] 内部轻量渲染）。
 * @param isUser true = 用户侧气泡（品牌色、靠右）；false = AI 侧气泡（卡片底、靠左）。
 * @param timestamp 可选时间戳，渲染在气泡下方作为 [AppTextCaption] 辅助说明；null 不渲染。
 * @param status 消息状态机，默认 [ChatMessageStatus.Complete]。
 */
data class ChatMessage(
    val id: String,
    val content: String,
    val isUser: Boolean,
    val timestamp: String? = null,
    val status: ChatMessageStatus = ChatMessageStatus.Complete,
)

/**
 * 聊天消息列表（organism · ChatMessageList）：组合 [AppChatBubble] + [AppMessageScroller]。
 *
 * 设计来源：§3.11 会话流 / 组件分层——
 * - 滚动与自动跟随交给分子层 [AppMessageScroller]（reverseLayout LazyColumn + 新消息平滑滚到底 + 回底浮钮）；
 * - 单条气泡交给分子层 [AppChatBubble]（状态机 / 流式光标 / 失败重试 / Markdown）；
 * - organism 层只负责「消息列表」这一业务编排：id 做 key、状态映射、左右对齐、间距与回调接线。
 *
 * 间距：消息间垂直间距走 [AppSpacing.Sm]（已由 [AppMessageScroller] 的 spacedBy 提供，无需重复声明）。
 *
 * @param messages 消息列表，按时间正序传入（旧 → 新）；内部经 reverseLayout + asReversed 把最新消息锚定视觉底部。
 * @param onRetry 失败消息重试回调，参数为消息 [ChatMessage.id]；null 表示气泡不展示重试入口。
 * @param onMessageClick 点击消息回调，参数为消息 [ChatMessage.id]；null 表示消息不可点。
 */
@Composable
fun ChatMessageList(
    messages: List<ChatMessage>,
    modifier: Modifier = Modifier,
    onRetry: ((String) -> Unit)? = null,
    onMessageClick: ((String) -> Unit)? = null,
) {
    // messages 按时间正序传入；AppMessageScroller 的 reverseLayout 下 index 0 = 视觉底部 = 最新消息，
    // 故渲染前 asReversed，使最新消息落在 index 0（与 DesignGallery / ChatFlowGallery 用法一致）。
    AppMessageScroller(
        modifier = modifier,
        // 以最新一条消息的 id 作为 newMessageKey，触发自动跟随
        newMessageKey = messages.lastOrNull()?.id,
    ) {
        items(items = messages.asReversed(), key = { it.id }) { message ->
            val rowModifier = if (onMessageClick != null) {
                Modifier
                    .fillMaxWidth()
                    .clickable { onMessageClick(message.id) }
            } else {
                Modifier.fillMaxWidth()
            }
            Row(
                modifier = rowModifier,
                horizontalArrangement = if (message.isUser) Arrangement.End else Arrangement.Start,
            ) {
                // 时间戳挂在气泡下方；气泡本身宽度由内容撑开，靠右/靠左由 Row 对齐决定
                Column(
                    horizontalAlignment = if (message.isUser) Alignment.End else Alignment.Start,
                ) {
                    AppChatBubble(
                        text = message.content,
                        state = message.status.toBubbleState(),
                        isUser = message.isUser,
                        onRetry = onRetry?.let { callback -> { callback(message.id) } },
                    )
                    if (!message.timestamp.isNullOrEmpty()) {
                        AppTextCaption(
                            text = message.timestamp,
                            modifier = Modifier
                                .padding(top = AppSpacing.Tiny)
                                .padding(horizontal = AppSpacing.Xs),
                        )
                    }
                }
            }
        }
    }
}

/** organism 层状态 → 分子层气泡状态的一一映射。 */
private fun ChatMessageStatus.toBubbleState(): AppChatMessageState = when (this) {
    ChatMessageStatus.Pending -> AppChatMessageState.Pending
    ChatMessageStatus.Streaming -> AppChatMessageState.Streaming
    ChatMessageStatus.Complete -> AppChatMessageState.Complete
    ChatMessageStatus.Error -> AppChatMessageState.Error
}
