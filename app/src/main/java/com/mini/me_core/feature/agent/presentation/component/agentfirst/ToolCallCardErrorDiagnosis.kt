package com.mini.me_core.feature.agent.presentation.component.agentfirst

import android.content.ClipData
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
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
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.ClipEntry
import androidx.compose.ui.platform.LocalClipboard
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.ContentCopy
import androidx.compose.material.icons.rounded.Warning
import com.mini.me_core.core.theme.LocalAppDarkMode
import com.mini.me_core.core.theme.Radius
import com.mini.me_core.core.theme.Spacing
import com.mini.me_core.feature.agent.presentation.component.agentfirst.derivers.ErrorDiagnosis
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

/**
 * 错误诊断提示条（Agent-First ToolCallCard 的子组件）。
 *
 * 当工具执行失败/超时时，由 [ErrorDiagnoser] 识别出常见错误模式，把诊断结论以淡色背景提示条呈现：
 * ⚠ 图标 + 诊断标题 + 修复建议；若 [ErrorDiagnosis.fixCommand] 非空，再附「复制修复命令」按钮，
 * 用户复制后可手动执行（不自动执行）。
 *
 * 诊断是建议性的，不替代真正的错误恢复；复制命令仅写入系统剪贴板并短暂提示「已复制」。
 *
 * @param diagnosis 诊断结果；为 null 时不渲染任何内容
 * @param modifier 外部修饰符
 */
@Composable
fun ToolCallCardErrorDiagnosis(
    diagnosis: ErrorDiagnosis?,
    modifier: Modifier = Modifier
) {
    if (diagnosis == null) return
    val isDark = LocalAppDarkMode.current

    val barBg = if (isDark) Color(0xFF422006).copy(alpha = 0.55f) else Color(0xFFFEF3C7)
    val barBorder = Color(0xFFF59E0B).copy(alpha = 0.45f)
    val iconTint = Color(0xFFF59E0B)

    Surface(
        shape = RoundedCornerShape(Radius.md),
        color = barBg,
        border = BorderStroke(1.dp, barBorder),
        modifier = modifier.fillMaxWidth()
    ) {
        Column(
            modifier = Modifier.padding(Spacing.sm),
            verticalArrangement = Arrangement.spacedBy(Spacing.xs)
        ) {
            // 标题行：⚠ + 标题
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(Spacing.sm)
            ) {
                Icon(
                    imageVector = Icons.Rounded.Warning,
                    contentDescription = null,
                    tint = iconTint,
                    modifier = Modifier.size(16.dp)
                )
                Text(
                    text = diagnosis.title,
                    color = MaterialTheme.colorScheme.onSurface,
                    style = MaterialTheme.typography.labelLarge.copy(fontWeight = FontWeight.SemiBold),
                    modifier = Modifier.weight(1f)
                )
            }
            // 修复建议
            Text(
                text = diagnosis.suggestion,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                style = MaterialTheme.typography.bodySmall
            )
            // 修复命令（可选）：命令文本 + 复制按钮
            diagnosis.fixCommand?.let { cmd ->
                FixCommandRow(fixCommand = cmd)
            }
        }
    }
}

/**
 * 修复命令行：等宽展示可执行命令 + 「复制修复命令」按钮。
 * 复制后按钮短暂显示「已复制」2 秒后恢复。
 */
@Composable
private fun FixCommandRow(fixCommand: String) {
    val clipboard = LocalClipboard.current
    val scope = rememberCoroutineScope()
    var copied by remember { mutableStateOf(false) }

    Row(
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(Spacing.sm),
        modifier = Modifier.fillMaxWidth()
    ) {
        // 命令文本（等宽，截断在一行内）
        Text(
            text = fixCommand,
            color = MaterialTheme.colorScheme.onSurface,
            style = MaterialTheme.typography.bodySmall.copy(fontFamily = FontFamily.Monospace),
            maxLines = 1,
            modifier = Modifier.weight(1f)
        )
        // 复制按钮
        Surface(
            shape = RoundedCornerShape(Radius.sm),
            color = MaterialTheme.colorScheme.primaryContainer,
            modifier = Modifier.clickable {
                scope.launch {
                    clipboard.setClipEntry(ClipEntry(ClipData.newPlainText("fix_command", fixCommand)))
                    copied = true
                }
            }
        ) {
            Row(
                modifier = Modifier
                    .padding(horizontal = Spacing.sm, vertical = Spacing.xs),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(Spacing.xs)
            ) {
                Icon(
                    imageVector = Icons.Rounded.ContentCopy,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.onPrimaryContainer,
                    modifier = Modifier.size(12.dp)
                )
                Text(
                    text = if (copied) "已复制" else "复制修复命令",
                    color = MaterialTheme.colorScheme.onPrimaryContainer,
                    style = MaterialTheme.typography.labelSmall
                )
            }
        }
    }

    // 2 秒后恢复按钮文案
    LaunchedEffect(copied) {
        if (copied) {
            delay(2000)
            copied = false
        }
    }
}
