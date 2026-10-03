package com.mini.me_core.feature.git.presentation.component

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.DeleteForever
import androidx.compose.material.icons.rounded.ExpandMore
import androidx.compose.material.icons.rounded.Sell
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.mini.me_core.core.theme.Spacing
import com.mini.me_core.core.theme.tokens.LocalCornerRadius

/**
 * 分支切换三态确认对话框。
 *
 * 当工作区存在未提交改动时，切换分支前必须让用户选择如何处理这些改动：
 *  - [onStashAndSwitch]：先 `git stash` 暂存改动再切换（推荐，可恢复）。
 *  - [onDiscardAndSwitch]：丢弃所有改动后切换（不可逆，需二次确认）。
 *  - [onCancel]：取消切换。
 *
 * 对话框顶部汇总当前工作区的暂存 / 未暂存 / 未跟踪文件数量。
 * 「放弃改动并切换」点击后会再弹出一次 [DangerousActionDialog] 二次确认。
 *
 * @param targetBranch 目标分支名
 * @param hasStagedChanges 是否存在已暂存改动
 * @param hasUnstagedChanges 是否存在未暂存改动
 * @param hasUntrackedFiles 是否存在未跟踪文件
 * @param onStashAndSwitch 暂存并切换回调
 * @param onDiscardAndSwitch 放弃改动并切换回调（已通过二次确认）
 * @param onCancel 取消回调
 */
@Composable
internal fun CheckoutConfirmDialog(
    targetBranch: String,
    hasStagedChanges: Boolean,
    hasUnstagedChanges: Boolean,
    hasUntrackedFiles: Boolean,
    onStashAndSwitch: () -> Unit,
    onDiscardAndSwitch: () -> Unit,
    onCancel: () -> Unit,
) {
    var showDiscardConfirm by remember { mutableStateOf(false) }

    if (showDiscardConfirm) {
        DangerousActionDialog(
            title = "放弃所有改动？",
            message = "工作区的暂存改动、未暂存改动以及未跟踪文件将被永久丢弃，且无法恢复。确定要切换到 $targetBranch 吗？",
            confirmText = "放弃并切换",
            dangerLevel = DangerLevel.EXTREME,
            requireInput = targetBranch,
            inputHint = "输入目标分支名以确认",
            onConfirm = {
                showDiscardConfirm = false
                onDiscardAndSwitch()
            },
            onDismiss = { showDiscardConfirm = false },
        )
        return
    }

    val hasAnyChanges = hasStagedChanges || hasUnstagedChanges || hasUntrackedFiles

    AlertDialog(
        onDismissRequest = onCancel,
        icon = {
            Icon(
                imageVector = Icons.Rounded.ExpandMore,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.primary,
                modifier = Modifier.size(40.dp),
            )
        },
        title = {
            Text(
                text = "切换到 $targetBranch",
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.SemiBold,
                color = MaterialTheme.colorScheme.onSurface,
            )
        },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(Spacing.sm)) {
                if (hasAnyChanges) {
                    Text(
                        text = "切换分支前需要处理当前工作区的改动：",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                    Spacer(Modifier.height(Spacing.xs))
                    ChangeSummaryRow("已暂存", hasStagedChanges)
                    ChangeSummaryRow("未暂存", hasUnstagedChanges)
                    ChangeSummaryRow("未跟踪", hasUntrackedFiles)
                    Spacer(Modifier.height(Spacing.xs))
                    Text(
                        text = "推荐先暂存改动，切换后可通过 stash 恢复。",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                } else {
                    Text(
                        text = "工作区干净，确定切换到 $targetBranch？",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
        },
        confirmButton = {
            Column {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.End,
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    TextButton(onClick = onCancel) {
                        Text(
                            text = "取消",
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                    Spacer(Modifier.width(Spacing.sm))
                    Button(
                        onClick = onStashAndSwitch,
                        shape = RoundedCornerShape(LocalCornerRadius.current.md),
                        colors = ButtonDefaults.buttonColors(
                            containerColor = MaterialTheme.colorScheme.primary,
                            contentColor = MaterialTheme.colorScheme.onPrimary,
                        ),
                    ) {
                        Icon(Icons.Rounded.Sell, contentDescription = null, modifier = Modifier.size(16.dp))
                        Spacer(Modifier.width(Spacing.xs))
                        Text("暂存并切换")
                    }
                }

                if (hasAnyChanges) {
                    HorizontalDivider(
                        modifier = Modifier.padding(vertical = Spacing.sm),
                        color = MaterialTheme.colorScheme.outlineVariant,
                    )
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.End,
                    ) {
                        TextButton(onClick = { showDiscardConfirm = true }) {
                            Icon(
                                Icons.Rounded.DeleteForever,
                                contentDescription = null,
                                tint = MaterialTheme.colorScheme.error,
                                modifier = Modifier.size(18.dp),
                            )
                            Spacer(Modifier.width(Spacing.xs))
                            Text(
                                text = "放弃改动并切换",
                                color = MaterialTheme.colorScheme.error,
                                fontWeight = FontWeight.SemiBold,
                            )
                        }
                    }
                }
            }
        },
        shape = RoundedCornerShape(LocalCornerRadius.current.xl),
        containerColor = MaterialTheme.colorScheme.surface,
    )
}

/**
 * 单行改动摘要：标签 + 状态点。
 */
@Composable
private fun ChangeSummaryRow(label: String, present: Boolean) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        val dotColor = if (present) MaterialTheme.colorScheme.error
            else MaterialTheme.colorScheme.outlineVariant
        Surface(
            color = dotColor,
            shape = RoundedCornerShape(50),
            modifier = Modifier.size(8.dp),
        ) {}
        Spacer(Modifier.width(Spacing.sm))
        Text(
            text = label,
            style = MaterialTheme.typography.bodyMedium,
            color = if (present) MaterialTheme.colorScheme.onSurface
            else MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Spacer(Modifier.width(Spacing.sm))
        Text(
            text = if (present) "有待处理改动" else "无",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}
