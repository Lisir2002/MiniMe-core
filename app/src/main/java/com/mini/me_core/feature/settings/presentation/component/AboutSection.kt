package com.mini.me_core.feature.settings.presentation.component

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.content.Intent
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.drawable.Drawable
import android.net.Uri
import android.os.Build
import android.os.Environment
import android.widget.Toast
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Android
import androidx.compose.material.icons.rounded.CalendarMonth
import androidx.compose.material.icons.rounded.ChatBubble
import androidx.compose.material.icons.rounded.CloudDownload
import androidx.compose.material.icons.rounded.CloudUpload
import androidx.compose.material.icons.rounded.Description
import androidx.compose.material.icons.rounded.FolderZip
import androidx.compose.material.icons.rounded.History
import androidx.compose.material.icons.rounded.Language
import androidx.compose.material.icons.rounded.MailOutline
import androidx.compose.material.icons.rounded.Public
import androidx.compose.material.icons.rounded.Schedule
import androidx.compose.material.icons.rounded.Smartphone
import androidx.compose.material.icons.rounded.Storage
import androidx.compose.material.icons.rounded.SystemUpdate
import androidx.compose.material.icons.rounded.Tag
import androidx.compose.material.icons.rounded.Terminal
import androidx.compose.material.icons.rounded.Code
import androidx.compose.material.icons.rounded.Balance
import androidx.compose.material.icons.rounded.BugReport
import androidx.compose.material.icons.rounded.Share
import androidx.compose.material.icons.rounded.Lock
import androidx.compose.material.icons.rounded.Policy
import androidx.compose.material.icons.rounded.Book
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.hilt.navigation.compose.hiltViewModel
import com.mini.me_core.BuildConfig
import com.mini.me_core.R
import com.mini.me_core.core.theme.Spacing
import com.mini.me_core.core.theme.components.AppCard
import com.mini.me_core.core.theme.components.AppSectionHeader
import com.mini.me_core.core.theme.components.AppListItem
import com.mini.me_core.feature.about.presentation.CodeBrowserScreen
import com.mini.me_core.feature.about.presentation.CodeBrowserViewModel
import com.mini.me_core.feature.proxy.domain.ClashProxyManager
import com.mini.me_core.feature.settings.presentation.AboutStatsViewModel
import com.mini.me_core.feature.settings.presentation.UsageStats
import com.mini.me_core.feature.update.domain.UpdateAvailability
import com.mini.me_core.feature.update.presentation.UpdateBadgeViewModel
import java.io.File
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

// ============================================================
// Entry point
// ============================================================

/** 关于页 SharedPreferences 名称。 */
private const val PREFS_ABOUT = "minime_about_prefs"

/** 开发者选项解锁状态的持久化 key。 */
private const val KEY_DEV_UNLOCKED = "dev_options_unlocked"

/** 连续点击版本号解锁开发者选项所需次数。 */
private const val DEV_TAP_THRESHOLD = 7

/** 默认 GitHub 仓库信息。 */
private const val GH_OWNER = "Lisir2002"
private const val GH_REPO = "MiniMe-core"
private const val GH_BRANCH = "main"

