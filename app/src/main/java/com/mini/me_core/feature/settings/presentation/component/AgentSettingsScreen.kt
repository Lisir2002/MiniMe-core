package com.mini.me_core.feature.settings.presentation.component

import androidx.compose.foundation.clickable
import androidx.compose.foundation.shape.RoundedCornerShape
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
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material.icons.automirrored.rounded.KeyboardArrowRight
import androidx.compose.material.icons.rounded.Cloud
import androidx.compose.material.icons.rounded.Bolt
import androidx.compose.material.icons.rounded.Dashboard
import androidx.compose.material.icons.rounded.Inventory2
import androidx.compose.material.icons.rounded.Lock
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.mini.me_core.R
import com.mini.me_core.core.theme.Radius
import com.mini.me_core.core.theme.Spacing
import com.mini.me_core.core.theme.components.AppTopAppBar
import com.mini.me_core.core.ui.rememberPersistentLazyListState
import com.mini.me_core.feature.capability.domain.CapabilityStatusCard
import com.mini.me_core.feature.capability.presentation.UnifiedCapabilityViewModel
import com.mini.me_core.feature.settings.data.repository.ExecutionMode

/**
 * H1：Agent 独立设置页。
 *
 * 由来：合并后的统一能力页顶部曾挂一张「Agent 概览」状态卡（执行模式 / Provider /
 * 权限规则 / 工具 / 技能），它既不是能力列表的一项，又占据入口页最显眼位置——
 * 用户明确要求「把 agent 单独拿出来做一个单独的设置页面」。状态卡与三个跳转入口
 * 一并迁到此处，能力页回归「server 列表」单一职责。
 *
 * 数据仍来自 [UnifiedCapabilityViewModel]（透出 [com.mini.me_core.feature.capability.domain.CapabilityRegistry]
 * 的聚合），此处不二次加工——同一份数据两个真源是旧 CapabilityCenterViewModel 的教训。
 */
@Composable
fun AgentSettingsScreen(
    onNavigateBack: () -> Unit,
    onNavigateToCapabilityCenter: () -> Unit = {},
    onNavigateToPermissions: () -> Unit = {},
    onNavigateToProviders: () -> Unit = {},
    viewModel: UnifiedCapabilityViewModel = hiltViewModel(),
) {
    val overview by viewModel.overview.collectAsStateWithLifecycle()
    val listState = rememberPersistentLazyListState("agent_settings")

    Scaffold(
        containerColor = MaterialTheme.colorScheme.background,
        contentWindowInsets = WindowInsets(0, 0, 0, 0),
        topBar = {
            AppTopAppBar(
                title = stringResource(R.string.settings_agent),
                onNavigateBack = onNavigateBack,
                navigationIcon = Icons.AutoMirrored.Rounded.ArrowBack,
                navigationContentDescription = stringResource(R.string.common_back),
            )
        }
    ) { padding ->
        LazyColumn(
            state = listState,
            modifier = Modifier
                .fillMaxSize()
                .padding(padding),
            contentPadding = PaddingValues(
                start = Spacing.lg,
                end = Spacing.lg,
                bottom = Spacing.xxl
            ),
            verticalArrangement = Arrangement.spacedBy(Spacing.sm)
        ) {
            overview.statusCard?.let { card ->
                item(key = "agent_status") { AgentStatusCardItem(card) }
            }
            item(key = "agent_jumps") {
                Column(verticalArrangement = Arrangement.spacedBy(Spacing.sm)) {
                    AgentJumpRow(
                        icon = Icons.Rounded.Dashboard,
                        title = stringResource(R.string.capability_center_title),
                        subtitle = stringResource(R.string.capability_center_subtitle),
                        onClick = onNavigateToCapabilityCenter
                    )
                    AgentJumpRow(
                        icon = Icons.Rounded.Lock,
                        title = stringResource(R.string.settings_permissions),
                        subtitle = stringResource(R.string.settings_permissions_subtitle_agent),
                        onClick = onNavigateToPermissions
                    )
                    AgentJumpRow(
                        icon = Icons.Rounded.Cloud,
                        title = stringResource(R.string.settings_providers),
                        subtitle = stringResource(R.string.settings_providers_subtitle_agent),
                        onClick = onNavigateToProviders
                    )
                }
            }
        }
    }
}

