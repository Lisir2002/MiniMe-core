package com.mini.me_core.feature.terminal.presentation.component

import androidx.compose.foundation.background
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
import androidx.compose.material.icons.rounded.Dns
import androidx.compose.material.icons.rounded.Public
import androidx.compose.material.icons.rounded.Refresh
import androidx.compose.material.icons.rounded.RestartAlt
import androidx.compose.material.icons.rounded.Storage
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Button
import androidx.compose.material3.Icon
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.mini.me_core.R
import com.mini.me_core.core.theme.Spacing
import com.mini.me_core.core.theme.tokens.LocalCornerRadius
import com.mini.me_core.feature.agent.domain.container.ContainerArch
import com.mini.me_core.feature.agent.domain.container.ContainerInitState
import com.mini.me_core.feature.agent.domain.container.ContainerProfile
import com.mini.me_core.feature.agent.domain.container.RootfsSource
import com.mini.me_core.feature.settings.data.repository.ExecutionMode
import com.mini.me_core.feature.workspace.domain.model.RemoteConnection

/** 估算的容器总存储容量（MB）。 */
private const val ESTIMATED_TOTAL_MB = 4096f

/**
 * 增强版容器状态总览卡片。
 *
 * 替代 SharedContainerEnvCard，提供更丰富的状态展示：
 * - 状态徽章（圆点+文字）
 * - 当前 Profile 名 + 架构
 * - 存储用量进度条
 * - 2x2 操作按钮网格
 */
