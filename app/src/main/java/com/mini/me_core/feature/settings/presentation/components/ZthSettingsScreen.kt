package com.mini.me_core.feature.settings.presentation.components

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.clickable
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.RadioButton
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.mini.me_core.R
import com.mini.me_core.core.theme.Spacing
import com.mini.me_core.core.theme.components.AppButton
import com.mini.me_core.core.theme.components.AppButtonVariant
import com.mini.me_core.core.theme.components.AppCard
import com.mini.me_core.core.theme.components.AppDialog
import com.mini.me_core.core.theme.components.AppDialogType
import com.mini.me_core.core.theme.components.AppSectionHeader
import com.mini.me_core.feature.agent.domain.zth.ZthPerformanceClass
import com.mini.me_core.feature.agent.domain.zth.ZthPresetTier
import com.mini.me_core.feature.settings.presentation.ZthSettingsViewModel

/**
 * ZTH（零幻觉容忍）独立设置页：
 *  - 顶部：功能说明（可展开）
 *  - 状态摘要卡片：当前生效参数一览
 *  - 卡片1：档位选择（含四档对比表 + 展开详情）
 *  - 卡片2：性能等级
 *  - 卡片3：滑动确认开关
 *  - 底部：恢复默认
 *
 * 顶栏返回与标题由外层 SettingsScreen 的 AppTopAppBar 提供。
 */
@Composable
fun ZthSettingsScreen(
    viewModel: ZthSettingsViewModel,
) {
    val uiState by viewModel.uiState.collectAsState()
    val snackbarHostState = remember { SnackbarHostState() }

    LaunchedEffect(uiState.successMessage, uiState.error) {
        uiState.successMessage?.let {
            snackbarHostState.showSnackbar(it)
            viewModel.clearMessages()
        }
        uiState.error?.let {
            snackbarHostState.showSnackbar(it)
            viewModel.clearMessages()
        }
    }

    // 降级确认框
    uiState.pendingDowngradeTier?.let { target ->
        AppDialog(
            title = stringResource(R.string.settings_zth_downgrade_title),
            message = stringResource(R.string.settings_zth_downgrade_message),
            confirmText = stringResource(R.string.settings_zth_compare_yes),
            onDismiss = { viewModel.dismissDowngrade() },
            onConfirm = { viewModel.confirmDowngrade() },
        )
    }

    // 恢复默认确认框
    if (uiState.showResetConfirm) {
        AppDialog(
            title = stringResource(R.string.settings_zth_reset_confirm_title),
            message = stringResource(R.string.settings_zth_reset_confirm_message),
            confirmText = stringResource(R.string.settings_zth_reset),
            type = AppDialogType.Destructive,
            onDismiss = { viewModel.dismissResetConfirm() },
            onConfirm = { viewModel.confirmResetDefault() },
        )
    }

    androidx.compose.material3.Scaffold(
        containerColor = MaterialTheme.colorScheme.background,
        snackbarHost = { SnackbarHost(snackbarHostState) },
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .verticalScroll(rememberScrollState())
                .padding(Spacing.md),
            verticalArrangement = Arrangement.spacedBy(Spacing.md),
        ) {
            ZthInfoCard()
            ZthStatusCard(uiState.tier, uiState.swipeEnabled, uiState.perfClass)
            ZthTierCard(
                selectedTier = uiState.tier,
                onTierSelected = { viewModel.onTierSelected(it) },
            )
            ZthPerfCard(
                selectedPerf = uiState.perfClass,
                onPerfSelected = { viewModel.setPerf(it) },
            )
            ZthSwipeCard(
                tier = uiState.tier,
                swipeEnabled = uiState.swipeEnabled,
                onSwipeChange = { viewModel.setSwipe(it) },
            )
            AppButton(
                text = stringResource(R.string.settings_zth_reset),
                onClick = { viewModel.requestResetDefault() },
                variant = AppButtonVariant.Outlined,
                modifier = Modifier.fillMaxWidth(),
            )
        }
    }
}

// ─────────────────────────────────────────────────────────────────────────────
// 卡片 0：功能说明（可展开）
// ─────────────────────────────────────────────────────────────────────────────

