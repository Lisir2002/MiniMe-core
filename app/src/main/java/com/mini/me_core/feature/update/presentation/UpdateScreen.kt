package com.mini.me_core.feature.update.presentation

import androidx.compose.foundation.BorderStroke
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
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material.icons.rounded.Cancel
import androidx.compose.material.icons.rounded.CheckCircle
import androidx.compose.material.icons.rounded.CloudDownload
import androidx.compose.material.icons.rounded.ExpandMore
import androidx.compose.material.icons.rounded.ExpandLess
import androidx.compose.material.icons.rounded.History
import androidx.compose.material.icons.rounded.OpenInBrowser
import androidx.compose.material.icons.rounded.Refresh
import androidx.compose.material.icons.rounded.SystemUpdate
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SecondaryTabRow
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Switch
import androidx.compose.material3.Tab
import androidx.compose.material3.TabRowDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.pulltorefresh.PullToRefreshBox
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import com.mini.me_core.R
import com.mini.me_core.core.theme.Spacing
import com.mini.me_core.core.theme.components.AppButton
import com.mini.me_core.core.theme.components.AppButtonColor
import com.mini.me_core.core.theme.components.AppButtonVariant
import com.mini.me_core.core.theme.components.AppCard
import com.mini.me_core.core.theme.components.AppTopAppBar
import com.mini.me_core.feature.update.domain.ReleaseInfo
import kotlinx.coroutines.launch

