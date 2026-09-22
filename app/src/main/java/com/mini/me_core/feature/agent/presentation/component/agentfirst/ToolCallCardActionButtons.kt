package com.mini.me_core.feature.agent.presentation.component.agentfirst

import android.content.ClipData
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Check
import androidx.compose.material.icons.rounded.ContentCopy
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.ClipEntry
import androidx.compose.ui.platform.LocalClipboard
import androidx.compose.ui.unit.dp
import com.mini.me_core.core.theme.Radius
import com.mini.me_core.core.theme.Spacing
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

/**
 * 工具卡片操作按钮行（Agent-First ToolCallCard 的子组件）。
 *
 * 渲染一行小按钮：[复制命令] [复制输出]。点击后把对应文本写入系统剪贴板
 * （复用 [LocalClipboard]），按钮文案短暂变为「已复制」2 秒后恢复。
 *
 * - [command] 为空时不渲染「复制命令」按钮；
 * - [output] 为空时不渲染「复制输出」按钮；
 * - [onCopyCommand] / [onCopyOutput] 在复制完成后回调，供外部埋点/通知等附加副作用使用。
 *
 * @param command 待复制的命令文本（通常来自 toolArgs.command）
 * @param output 待复制的输出文本（清洗后的工具结果或实时输出）
 * @param onCopyCommand 复制命令完成后的回调
 * @param onCopyOutput 复制输出完成后的回调
 * @param modifier 外部修饰符
 */
@Composable
fun ToolCallCardActionButtons(
    command: String,
    output: String,
    onCopyCommand: () -> Unit,
    onCopyOutput: () -> Unit,
    modifier: Modifier = Modifier
) {
    Row(
        modifier = modifier,
        horizontalArrangement = Arrangement.spacedBy(Spacing.sm),
        verticalAlignment = Alignment.CenterVertically
    ) {
        if (command.isNotBlank()) {
            CopyChip(
                label = "复制命令",
                text = command,
                payload = "tool_command",
                onCopied = onCopyCommand
            )
        }
        if (output.isNotBlank()) {
            CopyChip(
                label = "复制输出",
                text = output,
                payload = "tool_output",
                onCopied = onCopyOutput
            )
        }
    }
}

/**
 * 单个复制小胶囊按钮：点击把 [text] 写入剪贴板，文案 2 秒内显示「已复制」。
 */
@Composable
private fun CopyChip(
    label: String,
    text: String,
    payload: String,
    onCopied: () -> Unit
) {
    val clipboard = LocalClipboard.current
    val scope = rememberCoroutineScope()
    var copied by remember { mutableStateOf(false) }

    Surface(
        shape = RoundedCornerShape(Radius.sm),
        color = MaterialTheme.colorScheme.surfaceVariant,
        onClick = {
            scope.launch {
                clipboard.setClipEntry(ClipEntry(ClipData.newPlainText(payload, text)))
                copied = true
                onCopied()
            }
        }
    ) {
        Row(
            modifier = Modifier.padding(horizontal = Spacing.sm, vertical = Spacing.xs),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(Spacing.xs)
        ) {
            Icon(
                imageVector = if (copied) Icons.Rounded.Check else Icons.Rounded.ContentCopy,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.size(12.dp)
            )
            Text(
                text = if (copied) "已复制" else label,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                style = MaterialTheme.typography.labelSmall
            )
        }
    }

    LaunchedEffect(copied) {
        if (copied) {
            delay(2000)
            copied = false
        }
    }
}