@Composable
private fun ZthInfoCard() {
    var expanded by remember { mutableStateOf(false) }
    AppCard {
        Column(modifier = Modifier.padding(Spacing.md)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(
                    text = stringResource(R.string.settings_zth_info_title),
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.SemiBold,
                    modifier = Modifier.weight(1f),
                )
                Text(
                    text = if (expanded) "−" else "+",
                    style = MaterialTheme.typography.titleMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            Spacer(Modifier.height(Spacing.xs))
            Text(
                text = stringResource(R.string.settings_zth_subtitle),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            if (expanded) {
                Spacer(Modifier.height(Spacing.sm))
                Text(
                    text = stringResource(R.string.settings_zth_info_body),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
    }
}

// ─────────────────────────────────────────────────────────────────────────────
// 卡片 1：当前生效状态摘要
// ─────────────────────────────────────────────────────────────────────────────

@Composable
private fun ZthStatusCard(
    tier: ZthPresetTier,
    swipeEnabled: Boolean,
    perf: ZthPerformanceClass,
) {
    val params = ZthDisplayMapper.tierParams(tier)
    AppCard {
        Column(modifier = Modifier.padding(Spacing.md)) {
            Text(
                text = stringResource(R.string.settings_zth_status_title),
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.SemiBold,
            )
            Spacer(Modifier.height(Spacing.sm))
            StatusRow(
                label = stringResource(R.string.settings_zth_status_row_tier),
                value = tier.displayName(),
            )
            StatusRow(
                label = stringResource(R.string.settings_zth_status_row_circuit),
                value = params.circuitBreakFails?.let {
                    stringResource(R.string.settings_zth_status_circuit_times, it)
                } ?: stringResource(R.string.settings_zth_status_circuit_never),
            )
            StatusRow(
                label = stringResource(R.string.settings_zth_status_row_threshold),
                value = params.hallucinationThreshold?.let { "%.1f".format(it) }
                    ?: stringResource(R.string.settings_zth_compare_dash),
            )
            StatusRow(
                label = stringResource(R.string.settings_zth_status_row_swipe),
                value = when {
                    params.swipeForced -> stringResource(R.string.settings_zth_status_swipe_forced)
                    swipeEnabled -> stringResource(R.string.settings_zth_status_swipe_optional)
                    else -> stringResource(R.string.settings_zth_status_swipe_off)
                },
            )
            StatusRow(
                label = stringResource(R.string.settings_zth_status_row_llm),
                value = stringResource(
                    if (params.llmFinalCheck) R.string.settings_zth_status_llm_on
                    else R.string.settings_zth_status_llm_off
                ),
            )
        }
    }
}

@Composable
private fun StatusRow(label: String, value: String) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = Spacing.xs),
        horizontalArrangement = Arrangement.SpaceBetween,
    ) {
        Text(
            text = label,
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Text(
            text = value,
            style = MaterialTheme.typography.bodyMedium,
            fontWeight = FontWeight.Medium,
        )
    }
}

// ─────────────────────────────────────────────────────────────────────────────
// 卡片 2：档位选择 + 对比表
// ─────────────────────────────────────────────────────────────────────────────

@Composable
private fun ZthTierCard(
    selectedTier: ZthPresetTier,
    onTierSelected: (ZthPresetTier) -> Unit,
) {
    var expandedTier by remember { mutableStateOf<ZthPresetTier?>(null) }
    AppCard {
        Column(modifier = Modifier.padding(Spacing.md)) {
            Text(
                text = stringResource(R.string.settings_zth_section_tier),
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.SemiBold,
            )
            Spacer(Modifier.height(Spacing.sm))

            ZthDisplayMapper.allTiers.forEach { tier ->
                val params = ZthDisplayMapper.tierParams(tier)
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.Top,
                ) {
                    RadioButton(
                        selected = selectedTier == tier,
                        onClick = { onTierSelected(tier) },
                    )
                    Spacer(Modifier.width(Spacing.sm))
                    Column(modifier = Modifier.weight(1f)) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            Text(
                                text = tier.displayName(),
                                style = MaterialTheme.typography.bodyMedium,
                                fontWeight = if (selectedTier == tier) FontWeight.SemiBold else FontWeight.Normal,
                                modifier = Modifier.weight(1f),
                            )
                            Text(
                                text = if (expandedTier == tier) "−" else "+",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                modifier = Modifier
                                    .padding(start = Spacing.sm)
                                    .clickable {
                                        expandedTier = if (expandedTier == tier) null else tier
                                    },
                            )
                        }
                        Text(
                            text = stringResource(params.descriptionRes),
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                        if (expandedTier == tier) {
                            Spacer(Modifier.height(Spacing.xs))
                            TierDetailExpanded(params)
                        }
                    }
                }
            }

            HorizontalDivider(Modifier.padding(vertical = Spacing.sm))
            ZthTierComparisonTable()
        }
    }
}

@Composable
private fun TierDetailExpanded(params: ZthDisplayMapper.TierParams) {
    Column {
        DetailRow(
            label = stringResource(R.string.settings_zth_detail_circuit),
            value = params.circuitBreakFails?.let { "$it 次" }
                ?: stringResource(R.string.settings_zth_compare_never),
        )
        DetailRow(
            label = stringResource(R.string.settings_zth_detail_threshold),
            value = params.hallucinationThreshold?.let { "%.1f".format(it) }
                ?: stringResource(R.string.settings_zth_compare_dash),
        )
        DetailRow(
            label = stringResource(R.string.settings_zth_detail_swipe),
            value = if (params.swipeForced)
                stringResource(R.string.settings_zth_compare_forced)
            else
                stringResource(R.string.settings_zth_compare_optional),
        )
        DetailRow(
            label = stringResource(R.string.settings_zth_detail_cancel),
            value = if (params.cancelAllowed)
                stringResource(R.string.settings_zth_compare_yes)
            else
                stringResource(R.string.settings_zth_compare_no),
        )
        DetailRow(
            label = stringResource(R.string.settings_zth_detail_llm),
            value = if (params.llmFinalCheck)
                stringResource(R.string.settings_zth_status_llm_on)
            else
                stringResource(R.string.settings_zth_status_llm_off),
        )
        DetailRow(
            label = stringResource(R.string.settings_zth_detail_auto_break),
            value = if (params.autoCircuitBreak)
                stringResource(R.string.settings_zth_compare_yes)
            else
                stringResource(R.string.settings_zth_compare_no),
        )
    }
}

@Composable
private fun DetailRow(label: String, value: String) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 2.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
    ) {
        Text(
            text = label,
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Text(
            text = value,
            style = MaterialTheme.typography.bodySmall,
        )
    }
}