@Composable
internal fun AboutSection(
    onOpenDevOptions: () -> Unit = {},
    onOpenUpdate: () -> Unit = {},
    onOpenContainerSettings: () -> Unit = {},
    onOpenProxySettings: () -> Unit = {},
) {
    val context = LocalContext.current
    val aboutVM: AboutStatsViewModel = hiltViewModel()
    val stats by aboutVM.stats.collectAsStateWithLifecycle()
    val proxyState by aboutVM.proxyState.collectAsStateWithLifecycle()
    val terminalReady by aboutVM.terminalReady.collectAsStateWithLifecycle()
    val updateBadgeVM: UpdateBadgeViewModel = hiltViewModel()
    val updateAvailability by updateBadgeVM.availability.collectAsStateWithLifecycle()
    val codeBrowserVM: CodeBrowserViewModel = hiltViewModel()

    // F5.6：连续点击版本号 7 次解锁开发者选项，解锁状态持久化
    val prefs = remember { context.getSharedPreferences(PREFS_ABOUT, Context.MODE_PRIVATE) }
    var devUnlocked by remember { mutableStateOf(prefs.getBoolean(KEY_DEV_UNLOCKED, false)) }
    var versionTaps by remember { mutableStateOf(0) }

    // 本地导航 / 弹窗状态
    var showCodeBrowser by remember { mutableStateOf(false) }
    var showResetConfirm by remember { mutableStateOf(false) }
    var showResetMenu by remember { mutableStateOf(false) }
    var pendingDialog by remember { mutableStateOf<PendingDialog?>(null) }
    var showHostInfo by remember { mutableStateOf(false) }
    var showWebViewInfo by remember { mutableStateOf(false) }
    var expandedCredit by remember { mutableStateOf<OpenSourceLib?>(null) }

    fun onVersionTap() {
        if (devUnlocked) {
            onOpenDevOptions()
            return
        }
        versionTaps++
        val remaining = DEV_TAP_THRESHOLD - versionTaps
        if (remaining > 0) {
            Toast.makeText(context, context.getString(R.string.about_dev_unlock_toast, remaining), Toast.LENGTH_SHORT).show()
        } else {
            prefs.edit().putBoolean(KEY_DEV_UNLOCKED, true).apply()
            devUnlocked = true
            versionTaps = 0
            Toast.makeText(context, context.getString(R.string.about_dev_unlocked_toast), Toast.LENGTH_SHORT).show()
            onOpenDevOptions()
        }
    }

    val appInfo = remember {
        runCatching {
            val pm = context.packageManager
            val info = pm.getPackageInfo(context.packageName, 0)
            val code = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
                info.longVersionCode
            } else {
                @Suppress("DEPRECATED") info.versionCode.toLong()
            }
            val minSdk = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N) {
                info.applicationInfo?.minSdkVersion ?: Build.VERSION_CODES.P
            } else {
                Build.VERSION_CODES.P
            }
            AppInfo(
                name = info.versionName ?: "unknown",
                code = code,
                packageName = context.packageName,
                minSdk = minSdk,
            )
        }.getOrDefault(AppInfo("unknown", 0L, context.packageName, Build.VERSION_CODES.P))
    }

    val appIcon = remember { loadAppIconBitmap(context) }
    val webViewVersion = remember {
        runCatching {
            android.webkit.WebView.getCurrentWebViewPackage()?.versionName?.takeIf { it.isNotBlank() }
        }.getOrNull() ?: "--"
    }

    // 代码浏览器全屏覆盖
    if (showCodeBrowser) {
        CodeBrowserScreen(
            owner = GH_OWNER,
            repo = GH_REPO,
            branch = GH_BRANCH,
            onBack = { showCodeBrowser = false },
            viewModel = codeBrowserVM,
        )
        return
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(vertical = Spacing.lg),
        verticalArrangement = Arrangement.spacedBy(Spacing.lg)
    ) {
        // 1. HeroCard
        HeroCard(
            appName = stringResource(R.string.app_name),
            appIcon = appIcon,
            appInfo = appInfo,
            isDebug = BuildConfig.DEBUG,
            devUnlocked = devUnlocked,
            onVersionTap = { onVersionTap() },
        )

        // 2. UsageStatsSection（2 列，长按重置）
        UsageStatsSection(
            stats = stats,
            showResetMenu = showResetMenu,
            onDismissResetMenu = { showResetMenu = false },
            onRequestReset = { showResetMenu = false; showResetConfirm = true },
            onLongPress = { showResetMenu = true },
        )

        // 3. CoreComponentsSection（垂直列表，可点击）
        CoreComponentsSection(
            appVersion = appInfo.name,
            terminalReady = terminalReady,
            proxyRunning = proxyState.enabled && proxyState.controllerReachable,
            proxyPort = proxyState.mixedPort,
            webViewVersion = webViewVersion,
            onTerminalClick = {
                if (!terminalReady) pendingDialog = PendingDialog.TerminalNotReady
            },
            onProxyClick = {
                if (!(proxyState.enabled && proxyState.controllerReachable)) pendingDialog = PendingDialog.ProxyNotRunning
            },
            onBrowserClick = { showWebViewInfo = true },
            onHostClick = { showHostInfo = true },
        )

        // 4. UpdateEntryCard
        AppSectionHeader(title = stringResource(R.string.update_title))
        UpdateEntryCard(
            appInfo = appInfo,
            availability = updateAvailability,
            onClick = onOpenUpdate,
        )

        // 5. DeviceInfoSection（新增）
        DeviceInfoSection(appInfo = appInfo, webViewVersion = webViewVersion)

        // 6. LegalFeedbackSection（新增）
        val licenseUrl = stringResource(R.string.about_license_url)
        val contributingUrl = stringResource(R.string.about_contributing_url)
        val issuesUrl = stringResource(R.string.about_issues_url)
        LegalFeedbackSection(
            onOpenUserAgreement = { openUrl(context, licenseUrl) },
            onOpenPrivacy = { openUrl(context, licenseUrl) },
            onFeedback = { openFeedbackEmail(context) },
            onShare = { shareApp(context) },
            onOpenLicense = { openUrl(context, licenseUrl) },
            onOpenContributing = { openUrl(context, contributingUrl) },
        )

        // 开源信息卡片
        OpenSourceInfoSection(
            onBrowseSource = { showCodeBrowser = true },
            onSubmitIssue = { openUrl(context, issuesUrl) },
            onDownloadSource = { codeBrowserVM.downloadZip() },
        )

        // 7. OpenSourceCreditsSection（可展开）
        OpenSourceCreditsSection(expanded = expandedCredit, onExpand = { expandedCredit = it })

        // 版权底栏
        Text(
            text = stringResource(R.string.about_copyright),
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = Spacing.lg, vertical = Spacing.md)
        )

        Spacer(Modifier.height(Spacing.lg))
    }

    // ── 弹窗 ──────────────────────────────────────────────────────
    if (showResetConfirm) {
        AlertDialog(
            onDismissRequest = { showResetConfirm = false },
            title = { Text(stringResource(R.string.about_reset_stats_title)) },
            text = { Text(stringResource(R.string.about_reset_stats_msg)) },
            confirmButton = {
                TextButton(onClick = {
                    showResetConfirm = false
                    aboutVM.resetStats()
                    Toast.makeText(context, R.string.about_reset_stats_done, Toast.LENGTH_SHORT).show()
                }) { Text(stringResource(R.string.about_reset_stats_confirm)) }
            },
            dismissButton = {
                TextButton(onClick = { showResetConfirm = false }) { Text(stringResource(R.string.common_cancel)) }
            },
        )
    }

    pendingDialog?.let { dlg ->
        when (dlg) {
            PendingDialog.TerminalNotReady -> AlertDialog(
                onDismissRequest = { pendingDialog = null },
                title = { Text(stringResource(R.string.about_terminal_not_ready_title)) },
                text = { Text(stringResource(R.string.about_terminal_not_ready_msg)) },
                confirmButton = {
                    TextButton(onClick = { pendingDialog = null; onOpenContainerSettings() }) {
                        Text(stringResource(R.string.about_go_init))
                    }
                },
                dismissButton = {
                    TextButton(onClick = { pendingDialog = null }) { Text(stringResource(R.string.common_cancel)) }
                },
            )
            PendingDialog.ProxyNotRunning -> AlertDialog(
                onDismissRequest = { pendingDialog = null },
                title = { Text(stringResource(R.string.about_proxy_core)) },
                text = { Text(stringResource(R.string.about_proxy_not_running_msg)) },
                confirmButton = {
                    TextButton(onClick = { pendingDialog = null; onOpenProxySettings() }) {
                        Text(stringResource(R.string.about_go_init))
                    }
                },
                dismissButton = {
                    TextButton(onClick = { pendingDialog = null }) { Text(stringResource(R.string.common_cancel)) }
                },
            )
        }
    }

    if (showHostInfo) {
        AlertDialog(
            onDismissRequest = { showHostInfo = false },
            title = { Text(stringResource(R.string.about_host_info_dialog_title)) },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(Spacing.sm)) {
                    DialogRow(stringResource(R.string.about_host_info_abi), Build.SUPPORTED_ABIS.joinToString())
                    DialogRow(stringResource(R.string.about_host_info_sdk), "API ${Build.VERSION.SDK_INT}")
                    DialogRow(stringResource(R.string.about_device_android_version), Build.VERSION.RELEASE)
                }
            },
            confirmButton = { TextButton(onClick = { showHostInfo = false }) { Text(stringResource(R.string.common_close)) } },
        )
    }

    if (showWebViewInfo) {
        AlertDialog(
            onDismissRequest = { showWebViewInfo = false },
            title = { Text(stringResource(R.string.about_webview_dialog_title)) },
            text = {
                DialogRow(stringResource(R.string.about_webview_version_label), webViewVersion)
            },
            confirmButton = { TextButton(onClick = { showWebViewInfo = false }) { Text(stringResource(R.string.common_close)) } },
        )
    }
}

