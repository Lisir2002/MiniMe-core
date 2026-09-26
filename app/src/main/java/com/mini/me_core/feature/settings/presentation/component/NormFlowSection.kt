package com.mini.me_core.feature.settings.presentation.component

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkVertically
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.KeyboardArrowRight
import androidx.compose.material.icons.rounded.ArrowForward
import androidx.compose.material.icons.rounded.AutoAwesome
import androidx.compose.material.icons.rounded.BarChart
import androidx.compose.material.icons.rounded.Book
import androidx.compose.material.icons.rounded.Bolt
import androidx.compose.material.icons.rounded.CheckCircle
import androidx.compose.material.icons.rounded.Block
import androidx.compose.material.icons.rounded.ExpandMore
import androidx.compose.material.icons.rounded.FactCheck
import androidx.compose.material.icons.rounded.FolderOpen
import androidx.compose.material.icons.rounded.FolderZip
import androidx.compose.material.icons.rounded.Input
import androidx.compose.material.icons.rounded.ListAlt
import androidx.compose.material.icons.rounded.Memory
import androidx.compose.material.icons.rounded.Pin
import androidx.compose.material.icons.rounded.Refresh
import androidx.compose.material.icons.rounded.Rule
import androidx.compose.material.icons.rounded.Shield
import androidx.compose.material.icons.rounded.Timer
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.mini.me_core.R
import com.mini.me_core.core.theme.Spacing
import com.mini.me_core.core.theme.tokens.LocalCornerRadius
import com.mini.me_core.feature.agent.presentation.component.formatTokenCountShort

/**
 * 「规范流程」二级页（P0+P1+P2 + 布局重构）：
 * 仪表盘卡 + 总开关 + 预设卡片 + 三个可折叠主题色分组。
 */
