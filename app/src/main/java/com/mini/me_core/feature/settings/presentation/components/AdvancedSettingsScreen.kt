package com.mini.me_core.feature.settings.presentation.components

import androidx.compose.foundation.layout.Arrangement
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
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Tune
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.mini.me_core.R
import com.mini.me_core.core.theme.Spacing
import com.mini.me_core.core.theme.components.AppCard
import com.mini.me_core.core.theme.components.AppSectionHeader
import com.mini.me_core.feature.settings.presentation.ZthSettingsViewModel

/**
 * 高级设置页（Advanced Settings）。
 *
 * 统一收纳需要谨慎配置的高级功能；当前已迁入：
 *  - 零幻觉容忍（ZTH）——见 [ZthSettingsContent]。
 *
 * 顶栏返回与标题由外层 SettingsScreen 的 AppTopAppBar 提供；
 * 本页只提供背景、SnackbarHost 与 LazyColumn 滚动容器。
 */
@Composable
fun AdvancedSettingsScreen(
    zthViewModel: ZthSettingsViewModel,
) {
    val snackbarHostState = remember { SnackbarHostState() }

    Scaffold(
        containerColor = MaterialTheme.colorScheme.background,
        snackbarHost = { SnackbarHost(snackbarHostState) },
    ) { padding ->
        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding),
            contentPadding = PaddingValues(Spacing.md),
            verticalArrangement = Arrangement.spacedBy(Spacing.md),
        ) {
            // 顶部说明卡片
            item { AdvancedIntroCard() }

            // 分组 1：零幻觉容忍（ZTH）
            item {
                AppSectionHeader(title = stringResource(R.string.settings_zth_title))
            }
            item {
                ZthSettingsContent(
                    viewModel = zthViewModel,
                    snackbarHostState = snackbarHostState,
                )
            }

            // ── 后续高级设置分组在此添加 ──
        }
    }
}

/** 顶部说明卡片：图标 + 标题 + 说明文字。 */
@Composable
private fun AdvancedIntroCard() {
    AppCard {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(Spacing.md),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Icon(
                imageVector = Icons.Rounded.Tune,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.primary,
                modifier = Modifier.size(32.dp),
            )
            Spacer(Modifier.width(Spacing.md))
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = stringResource(R.string.settings_advanced_title),
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.SemiBold,
                    color = MaterialTheme.colorScheme.onSurface,
                )
                Spacer(Modifier.height(Spacing.xs))
                Text(
                    text = stringResource(R.string.settings_advanced_desc),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
    }
}
