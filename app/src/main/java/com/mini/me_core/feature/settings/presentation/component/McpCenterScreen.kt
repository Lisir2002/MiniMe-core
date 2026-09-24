package com.mini.me_core.feature.settings.presentation.component

import com.mini.me_core.core.theme.tokens.LocalCornerRadius
import com.mini.me_core.core.theme.tokens.PrimitiveSpacing

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.CheckCircle
import androidx.compose.material.icons.rounded.RadioButtonUnchecked
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.mini.me_core.R
import com.mini.me_core.core.theme.Spacing
import com.mini.me_core.feature.agent.domain.mcp.McpServerConfig
import com.mini.me_core.feature.agent.domain.mcp.McpServerStatus

/**
 * MCP 中心页面：顶部 Segmented Control 切换「外部工具」与「开放服务」，
 * Tab 下方固定显示跨 Tab 全局状态卡。
 */
@Composable
internal fun McpCenterScreen(
    // 外部工具（客户端）参数
    servers: List<McpServerConfig>,
    statuses: List<McpServerStatus>,
    reloading: Boolean,
    onReload: () -> Unit,
    onToggleServerEnabled: (String, Boolean) -> Unit,
    onEditServer: (McpServerConfig) -> Unit,
    onDeleteServer: (String) -> Unit,
    onAddServer: () -> Unit,
    // 开放服务（服务端）参数
    isRunning: Boolean,
    port: Int,
    token: String,
    requireApproval: Boolean,
    autoStart: Boolean,
    serverUrl: String,
    errorMessage: String?,
    onToggleHostServer: () -> Unit,
    onSaveHostConfig: (port: Int, requireApproval: Boolean, autoStart: Boolean) -> Unit,
    onRegenerateToken: () -> Unit
) {
    var selectedTab by remember { mutableIntStateOf(0) }

    Column(
        modifier = Modifier.fillMaxSize()
    ) {
        // ── Segmented Control ──
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = Spacing.lg, vertical = Spacing.sm)
                .background(
                    MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.6f),
                    RoundedCornerShape(LocalCornerRadius.current.xl)
                )
                .padding(PrimitiveSpacing.Xs),
            horizontalArrangement = Arrangement.spacedBy(4.dp)
        ) {
            val tabs = listOf(
                stringResource(R.string.mcp_tab_external_tools),
                stringResource(R.string.mcp_tab_open_services)
            )
            tabs.forEachIndexed { index, title ->
                val isSelected = selectedTab == index
                Box(
                    modifier = Modifier
                        .weight(1f)
                        .clip(RoundedCornerShape(LocalCornerRadius.current.lg))
                        .background(if (isSelected) MaterialTheme.colorScheme.surface else Color.Transparent)
                        .clickable { selectedTab = index }
                        .padding(vertical = 10.dp),
                    contentAlignment = Alignment.Center
                ) {
                    Text(
                        text = title,
                        style = MaterialTheme.typography.bodyMedium.copy(
                            fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Normal
                        ),
                        color = if (isSelected) MaterialTheme.colorScheme.primary
                        else MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
        }

        // ── 总览状态卡（跨 Tab 固定显示） ──
        McpOverviewCard(
            externalTotal = servers.size,
            externalConnected = statuses.count { it.state == McpServerStatus.State.CONNECTED },
            hostRunning = isRunning
        )

        // ── Tab 内容 ──
        Box(modifier = Modifier.weight(1f)) {
            when (selectedTab) {
                0 -> McpSection(
                    servers = servers,
                    statuses = statuses,
                    reloading = reloading,
                    onReload = onReload,
                    onToggle = onToggleServerEnabled,
                    onEdit = onEditServer,
                    onDelete = onDeleteServer,
                    onAddServer = onAddServer
                )
                else -> McpServerSection(
                    isRunning = isRunning,
                    port = port,
                    token = token,
                    requireApproval = requireApproval,
                    autoStart = autoStart,
                    serverUrl = serverUrl,
                    errorMessage = errorMessage,
                    onToggleServer = onToggleHostServer,
                    onSaveConfig = onSaveHostConfig,
                    onRegenerateToken = onRegenerateToken
                )
            }
        }
    }
}

/** 跨 Tab 全局状态卡：外部已连接 N/M + 开放服务运行/停止。 */
@Composable
private fun McpOverviewCard(
    externalTotal: Int,
    externalConnected: Int,
    hostRunning: Boolean
) {
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = Spacing.lg, vertical = Spacing.sm),
        shape = RoundedCornerShape(LocalCornerRadius.current.xl),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        border = androidx.compose.foundation.BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant)
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = Spacing.lg, vertical = Spacing.md),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(Spacing.lg)
        ) {
            // 外部工具状态
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(
                    imageVector = if (externalConnected > 0)
                        Icons.Rounded.CheckCircle
                    else
                        Icons.Rounded.RadioButtonUnchecked,
                    contentDescription = null,
                    modifier = Modifier.width(16.dp).height(16.dp),
                    tint = if (externalConnected > 0) MaterialTheme.colorScheme.tertiary
                    else MaterialTheme.colorScheme.onSurfaceVariant
                )
                Spacer(Modifier.width(6.dp))
                Text(
                    text = stringResource(R.string.mcp_overview_external_connected, externalConnected, externalTotal),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurface
                )
            }

            // 分隔点
            Box(
                modifier = Modifier
                    .width(4.dp)
                    .height(4.dp)
                    .clip(RoundedCornerShape(50))
                    .background(MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.3f))
            )

            // 开放服务状态
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(
                    imageVector = if (hostRunning)
                        Icons.Rounded.CheckCircle
                    else
                        Icons.Rounded.RadioButtonUnchecked,
                    contentDescription = null,
                    modifier = Modifier.width(16.dp).height(16.dp),
                    tint = if (hostRunning) MaterialTheme.colorScheme.tertiary
                    else MaterialTheme.colorScheme.onSurfaceVariant
                )
                Spacer(Modifier.width(6.dp))
                Text(
                    text = stringResource(
                        if (hostRunning) R.string.mcp_overview_open_running
                        else R.string.mcp_overview_open_stopped
                    ),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurface
                )
            }
        }
    }
}