/**
 * Agent 概览状态卡：执行模式 · Provider · 权限规则 N · 工具 N · 技能 N。
 *
 * 由统一能力页顶部原样迁入（D4 的「旧 Agent Tab 升格」产物），行为与视觉不变。
 */
@Composable
private fun AgentStatusCardItem(card: CapabilityStatusCard) {
    val colors = MaterialTheme.colorScheme
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(Radius.lg),
        colors = CardDefaults.cardColors(containerColor = colors.surfaceVariant.copy(alpha = 0.45f))
    ) {
        Column(modifier = Modifier.padding(Spacing.lg)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = stringResource(R.string.capability_status_card_title),
                    style = MaterialTheme.typography.labelMedium,
                    color = colors.onSurfaceVariant
                )
                Text(
                    text = executionModeText(card.executionMode),
                    style = MaterialTheme.typography.labelMedium,
                    fontWeight = FontWeight.Bold,
                    color = colors.primary
                )
            }
            Spacer(Modifier.height(Spacing.md))
            Text(
                text = card.activeProviderName
                    ?: stringResource(R.string.capability_agent_no_provider),
                style = MaterialTheme.typography.titleSmall,
                color = colors.onSurface
            )
            card.activeProviderModel?.let { model ->
                Text(
                    text = model,
                    style = MaterialTheme.typography.bodySmall,
                    color = colors.onSurfaceVariant
                )
            }
            Spacer(Modifier.height(Spacing.md))
            Row(horizontalArrangement = Arrangement.spacedBy(Spacing.lg)) {
                MiniStat(Icons.Rounded.Lock, card.permissionRuleCount.toString())
                MiniStat(Icons.Rounded.Inventory2, card.toolCount.toString())
                MiniStat(Icons.Rounded.Bolt, card.skillCount.toString())
            }
        }
    }
}

@Composable
private fun MiniStat(icon: ImageVector, value: String) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        Icon(
            imageVector = icon,
            contentDescription = null,
            tint = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.size(14.dp)
        )
        Spacer(Modifier.width(Spacing.xs))
        Text(
            text = value,
            style = MaterialTheme.typography.labelLarge,
            color = MaterialTheme.colorScheme.onSurface
        )
    }
}

/** Agent 页跳转行：与能力页分组卡同形（同圆角、同箭头语义），读起来是同一套东西。 */
@Composable
private fun AgentJumpRow(
    icon: ImageVector,
    title: String,
    subtitle: String,
    onClick: () -> Unit
) {
    val colors = MaterialTheme.colorScheme
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick),
        shape = RoundedCornerShape(Radius.lg),
        colors = CardDefaults.cardColors(containerColor = colors.surface)
    ) {
        Row(
            modifier = Modifier.padding(Spacing.lg),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Icon(
                imageVector = icon,
                contentDescription = null,
                tint = colors.primary,
                modifier = Modifier.size(20.dp)
            )
            Spacer(Modifier.width(Spacing.sm))
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = title,
                    style = MaterialTheme.typography.titleSmall,
                    color = colors.onSurface
                )
                Text(
                    text = subtitle,
                    style = MaterialTheme.typography.bodySmall,
                    color = colors.onSurfaceVariant
                )
            }
            Icon(
                imageVector = Icons.AutoMirrored.Rounded.KeyboardArrowRight,
                contentDescription = null,
                tint = colors.onSurfaceVariant
            )
        }
    }
}

@Composable
private fun executionModeText(mode: ExecutionMode): String = when (mode) {
    ExecutionMode.LOCAL_PROOT -> stringResource(R.string.capability_agent_execution_local)
    ExecutionMode.REMOTE_SSH -> stringResource(R.string.capability_agent_execution_remote)
}