/**
 * 版本更新页（双 Tab：最新版本 / 历史版本）。
 *
 * 独立全屏页，自带顶栏（返回 + 标题 + 刷新）。由 SettingsScreen 以 section 方式进入。
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun UpdateScreen(
    onNavigateBack: () -> Unit,
    viewModel: UpdateViewModel = hiltViewModel(),
) {
    val state by viewModel.state.collectAsState()
    val snackbarHostState = remember { SnackbarHostState() }
    val scope = rememberCoroutineScope()
    val context = LocalContext.current

    androidx.compose.runtime.LaunchedEffect(Unit) {
        viewModel.snackbar.collect { msg ->
            snackbarHostState.showSnackbar(msg)
        }
    }

    var tabIndex by remember { mutableIntStateOf(0) }

    Scaffold(
        containerColor = MaterialTheme.colorScheme.background,
        snackbarHost = { SnackbarHost(snackbarHostState) },
        topBar = {
            AppTopAppBar(
                title = stringResource(R.string.update_title),
                onNavigateBack = onNavigateBack,
                navigationIcon = Icons.AutoMirrored.Rounded.ArrowBack,
                navigationContentDescription = stringResource(R.string.common_back),
            ) {
                IconButton(
                    onClick = { viewModel.refresh(force = true) },
                    modifier = Modifier.size(40.dp),
                ) {
                    if (state.checking) {
                        CircularProgressIndicator(
                            modifier = Modifier.size(18.dp),
                            strokeWidth = 2.dp,
                        )
                    } else {
                        Icon(
                            Icons.Rounded.Refresh,
                            contentDescription = stringResource(R.string.update_refresh),
                            modifier = Modifier.size(20.dp),
                        )
                    }
                }
            }
        },
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding),
        ) {
            SecondaryTabRow(
                selectedTabIndex = tabIndex,
                containerColor = MaterialTheme.colorScheme.background,
            ) {
                Tab(
                    selected = tabIndex == 0,
                    onClick = { tabIndex = 0 },
                    text = { Text(stringResource(R.string.update_tab_latest)) },
                )
                Tab(
                    selected = tabIndex == 1,
                    onClick = { tabIndex = 1 },
                    text = { Text(stringResource(R.string.update_tab_history)) },
                )
            }

            when (tabIndex) {
                0 -> LatestTab(
                    state = state,
                    viewModel = viewModel,
                    onOpenLink = { url ->
                        runCatching {
                            context.startActivity(
                                android.content.Intent(
                                    android.content.Intent.ACTION_VIEW,
                                    android.net.Uri.parse(url),
                                ).addFlags(android.content.Intent.FLAG_ACTIVITY_NEW_TASK),
                            )
                        }
                    },
                )
                else -> HistoryTab(
                    state = state,
                    viewModel = viewModel,
                    onRefresh = { viewModel.refresh(force = true) },
                )
            }
        }
    }
}

// ============================================================
// Tab 1：最新版本
// ============================================================

@Composable
private fun LatestTab(
    state: UpdateUiState,
    viewModel: UpdateViewModel,
    onOpenLink: (String) -> Unit,
) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(horizontal = Spacing.lg, vertical = Spacing.md),
        verticalArrangement = Arrangement.spacedBy(Spacing.md),
    ) {
        // 自动检查开关
        AutoCheckRow(
            checked = state.autoCheck,
            onCheckedChange = viewModel::setAutoCheck,
        )

        // 状态卡
        LatestStatusCard(state = state, viewModel = viewModel)

        // 下载进度（页面内，非弹窗）
        if (state.download is DownloadUiState.Downloading) {
            DownloadingCard(
                state = state.download,
                viewModel = viewModel,
                onCancel = viewModel::cancelDownload,
            )
        } else if (state.download is DownloadUiState.Done) {
            DownloadDoneCard(
                state = state.download,
                onInstall = { viewModel.install(state.downloadedFilePath ?: (state.download as DownloadUiState.Done).filePath) },
            )
        } else if (state.download is DownloadUiState.Failed) {
            DownloadFailedCard(
                state = state.download,
                onRetry = { state.latest?.let { viewModel.download(it) } },
            )
        }

        // 详情卡
        state.latest?.let { latest ->
            DetailCard(
                release = latest,
                state = state,
                viewModel = viewModel,
                onOpenLink = onOpenLink,
            )
        }
        if (state.latest == null && state.checking) {
            Box(
                modifier = Modifier.fillMaxWidth(),
                contentAlignment = Alignment.Center,
            ) {
                CircularProgressIndicator()
            }
        }
    }
}

@Composable
private fun AutoCheckRow(checked: Boolean, onCheckedChange: (Boolean) -> Unit) {
    AppCard {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(Spacing.md),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = stringResource(R.string.update_auto_check),
                    style = MaterialTheme.typography.titleSmall,
                    color = MaterialTheme.colorScheme.onSurface,
                )
                Text(
                    text = stringResource(R.string.update_auto_check_subtitle),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            Switch(checked = checked, onCheckedChange = onCheckedChange)
        }
    }
}

@Composable
private fun LatestStatusCard(state: UpdateUiState, viewModel: UpdateViewModel) {
    val latest = state.latest
    AppCard {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(Spacing.md),
            verticalArrangement = Arrangement.spacedBy(Spacing.sm),
        ) {
            if (latest == null) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    CircularProgressIndicator(modifier = Modifier.size(18.dp), strokeWidth = 2.dp)
                    Spacer(Modifier.width(Spacing.sm))
                    Text(
                        text = stringResource(R.string.update_checking),
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            } else if (state.hasUpdate) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(
                        Icons.Rounded.SystemUpdate,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.primary,
                    )
                    Spacer(Modifier.width(Spacing.sm))
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = stringResource(R.string.update_new_version_found, latest.versionName),
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.primary,
                        )
                        Text(
                            text = stringResource(
                                R.string.update_new_version_detail,
                                state.currentVersion,
                                latest.versionName,
                            ),
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                        Text(
                            text = stringResource(R.string.update_published_at_short, viewModel.formatTime(latest.publishedAt)),
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                }
            } else {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(
                        Icons.Rounded.CheckCircle,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.tertiary,
                    )
                    Spacer(Modifier.width(Spacing.sm))
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = stringResource(R.string.update_up_to_date),
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.tertiary,
                        )
                        Text(
                            text = stringResource(R.string.update_up_to_date_detail, state.currentVersion),
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                        if (state.lastCheckTime > 0) {
                            Text(
                                text = stringResource(
                                    R.string.update_last_check,
                                    viewModel.formatTime(state.lastCheckTime),
                                ),
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun DetailCard(
    release: ReleaseInfo,
    state: UpdateUiState,
    viewModel: UpdateViewModel,
    onOpenLink: (String) -> Unit,
) {
    AppCard {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(Spacing.md),
            verticalArrangement = Arrangement.spacedBy(Spacing.sm),
        ) {
            Text(
                text = stringResource(R.string.update_release_notes),
                style = MaterialTheme.typography.titleSmall,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.onSurface,
            )
            // 元信息行
            Row(horizontalArrangement = Arrangement.spacedBy(Spacing.md)) {
                MetaChip(stringResource(R.string.update_version) + ": v" + release.versionName)
                if (release.fileSizeBytes > 0) {
                    MetaChip(stringResource(R.string.update_apk_size) + ": " + viewModel.formatSize(release.fileSizeBytes))
                }
            }

            // 完整更新日志（可滚动，不限高）
            MarkdownNotes(
                markdown = release.body,
                onOpenLink = onOpenLink,
                modifier = Modifier
                    .fillMaxWidth()
                    .heightIn(max = 420.dp),
            )

            Spacer(Modifier.height(Spacing.xs))
            // 操作按钮
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(Spacing.sm),
            ) {
                if (state.hasUpdate && release.hasApk) {
                    AppButton(
                        text = stringResource(R.string.update_download_now),
                        onClick = { viewModel.download(release) },
                        modifier = Modifier.weight(1f),
                        buttonColor = AppButtonColor.Primary,
                    )
                }
                AppButton(
                    text = stringResource(R.string.update_open_browser),
                    onClick = { viewModel.openInBrowser(release) },
                    modifier = Modifier.weight(1f),
                    variant = AppButtonVariant.Outlined,
                    buttonColor = AppButtonColor.Neutral,
                )
            }
        }
    }
}

/** 给更新日志一个较大的可滚动高度（而非硬限 180dp）。 */
@Composable
private fun MetaChip(text: String) {
    Card(
        shape = MaterialTheme.shapes.small,
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant),
    ) {
        Text(
            text = text,
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp),
        )
    }
}