@Composable
internal fun NormFlowSection(
    normFlowEnabled: Boolean,
    stepInjectEnabled: Boolean,
    toolGuardEnabled: Boolean,
    fileObservationEnabled: Boolean,
    reasoningBudgetEnabled: Boolean,
    usageCardEnabled: Boolean,
    sopSummaryEnabled: Boolean,
    playbookAutoEnabled: Boolean,
    idleConvergeEnabled: Boolean,
    stepInjectGoalEnabled: Boolean,
    stepInjectStaticRulesEnabled: Boolean,
    stepInjectLayeredRulesEnabled: Boolean,
    stepInjectProjectAgentsEnabled: Boolean,
    reasoningBudgetLevel: String,
    idleConvergeRounds: Int,
    activePreset: String,
    dangerousCommandEnabled: Boolean,
    largeFileEnabled: Boolean,
    pathBoundaryEnabled: Boolean,
    usageCardItems: Set<String>,
    injectTokenCount: Int,
    guardBlockCount: Int,
    idleRoundCount: Int,
    onToggleNormFlow: (Boolean) -> Unit,
    onToggleStepInject: (Boolean) -> Unit,
    onToggleToolGuard: (Boolean) -> Unit,
    onToggleFileObservation: (Boolean) -> Unit,
    onToggleReasoningBudget: (Boolean) -> Unit,
    onToggleUsageCard: (Boolean) -> Unit,
    onToggleSopSummary: (Boolean) -> Unit,
    onTogglePlaybookAuto: (Boolean) -> Unit,
    onToggleIdleConverge: (Boolean) -> Unit,
    onToggleStepInjectGoal: (Boolean) -> Unit,
    onToggleStepInjectStaticRules: (Boolean) -> Unit,
    onToggleStepInjectLayeredRules: (Boolean) -> Unit,
    onToggleStepInjectProjectAgents: (Boolean) -> Unit,
    onSetReasoningBudgetLevel: (String) -> Unit,
    onSetIdleConvergeRounds: (Int) -> Unit,
    onApplyPreset: (String) -> Unit,
    onViewStaticRules: () -> Unit,
    onViewSop: () -> Unit,
    onToggleDangerousCommand: (Boolean) -> Unit,
    onToggleLargeFile: (Boolean) -> Unit,
    onTogglePathBoundary: (Boolean) -> Unit,
    onToggleUsageCardItem: (String) -> Unit,
    onOpenDiagnosis: () -> Unit,
    onOpenGuardLogs: () -> Unit,
    onManageRules: () -> Unit,
    onExportConfig: () -> Unit,
    onImportConfig: () -> Unit,
    onResetStats: () -> Unit
) {
    val injectionChildrenEnabled = normFlowEnabled && stepInjectEnabled
    val guardChildrenEnabled = normFlowEnabled && toolGuardEnabled
    val reasoningLevelEnabled = normFlowEnabled && reasoningBudgetEnabled
    val idleRoundsEnabled = normFlowEnabled && idleConvergeEnabled
    val dimAlpha = if (normFlowEnabled) 1f else 0.4f

    val presetNameRes = when (activePreset) {
        "strict" -> R.string.norm_flow_preset_strict
        "minimal" -> R.string.norm_flow_preset_minimal
        "custom" -> R.string.norm_flow_preset_custom
        else -> R.string.norm_flow_preset_standard
    }

    // 分组展开状态
    var injectionExpanded by remember { mutableStateOf(true) }
    var guardExpanded by remember { mutableStateOf(false) }
    var runtimeExpanded by remember { mutableStateOf(false) }

    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(horizontal = Spacing.lg, vertical = Spacing.md),
        verticalArrangement = Arrangement.spacedBy(Spacing.md),
    ) {
        // ── 仪表盘卡 ──
        item {
            DashboardCard(
                normFlowEnabled = normFlowEnabled,
                presetName = stringResource(presetNameRes),
                injectTokenCount = injectTokenCount,
                guardBlockCount = guardBlockCount,
                idleRoundCount = idleRoundCount,
                onResetStats = onResetStats,
                onClick = onOpenDiagnosis
            )
        }

        // ── 总开关（醒目行） ──
        item {
            MasterSwitchRow(
                checked = normFlowEnabled,
                onCheckedChange = onToggleNormFlow
            )
        }

        // ── 预设卡片（基础版：3 个卡片横向排列） ──
        item {
            PresetCardsRow(
                activePreset = activePreset,
                onApplyPreset = onApplyPreset
            )
        }

        // ── 分组 1：注入纪律（蓝色主题） ──
        item {
            ThemedCollapsibleGroup(
                themeColor = MaterialTheme.colorScheme.primary,
                icon = Icons.Rounded.Input,
                title = stringResource(R.string.norm_flow_group_injection),
                subtitle = stringResource(R.string.norm_flow_group_injection_desc),
                enabledCount = listOf(
                    stepInjectEnabled, stepInjectGoalEnabled, stepInjectStaticRulesEnabled,
                    sopSummaryEnabled, stepInjectLayeredRulesEnabled, stepInjectProjectAgentsEnabled
                ).count { it },
                totalCount = 6,
                expanded = injectionExpanded,
                onToggleExpand = { injectionExpanded = !injectionExpanded }
            ) {
                GroupSwitchRow(
                    icon = Icons.Rounded.Input,
                    title = stringResource(R.string.settings_norm_flow_step_inject),
                    subtitle = stringResource(R.string.settings_norm_flow_step_inject_desc),
                    checked = stepInjectEnabled,
                    onCheckedChange = onToggleStepInject,
                    enabled = normFlowEnabled
                )
                GroupSwitchRow(
                    icon = Icons.Rounded.FactCheck,
                    title = stringResource(R.string.norm_flow_step_inject_goal),
                    subtitle = stringResource(R.string.norm_flow_step_inject_goal_desc),
                    checked = stepInjectGoalEnabled,
                    onCheckedChange = onToggleStepInjectGoal,
                    enabled = injectionChildrenEnabled,
                    isChild = true
                )
                GroupSwitchRow(
                    icon = Icons.Rounded.Book,
                    title = stringResource(R.string.norm_flow_step_inject_static_rules),
                    subtitle = stringResource(R.string.norm_flow_step_inject_static_rules_desc),
                    checked = stepInjectStaticRulesEnabled,
                    onCheckedChange = onToggleStepInjectStaticRules,
                    enabled = normFlowEnabled,
                    isChild = true,
                    onViewClick = onViewStaticRules
                )
                GroupSwitchRow(
                    icon = Icons.Rounded.ListAlt,
                    title = stringResource(R.string.norm_flow_step_inject_sop_summary),
                    subtitle = stringResource(R.string.norm_flow_step_inject_sop_summary_desc),
                    checked = sopSummaryEnabled,
                    onCheckedChange = onToggleSopSummary,
                    enabled = normFlowEnabled,
                    isChild = true,
                    onViewClick = onViewSop
                )
                GroupSwitchRow(
                    icon = Icons.Rounded.Rule,
                    title = stringResource(R.string.norm_flow_step_inject_layered_rules),
                    subtitle = stringResource(R.string.norm_flow_step_inject_layered_rules_desc),
                    checked = stepInjectLayeredRulesEnabled,
                    onCheckedChange = onToggleStepInjectLayeredRules,
                    enabled = injectionChildrenEnabled,
                    isChild = true,
                    onViewClick = onManageRules
                )
                GroupSwitchRow(
                    icon = Icons.Rounded.FolderOpen,
                    title = stringResource(R.string.norm_flow_step_inject_project_agents),
                    subtitle = stringResource(R.string.norm_flow_step_inject_project_agents_desc),
                    checked = stepInjectProjectAgentsEnabled,
                    onCheckedChange = onToggleStepInjectProjectAgents,
                    enabled = normFlowEnabled,
                    isChild = true
                )
            }
        }

        // ── 分组 2：执行护栏（橙红主题） ──
        item {
            ThemedCollapsibleGroup(
                themeColor = MaterialTheme.colorScheme.error,
                icon = Icons.Rounded.Shield,
                title = stringResource(R.string.norm_flow_group_guard),
                subtitle = stringResource(R.string.norm_flow_group_guard_desc),
                enabledCount = listOf(
                    toolGuardEnabled, fileObservationEnabled, dangerousCommandEnabled,
                    largeFileEnabled, pathBoundaryEnabled
                ).count { it },
                totalCount = 5,
                expanded = guardExpanded,
                onToggleExpand = { guardExpanded = !guardExpanded }
            ) {
                GroupSwitchRow(
                    icon = Icons.Rounded.Shield,
                    title = stringResource(R.string.settings_norm_flow_tool_guard),
                    subtitle = stringResource(R.string.settings_norm_flow_tool_guard_desc),
                    checked = toolGuardEnabled,
                    onCheckedChange = onToggleToolGuard,
                    enabled = normFlowEnabled,
                    onViewClick = onOpenGuardLogs
                )
                GroupSwitchRow(
                    icon = Icons.Rounded.CheckCircle,
                    title = stringResource(R.string.norm_flow_guard_file_observation),
                    subtitle = stringResource(R.string.norm_flow_guard_file_observation_desc),
                    checked = fileObservationEnabled,
                    onCheckedChange = onToggleFileObservation,
                    enabled = guardChildrenEnabled,
                    isChild = true
                )
                GroupSwitchRow(
                    icon = Icons.Rounded.Block,
                    title = stringResource(R.string.norm_flow_guard_dangerous_command),
                    subtitle = stringResource(R.string.norm_flow_guard_dangerous_command_desc),
                    checked = dangerousCommandEnabled,
                    onCheckedChange = onToggleDangerousCommand,
                    enabled = guardChildrenEnabled,
                    isChild = true
                )
                GroupSwitchRow(
                    icon = Icons.Rounded.FolderZip,
                    title = stringResource(R.string.norm_flow_guard_large_file),
                    subtitle = stringResource(R.string.norm_flow_guard_large_file_desc),
                    checked = largeFileEnabled,
                    onCheckedChange = onToggleLargeFile,
                    enabled = guardChildrenEnabled,
                    isChild = true
                )
                GroupSwitchRow(
                    icon = Icons.Rounded.Pin,
                    title = stringResource(R.string.norm_flow_guard_path_boundary),
                    subtitle = stringResource(R.string.norm_flow_guard_path_boundary_desc),
                    checked = pathBoundaryEnabled,
                    onCheckedChange = onTogglePathBoundary,
                    enabled = guardChildrenEnabled,
                    isChild = true
                )
            }
        }

        // ── 分组 3：运行参数（绿色主题） ──
        item {
            ThemedCollapsibleGroup(
                themeColor = MaterialTheme.colorScheme.tertiary,
                icon = Icons.Rounded.Timer,
                title = stringResource(R.string.norm_flow_group_runtime),
                subtitle = stringResource(R.string.norm_flow_group_runtime_desc),
                enabledCount = listOf(
                    reasoningBudgetEnabled, usageCardEnabled, idleConvergeEnabled, playbookAutoEnabled
                ).count { it },
                totalCount = 4,
                expanded = runtimeExpanded,
                onToggleExpand = { runtimeExpanded = !runtimeExpanded }
            ) {
                GroupSwitchRow(
                    icon = Icons.Rounded.Memory,
                    title = stringResource(R.string.settings_norm_flow_reasoning_budget),
                    subtitle = stringResource(R.string.settings_norm_flow_reasoning_budget_desc),
                    checked = reasoningBudgetEnabled,
                    onCheckedChange = onToggleReasoningBudget,
                    enabled = normFlowEnabled,
                    valueSummary = if (reasoningBudgetEnabled)
                        stringResource(R.string.norm_flow_reasoning_level_label, reasoningBudgetLevelLabel(reasoningBudgetLevel))
                    else null
                )
                SubSegmentedControl(
                    options = listOf(
                        stringResource(R.string.norm_flow_reasoning_level_low),
                        stringResource(R.string.norm_flow_reasoning_level_medium),
                        stringResource(R.string.norm_flow_reasoning_level_high)
                    ),
                    selectedIndex = when (reasoningBudgetLevel) {
                        "low" -> 0
                        "high" -> 2
                        else -> 1
                    },
                    onSelect = { idx ->
                        onSetReasoningBudgetLevel(when (idx) { 0 -> "low"; 2 -> "high"; else -> "medium" })
                    },
                    enabled = reasoningLevelEnabled
                )
                GroupSwitchRow(
                    icon = Icons.Rounded.BarChart,
                    title = stringResource(R.string.settings_norm_flow_usage_card),
                    subtitle = stringResource(R.string.settings_norm_flow_usage_card_desc),
                    checked = usageCardEnabled,
                    onCheckedChange = onToggleUsageCard,
                    enabled = normFlowEnabled,
                    valueSummary = if (usageCardEnabled && usageCardItems.isNotEmpty())
                        usageCardItems.joinToString(", ") { usageItemLabel(it) }
                    else null
                )
                val usageItemsEnabled = normFlowEnabled && usageCardEnabled
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = Spacing.lg, vertical = Spacing.xs)
                        .then(if (!usageItemsEnabled) Modifier.alpha(0.4f) else Modifier),
                    horizontalArrangement = Arrangement.spacedBy(Spacing.sm)
                ) {
                    listOf(
                        "token" to stringResource(R.string.norm_flow_usage_item_token),
                        "calls" to stringResource(R.string.norm_flow_usage_item_calls),
                        "tools" to stringResource(R.string.norm_flow_usage_item_tools),
                        "duration" to stringResource(R.string.norm_flow_usage_item_duration)
                    ).forEach { (key, label) ->
                        androidx.compose.material3.FilterChip(
                            selected = key in usageCardItems,
                            onClick = { onToggleUsageCardItem(key) },
                            enabled = usageItemsEnabled,
                            label = { Text(label, style = MaterialTheme.typography.labelMedium) }
                        )
                    }
                }
                GroupSwitchRow(
                    icon = Icons.Rounded.Timer,
                    title = stringResource(R.string.settings_norm_flow_idle_converge),
                    subtitle = stringResource(R.string.settings_norm_flow_idle_converge_desc),
                    checked = idleConvergeEnabled,
                    onCheckedChange = onToggleIdleConverge,
                    enabled = normFlowEnabled,
                    valueSummary = if (idleConvergeEnabled)
                        stringResource(R.string.norm_flow_idle_rounds_label, idleConvergeRounds)
                    else null
                )
                SubSegmentedControl(
                    options = listOf(
                        stringResource(R.string.norm_flow_idle_rounds_3),
                        stringResource(R.string.norm_flow_idle_rounds_6),
                        stringResource(R.string.norm_flow_idle_rounds_10)
                    ),
                    selectedIndex = when (idleConvergeRounds) {
                        3 -> 0
                        10 -> 2
                        else -> 1
                    },
                    onSelect = { idx ->
                        onSetIdleConvergeRounds(when (idx) { 0 -> 3; 2 -> 10; else -> 6 })
                    },
                    enabled = idleRoundsEnabled
                )
                GroupSwitchRow(
                    icon = Icons.Rounded.AutoAwesome,
                    title = stringResource(R.string.settings_norm_flow_playbook_auto),
                    subtitle = stringResource(R.string.settings_norm_flow_playbook_auto_desc),
                    checked = playbookAutoEnabled,
                    onCheckedChange = onTogglePlaybookAuto,
                    enabled = normFlowEnabled
                )
            }
        }

        // 导出/导入配置
        item {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(Spacing.sm),
                verticalAlignment = Alignment.CenterVertically
            ) {
                androidx.compose.material3.TextButton(onClick = onExportConfig) {
                    Text(stringResource(R.string.norm_flow_export_config))
                }
                androidx.compose.material3.TextButton(onClick = onImportConfig) {
                    Text(stringResource(R.string.norm_flow_import_config))
                }
            }
        }

        // 底部留白
        item { Spacer(Modifier.height(Spacing.xl)) }
    }
}

