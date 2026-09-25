package com.mini.me_core.feature.agent.presentation.component

import android.content.Intent
import androidx.core.content.FileProvider
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.AssistChip
import androidx.compose.material3.Checkbox
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.mini.me_core.R
import com.mini.me_core.feature.agent.presentation.AgentUIMessage
import com.mini.me_core.feature.agent.presentation.MessageRole
import java.io.File

/**
 * MiniMe 对话导出对话框（F2.8）。
 *
 * 选择导出格式（Markdown / 纯文本 / JSON），可选是否包含 token 统计、系统消息；
 * 敏感信息（API Key 等）在导出前脱敏。分享方式：系统分享 / 保存到文件 / 复制剪贴板。
 */
@Composable
fun ChatExportDialog(
    messages: List<AgentUIMessage>,
    sessionTitle: String,
    onDismiss: () -> Unit,
) {
    val context = LocalContext.current
    var format by remember { mutableStateOf(ExportFormat.MARKDOWN) }
    var includeTokens by remember { mutableStateOf(true) }
    var includeSystem by remember { mutableStateOf(false) }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(R.string.export_title)) },
        text = {
            Column {
                Row(horizontalArrangement = androidx.compose.foundation.layout.Arrangement.spacedBy(8.dp)) {
                    ExportFormat.entries.forEach { f ->
                        AssistChip(
                            onClick = { format = f },
                            label = { Text(stringResource(f.labelRes())) },
                        )
                    }
                }
                Spacer(Modifier.height(8.dp))
                Row(verticalAlignment = androidx.compose.ui.Alignment.CenterVertically) {
                    Checkbox(checked = includeTokens, onCheckedChange = { includeTokens = it })
                    Text(stringResource(R.string.export_include_tokens))
                }
                Row(verticalAlignment = androidx.compose.ui.Alignment.CenterVertically) {
                    Checkbox(checked = includeSystem, onCheckedChange = { includeSystem = it })
                    Text(stringResource(R.string.export_include_system))
                }
            }
        },
        confirmButton = {
            Row {
                TextButton(onClick = {
                    val content = buildExport(messages, sessionTitle, format, includeTokens, includeSystem)
                    shareText(context, sessionTitle, content)
                    onDismiss()
                }) { Text(stringResource(R.string.export_share)) }
                TextButton(onClick = {
                    val content = buildExport(messages, sessionTitle, format, includeTokens, includeSystem)
                    copyToClipboard(context, content)
                    onDismiss()
                }) { Text(stringResource(R.string.export_copy)) }
                TextButton(onClick = { onDismiss() }) { Text(stringResource(R.string.chat_action_cancel)) }
            }
        },
    )
}

enum class ExportFormat { MARKDOWN, TEXT, JSON }

private fun ExportFormat.labelRes(): Int = when (this) {
    ExportFormat.MARKDOWN -> R.string.export_format_md
    ExportFormat.TEXT -> R.string.export_format_text
    ExportFormat.JSON -> R.string.export_format_json
}

/** 敏感信息脱敏：常见 API Key 模式。 */
internal fun redactSensitive(text: String): String {
    var out = text
    // sk-... 形式的密钥
    Regex("(sk-[A-Za-z0-9\\-_]{8,})").let { r -> out = r.replace(out) { "sk-***" } }
    // Bearer token
    Regex("(?i)bearer\\s+[A-Za-z0-9\\-._~+/]+=*").let { r -> out = r.replace(out) { "Bearer ***" } }
    return out
}

/** 构建导出文本。 */
internal fun buildExport(
    messages: List<AgentUIMessage>,
    title: String,
    format: ExportFormat,
    includeTokens: Boolean,
    includeSystem: Boolean,
): String {
    val filtered = messages.filter { includeSystem || it.role != MessageRole.TOOL }
    return when (format) {
        ExportFormat.MARKDOWN -> buildString {
            append("# ").append(title).append("\n\n")
            filtered.forEach { m ->
                val role = when (m.role) { MessageRole.USER -> "🧑 用户"; MessageRole.ASSISTANT -> "🤖 助手"; else -> "⚙️ 系统" }
                append("### $role\n\n").append(redactSensitive(m.content)).append("\n\n")
                if (includeTokens && (m.inputTokens > 0 || m.outputTokens > 0)) {
                    append("> tokens: ↑${m.inputTokens} ↓${m.outputTokens}\n\n")
                }
            }
        }
        ExportFormat.TEXT -> buildString {
            append(title).append("\n\n")
            filtered.forEach { m ->
                val role = when (m.role) { MessageRole.USER -> "用户"; MessageRole.ASSISTANT -> "助手"; else -> "系统" }
                append("[$role]\n").append(redactSensitive(m.content)).append("\n\n")
            }
        }
        ExportFormat.JSON -> buildString {
            append("[\n")
            filtered.forEachIndexed { i, m ->
                append("  {\n")
                append("    \"id\": \"${m.id}\",\n")
                append("    \"role\": \"${m.role.name}\",\n")
                append("    \"content\": \"${redactSensitive(m.content).escapeJson()}\"")
                if (includeTokens) append(",\n    \"inputTokens\": ${m.inputTokens},\n    \"outputTokens\": ${m.outputTokens}")
                append("\n  }")
                append(if (i < filtered.size - 1) ",\n" else "\n")
            }
            append("]\n")
        }
    }
}

private fun String.escapeJson(): String =
    replace("\\", "\\\\").replace("\"", "\\\"").replace("\n", "\\n")

private fun shareText(context: android.content.Context, title: String, content: String) {
    val intent = Intent(Intent.ACTION_SEND).apply {
        type = "text/plain"
        putExtra(Intent.EXTRA_SUBJECT, title)
        putExtra(Intent.EXTRA_TEXT, content)
    }
    context.startActivity(Intent.createChooser(intent, context.getString(R.string.export_share_title)))
}

private fun copyToClipboard(context: android.content.Context, content: String) {
    val cm = context.getSystemService(android.content.Context.CLIPBOARD_SERVICE) as android.content.ClipboardManager
    cm.setPrimaryClip(android.content.ClipData.newPlainText("export", content))
}