private enum class PendingDialog { TerminalNotReady, ProxyNotRunning }

@Composable
private fun DialogRow(label: String, value: String) {
    Row(horizontalArrangement = Arrangement.spacedBy(Spacing.sm)) {
        Text(label, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
        Text(value, style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.SemiBold)
    }
}

// ============================================================
// 1. Hero：软件介绍
// ============================================================

@Composable
private fun HeroCard(
    appName: String,
    appIcon: ImageBitmap?,
    appInfo: AppInfo,
    isDebug: Boolean,
    devUnlocked: Boolean,
    onVersionTap: () -> Unit,
) {
    Box(modifier = Modifier.padding(horizontal = Spacing.lg)) {
        AppCard {
            Column(modifier = Modifier.padding(Spacing.lg)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Box(
                        modifier = Modifier
                            .size(80.dp)
                            .clip(MaterialTheme.shapes.large)
                            .background(MaterialTheme.colorScheme.primaryContainer),
                        contentAlignment = Alignment.Center,
                    ) {
                        if (appIcon != null) {
                            Image(bitmap = appIcon, contentDescription = null, modifier = Modifier.size(64.dp))
                        }
                    }
                    Spacer(Modifier.width(Spacing.lg))
                    Column(verticalArrangement = Arrangement.spacedBy(Spacing.xs)) {
                        Text(
                            text = appName,
                            style = MaterialTheme.typography.headlineSmall,
                            fontWeight = FontWeight.ExtraBold,
                            color = MaterialTheme.colorScheme.onSurface,
                        )
                        Text(
                            text = stringResource(R.string.about_slogan),
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                        Spacer(Modifier.height(2.dp))
                        Row(
                            horizontalArrangement = Arrangement.spacedBy(Spacing.xs),
                            modifier = Modifier.clip(MaterialTheme.shapes.small).clickableSafe(onVersionTap),
                        ) {
                            HeroPill(text = "v${appInfo.name}", container = true)
                            HeroPill(text = "#${appInfo.code}")
                            VariantPill(isDebug = isDebug)
                            if (devUnlocked) {
                                HeroPill(text = stringResource(R.string.about_dev_options_label))
                            }
                        }
                    }
                }

                Spacer(Modifier.height(Spacing.md))
                HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
                Spacer(Modifier.height(Spacing.md))

                // 功能亮点标签行
                FlowRow(
                    horizontalArrangement = Arrangement.spacedBy(Spacing.sm),
                    verticalArrangement = Arrangement.spacedBy(Spacing.sm),
                ) {
                    FeatureChip(stringResource(R.string.about_feature_ai))
                    FeatureChip(stringResource(R.string.about_feature_terminal))
                    FeatureChip(stringResource(R.string.about_feature_browser))
                    FeatureChip(stringResource(R.string.about_feature_mcp))
                }
            }
        }
    }
}

@Composable
private fun FeatureChip(text: String) {
    Box(
        modifier = Modifier
            .clip(MaterialTheme.shapes.small)
            .background(MaterialTheme.colorScheme.surfaceVariant)
            .padding(horizontal = Spacing.sm, vertical = 4.dp),
    ) {
        Text(
            text = text,
            style = MaterialTheme.typography.labelMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            fontWeight = FontWeight.Medium,
        )
    }
}

@Composable
private fun HeroPill(text: String, container: Boolean = false) {
    val bg = if (container) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.surfaceVariant
    val fg = if (container) MaterialTheme.colorScheme.onPrimary else MaterialTheme.colorScheme.onSurfaceVariant
    Box(
        modifier = Modifier
            .clip(MaterialTheme.shapes.extraLarge)
            .background(bg)
            .padding(horizontal = 10.dp, vertical = 4.dp),
    ) {
        Text(text = text, style = MaterialTheme.typography.labelMedium, fontWeight = FontWeight.Bold, color = fg)
    }
}

@Composable
private fun VariantPill(isDebug: Boolean) {
    val bg = if (isDebug) MaterialTheme.colorScheme.errorContainer else MaterialTheme.colorScheme.secondaryContainer
    val fg = if (isDebug) MaterialTheme.colorScheme.onErrorContainer else MaterialTheme.colorScheme.onSecondaryContainer
    Box(
        modifier = Modifier
            .clip(MaterialTheme.shapes.extraLarge)
            .background(bg)
            .padding(horizontal = 10.dp, vertical = 4.dp),
    ) {
        Text(
            text = stringResource(
                if (isDebug) R.string.about_variant_debug else R.string.about_variant_release
            ),
            style = MaterialTheme.typography.labelMedium,
            fontWeight = FontWeight.Bold,
            color = fg,
        )
    }
}

// ============================================================
// 2. 使用统计（2 列，长按重置）
// ============================================================

@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun UsageStatsSection(
    stats: UsageStats,
    showResetMenu: Boolean,
    onDismissResetMenu: () -> Unit,
    onRequestReset: () -> Unit,
    onLongPress: () -> Unit,
) {
    Column(modifier = Modifier.padding(horizontal = Spacing.lg)) {
        AppSectionHeader(title = stringResource(R.string.about_stats))
        Box {
            AppCard(
                modifier = Modifier.combinedClickable(
                    onClick = {},
                    onLongClick = onLongPress,
                ),
            ) {
                Column(
                    modifier = Modifier.padding(Spacing.md),
                    verticalArrangement = Arrangement.spacedBy(Spacing.sm),
                ) {
                    StatRow(
                        cell1 = StatCell(Icons.Rounded.ChatBubble, stringResource(R.string.about_sessions), stats.totalSessions.toString()),
                        cell2 = StatCell(Icons.Rounded.Tag, stringResource(R.string.about_messages), stats.totalMessages.toString()),
                    )
                    StatRow(
                        cell1 = StatCell(Icons.Rounded.CalendarMonth, stringResource(R.string.about_active_days), stats.activeDays.toString()),
                        cell2 = StatCell(Icons.Rounded.CloudUpload, stringResource(R.string.about_input_tokens), compactNumber(stats.totalInputTokens)),
                    )
                    StatRow(
                        cell1 = StatCell(Icons.Rounded.CloudDownload, stringResource(R.string.about_output_tokens), compactNumber(stats.totalOutputTokens)),
                        cell2 = StatCell(
                            Icons.Rounded.Schedule,
                            stringResource(R.string.about_first_used),
                            if (stats.firstUsedMs > 0L) formatShortDate(stats.firstUsedMs) else "--",
                        ),
                    )
                }
            }
            DropdownMenu(expanded = showResetMenu, onDismissRequest = onDismissResetMenu) {
                DropdownMenuItem(
                    text = { Text(stringResource(R.string.about_reset_stats)) },
                    onClick = onRequestReset,
                )
            }
        }
    }
}

private data class StatCell(val icon: ImageVector, val label: String, val value: String)

@Composable
private fun StatRow(cell1: StatCell, cell2: StatCell) {
    Row(horizontalArrangement = Arrangement.spacedBy(Spacing.sm)) {
        StatCellBox(cell1, modifier = Modifier.weight(1f))
        StatCellBox(cell2, modifier = Modifier.weight(1f))
    }
}

@Composable
private fun StatCellBox(cell: StatCell, modifier: Modifier = Modifier) {
    Column(
        modifier = modifier
            .clip(MaterialTheme.shapes.large)
            .background(MaterialTheme.colorScheme.surfaceVariant)
            .padding(horizontal = Spacing.md, vertical = Spacing.md),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
            Icon(
                cell.icon,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.primary,
                modifier = Modifier.size(16.dp),
            )
            Text(
                cell.label,
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 1,
            )
        }
        Spacer(Modifier.height(Spacing.xs))
        Text(
            cell.value,
            style = MaterialTheme.typography.headlineSmall,
            fontWeight = FontWeight.ExtraBold,
            color = MaterialTheme.colorScheme.onSurface,
            maxLines = 1,
        )
    }
}