// ── 仪表盘卡 ──────────────────────────────────────────────

@Composable
private fun DashboardCard(
    normFlowEnabled: Boolean,
    presetName: String,
    injectTokenCount: Int,
    guardBlockCount: Int,
    idleRoundCount: Int,
    onResetStats: () -> Unit,
    onClick: () -> Unit
) {
    val statusColor = if (normFlowEnabled) Color(0xFF4CAF50) else MaterialTheme.colorScheme.onSurfaceVariant
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .clickable { onClick() },
        shape = RoundedCornerShape(LocalCornerRadius.current.lg),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.4f)
        ),
        elevation = CardDefaults.cardElevation(defaultElevation = 0.dp)
    ) {
        Column(modifier = Modifier.padding(Spacing.lg)) {
            // 顶部行：状态指示器 + 预设名 + 重置统计
            Row(verticalAlignment = Alignment.CenterVertically) {
                Box(
                    modifier = Modifier
                        .size(10.dp)
                        .background(statusColor, CircleShape)
                )
                Spacer(Modifier.width(Spacing.sm))
                Text(
                    text = if (normFlowEnabled) stringResource(R.string.norm_flow_status_running)
                    else stringResource(R.string.norm_flow_status_disabled),
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                Spacer(Modifier.weight(1f))
                Text(
                    text = presetName,
                    style = MaterialTheme.typography.titleSmall,
                    fontWeight = FontWeight.SemiBold,
                    color = MaterialTheme.colorScheme.onSurface
                )
                Spacer(Modifier.width(Spacing.xs))
                // 重置统计小图标按钮（独立于卡片点击，避免冒泡到诊断页跳转）
                IconButton(onClick = onResetStats, modifier = Modifier.size(28.dp)) {
                    Icon(
                        Icons.Rounded.Refresh,
                        contentDescription = stringResource(R.string.norm_flow_reset_stats),
                        tint = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.size(16.dp)
                    )
                }
            }
            Spacer(Modifier.height(Spacing.md))
            // 三列数据
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically
            ) {
                DashboardStat(
                    stringResource(R.string.norm_flow_status_inject_tokens),
                    formatTokenCountShort(injectTokenCount),
                    Modifier.weight(1f)
                )
                VerticalDivider()
                DashboardStat(
                    stringResource(R.string.norm_flow_status_guard_blocks),
                    guardBlockCount.toString(),
                    Modifier.weight(1f)
                )
                VerticalDivider()
                DashboardStat(
                    stringResource(R.string.norm_flow_status_idle_rounds),
                    idleRoundCount.toString(),
                    Modifier.weight(1f)
                )
            }
            Spacer(Modifier.height(Spacing.md))
            // 链路可视化
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.Center,
                verticalAlignment = Alignment.CenterVertically
            ) {
                ChainStep("User", active = true)
                ChainArrow()
                ChainStep("Inject", active = normFlowEnabled)
                ChainArrow()
                ChainStep("Model", active = true)
                ChainArrow()
                ChainStep("Guard", active = normFlowEnabled)
                ChainArrow()
                ChainStep("Out", active = true)
            }
        }
    }
}