@Composable
private fun DownloadingCard(
    state: DownloadUiState.Downloading,
    viewModel: UpdateViewModel,
    onCancel: () -> Unit,
) {
    val p = state.progress
    AppCard {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(Spacing.md),
            verticalArrangement = Arrangement.spacedBy(Spacing.sm),
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
            ) {
                Text(
                    text = stringResource(R.string.update_downloading, p.percent),
                    style = MaterialTheme.typography.titleSmall,
                    fontWeight = FontWeight.SemiBold,
                    color = MaterialTheme.colorScheme.onSurface,
                )
                if (p.speedBytesPerSec > 0) {
                    Text(
                        text = stringResource(
                            R.string.update_download_speed,
                            viewModel.formatSize(p.speedBytesPerSec),
                        ),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.primary,
                    )
                }
            }
            LinearProgressIndicator(
                progress = { (p.percent.coerceIn(0, 100)) / 100f },
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(MaterialTheme.shapes.small),
            )
            Text(
                text = stringResource(
                    R.string.update_download_progress_detail,
                    viewModel.formatSize(p.downloadedBytes),
                    if (p.totalBytes > 0) viewModel.formatSize(p.totalBytes) else stringResource(R.string.update_size_unknown),
                ),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Row(horizontalArrangement = Arrangement.End, modifier = Modifier.fillMaxWidth()) {
                AppButton(
                    text = stringResource(R.string.update_cancel),
                    onClick = onCancel,
                    variant = AppButtonVariant.Text,
                    buttonColor = AppButtonColor.Error,
                )
            }
        }
    }
}

@Composable
private fun DownloadDoneCard(
    state: DownloadUiState.Done,
    onInstall: () -> Unit,
) {
    AppCard {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(Spacing.md),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Icon(
                Icons.Rounded.CheckCircle,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.tertiary,
            )
            Spacer(Modifier.width(Spacing.sm))
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = stringResource(R.string.update_downloaded_to),
                    style = MaterialTheme.typography.titleSmall,
                    fontWeight = FontWeight.SemiBold,
                    color = MaterialTheme.colorScheme.onSurface,
                )
            }
            AppButton(
                text = stringResource(R.string.update_install),
                onClick = onInstall,
                buttonColor = AppButtonColor.Success,
            )
        }
    }
}

@Composable
private fun DownloadFailedCard(
    state: DownloadUiState.Failed,
    onRetry: () -> Unit,
) {
    AppCard {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(Spacing.md),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = stringResource(R.string.update_download_failed),
                    style = MaterialTheme.typography.titleSmall,
                    fontWeight = FontWeight.SemiBold,
                    color = MaterialTheme.colorScheme.error,
                )
                Text(
                    text = state.message,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            AppButton(
                text = stringResource(R.string.update_retry),
                onClick = onRetry,
                variant = AppButtonVariant.Outlined,
            )
        }
    }
}

