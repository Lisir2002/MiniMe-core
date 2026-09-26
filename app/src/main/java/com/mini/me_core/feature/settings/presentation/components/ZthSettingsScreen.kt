package com.mini.me_core.feature.settings.presentation.components

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
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
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.mini.me_core.R
import com.mini.me_core.core.theme.Spacing
import com.mini.me_core.core.theme.components.AppCard
import com.mini.me_core.feature.agent.domain.zth.ZthPerformanceClass
import com.mini.me_core.feature.agent.domain.zth.ZthPresetTier
import com.mini.me_core.feature.settings.presentation.ZthSettingsViewModel

/**
 * ZTH（零幻觉容忍）独立设置页：档位选择 + 性能等级 + 滑动确认开关。
 *
 * 顶栏返回与标题由外层 SettingsScreen 的 AppTopAppBar 提供（section.titleRes）。
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
            AppCard {
                Column(modifier = Modifier.padding(Spacing.md)) {
                    Text(
                        text = stringResource(R.string.settings_zth_title),
                        style = MaterialTheme.typography.titleMedium,
                    )
                    Spacer(Modifier.height(Spacing.xs))
                    Text(
                        text = stringResource(R.string.security_zth_desc),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                    Spacer(Modifier.height(Spacing.sm))

                    Text(
                        stringResource(R.string.ui____0f215ee0),
                        style = MaterialTheme.typography.labelLarge,
                        fontWeight = FontWeight.SemiBold,
                    )
                    Spacer(Modifier.height(Spacing.xs))
                    val tiers = listOf(
                        ZthPresetTier.DISABLED to stringResource(R.string.ui____6f309ebf),
                        ZthPresetTier.MINIMAL to stringResource(R.string.ui____5c5c0db9),
                        ZthPresetTier.BALANCED to stringResource(R.string.ui____b6f5f134),
                        ZthPresetTier.STRICT to stringResource(R.string.ui____b59f6788),
                    )
                    tiers.forEach { (tier, desc) ->
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            modifier = Modifier.fillMaxWidth().padding(vertical = Spacing.xs),
                        ) {
                            RadioButton(
                                selected = uiState.tier == tier,
                                onClick = { viewModel.setTier(tier) },
                            )
                            Spacer(Modifier.width(Spacing.sm))
                            Column {
                                Text(
                                    text = "${tier.tier}. $tier · ${desc.substringBefore("——")}",
                                    style = MaterialTheme.typography.bodyMedium,
                                    fontWeight = if (uiState.tier == tier) FontWeight.SemiBold else FontWeight.Normal,
                                )
                                Text(
                                    text = desc.substringAfter("——"),
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                )
                            }
                        }
                    }

                    HorizontalDivider(Modifier.padding(vertical = Spacing.sm))
                    Text(
                        stringResource(R.string.ui______e17b4696),
                        style = MaterialTheme.typography.labelLarge,
                        fontWeight = FontWeight.SemiBold,
                    )
                    Spacer(Modifier.height(Spacing.xs))
                    val perfs = listOf(
                        ZthPerformanceClass.LOW_END_SKIP_LLM to stringResource(R.string.ui_____a5d93376),
                        ZthPerformanceClass.MID_RANGE to stringResource(R.string.ui____b87596cc),
                        ZthPerformanceClass.HIGH_END to stringResource(R.string.ui____485bd1f1),
                    )
                    perfs.forEach { (perf, desc) ->
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            modifier = Modifier.fillMaxWidth().padding(vertical = 3.dp),
                        ) {
                            RadioButton(
                                selected = uiState.perfClass == perf,
                                onClick = { viewModel.setPerf(perf) },
                            )
                            Spacer(Modifier.width(Spacing.sm))
                            Text(
                                text = "${perf.name}: $desc",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        }
                    }

                    HorizontalDivider(Modifier.padding(vertical = Spacing.sm))
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                    ) {
                        Column {
                            Text(
                                stringResource(R.string.ui______cd9f5bd3),
                                style = MaterialTheme.typography.labelLarge,
                                fontWeight = FontWeight.SemiBold,
                            )
                            Text(
                                text = stringResource(R.string.security_zth_swipe_hint),
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        }
                        Switch(
                            checked = uiState.swipeEnabled,
                            onCheckedChange = { viewModel.setSwipe(it) },
                            enabled = uiState.tier.tier <= 1,
                        )
                    }
                    if (uiState.tier.tier >= 2) {
                        Spacer(Modifier.height(Spacing.xs))
                        Text(
                            text = stringResource(R.string.ui____2e6ca1c3),
                            color = MaterialTheme.colorScheme.error,
                            style = MaterialTheme.typography.bodySmall,
                        )
                    }
                }
            }
        }
    }
}