@Composable
private fun DashboardStat(label: String, value: String, modifier: Modifier = Modifier) {
    Column(modifier = modifier, horizontalAlignment = Alignment.CenterHorizontally) {
        Text(
            text = value,
            style = MaterialTheme.typography.titleMedium,
            color = MaterialTheme.colorScheme.onSurface
        )
        Spacer(Modifier.height(2.dp))
        Text(
            text = label,
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
    }
}

@Composable
private fun VerticalDivider() {
    Box(
        modifier = Modifier
            .height(32.dp)
            .width(0.5.dp)
            .background(MaterialTheme.colorScheme.outlineVariant)
    )
}

@Composable
private fun ChainStep(label: String, active: Boolean) {
    Text(
        text = label,
        style = MaterialTheme.typography.labelSmall,
        color = if (active) MaterialTheme.colorScheme.primary
        else MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.5f)
    )
}

@Composable
private fun ChainArrow() {
    Icon(
        Icons.Rounded.ArrowForward,
        contentDescription = null,
        modifier = Modifier.size(12.dp).padding(horizontal = 2.dp),
        tint = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.4f)
    )
}

// ── 总开关行 ──────────────────────────────────────────────

@Composable
private fun MasterSwitchRow(
    checked: Boolean,
    onCheckedChange: (Boolean) -> Unit
) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(LocalCornerRadius.current.lg),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.primary.copy(alpha = 0.08f)),
        elevation = CardDefaults.cardElevation(defaultElevation = 0.dp)
    ) {
        Row(
            modifier = Modifier.padding(Spacing.lg),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Box(
                modifier = Modifier
                    .size(44.dp)
                    .clip(RoundedCornerShape(LocalCornerRadius.current.md))
                    .background(MaterialTheme.colorScheme.primary),
                contentAlignment = Alignment.Center
            ) {
                Icon(Icons.Rounded.Shield, contentDescription = null, tint = Color.White)
            }
            Spacer(Modifier.width(Spacing.md))
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = stringResource(R.string.settings_norm_flow_master),
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.SemiBold
                )
                Text(
                    text = stringResource(R.string.settings_norm_flow_master_desc),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
            Switch(checked = checked, onCheckedChange = onCheckedChange)
        }
    }
}

