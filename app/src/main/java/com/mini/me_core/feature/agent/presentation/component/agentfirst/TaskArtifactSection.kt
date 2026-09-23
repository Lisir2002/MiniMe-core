package com.mini.me_core.feature.agent.presentation.component.agentfirst
import com.mini.me_core.core.theme.tokens.LocalCornerRadius

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.mini.me_core.core.theme.Radius
import com.mini.me_core.core.theme.Spacing
import com.mini.me_core.feature.agent.presentation.component.EditDiff
import com.mini.me_core.feature.agent.presentation.component.FileChangeType

/**
 * TaskCard Artifact 产物区域（Stage 2 - S2-4）。
 *
 * 展示任务执行过程中产生的文件变更（新增 / 修改 / 删除）列表。
 * 每个文件一行：文件名 + 变更类型标记 + [diff] 按钮（点击查看完整 diff）。
 *
 * 当 [fileDiffs] 为空时不渲染（返回空），不占用布局空间。
 *
 * @param fileDiffs 文件变更列表（从 TOOL 消息解析得到）
 * @param onViewDiff 点击某文件的 [diff] 按钮回调
 * @param modifier 外部修饰符
 */
@Composable
internal fun TaskArtifactSection(
    fileDiffs: List<EditDiff>,
    onViewDiff: (EditDiff) -> Unit,
    modifier: Modifier = Modifier
) {
    if (fileDiffs.isEmpty()) return

    Column(
        modifier = modifier
            .fillMaxWidth()
            .padding(top = Spacing.sm)
    ) {
        // 标题行
        Row(
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier.padding(bottom = Spacing.xs)
        ) {
            Text(
                text = "📝",
                style = MaterialTheme.typography.labelLarge
            )
            Spacer(Modifier.width(Spacing.xs))
            Text(
                text = "产物 (${fileDiffs.size})",
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                style = MaterialTheme.typography.labelMedium.copy(fontWeight = FontWeight.SemiBold)
            )
        }

        // 文件列表
        fileDiffs.forEach { diff ->
            FileDiffRow(
                diff = diff,
                onViewDiff = { onViewDiff(diff) }
            )
        }
    }
}

/**
 * 单个文件变更行：文件名 + 类型标记 + [diff] 按钮。
 */
@Composable
private fun FileDiffRow(
    diff: EditDiff,
    onViewDiff: () -> Unit
) {
    val (typeLabel, typeColor) = when (diff.type) {
        FileChangeType.CREATE -> "新增" to Color(0xFF16A34A)
        FileChangeType.MODIFY -> "修改" to Color(0xFF2563EB)
        FileChangeType.DELETE -> "删除" to Color(0xFFDC2626)
    }

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = Spacing.xs),
        verticalAlignment = Alignment.CenterVertically
    ) {
        // 文件名
        Text(
            text = diff.path,
            color = MaterialTheme.colorScheme.onSurface,
            style = MaterialTheme.typography.bodySmall.copy(fontFamily = FontFamily.Monospace),
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.weight(1f)
        )
        Spacer(Modifier.width(Spacing.sm))
        // 变更类型标记
        Text(
            text = typeLabel,
            color = typeColor,
            style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.Medium)
        )
        Spacer(Modifier.width(Spacing.sm))
        // [diff] 按钮
        Row(
            modifier = Modifier
                .clip(RoundedCornerShape(LocalCornerRadius.current.md))
                .background(MaterialTheme.colorScheme.surfaceVariant)
                .clickable { onViewDiff() }
                .padding(horizontal = Spacing.sm, vertical = Spacing.xs)
        ) {
            Text(
                text = "diff",
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                style = MaterialTheme.typography.labelSmall.copy(
                    fontFamily = FontFamily.Monospace,
                    fontWeight = FontWeight.Medium
                )
            )
        }
    }
}
