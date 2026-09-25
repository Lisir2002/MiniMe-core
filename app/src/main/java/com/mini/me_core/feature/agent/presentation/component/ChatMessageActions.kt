package com.mini.me_core.feature.agent.presentation.component

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Bookmark
import androidx.compose.material.icons.rounded.ContentCopy
import androidx.compose.material.icons.rounded.Delete
import androidx.compose.material.icons.rounded.Edit
import androidx.compose.material.icons.rounded.Refresh
import androidx.compose.material.icons.rounded.Share
import androidx.compose.material.icons.rounded.VolumeUp
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Text
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.mini.me_core.R
import com.mini.me_core.feature.agent.presentation.AgentUIMessage
import com.mini.me_core.feature.agent.presentation.MessageRole

/**
 * MiniMe 消息快捷操作面板（F2.3）。
 *
 * 长按消息弹出 ModalBottomSheet：
 *  - 顶部消息摘要（前 50 字，12sp onSurfaceVariant）；
 *  - AI 消息：复制全文 / 复制代码块(多块子列表) / 重新生成 / 编辑并重发 / 收藏 / 分享 / 朗读；
 *  - 用户消息：复制 / 编辑并重发 / 删除（危险操作 error 色，外部弹确认框）；
 *  - 每项高 48dp，图标 24dp + 文字 14sp。
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ChatMessageActionsSheet(
    message: AgentUIMessage,
    onDismiss: () -> Unit,
    onCopyText: () -> Unit,
    onCopyCodeBlock: (String) -> Unit,
    onRegenerate: () -> Unit = {},
    onEditResend: () -> Unit = {},
    onBookmark: () -> Unit = {},
    onShare: () -> Unit = {},
    onReadAloud: () -> Unit = {},
    onDelete: () -> Unit = {},
) {
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    val isUser = message.role == MessageRole.USER
    // 提取消息中的代码块，供「复制代码块」子列表选择
    val codeBlocks = rememberCodeBlocks(message.content)

    ModalBottomSheet(onDismissRequest = onDismiss, sheetState = sheetState) {
        Column(Modifier.padding(bottom = 16.dp)) {
            // 顶部摘要（前 50 字）
            Text(
                text = message.content.take(50),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 2,
                modifier = Modifier.padding(horizontal = 20.dp, vertical = 8.dp),
            )
            androidx.compose.material3.HorizontalDivider()

            // AI 消息操作
            ActionRow(Icons.Rounded.ContentCopy, stringResource(R.string.chat_action_copy_all), onDismiss, onCopyText)
            if (!isUser && codeBlocks.isNotEmpty()) {
                codeBlocks.forEachIndexed { idx, code ->
                    ActionRow(
                        icon = Icons.Rounded.ContentCopy,
                        label = stringResource(R.string.chat_action_copy_code_item, idx + 1),
                        onClick = { onDismiss(); onCopyCodeBlock(code) },
                    )
                }
            }
            if (!isUser) {
                ActionRow(Icons.Rounded.Refresh, stringResource(R.string.chat_action_regenerate), onDismiss, onRegenerate)
            }
            ActionRow(Icons.Rounded.Edit, stringResource(R.string.chat_action_edit_resend), onDismiss, onEditResend)
            if (!isUser) {
                ActionRow(Icons.Rounded.Bookmark, stringResource(R.string.chat_action_bookmark), onDismiss, onBookmark)
                ActionRow(Icons.Rounded.Share, stringResource(R.string.chat_action_share), onDismiss, onShare)
                ActionRow(Icons.Rounded.VolumeUp, stringResource(R.string.chat_action_read_aloud), onDismiss, onReadAloud)
            }
            // 用户消息删除（危险操作，error 色）
            if (isUser) {
                ActionRow(
                    icon = Icons.Rounded.Delete,
                    label = stringResource(R.string.chat_action_delete),
                    onClick = { onDismiss(); onDelete() },
                    danger = true,
                )
            }
        }
    }
}

@Composable
private fun ActionRow(
    icon: ImageVector,
    label: String,
    dismiss: () -> Unit = {},
    onClick: () -> Unit,
    danger: Boolean = false,
) {
    val tint = if (danger) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.onSurface
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .height(48.dp)
            .clickable { onClick() }
            .padding(horizontal = 20.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Icon(icon, contentDescription = label, tint = tint, modifier = Modifier.size(24.dp))
        Spacer(Modifier.width(20.dp))
        Text(label, style = MaterialTheme.typography.bodyLarge, color = tint)
    }
}

/** 从 Markdown 文本提取所有围栏代码块内容。 */
private fun rememberCodeBlocks(content: String): List<String> {
    val regex = Regex("```[\\w]*\\n([\\s\\S]*?)```")
    return regex.findAll(content).map { it.groupValues[1].trimEnd() }.toList()
}