// ── 预设卡片（基础版） ────────────────────────────────────

@Composable
private fun PresetCardsRow(
    activePreset: String,
    onApplyPreset: (String) -> Unit
) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(Spacing.sm)
    ) {
        PresetCard(
            modifier = Modifier.weight(1f),
            title = stringResource(R.string.norm_flow_preset_strict),
            icon = Icons.Rounded.Shield,
            isActive = activePreset == "strict",
            onClick = { onApplyPreset("strict") }
        )
        PresetCard(
            modifier = Modifier.weight(1f),
            title = stringResource(R.string.norm_flow_preset_standard),
            icon = Icons.Rounded.CheckCircle,
            isActive = activePreset == "standard",
            onClick = { onApplyPreset("standard") }
        )
        PresetCard(
            modifier = Modifier.weight(1f),
            title = stringResource(R.string.norm_flow_preset_minimal),
            icon = Icons.Rounded.Bolt,
            isActive = activePreset == "minimal",
            onClick = { onApplyPreset("minimal") }
        )
    }
}

@Composable
private fun PresetCard(
    modifier: Modifier = Modifier,
    title: String,
    icon: ImageVector,
    isActive: Boolean,
    onClick: () -> Unit
) {
    val borderColor = if (isActive) MaterialTheme.colorScheme.primary
    else MaterialTheme.colorScheme.outlineVariant
    Card(
        modifier = modifier.clickable { onClick() },
        shape = RoundedCornerShape(LocalCornerRadius.current.md),
        colors = CardDefaults.cardColors(
            containerColor = if (isActive) MaterialTheme.colorScheme.primary.copy(alpha = 0.08f)
            else MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.3f)
        ),
        elevation = CardDefaults.cardElevation(defaultElevation = 0.dp),
        border = CardDefaults.outlinedCardBorder().let { androidx.compose.foundation.BorderStroke(1.dp, borderColor) }
    ) {
        Column(
            modifier = Modifier.padding(Spacing.md),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Icon(
                icon, contentDescription = null,
                tint = if (isActive) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant
            )
            Spacer(Modifier.height(Spacing.xs))
            Text(
                text = title,
                style = MaterialTheme.typography.labelLarge,
                fontWeight = if (isActive) FontWeight.SemiBold else FontWeight.Normal
            )
            if (isActive) {
                Text(
                    text = stringResource(R.string.norm_flow_preset_current),
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.primary
                )
            }
        }
    }
}