/** 四档对比简表。 */
@Composable
private fun ZthTierComparisonTable() {
    val tiers = ZthDisplayMapper.allTiers
    Text(
        text = stringResource(R.string.settings_zth_compare_title),
        style = MaterialTheme.typography.labelLarge,
        fontWeight = FontWeight.SemiBold,
    )
    Spacer(Modifier.height(Spacing.xs))

    // 表头
    Row(modifier = Modifier.fillMaxWidth()) {
        TableCell(
            text = stringResource(R.string.settings_zth_compare_param),
            weight = 1.6f,
            bold = true,
        )
        tiers.forEach { tier ->
            TableCell(text = tier.displayName(), weight = 1f, bold = true, center = true)
        }
    }
    HorizontalDivider(Modifier.padding(vertical = 2.dp))

    // 熔断阈值
    Row(modifier = Modifier.fillMaxWidth()) {
        TableCell(text = stringResource(R.string.settings_zth_compare_circuit), weight = 1.6f)
        tiers.forEach { tier ->
            val p = ZthDisplayMapper.tierParams(tier)
            TableCell(
                text = p.circuitBreakFails?.let {
                    stringResource(R.string.settings_zth_compare_times, it)
                } ?: stringResource(R.string.settings_zth_compare_never),
                weight = 1f,
                center = true,
            )
        }
    }
    // 幻觉阈值
    Row(modifier = Modifier.fillMaxWidth()) {
        TableCell(text = stringResource(R.string.settings_zth_compare_threshold), weight = 1.6f)
        tiers.forEach { tier ->
            val p = ZthDisplayMapper.tierParams(tier)
            TableCell(
                text = p.hallucinationThreshold?.let { "%.1f".format(it) }
                    ?: stringResource(R.string.settings_zth_compare_dash),
                weight = 1f,
                center = true,
            )
        }
    }
    // 滑动确认
    Row(modifier = Modifier.fillMaxWidth()) {
        TableCell(text = stringResource(R.string.settings_zth_compare_swipe), weight = 1.6f)
        tiers.forEach { tier ->
            val p = ZthDisplayMapper.tierParams(tier)
            TableCell(
                text = if (p.swipeForced)
                    stringResource(R.string.settings_zth_compare_forced)
                else
                    stringResource(R.string.settings_zth_compare_optional),
                weight = 1f,
                center = true,
            )
        }
    }
    // 允许取消
    Row(modifier = Modifier.fillMaxWidth()) {
        TableCell(text = stringResource(R.string.settings_zth_compare_cancel), weight = 1.6f)
        tiers.forEach { tier ->
            val p = ZthDisplayMapper.tierParams(tier)
            TableCell(
                text = if (p.cancelAllowed)
                    stringResource(R.string.settings_zth_compare_yes)
                else
                    stringResource(R.string.settings_zth_compare_no),
                weight = 1f,
                center = true,
            )
        }
    }
    // LLM 终检
    Row(modifier = Modifier.fillMaxWidth()) {
        TableCell(text = stringResource(R.string.settings_zth_compare_llm), weight = 1.6f)
        tiers.forEach { tier ->
            val p = ZthDisplayMapper.tierParams(tier)
            TableCell(
                text = if (p.llmFinalCheck)
                    stringResource(R.string.settings_zth_status_llm_on)
                else
                    stringResource(R.string.settings_zth_status_llm_off),
                weight = 1f,
                center = true,
            )
        }
    }
}

