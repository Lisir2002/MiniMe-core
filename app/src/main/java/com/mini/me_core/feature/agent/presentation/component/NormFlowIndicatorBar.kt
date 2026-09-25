package com.mini.me_core.feature.agent.presentation.component

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkVertically
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.combinedClickable
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
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowForward
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.mini.me_core.R
import com.mini.me_core.core.theme.tokens.LocalCornerRadius
import com.mini.me_core.core.theme.Spacing

/**
 * 聊天输入框上方的规范运行指示器。
 *
 * 交互：
 * - 单击：展开规范概览浮层（7 项主规范启用状态 + 前往设置入口）
 * - 长按：快速切换规范总开关
 * - 状态文字显示启用数量：规范运行中 · X/7 项
 */
@OptIn(ExperimentalFoundationApi::class)
@Composable
fun NormFlowIndicatorBar(
    onNavigateToNormFlow: () -> Unit = {},
    viewModel: NormFlowIndicatorViewModel = hiltViewModel()
) {
    val enabled by viewModel.normFlowEnabled.collectAsStateWithLifecycle()
    val enabledCount by viewModel.enabledCount.collectAsStateWithLifecycle()
    var showSheet by remember { mutableStateOf(false) }

    val dotColor = if (enabled) Color(0xFF4CAF50) else Color(0xFF9E9E9E)
    val statusText = if (enabled) {
        stringResource(R.string.norm_flow_indicator_running_with_count, enabledCount, NormFlowIndicatorViewModel.TOTAL_MAIN_RULES)
    } else {
        stringResource(R.string.norm_flow_indicator_disabled)
    }

    Column(modifier = Modifier.fillMaxWidth()) {
        // 规范概览浮层：向上展开
        AnimatedVisibility(
            visible = showSheet,
            enter = expandVertically(expandFrom = Alignment.Bottom) + fadeIn(),
            exit = shrinkVertically(shrinkTowards = Alignment.Bottom) + fadeOut()
        ) {
            NormFlowOverviewSheet(
                viewModel = viewModel,
                onGoSettings = {
                    showSheet = false
                    onNavigateToNormFlow()
                },
                onDismiss = { showSheet = false }
            )
        }

        // 指示器横条
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .background(MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f))
                .combinedClickable(
                    onClick = { showSheet = !showSheet },
                    onLongClick = { viewModel.toggleNormFlow() }
                )
                .padding(horizontal = 16.dp, vertical = 4.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Box(
                modifier = Modifier
                    .size(8.dp)
                    .background(dotColor, CircleShape)
            )
            Spacer(Modifier.width(8.dp))
            Text(
                text = statusText,
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            Spacer(Modifier.weight(1f))
            Text(
                text = if (showSheet) "收起" else "详情",
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.primary,
                fontWeight = FontWeight.Medium
            )
        }
    }
}

/** 规范概览浮层：7 项主规范启用状态 + 前往设置按钮。 */
@Composable
private fun NormFlowOverviewSheet(
    viewModel: NormFlowIndicatorViewModel,
    onGoSettings: () -> Unit,
    onDismiss: () -> Unit
) {
    val stepInject by viewModel.stepInjectEnabled.collectAsStateWithLifecycle()
    val toolGuard by viewModel.toolGuardEnabled.collectAsStateWithLifecycle()
    val reasoningBudget by viewModel.reasoningBudgetEnabled.collectAsStateWithLifecycle()
    val usageCard by viewModel.usageCardEnabled.collectAsStateWithLifecycle()
    val sopSummary by viewModel.sopSummaryEnabled.collectAsStateWithLifecycle()
    val playbookAuto by viewModel.playbookAutoEnabled.collectAsStateWithLifecycle()
    val idleConverge by viewModel.idleConvergeEnabled.collectAsStateWithLifecycle()

    val items = listOf(
        Triple(R.string.settings_norm_flow_step_inject, stepInject, "注入"),
        Triple(R.string.settings_norm_flow_tool_guard, toolGuard, "护栏"),
        Triple(R.string.settings_norm_flow_reasoning_budget, reasoningBudget, "运行时"),
        Triple(R.string.settings_norm_flow_usage_card, usageCard, "运行时"),
        Triple(R.string.norm_flow_step_inject_sop_summary, sopSummary, "注入"),
        Triple(R.string.settings_norm_flow_playbook_auto, playbookAuto, "运行时"),
        Triple(R.string.settings_norm_flow_idle_converge, idleConverge, "运行时")
    )

    Surface(
        color = MaterialTheme.colorScheme.surface,
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant),
        shape = RoundedCornerShape(
            topStart = LocalCornerRadius.current.lg,
            topEnd = LocalCornerRadius.current.lg
        ),
        modifier = Modifier.fillMaxWidth()
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = Spacing.md, vertical = Spacing.sm)
        ) {
            Text(
                text = stringResource(R.string.norm_flow_indicator_sheet_title),
                style = MaterialTheme.typography.titleSmall,
                fontWeight = FontWeight.SemiBold,
                color = MaterialTheme.colorScheme.onSurface
            )
            Spacer(Modifier.height(Spacing.sm))

            // 两列网格展示 7 项规范
            val rows = items.chunked(2)
            rows.forEach { rowItems ->
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(Spacing.sm)
                ) {
                    rowItems.forEach { (titleRes, isEnabled, group) ->
                        Row(
                            modifier = Modifier
                                .weight(1f)
                                .padding(vertical = 2.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Box(
                                modifier = Modifier
                                    .size(6.dp)
                                    .background(
                                        if (isEnabled) Color(0xFF4CAF50) else Color(0xFF9E9E9E),
                                        CircleShape
                                    )
                            )
                            Spacer(Modifier.width(6.dp))
                            Text(
                                text = stringResource(titleRes),
                                style = MaterialTheme.typography.labelSmall,
                                color = if (isEnabled)
                                    MaterialTheme.colorScheme.onSurface
                                else
                                    MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.6f),
                                maxLines = 1
                            )
                        }
                    }
                    if (rowItems.size == 1) Spacer(Modifier.weight(1f))
                }
            }

            Spacer(Modifier.height(Spacing.sm))
            // 前往详细设置按钮
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(LocalCornerRadius.current.md))
                    .background(MaterialTheme.colorScheme.primary.copy(alpha = 0.1f))
                    .clickable { onGoSettings() }
                    .padding(horizontal = Spacing.md, vertical = Spacing.sm),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = stringResource(R.string.norm_flow_indicator_go_settings),
                    style = MaterialTheme.typography.labelLarge,
                    color = MaterialTheme.colorScheme.primary,
                    fontWeight = FontWeight.Medium
                )
                Spacer(Modifier.weight(1f))
                Icon(
                    imageVector = Icons.AutoMirrored.Rounded.ArrowForward,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.size(16.dp)
                )
            }
        }
    }
}
