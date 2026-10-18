// Compose 约定：@Composable 函数用大驼峰命名（与仓库既有页面一致，见 config/detekt/baseline.xml 中
// CapabilityCenterScreen / McpEditorDialog 等既有条目）。此处按文件级豁免 detekt 的
// FunctionNaming，而不是逐条塞进 baseline —— baseline 只会静默膨胀，且看不出豁免理由。
@file:Suppress("FunctionNaming")

package com.mini.me_core.feature.capability.presentation.component


import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListScope
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.mini.me_core.R
import com.mini.me_core.core.theme.Radius
import com.mini.me_core.core.theme.Spacing
import com.mini.me_core.core.theme.components.AppEmptyState
import com.mini.me_core.core.theme.components.AppSegmentedControl
import com.mini.me_core.core.theme.components.AppTopAppBar
import com.mini.me_core.core.ui.rememberPersistentLazyListState
import com.mini.me_core.feature.agent.domain.execution.mcp.McpServerStatus
import com.mini.me_core.feature.capability.domain.CapabilityGroupCard
import com.mini.me_core.feature.capability.presentation.UnifiedCapabilityViewModel

/**
 * F2 能力分组详情页：详情 / 工具 / 提示词 / 资源 四 Tab。
 *
 * 内置分组与远端 server 共用本页面——二者在协议层同构，详情页自然也同构。
 * 内置分组走详情 Tab 时展示的是分组元信息（server 名、对外暴露状态、工具总数），
 * 因为内置 server 不可编辑（配置在代码里，不落盘）；远端 server 则额外承载
 * 编辑入口（由调用方通过回调接入）。
 *
 * G4：后两个 Tab 是可选能力的浏览面。它们**恒定存在而不按 handshake 声明裁剪**——
 * `capabilities` 只是自述，据此隐藏 Tab 会让「没声明却能用」的 server 永久无从下手。
 * 取不到时由页面自己把原因说清楚。
 *
 * @param groupId 分组 id：内置为 `BuiltinGroup.serverName`（`MiniMe-Core` 等），远端为 server 名。
 *   该 id 同时也是 `McpManager` 里那份 client 的键，故可直接用作 prompts/resources 的寻址依据。
 */
@Composable
fun CapabilityGroupDetailScreen(
    groupId: String,
    onNavigateBack: () -> Unit,
    viewModel: UnifiedCapabilityViewModel = hiltViewModel()
) {
    val overview by viewModel.overview.collectAsStateWithLifecycle()
    val group = overview.groups.firstOrNull { it.id == groupId }
    var selectedTab by remember(groupId) { mutableIntStateOf(0) }

    Scaffold(
        containerColor = MaterialTheme.colorScheme.background,
        contentWindowInsets = WindowInsets(0, 0, 0, 0),
        topBar = {
            AppTopAppBar(
                title = group?.displayName ?: groupId,
                onNavigateBack = onNavigateBack,
                navigationIcon = Icons.AutoMirrored.Rounded.ArrowBack,
                navigationContentDescription = stringResource(R.string.common_back)
            )
        }
    ) { padding ->
        if (group == null) {
            // 分组在刷新后消失（远端 server 被删/ 被禁用）——不空屏，给出明确空态与返回。
            Column(modifier = Modifier.padding(padding)) {
                AppEmptyState(
                    icon = Icons.AutoMirrored.Rounded.ArrowBack,
                    title = stringResource(R.string.capability_detail_group_missing),
                    subtitle = groupId,
                    modifier = Modifier.fillMaxWidth()
                )
            }
            return@Scaffold
        }

        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
        ) {
            AppSegmentedControl(
                tabs = listOf(
                    stringResource(R.string.capability_detail_tab_info),
                    stringResource(R.string.capability_detail_tab_tools),
                    stringResource(R.string.capability_detail_tab_prompts),
                    stringResource(R.string.capability_detail_tab_resources)
                ),
                selectedIndex = selectedTab,
                onSelect = { selectedTab = it }
            )
            when (selectedTab) {
                0 -> GroupInfoTab(group)
                1 -> GroupToolsTab(group)
                2 -> GroupPromptsTab(group)
                else -> GroupResourcesTab(group)
            }
        }
    }
}