@Composable
private fun RowScope.TableCell(
    text: String,
    weight: Float,
    bold: Boolean = false,
    center: Boolean = false,
) {
    Column(
        modifier = Modifier
            .weight(weight)
            .padding(vertical = 2.dp, horizontal = 2.dp),
        horizontalAlignment = if (center) Alignment.CenterHorizontally else Alignment.Start,
    ) {
        Text(
            text = text,
            style = MaterialTheme.typography.bodySmall,
            fontWeight = if (bold) FontWeight.SemiBold else FontWeight.Normal,
            color = if (bold) MaterialTheme.colorScheme.onSurface
            else MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}

// ─────────────────────────────────────────────────────────────────────────────
// 卡片 3：性能等级
// ─────────────────────────────────────────────────────────────────────────────

@Composable
private fun ZthPerfCard(
    selectedPerf: ZthPerformanceClass,
    onPerfSelected: (ZthPerformanceClass) -> Unit,
) {
    AppCard {
        Column(modifier = Modifier.padding(Spacing.md)) {
            Text(
                text = stringResource(R.string.settings_zth_section_perf),
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.SemiBold,
            )
            Spacer(Modifier.height(Spacing.sm))
            ZthDisplayMapper.allPerfs.forEach { perf ->
                val params = ZthDisplayMapper.perfParams(perf)
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(vertical = Spacing.xs),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    RadioButton(
                        selected = selectedPerf == perf,
                        onClick = { onPerfSelected(perf) },
                    )
                    Spacer(Modifier.width(Spacing.sm))
                    Column {
                        Text(
                            text = perf.displayName(),
                            style = MaterialTheme.typography.bodyMedium,
                            fontWeight = if (selectedPerf == perf) FontWeight.SemiBold else FontWeight.Normal,
                        )
                        Text(
                            text = stringResource(params.descriptionRes),
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                }
            }
        }
    }
}

// ─────────────────────────────────────────────────────────────────────────────
// 卡片 4：滑动确认
// ─────────────────────────────────────────────────────────────────────────────

@Composable
private fun ZthSwipeCard(
    tier: ZthPresetTier,
    swipeEnabled: Boolean,
    onSwipeChange: (Boolean) -> Unit,
) {
    val forced = tier.tier >= 2
    AppCard {
        Column(modifier = Modifier.padding(Spacing.md)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween,
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = stringResource(R.string.settings_zth_swipe_title),
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.SemiBold,
                    )
                    Spacer(Modifier.height(Spacing.xs))
                    // 常驻说明：不等用户尝试关闭才弹
                    Text(
                        text = stringResource(R.string.settings_zth_swipe_hint),
                        style = MaterialTheme.typography.bodySmall,
                        color = if (forced) MaterialTheme.colorScheme.error
                        else MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
                Switch(
                    checked = swipeEnabled,
                    onCheckedChange = { onSwipeChange(it) },
                    enabled = !forced,
                )
            }
        }
    }
}