// ============================================================
// 3. 核心组件（垂直列表，可点击）
// ============================================================

private data class CoreEntry(val item: CoreItem, val onClick: () -> Unit)

private data class CoreItem(
    val icon: ImageVector,
    val name: String,
    val desc: String,
    val status: String,
    val statusOk: Boolean,
)

@Composable
private fun CoreComponentsSection(
    appVersion: String,
    terminalReady: Boolean,
    proxyRunning: Boolean,
    proxyPort: Int,
    webViewVersion: String,
    onTerminalClick: () -> Unit,
    onProxyClick: () -> Unit,
    onBrowserClick: () -> Unit,
    onHostClick: () -> Unit,
) {
    Column(modifier = Modifier.padding(horizontal = Spacing.lg)) {
        AppSectionHeader(
            title = stringResource(R.string.about_core_components),
            subtitle = stringResource(R.string.about_core_components_subtitle),
        )
        AppCard {
            val items = listOf(
                CoreEntry(
                    CoreItem(
                        icon = Icons.Rounded.Smartphone,
                        name = stringResource(R.string.about_host_core),
                        desc = stringResource(R.string.about_host_core_desc, appVersion),
                        status = stringResource(R.string.about_core_status_running),
                        statusOk = true,
                    ),
                    onHostClick,
                ),
                CoreEntry(
                    CoreItem(
                        icon = Icons.Rounded.Terminal,
                        name = stringResource(R.string.about_terminal_core),
                        desc = stringResource(R.string.about_terminal_core_desc),
                        status = if (terminalReady) stringResource(R.string.about_core_status_ready)
                        else stringResource(R.string.about_core_status_not_installed),
                        statusOk = terminalReady,
                    ),
                    onTerminalClick,
                ),
                CoreEntry(
                    CoreItem(
                        icon = Icons.Rounded.Public,
                        name = stringResource(R.string.about_proxy_core),
                        desc = stringResource(R.string.about_proxy_core_desc, ClashProxyManager.MIHOMO_VERSION) + " · :$proxyPort",
                        status = if (proxyRunning) stringResource(R.string.about_core_status_running)
                        else stringResource(R.string.about_core_status_stopped),
                        statusOk = proxyRunning,
                    ),
                    onProxyClick,
                ),
                CoreEntry(
                    CoreItem(
                        icon = Icons.Rounded.Language,
                        name = stringResource(R.string.about_browser_core),
                        desc = stringResource(R.string.about_browser_core_desc, webViewVersion),
                        status = stringResource(R.string.about_core_status_ready),
                        statusOk = true,
                    ),
                    onBrowserClick,
                ),
            )
            items.forEachIndexed { index, entry ->
                CoreRow(item = entry.item, onClick = entry.onClick)
                if (index < items.size - 1) {
                    HorizontalDivider(
                        thickness = 0.5.dp,
                        color = MaterialTheme.colorScheme.outlineVariant,
                        modifier = Modifier.padding(start = Spacing.lg + 38.dp + Spacing.md),
                    )
                }
            }
        }
    }
}