// ============================================================
// Tab 2：历史版本
// ============================================================

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun HistoryTab(
    state: UpdateUiState,
    viewModel: UpdateViewModel,
    onRefresh: () -> Unit,
) {
    Column(modifier = Modifier.fillMaxSize()) {
        // 预发布开关
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = Spacing.lg, vertical = Spacing.sm),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(
                text = stringResource(R.string.update_show_prerelease),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurface,
                modifier = Modifier.weight(1f),
            )
            Switch(
                checked = state.showPrerelease,
                onCheckedChange = viewModel::togglePrerelease,
            )
        }

        PullToRefreshBox(
            isRefreshing = state.historyLoading || state.checking,
            onRefresh = onRefresh,
            modifier = Modifier.fillMaxSize(),
        ) {
            when {
                state.historyLoading && state.history.isEmpty() -> {
                    Box(
                        modifier = Modifier.fillMaxSize(),
                        contentAlignment = Alignment.Center,
                    ) { CircularProgressIndicator() }
                }
                state.historyError != null && state.history.isEmpty() -> {
                    ErrorRetryState(
                        message = state.historyError ?: "",
                        onRetry = onRefresh,
                    )
                }
                state.history.isEmpty() -> {
                    Box(
                        modifier = Modifier.fillMaxSize(),
                        contentAlignment = Alignment.Center,
                    ) {
                        Text(
                            text = stringResource(R.string.update_no_history),
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                }
                else -> {
                    LazyColumn(
                        modifier = Modifier.fillMaxSize(),
                        contentPadding = androidx.compose.foundation.layout.PaddingValues(
                            horizontal = Spacing.lg,
                            vertical = Spacing.md,
                        ),
                        verticalArrangement = Arrangement.spacedBy(Spacing.sm),
                    ) {
                        items(state.history, key = { it.tag }) { release ->
                            HistoryItem(
                                release = release,
                                state = state,
                                viewModel = viewModel,
                            )
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun ErrorRetryState(message: String, onRetry: () -> Unit) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(Spacing.lg),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center,
    ) {
        Text(
            text = message,
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.error,
        )
        Spacer(Modifier.height(Spacing.md))
        AppButton(
            text = stringResource(R.string.update_retry),
            onClick = onRetry,
            variant = AppButtonVariant.Outlined,
        )
    }
}

@Composable
private fun HistoryItem(
    release: ReleaseInfo,
    state: UpdateUiState,
    viewModel: UpdateViewModel,
) {
    val expanded = state.expandedTag == release.tag
    val isCurrent = state.isCurrent(release)
    Card(
        shape = MaterialTheme.shapes.large,
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        border = androidx.compose.foundation.BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant),
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(Spacing.md),
        ) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .clickable { viewModel.toggleExpand(release.tag) },
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text(
                            text = "v" + release.versionName,
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.onSurface,
                        )
                        if (isCurrent) {
                            Spacer(Modifier.width(Spacing.sm))
                            CurrentBadge()
                        }
                        if (release.isPrerelease) {
                            Spacer(Modifier.width(Spacing.sm))
                            PrereleaseBadge()
                        }
                    }
                    Text(
                        text = viewModel.formatTime(release.publishedAt),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                    val summary = release.summary()
                    if (summary.isNotBlank()) {
                        Text(
                            text = summary,
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            maxLines = 2,
                        )
                    }
                }
                Icon(
                    imageVector = if (expanded) Icons.Rounded.ExpandLess else Icons.Rounded.ExpandMore,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }

            if (expanded) {
                HorizontalDivider(modifier = Modifier.padding(vertical = Spacing.sm))
                MarkdownNotes(markdown = release.body)
                Spacer(Modifier.height(Spacing.sm))
                Row(horizontalArrangement = Arrangement.spacedBy(Spacing.sm)) {
                    if (release.hasApk) {
                        AppButton(
                            text = stringResource(R.string.update_download_now),
                            onClick = { viewModel.download(release) },
                            modifier = Modifier.weight(1f),
                            size = com.mini.me_core.core.theme.components.AppButtonSize.Small,
                        )
                    }
                    AppButton(
                        text = stringResource(R.string.update_open_browser),
                        onClick = { viewModel.openInBrowser(release) },
                        modifier = Modifier.weight(1f),
                        variant = AppButtonVariant.Outlined,
                        size = com.mini.me_core.core.theme.components.AppButtonSize.Small,
                    )
                }
            }
        }
    }
}

@Composable
private fun CurrentBadge() {
    Box(
        modifier = Modifier
            .clip(MaterialTheme.shapes.small)
            .background(MaterialTheme.colorScheme.primaryContainer)
            .padding(horizontal = 6.dp, vertical = 2.dp),
    ) {
        Text(
            text = stringResource(R.string.update_current_version_badge),
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onPrimaryContainer,
        )
    }
}

@Composable
private fun PrereleaseBadge() {
    Box(
        modifier = Modifier
            .clip(MaterialTheme.shapes.small)
            .background(MaterialTheme.colorScheme.errorContainer)
            .padding(horizontal = 6.dp, vertical = 2.dp),
    ) {
        Text(
            text = "Pre-release",
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onErrorContainer,
        )
    }
}
