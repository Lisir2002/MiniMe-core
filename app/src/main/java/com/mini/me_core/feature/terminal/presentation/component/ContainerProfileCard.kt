package com.mini.me_core.feature.terminal.presentation.component

import androidx.compose.animation.animateColorAsState
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
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
import androidx.compose.material.icons.rounded.Delete
import androidx.compose.material.icons.rounded.Edit
import androidx.compose.material.icons.rounded.Refresh
import androidx.compose.material3.AssistChip
import androidx.compose.material3.AssistChipDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.mini.me_core.R
import com.mini.me_core.core.theme.Spacing
import com.mini.me_core.core.theme.tokens.LocalCornerRadius
import com.mini.me_core.feature.agent.domain.container.ContainerArch
import com.mini.me_core.feature.agent.domain.container.ContainerProfile
import com.mini.me_core.feature.agent.domain.container.RootfsSource
import com.mini.me_core.feature.settings.data.repository.ExecutionMode
import com.mini.me_core.feature.workspace.domain.model.RemoteConnection

/**
 * 单个 Profile 卡片组件。
 *
 * - 激活 Profile：左侧 3dp 主色色条 + 浅色背景高亮
 * - 名称（内置 Alpine 前加 ★ 标记）
 * - 模式标签 pill：本地（蓝色）/ SSH（绿色）
 * - 架构 + Shell 路径
 * - 额外绑定目录（AssistChip 横向排列）
 * - 内置 Profile 右侧 [重置]，自定义 Profile 右侧 [编辑] [删除]
 */
@Composable
fun ContainerProfileCard(
    profile: ContainerProfile,
    isActive: Boolean,
    remoteConnections: List<RemoteConnection>,
    highlight: Boolean = false,
    onSelect: () -> Unit,
    onEdit: () -> Unit,
    onDelete: () -> Unit,
    onReset: () -> Unit,
    modifier: Modifier = Modifier
) {
    // 高亮闪烁动画
    val animatedBorderColor by animateColorAsState(
        targetValue = when {
            highlight -> MaterialTheme.colorScheme.primary
            isActive -> MaterialTheme.colorScheme.primary
            else -> MaterialTheme.colorScheme.outlineVariant
        },
        label = "profileBorder"
    )

    val animatedBgColor by animateColorAsState(
        targetValue = when {
            highlight -> MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.5f)
            isActive -> MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.3f)
            else -> MaterialTheme.colorScheme.surface
        },
        label = "profileBg"
    )

    Card(
        modifier = modifier
            .fillMaxWidth()
            .border(1.dp, animatedBorderColor, RoundedCornerShape(LocalCornerRadius.current.lg))
            .clickable(enabled = !isActive) { onSelect() },
        colors = CardDefaults.cardColors(containerColor = animatedBgColor),
        elevation = CardDefaults.cardElevation(defaultElevation = 0.dp),
        shape = RoundedCornerShape(LocalCornerRadius.current.lg)
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.Top
        ) {
            // 左侧 3dp 主色色条（仅激活时显示）
            Box(
                modifier = Modifier
                    .width(3.dp)
                    .height(if (isActive) 80.dp else 0.dp)
                    .background(
                        if (isActive) MaterialTheme.colorScheme.primary else Color.Transparent
                    )
            )

            Column(
                modifier = Modifier
                    .weight(1f)
                    .padding(horizontal = Spacing.md, vertical = Spacing.sm)
            ) {
                // 第一行：名称 + 模式标签
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        text = buildString {
                            if (profile.isBuiltin) append("★ ")
                            append(profile.name)
                        },
                        style = MaterialTheme.typography.titleMedium,
                        color = MaterialTheme.colorScheme.onSurface,
                        modifier = Modifier.weight(1f)
                    )
                    // 模式标签 pill
                    AssistChip(
                        onClick = {},
                        enabled = false,
                        label = {
                            Text(
                                if (profile.mode == ExecutionMode.REMOTE_SSH)
                                    stringResource(R.string.tc_profile_ssh)
                                else
                                    stringResource(R.string.tc_profile_local),
                                fontSize = 11.sp
                            )
                        },
                        colors = AssistChipDefaults.assistChipColors(
                            disabledLabelColor = if (profile.mode == ExecutionMode.REMOTE_SSH)
                                Color(0xFF2E7D32)
                            else
                                MaterialTheme.colorScheme.primary,
                            disabledContainerColor = if (profile.mode == ExecutionMode.REMOTE_SSH)
                                Color(0xFFE8F5E9)
                            else
                                MaterialTheme.colorScheme.primaryContainer
                        )
                    )
                }

                Spacer(modifier = Modifier.height(4.dp))

                // 第二行：架构 + Shell 路径
                val archLabel = when (profile.arch) {
                    ContainerArch.ARM64 -> "aarch64"
                    ContainerArch.X86_64 -> "x86_64"
                }
                val shellLabel = profile.shellPath ?: "/bin/sh"
                Text(
                    text = "$archLabel · $shellLabel",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )

                // 远程模式下显示连接名
                if (profile.mode == ExecutionMode.REMOTE_SSH) {
                    val ssh = profile.rootfsSource as? RootfsSource.RemoteSsh
                    val connName = ssh?.connectionId?.let { cid ->
                        remoteConnections.firstOrNull { it.id == cid }?.name
                    }
                    if (!connName.isNullOrBlank()) {
                        Spacer(modifier = Modifier.height(2.dp))
                        Text(
                            text = "→ $connName",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }

                // 额外绑定目录
                if (profile.extraBindings.isNotEmpty()) {
                    Spacer(modifier = Modifier.height(6.dp))
                    Row(
                        horizontalArrangement = Arrangement.spacedBy(4.dp)
                    ) {
                        profile.extraBindings.take(3).forEach { binding ->
                            AssistChip(
                                onClick = {},
                                enabled = false,
                                label = { Text(binding, fontSize = 10.sp) }
                            )
                        }
                        if (profile.extraBindings.size > 3) {
                            AssistChip(
                                onClick = {},
                                enabled = false,
                                label = { Text("+${profile.extraBindings.size - 3}", fontSize = 10.sp) }
                            )
                        }
                    }
                }
            }

            // 右侧操作按钮
            Row {
                if (profile.isBuiltin) {
                    IconButton(onClick = onReset) {
                        Icon(
                            Icons.Rounded.Refresh,
                            contentDescription = stringResource(R.string.container_reset),
                            tint = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.size(20.dp)
                        )
                    }
                } else {
                    IconButton(onClick = onEdit) {
                        Icon(
                            Icons.Rounded.Edit,
                            contentDescription = stringResource(R.string.common_edit),
                            tint = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.size(20.dp)
                        )
                    }
                    IconButton(onClick = onDelete) {
                        Icon(
                            Icons.Rounded.Delete,
                            contentDescription = stringResource(R.string.common_delete),
                            tint = MaterialTheme.colorScheme.error,
                            modifier = Modifier.size(20.dp)
                        )
                    }
                }
            }
        }
    }
}