// ── 可折叠主题色分组卡片 ───────────────────────────────────

@Composable
private fun ThemedCollapsibleGroup(
    themeColor: Color,
    icon: ImageVector,
    title: String,
    subtitle: String,
    enabledCount: Int,
    totalCount: Int,
    expanded: Boolean,
    onToggleExpand: () -> Unit,
    content: @Composable () -> Unit
) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(LocalCornerRadius.current.lg),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.3f)),
        elevation = CardDefaults.cardElevation(defaultElevation = 0.dp)
    ) {
        Column {
            // Header 行
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .background(themeColor.copy(alpha = 0.1f))
                    .clickable { onToggleExpand() }
                    .padding(Spacing.md),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Box(
                    modifier = Modifier
                        .size(38.dp)
                        .clip(RoundedCornerShape(LocalCornerRadius.current.sm))
                        .background(themeColor),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(icon, contentDescription = null, tint = Color.White, modifier = Modifier.size(20.dp))
                }
                Spacer(Modifier.width(Spacing.md))
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = title,
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.SemiBold
                    )
                    Text(
                        text = subtitle,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
                Text(
                    text = "$enabledCount/$totalCount",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                Spacer(Modifier.width(Spacing.sm))
                Icon(
                    Icons.Rounded.ExpandMore,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.graphicsLayer { rotationZ = if (expanded) 0f else -90f }
                )
            }
            // 可折叠内容
            AnimatedVisibility(
                visible = expanded,
                enter = fadeIn() + expandVertically(),
                exit = fadeOut() + shrinkVertically()
            ) {
                Column { content() }
            }
        }
    }
}