@Composable
private fun CoreRow(item: CoreItem, onClick: () -> Unit) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickableSafe(onClick)
            .padding(horizontal = Spacing.lg, vertical = Spacing.md),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(Spacing.md),
    ) {
        Box(
            modifier = Modifier
                .size(38.dp)
                .clip(MaterialTheme.shapes.medium)
                .background(MaterialTheme.colorScheme.primaryContainer),
            contentAlignment = Alignment.Center,
        ) {
            Icon(item.icon, contentDescription = null, tint = MaterialTheme.colorScheme.onPrimaryContainer, modifier = Modifier.size(20.dp))
        }
        Column(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
            Text(item.name, style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.SemiBold, color = MaterialTheme.colorScheme.onSurface)
            Text(item.desc, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant, maxLines = 2)
        }
        StatusPill(text = item.status, ok = item.statusOk)
    }
}

@Composable
private fun StatusPill(text: String, ok: Boolean) {
    val bg = if (ok) MaterialTheme.colorScheme.primaryContainer else MaterialTheme.colorScheme.surfaceVariant
    val fg = if (ok) MaterialTheme.colorScheme.onPrimaryContainer else MaterialTheme.colorScheme.onSurfaceVariant
    Row(
        modifier = Modifier
            .clip(MaterialTheme.shapes.extraLarge)
            .background(bg)
            .padding(horizontal = 8.dp, vertical = 3.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(4.dp),
    ) {
        Box(modifier = Modifier.size(6.dp).clip(CircleShape).background(fg))
        Text(text, style = MaterialTheme.typography.labelSmall, fontWeight = FontWeight.SemiBold, color = fg)
    }
}

// ============================================================
// 4. 版本更新入口
// ============================================================

@Composable
private fun UpdateEntryCard(
    appInfo: AppInfo,
    availability: UpdateAvailability,
    onClick: () -> Unit,
) {
    Box(modifier = Modifier.padding(horizontal = Spacing.lg)) {
        AppCard {
            AppListItem(
                icon = Icons.Rounded.SystemUpdate,
                title = stringResource(R.string.update_title),
                subtitle = when (availability) {
                    is UpdateAvailability.UpdateAvailable ->
                        stringResource(R.string.about_update_status_new, availability.latestTag.removePrefix("v"))
                    else -> stringResource(R.string.about_update_status_latest)
                },
                onClick = onClick,
                showDivider = false,
                trailing = {
                    when (availability) {
                        is UpdateAvailability.UpdateAvailable -> Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(Spacing.xs),
                        ) {
                            Box(
                                modifier = Modifier
                                    .size(8.dp)
                                    .clip(CircleShape)
                                    .background(MaterialTheme.colorScheme.error),
                            )
                            Text(
                                stringResource(R.string.about_update_button),
                                style = MaterialTheme.typography.labelMedium,
                                fontWeight = FontWeight.Bold,
                                color = MaterialTheme.colorScheme.error,
                            )
                        }
                        else -> Icon(
                            Icons.Rounded.History,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                },
            )
        }
    }
}

// ============================================================
// 5. 设备与应用信息（新增）
// ============================================================

@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun DeviceInfoSection(appInfo: AppInfo, webViewVersion: String) {
    val context = LocalContext.current
    val freeStorageMb = remember {
        runCatching {
            val stat = android.os.StatFs(Environment.getDataDirectory().absolutePath)
            stat.availableBlocksLong * stat.blockSizeLong
        }.getOrDefault(0L)
    }

    fun copyText(label: String, value: String) {
        val cm = context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
        cm.setPrimaryClip(ClipData.newPlainText(label, value))
        Toast.makeText(context, R.string.about_device_copied, Toast.LENGTH_SHORT).show()
    }

    Column(modifier = Modifier.padding(horizontal = Spacing.lg)) {
        AppSectionHeader(
            title = stringResource(R.string.about_device_info),
            subtitle = stringResource(R.string.about_device_long_press_copy),
        )
        AppCard {
            DeviceRow(stringResource(R.string.about_device_android_version), Build.VERSION.RELEASE) { copyText("android", Build.VERSION.RELEASE) }
            DeviceRow(stringResource(R.string.about_device_model), Build.MODEL) { copyText("model", Build.MODEL) }
            DeviceRow(stringResource(R.string.about_device_storage), formatSize(freeStorageMb)) { copyText("storage", formatSize(freeStorageMb)) }
            HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant, modifier = Modifier.padding(start = Spacing.lg))
            DeviceRow(stringResource(R.string.about_version), "v${appInfo.name}") { copyText("version", appInfo.name) }
            DeviceRow(stringResource(R.string.about_build_no), "#${appInfo.code}") { copyText("code", appInfo.code.toString()) }
            DeviceRow(stringResource(R.string.about_package), appInfo.packageName) { copyText("pkg", appInfo.packageName) }
            DeviceRow(stringResource(R.string.about_sdk_min), "API ${appInfo.minSdk}", showDivider = false) { copyText("sdk", appInfo.minSdk.toString()) }
        }
    }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun DeviceRow(label: String, value: String, showDivider: Boolean = true, onLongCopy: () -> Unit) {
    Column {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .combinedClickable(onClick = {}, onLongClick = onLongCopy)
                .padding(horizontal = Spacing.lg, vertical = Spacing.md),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(Spacing.md),
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Text(label, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                Text(value, style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.SemiBold, color = MaterialTheme.colorScheme.onSurface)
            }
        }
        if (showDivider) {
            HorizontalDivider(
                thickness = 0.5.dp,
                color = MaterialTheme.colorScheme.outlineVariant,
                modifier = Modifier.padding(start = Spacing.lg),
            )
        }
    }
}

// ============================================================
// 6. 法律与反馈（新增）
// ============================================================

@Composable
private fun LegalFeedbackSection(
    onOpenUserAgreement: () -> Unit,
    onOpenPrivacy: () -> Unit,
    onFeedback: () -> Unit,
    onShare: () -> Unit,
    onOpenLicense: () -> Unit,
    onOpenContributing: () -> Unit,
) {
    Column(modifier = Modifier.padding(horizontal = Spacing.lg)) {
        AppSectionHeader(title = stringResource(R.string.about_legal_feedback))
        AppCard {
            AppListItem(
                icon = Icons.Rounded.Description,
                title = stringResource(R.string.about_user_agreement),
                onClick = onOpenUserAgreement,
                showDivider = true,
            )
            AppListItem(
                icon = Icons.Rounded.Policy,
                title = stringResource(R.string.about_privacy_policy),
                onClick = onOpenPrivacy,
                showDivider = true,
            )
            AppListItem(
                icon = Icons.Rounded.MailOutline,
                title = stringResource(R.string.about_feedback_entry),
                onClick = onFeedback,
                showDivider = true,
            )
            AppListItem(
                icon = Icons.Rounded.Share,
                title = stringResource(R.string.about_share_app),
                onClick = onShare,
                showDivider = true,
            )
            AppListItem(
                icon = Icons.Rounded.Balance,
                title = stringResource(R.string.about_license_entry),
                subtitle = stringResource(R.string.about_license_gpl),
                onClick = onOpenLicense,
                showDivider = true,
            )
            AppListItem(
                icon = Icons.Rounded.Book,
                title = stringResource(R.string.about_contributing_entry),
                onClick = onOpenContributing,
                showDivider = false,
            )
        }
    }
}

// ============================================================
// 开源信息卡片
// ============================================================

@Composable
private fun OpenSourceInfoSection(
    onBrowseSource: () -> Unit,
    onSubmitIssue: () -> Unit,
    onDownloadSource: () -> Unit,
) {
    Column(modifier = Modifier.padding(horizontal = Spacing.lg)) {
        AppSectionHeader(title = stringResource(R.string.about_opensource))
        AppCard {
            Column(modifier = Modifier.padding(horizontal = Spacing.lg, vertical = Spacing.md)) {
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(Spacing.sm)) {
                    Icon(
                        Icons.Rounded.Balance,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.primary,
                    )
                    Text(
                        stringResource(R.string.about_license_gpl),
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.onSurface,
                    )
                }
                Spacer(Modifier.height(Spacing.xs))
                Text(
                    stringResource(R.string.about_opensource_desc),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
            AppListItem(
                icon = Icons.Rounded.Code,
                title = stringResource(R.string.about_source_code),
                subtitle = "$GH_OWNER/$GH_REPO",
                onClick = onBrowseSource,
                showDivider = true,
            )
            AppListItem(
                icon = Icons.Rounded.BugReport,
                title = stringResource(R.string.about_submit_issue),
                onClick = onSubmitIssue,
                showDivider = true,
            )
            AppListItem(
                icon = Icons.Rounded.FolderZip,
                title = stringResource(R.string.about_download_source),
                onClick = onDownloadSource,
                showDivider = false,
            )
        }
    }
}

// ============================================================
// 7. 开源致谢（可展开）
// ============================================================

private data class OpenSourceLib(
    val name: String,
    val author: String,
    val version: String,
    val license: String,
    val website: String,
)

@Composable
private fun OpenSourceCreditsSection(expanded: OpenSourceLib?, onExpand: (OpenSourceLib?) -> Unit) {
    Column(modifier = Modifier.padding(horizontal = Spacing.lg)) {
        AppSectionHeader(
            title = stringResource(R.string.about_credits),
            subtitle = stringResource(R.string.about_credits_subtitle),
        )

        val libs = listOf(
            OpenSourceLib(stringResource(R.string.about_credit_kotlin), "JetBrains", "2.2.21", "Apache-2.0", "https://kotlinlang.org"),
            OpenSourceLib(stringResource(R.string.about_credit_compose), "Google", "BOM 2025.12", "Apache-2.0", "https://developer.android.com/jetpack/compose"),
            OpenSourceLib(stringResource(R.string.about_credit_coroutines), "JetBrains", "1.10.x", "Apache-2.0", "https://github.com/Kotlin/kotlinx.coroutines"),
            OpenSourceLib(stringResource(R.string.about_credit_hilt), "Google", "2.56.1", "Apache-2.0", "https://dagger.dev/hilt"),
            OpenSourceLib(stringResource(R.string.about_credit_material), "Google", "M3", "Apache-2.0", "https://m3.material.io"),
            OpenSourceLib(stringResource(R.string.about_credit_okhttp), "Square", "4.12.0", "Apache-2.0", "https://square.github.io/okhttp"),
            OpenSourceLib(stringResource(R.string.about_credit_ktor), "JetBrains", "2.x", "Apache-2.0", "https://ktor.io"),
            OpenSourceLib(stringResource(R.string.about_credit_sqlite), "SQLDelight", "2.2.1", "Apache-2.0", "https://sqldelight.github.io/sqldelight"),
            OpenSourceLib(stringResource(R.string.about_credit_sqlcipher), "Zetetic", "4.x", "BSD-like", "https://www.zetetic.net/sqlcipher"),
            OpenSourceLib(stringResource(R.string.about_credit_coil), "Coil", "2.x", "Apache-2.0", "https://coil-kt.github.io/coil"),
            OpenSourceLib(stringResource(R.string.about_credit_accompanist), "Google", "0.34.x", "Apache-2.0", "https://github.com/google/accompanist"),
            OpenSourceLib(stringResource(R.string.about_credit_treesitter), "tree-sitter", "0.24.x", "MIT", "https://tree-sitter.github.io/tree-sitter"),
            OpenSourceLib(stringResource(R.string.about_credit_ssh), "SSHJ", "0.38.0", "Apache-2.0", "https://github.com/hierynomus/sshj"),
            OpenSourceLib(stringResource(R.string.about_credit_terminal), "Termux", "JNI", "GPL-3.0", "https://github.com/termux/termux-app"),
        )

        AppCard {
            FlowRow(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(Spacing.sm),
                horizontalArrangement = Arrangement.spacedBy(Spacing.sm),
                verticalArrangement = Arrangement.spacedBy(Spacing.sm),
            ) {
                libs.forEach { lib ->
                    CreditChip(lib = lib, expanded = expanded == lib, onClick = {
                        onExpand(if (expanded == lib) null else lib)
                    })
                }
            }
        }
    }
}

@Composable
private fun CreditChip(lib: OpenSourceLib, expanded: Boolean, onClick: () -> Unit) {
    val context = LocalContext.current
    Column {
        Row(
            modifier = Modifier
                .clip(MaterialTheme.shapes.medium)
                .background(MaterialTheme.colorScheme.surfaceVariant)
                .clickableSafe(onClick)
                .padding(horizontal = Spacing.sm, vertical = 6.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(6.dp),
        ) {
            Text(
                lib.name,
                style = MaterialTheme.typography.labelMedium,
                fontWeight = FontWeight.SemiBold,
                color = MaterialTheme.colorScheme.onSurface,
            )
            Text(
                "· ${lib.author}",
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        if (expanded) {
            Column(
                modifier = Modifier
                    .padding(top = Spacing.xs)
                    .background(MaterialTheme.colorScheme.surfaceVariant, MaterialTheme.shapes.medium)
                    .padding(Spacing.sm),
                verticalArrangement = Arrangement.spacedBy(4.dp),
            ) {
                ChipDetailRow(stringResource(R.string.about_credit_version_label), lib.version)
                ChipDetailRow(stringResource(R.string.about_credit_license_label), lib.license)
                Text(
                    stringResource(R.string.about_credit_website_label) + ": " + lib.website,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.clickableSafe { openUrl(context, lib.website) },
                )
            }
        }
    }
}

@Composable
private fun ChipDetailRow(label: String, value: String) {
    Row(horizontalArrangement = Arrangement.spacedBy(Spacing.xs)) {
        Text("$label:", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        Text(value, style = MaterialTheme.typography.bodySmall, fontWeight = FontWeight.Medium, color = MaterialTheme.colorScheme.onSurface)
    }
}

// ============================================================
// Helpers
// ============================================================

private fun Modifier.clickableSafe(onClick: () -> Unit): Modifier =
    this.clickable { onClick() }

private fun compactNumber(n: Long): String = when {
    n >= 1_000_000_000 -> "%.1fB".format(n / 1_000_000_000.0)
    n >= 1_000_000 -> "%.1fM".format(n / 1_000_000.0)
    n >= 10_000 -> "%.1fK".format(n / 1_000.0)
    else -> "%,d".format(n)
}

private fun formatShortDate(ms: Long): String {
    val fmt = SimpleDateFormat("yy/MM/dd", Locale.getDefault())
    return fmt.format(Date(ms))
}

private fun formatSize(bytes: Long): String = when {
    bytes <= 0L -> "--"
    bytes < 1024 -> "$bytes B"
    bytes < 1024 * 1024 -> "%.1f KB".format(bytes / 1024.0)
    bytes < 1024L * 1024 * 1024 -> "%.1f MB".format(bytes / (1024.0 * 1024))
    else -> "%.2f GB".format(bytes / (1024.0 * 1024 * 1024))
}

private fun openUrl(context: Context, url: String) {
    runCatching {
        val intent = Intent(Intent.ACTION_VIEW, Uri.parse(url))
        context.startActivity(intent)
    }.onFailure {
        Toast.makeText(context, R.string.about_open_failed, Toast.LENGTH_SHORT).show()
    }
}

private fun openFeedbackEmail(context: Context) {
    runCatching {
        val intent = Intent(Intent.ACTION_SENDTO).apply {
            data = Uri.parse("mailto:")
            putExtra(Intent.EXTRA_EMAIL, arrayOf("lisir2002@users.noreply.github.com"))
            putExtra(Intent.EXTRA_SUBJECT, "MiniMe-core Feedback")
        }
        context.startActivity(Intent.createChooser(intent, null))
    }.onFailure {
        openUrl(context, context.getString(R.string.about_issues_url))
    }
}

private fun shareApp(context: Context) {
    runCatching {
        val intent = Intent(Intent.ACTION_SEND).apply {
            type = "text/plain"
            putExtra(Intent.EXTRA_SUBJECT, context.getString(R.string.about_share_title))
            putExtra(Intent.EXTRA_TEXT, context.getString(R.string.about_share_text_fallback))
        }
        context.startActivity(Intent.createChooser(intent, null))
    }
}

@Suppress("DEPRECATION")
private fun loadAppIconBitmap(context: Context): ImageBitmap? {
    return runCatching {
        val pm = context.packageManager
        val appInfo = pm.getApplicationInfo(context.packageName, 0)
        val drawable: Drawable = pm.getApplicationIcon(appInfo)
        val bmp = Bitmap.createBitmap(
            drawable.intrinsicWidth.coerceAtLeast(1),
            drawable.intrinsicHeight.coerceAtLeast(1),
            Bitmap.Config.ARGB_8888
        )
        val canvas = Canvas(bmp)
        drawable.setBounds(0, 0, canvas.width, canvas.height)
        drawable.draw(canvas)
        bmp.asImageBitmap()
    }.getOrNull()
}

// ============================================================
// Data classes
// ============================================================

private data class AppInfo(
    val name: String,
    val code: Long,
    val packageName: String,
    val minSdk: Int,
)