@Composable
private fun GroupInfoTab(group: CapabilityGroupCard) {
    val listState = rememberPersistentLazyListState("group_info_${group.id}")
    val colors = MaterialTheme.colorScheme
    LazyColumn(
        state = listState,
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(
            start = Spacing.lg,
            end = Spacing.lg,
            bottom = Spacing.xxl
        ),
        verticalArrangement = Arrangement.spacedBy(Spacing.sm)
    ) {
        groupBasicRows(group)
        group.status?.let { status ->
            item {
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(Radius.lg),
                    colors = CardDefaults.cardColors(
                        containerColor = if (status.state == McpServerStatus.State.FAILED) {
                            colors.errorContainer
                        } else {
                            colors.surfaceVariant.copy(alpha = 0.45f)
                        }
                    )
                ) {
                    Column(modifier = Modifier.padding(Spacing.lg)) {
                        Text(
                            text = stringResource(R.string.capability_detail_status),
                            style = MaterialTheme.typography.labelMedium,
                            color = colors.onSurfaceVariant
                        )
                        Spacer(Modifier.height(Spacing.xs))
                        Text(
                            text = status.error ?: statusText(status),
                            style = MaterialTheme.typography.bodyMedium,
                            color = colors.onSurface
                        )
                    }
                }
            }
        }
    }
}

/** 连接状态文案；已连接时带上「已加载/ 总数」计数。 */
@Composable
private fun statusText(status: McpServerStatus): String {
    val label = when (status.state) {
        McpServerStatus.State.CONNECTED -> stringResource(R.string.mcp_connected)
        McpServerStatus.State.CONNECTING -> stringResource(R.string.mcp_connecting)
        McpServerStatus.State.FAILED -> stringResource(R.string.mcp_connection_failed)
        McpServerStatus.State.DISABLED -> stringResource(R.string.mcp_disabled)
    }
    return if (status.state == McpServerStatus.State.CONNECTED) {
        "$label ${status.toolCount}/${status.totalToolCount}"
    } else {
        label
    }
}

@Composable
private fun InfoRow(label: String, value: String) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = Spacing.sm),
        horizontalArrangement = Arrangement.SpaceBetween
    ) {
        Text(
            text = label,
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
        Text(
            text = value,
            style = MaterialTheme.typography.bodyMedium,
            fontWeight = FontWeight.Medium,
            color = MaterialTheme.colorScheme.onSurface
        )
    }
}

/** 分组的基础信息行（类型 / 暴露状态 / 工具数）。 */
private fun LazyListScope.groupBasicRows(group: CapabilityGroupCard) {
    item { InfoRow(stringResource(R.string.capability_detail_group_name), group.displayName) }
    item {
        InfoRow(
            stringResource(R.string.capability_detail_kind),
            stringResource(
                if (group.isBuiltin) {
                    R.string.capability_detail_kind_builtin
                } else {
                    R.string.capability_detail_kind_remote
                }
            )
        )
    }
    item {
        InfoRow(
            stringResource(R.string.capability_group_exposed),
            stringResource(
                if (group.remotelyExposed) {
                    R.string.capability_group_exposed
                } else {
                    R.string.capability_group_internal_only
                }
            )
        )
    }
    item {
        InfoRow(
            stringResource(R.string.capability_detail_tool_count),
            group.tools.size.toString()
        )
    }
}

@Composable
private fun GroupToolsTab(group: CapabilityGroupCard) {
    val listState = rememberPersistentLazyListState("group_tools_${group.id}")
    if (group.tools.isEmpty()) {
        AppEmptyState(
            icon = Icons.AutoMirrored.Rounded.ArrowBack,
            title = stringResource(R.string.capability_tools_empty),
            modifier = Modifier.fillMaxWidth()
        )
        return
    }
    LazyColumn(
        state = listState,
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(
            start = Spacing.lg,
            end = Spacing.lg,
            bottom = Spacing.xxl
        ),
        verticalArrangement = Arrangement.spacedBy(Spacing.xs)
    ) {
        items(group.tools, key = { it.name }) { tool ->
            Column(modifier = Modifier.padding(vertical = Spacing.sm)) {
                Text(
                    text = tool.name,
                    style = MaterialTheme.typography.titleSmall,
                    fontFamily = FontFamily.Monospace,
                    color = MaterialTheme.colorScheme.onSurface
                )
                Spacer(Modifier.height(Spacing.xs))
                Text(
                    text = tool.description.ifBlank {
                        stringResource(R.string.capability_tool_no_description)
                    },
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                Spacer(Modifier.height(Spacing.sm))
                ToolBadgeRow(tool)
            }
        }
    }
}