// ── 工具函数 ──────────────────────────────────────────────

private fun reasoningBudgetLevelLabel(level: String): String = when (level) {
    "low" -> "低"
    "high" -> "高"
    else -> "中"
}

private fun usageItemLabel(key: String): String = when (key) {
    "token" -> "Token"
    "calls" -> "调用"
    "tools" -> "工具"
    "duration" -> "耗时"
    else -> key
}

/** 子级 Segmented Control（推理强度 / 空转轮数），开关关闭时禁用。 */
@Composable
private fun SubSegmentedControl(
    options: List<String>,
    selectedIndex: Int,
    onSelect: (Int) -> Unit,
    enabled: Boolean
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = Spacing.lg, vertical = Spacing.xs)
            .then(if (!enabled) Modifier.alpha(0.4f) else Modifier)
            .clip(RoundedCornerShape(LocalCornerRadius.current.sm))
            .background(MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.3f))
            .padding(4.dp),
        horizontalArrangement = Arrangement.spacedBy(4.dp)
    ) {
        options.forEachIndexed { index, label ->
            val selected = index == selectedIndex
            Box(
                modifier = Modifier
                    .weight(1f)
                    .clip(RoundedCornerShape(LocalCornerRadius.current.xs))
                    .background(
                        if (selected) MaterialTheme.colorScheme.primary
                        else Color.Transparent
                    )
                    .clickable(enabled = enabled) { onSelect(index) }
                    .padding(vertical = 8.dp),
                contentAlignment = Alignment.Center
            ) {
                Text(
                    text = label,
                    style = MaterialTheme.typography.labelMedium,
                    color = if (selected) MaterialTheme.colorScheme.onPrimary
                    else MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }
    }
}