@Composable
fun ContainerStatusCard(
    containerInstalled: Boolean,
    initProgress: ContainerInitState,
    storageUsedMb: Long,
    activeProfile: ContainerProfile?,
    remoteConnections: List<RemoteConnection>,
    onInit: () -> Unit,
    onRestart: () -> Unit,
    onReset: () -> Unit,
    onSwitchImage: () -> Unit,
    onPickMirror: () -> Unit,
    modifier: Modifier = Modifier
) {
    // 状态徽章
    val (statusText, statusColor) = when (initProgress) {
        is ContainerInitState.Failed -> stringResource(R.string.tc_status_error) to MaterialTheme.colorScheme.error
        is ContainerInitState.ExtractingRootfs,
        ContainerInitState.DeployingProot -> stringResource(R.string.tc_status_initializing) to MaterialTheme.colorScheme.secondary
        is ContainerInitState.Ready,
        is ContainerInitState.BundleInstalling,
        is ContainerInitState.BundleUninstalling -> stringResource(R.string.tc_status_running) to SemanticColors.Success
        ContainerInitState.Idle -> stringResource(R.string.tc_status_not_initialized) to MaterialTheme.colorScheme.onSurfaceVariant
    }

    val isRemoteMode = activeProfile?.mode == ExecutionMode.REMOTE_SSH
    val profileName = activeProfile?.name ?: stringResource(R.string.tc_status_not_initialized)
    val archLabel = when (activeProfile?.arch) {
        ContainerArch.X86_64 -> "x86_64"
        ContainerArch.ARM64 -> "aarch64"
        null -> ""
    }

    // 远程模式下显示连接名
    val remoteConnName = if (isRemoteMode) {
        val ssh = activeProfile?.rootfsSource as? RootfsSource.RemoteSsh
        ssh?.connectionId?.let { cid ->
            remoteConnections.firstOrNull { it.id == cid }?.name
        }
    } else null

    Card(
        modifier = modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surface
        ),
        elevation = CardDefaults.cardElevation(defaultElevation = 1.dp),
        shape = RoundedCornerShape(LocalCornerRadius.current.lg)
    ) {
        Column(modifier = Modifier.padding(Spacing.md)) {
            // 第一行：图标 + 标题 + 状态徽章
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(
                    Icons.Rounded.Storage,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.size(22.dp)
                )
                Spacer(modifier = Modifier.width(Spacing.sm))
                Text(
                    text = profileName,
                    style = MaterialTheme.typography.titleMedium,
                    modifier = Modifier.weight(1f)
                )
                // 状态圆点 + 文字
                Box(
                    modifier = Modifier
                        .size(10.dp)
                        .background(statusColor, shape = RoundedCornerShape(50))
                )
                Spacer(modifier = Modifier.width(6.dp))
                Text(
                    text = statusText,
                    style = MaterialTheme.typography.bodySmall,
                    color = statusColor
                )
            }

            Spacer(modifier = Modifier.height(Spacing.xs))

            // 第二行：架构 / 远程模式
            Text(
                text = when {
                    isRemoteMode -> buildString {
                        append(stringResource(R.string.tc_container_remote_mode))
                        if (!remoteConnName.isNullOrBlank()) append(" · $remoteConnName")
                    }
                    else -> "$archLabel · PRoot"
                },
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )

            Spacer(modifier = Modifier.height(Spacing.sm))

            // 存储用量进度条（远程模式不显示）
            if (!isRemoteMode) {
                Text(
                    text = stringResource(R.string.tc_storage_usage) + ": ${storageUsedMb} MB",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                Spacer(modifier = Modifier.height(4.dp))
                val progress = (storageUsedMb / ESTIMATED_TOTAL_MB).coerceIn(0f, 1f)
                LinearProgressIndicator(
                    progress = { progress },
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(6.dp)
                        .clip(RoundedCornerShape(3.dp)),
                    color = MaterialTheme.colorScheme.primary,
                    trackColor = MaterialTheme.colorScheme.surfaceVariant
                )
                Spacer(modifier = Modifier.height(Spacing.sm))
            }

            // 操作按钮区域
            if (!containerInstalled && !isRemoteMode) {
                // 未初始化：主按钮
                Button(
                    onClick = onInit,
                    modifier = Modifier.fillMaxWidth().height(ButtonSpec.Height)
                ) {
                    Icon(Icons.Rounded.Refresh, contentDescription = null, modifier = Modifier.size(ButtonSpec.IconSize))
                    Spacer(modifier = Modifier.width(ButtonSpec.IconTextSpacer))
                    Text(stringResource(R.string.tc_init_container), fontSize = ButtonSpec.TextFontSize)
                }
            } else {
                // 2x2 按钮网格
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(Spacing.sm)
                ) {
                    GridActionButton(
                        icon = Icons.Rounded.RestartAlt,
                        label = stringResource(R.string.tc_restart_container),
                        onClick = onRestart,
                        enabled = containerInstalled,
                        modifier = Modifier.weight(1f)
                    )
                    GridActionButton(
                        icon = Icons.Rounded.Refresh,
                        label = stringResource(R.string.container_reset),
                        onClick = onReset,
                        enabled = containerInstalled,
                        modifier = Modifier.weight(1f)
                    )
                }
                Spacer(modifier = Modifier.height(Spacing.sm))
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(Spacing.sm)
                ) {
                    GridActionButton(
                        icon = Icons.Rounded.Dns,
                        label = stringResource(R.string.tc_switch_image),
                        onClick = onSwitchImage,
                        enabled = true,
                        modifier = Modifier.weight(1f)
                    )
                    GridActionButton(
                        icon = Icons.Rounded.Public,
                        label = stringResource(R.string.tc_mirror_source),
                        onClick = onPickMirror,
                        enabled = containerInstalled,
                        modifier = Modifier.weight(1f)
                    )
                }
            }
        }
    }
}

@Composable
private fun GridActionButton(
    icon: ImageVector,
    label: String,
    onClick: () -> Unit,
    enabled: Boolean,
    modifier: Modifier = Modifier
) {
    OutlinedButton(
        onClick = onClick,
        enabled = enabled,
        modifier = modifier.height(36.dp)
    ) {
        Icon(icon, contentDescription = null, modifier = Modifier.size(16.dp))
        Spacer(modifier = Modifier.width(4.dp))
        Text(label, fontSize = 12.sp)
    }
}
