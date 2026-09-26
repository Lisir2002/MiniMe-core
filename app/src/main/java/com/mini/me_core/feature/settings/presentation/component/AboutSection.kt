package com.mini.me_core.feature.settings.presentation.component
import com.mini.me_core.core.theme.tokens.LocalCornerRadius

import android.content.Context
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.drawable.Drawable
import android.os.Build
import android.widget.Toast
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
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
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.CalendarMonth
import androidx.compose.material.icons.rounded.ChatBubble
import androidx.compose.material.icons.rounded.CloudDownload
import androidx.compose.material.icons.rounded.CloudUpload
import androidx.compose.material.icons.rounded.History
import androidx.compose.material.icons.rounded.Language
import androidx.compose.material.icons.rounded.Public
import androidx.compose.material.icons.rounded.Schedule
import androidx.compose.material.icons.rounded.Smartphone
import androidx.compose.material.icons.rounded.SystemUpdate
import androidx.compose.material.icons.rounded.Tag
import androidx.compose.material.icons.rounded.Terminal
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
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
import com.mini.me_core.core.theme.LocalAppDarkMode
import com.mini.me_core.core.theme.Radius
import com.mini.me_core.core.theme.Spacing
import com.mini.me_core.core.theme.components.AppCard
import com.mini.me_core.core.theme.components.AppSectionHeader
import com.mini.me_core.core.theme.components.AppListItem
import com.mini.me_core.feature.proxy.domain.ClashProxyManager
import com.mini.me_core.feature.settings.presentation.AboutStatsViewModel
import com.mini.me_core.feature.settings.presentation.UsageStats
import com.mini.me_core.feature.update.domain.UpdateAvailability
import com.mini.me_core.feature.update.presentation.UpdateBadgeViewModel

// ============================================================
// Entry point
// ============================================================

/** 关于页语义色：Light/Dark 双值，由 LocalAppDarkMode 决定当前取值。 */
private data class AboutColors(
    val bg: Color,
    val card: Color,
    val border: Color,
    val title: Color,
    val subtitle: Color,
    val desc: Color,
    val faint: Color,
    val iconGray: Color,
    val heroBgStart: Color,
    val heroBgEnd: Color,
    val heroBorder: Color,
    val blueBg: Color,
    val blueBorder: Color,
    val blueAccent: Color,
    val greenBg: Color,
    val greenAccent: Color,
    val greenText: Color,
    val amberBg: Color,
    val amberAccent: Color,
    val skyBg: Color,
    val skyAccent: Color,
    val orangeBg: Color,
    val orangeBorder: Color,
    val orangeText: Color,
    val selectedBg: Color,
    val selectedBorder: Color,
    val selectedText: Color,
    val chipBg: Color,
    val statGrayBg: Color
)

@Composable
private fun aboutColors(): AboutColors {
    val dark = LocalAppDarkMode.current
    val theme = com.mini.me_core.core.theme.tokens.LocalAppTheme.current
    val sem = theme.colors
    fun c(light: Color, darkV: Color) = if (dark) darkV else light
    return AboutColors(
        bg = c(Color.White, Color(0xFF0D1B2E)),
        card = c(Color.White, Color(0xFF0D1B2E)),
        border = c(Color(0xFFE4E7EC), Color(0xFF223B57)),
        title = c(Color(0xFF101828), Color(0xFFEAF2FF)),
        subtitle = c(Color(0xFF475467), Color(0xFFB8C7DA)),
        desc = c(Color(0xFF667085), Color(0xFF8FA3BF)),
        faint = c(Color(0xFF98A2B3), Color(0xFF6E829C)),
        iconGray = c(Color(0xFF344054), Color(0xFFB8C7DA)),
        heroBgStart = c(Color(0xFFEFF4FF), Color(0xFF13273F)),
        heroBgEnd = c(Color(0xFFE3EDFF), Color(0xFF0F3A63)),
        heroBorder = c(Color(0xFFD6E4FF), Color(0xFF223B57)),
        blueBg = sem.infoContainer,
        blueBorder = c(Color(0xFFBFDBFE), Color(0xFF1E4E8C)),
        blueAccent = sem.info,
        greenBg = c(Color(0xFFECFDF5), Color(0xFF0B3B2E)),
        greenAccent = c(Color(0xFF059669), Color(0xFF34D399)),
        greenText = c(Color(0xFF047857), Color(0xFF6EE7B7)),
        amberBg = c(Color(0xFFFFFBEB), Color(0xFF451A03)),
        amberAccent = sem.warning,
        skyBg = sem.skyContainer,
        skyAccent = sem.sky,
        orangeBg = sem.orangeContainer,
        orangeBorder = c(Color(0xFFFFD6A5), Color(0xFF9A3412)),
        orangeText = sem.orange,
        selectedBg = c(Color(0xFFF0F6FF), Color(0xFF0F3A63)),
        selectedBorder = c(Color(0xFFBFDBFE), Color(0xFF1E4E8C)),
        selectedText = sem.brandPrimary,
        chipBg = sem.brandContainer,
        statGrayBg = c(Color(0xFFF2F3F5), Color(0xFF13273F))
    )
}

/** 关于页 SharedPreferences 名称。 */
private const val PREFS_ABOUT = "minime_about_prefs"

/** 开发者选项解锁状态的持久化 key。 */
private const val KEY_DEV_UNLOCKED = "dev_options_unlocked"

/** 连续点击版本号解锁开发者选项所需次数。 */
private const val DEV_TAP_THRESHOLD = 7

@Composable
internal fun AboutSection(
    onOpenDevOptions: () -> Unit = {},
    onOpenUpdate: () -> Unit = {},
) {
    val context = LocalContext.current
    val aboutVM: AboutStatsViewModel = hiltViewModel()
    val stats by aboutVM.stats.collectAsStateWithLifecycle()
    val proxyState by aboutVM.proxyState.collectAsStateWithLifecycle()
    val terminalReady by aboutVM.terminalReady.collectAsStateWithLifecycle()
    val updateBadgeVM: UpdateBadgeViewModel = hiltViewModel()
    val updateAvailability by updateBadgeVM.availability.collectAsStateWithLifecycle()

    // F5.6：连续点击版本号 7 次解锁开发者选项，解锁状态持久化
    val prefs = remember { context.getSharedPreferences(PREFS_ABOUT, Context.MODE_PRIVATE) }
    var devUnlocked by remember { mutableStateOf(prefs.getBoolean(KEY_DEV_UNLOCKED, false)) }
    var versionTaps by remember { mutableStateOf(0) }

    fun onVersionTap() {
        if (devUnlocked) {
            onOpenDevOptions()
            return
        }
        versionTaps++
        val remaining = DEV_TAP_THRESHOLD - versionTaps
        if (remaining > 0) {
            Toast.makeText(context, "再点 $remaining 次解锁开发者选项", Toast.LENGTH_SHORT).show()
        } else {
            prefs.edit().putBoolean(KEY_DEV_UNLOCKED, true).apply()
            devUnlocked = true
            versionTaps = 0
            Toast.makeText(context, "开发者选项已解锁", Toast.LENGTH_SHORT).show()
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
                @Suppress("DEPRECATED")
                info.versionCode.toLong()
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
                minSdk = minSdk
            )
        }.getOrDefault(AppInfo("unknown", 0L, context.packageName, Build.VERSION_CODES.P))
    }

    val appIcon = remember { loadAppIconBitmap(context) }
    val webViewVersion = remember {
        runCatching {
            android.webkit.WebView.getCurrentWebViewPackage()?.versionName?.takeIf { it.isNotBlank() }
        }.getOrNull() ?: "--"
    }
    val ac = aboutColors()

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(ac.bg)
            .verticalScroll(rememberScrollState())
            .padding(vertical = Spacing.lg),
        verticalArrangement = Arrangement.spacedBy(Spacing.lg)
    ) {
        // ===== 模块 1：软件介绍（Hero，独立卡片） =====
        HeroCard(
            appName = stringResource(R.string.app_name),
            appIcon = appIcon,
            appInfo = appInfo,
            isDebug = BuildConfig.DEBUG,
            devUnlocked = devUnlocked,
            onVersionTap = { onVersionTap() },
            ac = ac
        )

        // ===== 模块 2：核心组件（宿主 / 终端 / 代理 / 浏览器） =====
        CoreComponentsSection(
            appVersion = appInfo.name,
            terminalReady = terminalReady,
            proxyRunning = proxyState.enabled && proxyState.controllerReachable,
            proxyPort = proxyState.mixedPort,
            webViewVersion = webViewVersion,
            ac = ac
        )

        // ===== 模块 3：使用统计（独立卡片） =====
        UsageStatsSection(stats = stats, ac = ac)

        // ===== 模块 4：版本更新入口（跳转独立双 Tab 页面） =====
        AppSectionHeader(title = stringResource(R.string.update_title))
        UpdateEntryCard(
            appInfo = appInfo,
            availability = updateAvailability,
            onClick = onOpenUpdate,
            ac = ac
        )

        // ===== 模块 5：开源致谢 =====
        OpenSourceCreditsSection(ac = ac)

        // 版权底栏
        Text(
            text = stringResource(R.string.about_copyright),
            style = MaterialTheme.typography.bodySmall,
            color = ac.faint,
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = Spacing.lg, vertical = Spacing.md)
        )

        Spacer(Modifier.height(Spacing.lg))
    }
}

// ============================================================
// 1. Hero：软件介绍（App 图标 + 名称 + 版本信息）
// ============================================================

@Composable
private fun HeroCard(
    appName: String,
    appIcon: ImageBitmap?,
    appInfo: AppInfo,
    isDebug: Boolean,
    devUnlocked: Boolean,
    onVersionTap: () -> Unit,
    ac: AboutColors
) {
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = Spacing.lg)
    ) {
        AppCard {
            Column {
                Spacer(Modifier.height(Spacing.lg))

                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = Spacing.md),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    // 左：App Icon（浅冰蓝渐变底，圆角大方块）
                    Box(
                        modifier = Modifier
                            .size(92.dp)
                            .clip(RoundedCornerShape(LocalCornerRadius.current.map(24.dp)))
                            .background(
                                Brush.linearGradient(
                                    listOf(ac.heroBgStart, ac.heroBgEnd)
                                )
                            )
                            .border(
                                border = BorderStroke(0.8.dp, ac.heroBorder),
                                shape = RoundedCornerShape(LocalCornerRadius.current.map(24.dp))
                            )
                            .padding(com.mini.me_core.core.theme.tokens.PrimitiveSpacing.Md),
                        contentAlignment = Alignment.Center
                    ) {
                        if (appIcon != null) {
                            Image(
                                bitmap = appIcon,
                                contentDescription = null,
                                modifier = Modifier.fillMaxSize()
                            )
                        }
                    }

                    Spacer(Modifier.width(Spacing.lg))

                    // 右：名称 + 口号 + 版本胶囊
                    Column(
                        modifier = Modifier.weight(1f),
                        verticalArrangement = Arrangement.spacedBy(Spacing.xs)
                    ) {
                        Text(
                            text = appName,
                            style = MaterialTheme.typography.headlineSmall,
                            fontWeight = FontWeight.ExtraBold,
                            color = ac.title
                        )
                        Text(
                            text = stringResource(R.string.about_slogan),
                            style = MaterialTheme.typography.bodyMedium,
                            color = ac.subtitle,
                            fontWeight = FontWeight.Medium
                        )
                        Spacer(Modifier.height(2.dp))
                        Row(
                            horizontalArrangement = Arrangement.spacedBy(Spacing.xs),
                            modifier = Modifier.clickable { onVersionTap() }
                        ) {
                            VersionPill(text = "v${appInfo.name}", ac = ac)
                            VersionPill(text = "#${appInfo.code}", outline = true, ac = ac)
                            VariantPill(isDebug = isDebug, ac = ac)
                            if (devUnlocked) {
                                VersionPill(text = "开发者选项", ac = ac)
                            }
                        }
                    }
                }

                Spacer(Modifier.height(Spacing.lg))

                HorizontalDivider(
                    thickness = 0.8.dp,
                    color = ac.border,
                    modifier = Modifier.padding(horizontal = Spacing.md)
                )

                // 版本信息 2×2（浅灰纸感 chip）
                Spacer(Modifier.height(Spacing.md))
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = Spacing.md),
                    horizontalArrangement = Arrangement.spacedBy(Spacing.md)
                ) {
                    Column(
                        modifier = Modifier.weight(1f),
                        verticalArrangement = Arrangement.spacedBy(Spacing.xs)
                    ) {
                        HeroInfoChip(
                            label = stringResource(R.string.about_version),
                            value = "v${appInfo.name}",
                            ac = ac
                        )
                        HeroInfoChip(
                            label = stringResource(R.string.about_sdk_min),
                            value = "API ${appInfo.minSdk}",
                            ac = ac
                        )
                        HeroInfoChip(
                            label = stringResource(R.string.about_variant),
                            value = stringResource(
                                if (isDebug) R.string.about_variant_debug
                                else R.string.about_variant_release
                            ),
                            ac = ac
                        )
                    }
                    Column(
                        modifier = Modifier.weight(1f),
                        verticalArrangement = Arrangement.spacedBy(Spacing.xs)
                    ) {
                        HeroInfoChip(
                            label = stringResource(R.string.about_build_no),
                            value = "#${appInfo.code}",
                            ac = ac
                        )
                        HeroInfoChip(
                            label = stringResource(R.string.about_package),
                            value = appInfo.packageName,
                            ac = ac
                        )
                        HeroInfoChip(
                            label = stringResource(R.string.about_author_title),
                            value = stringResource(R.string.about_author),
                            ac = ac
                        )
                    }
                }
                Spacer(Modifier.height(Spacing.lg))
            }
        }
    }
}

@Composable
private fun VersionPill(text: String, outline: Boolean = false, ac: AboutColors) {
    val shape = RoundedCornerShape(LocalCornerRadius.current.pill)
    val bgModifier = if (outline) {
        Modifier.background(ac.card, shape)
    } else {
        Modifier.background(
            Brush.horizontalGradient(listOf(ac.blueAccent, ac.skyAccent)),
            shape
        )
    }
    Row(
        modifier = Modifier
            .clip(shape)
            .then(bgModifier)
            .border(
                border = BorderStroke(
                    0.8.dp,
                    if (outline) ac.border else Color.Transparent
                ),
                shape = shape
            )
            .padding(horizontal = 10.dp, vertical = 4.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(4.dp)
    ) {
        Text(
            text = text,
            style = MaterialTheme.typography.labelMedium,
            fontWeight = FontWeight.Bold,
            color = if (outline) ac.subtitle else Color.White
        )
    }
}

/**
 * 构建变体胶囊：Debug 版（com.mini.me_core.debug）与 Release 版（com.mini.me_core）数据目录相互隔离。
 * 展示在版本号旁，提醒用户勿混装 debug/release 包导致"历史对话消失"。
 */
@Composable
private fun VariantPill(isDebug: Boolean, ac: AboutColors) {
    val shape = RoundedCornerShape(LocalCornerRadius.current.pill)
    Row(
        modifier = Modifier
            .clip(shape)
            .background(if (isDebug) ac.amberBg else ac.blueBg)
            .border(
                border = BorderStroke(
                    0.8.dp,
                    if (isDebug) ac.orangeBorder else ac.blueBorder
                ),
                shape = shape
            )
            .padding(horizontal = 10.dp, vertical = 4.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(
            text = stringResource(
                if (isDebug) R.string.about_variant_debug else R.string.about_variant_release
            ),
            style = MaterialTheme.typography.labelMedium,
            fontWeight = FontWeight.Bold,
            color = if (isDebug) ac.orangeText else ac.blueAccent
        )
    }
}

@Composable
private fun HeroInfoChip(
    label: String,
    value: String,
    ac: AboutColors
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(LocalCornerRadius.current.md))
            .background(ac.statGrayBg)
            .border(
                border = BorderStroke(0.8.dp, ac.border),
                shape = RoundedCornerShape(LocalCornerRadius.current.md)
            )
            .padding(horizontal = Spacing.sm, vertical = 7.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(Spacing.xs)
    ) {
        Text(
            text = "$label:",
            style = MaterialTheme.typography.bodySmall,
            color = ac.desc,
            fontWeight = FontWeight.Medium
        )
        Text(
            text = value,
            style = MaterialTheme.typography.bodySmall,
            fontWeight = FontWeight.SemiBold,
            color = ac.title
        )
    }
}

// ============================================================
// 2. 核心组件：宿主 / 终端 / 代理 / 浏览器（Bento 2×2）
// ============================================================

private data class CoreItem(
    val icon: ImageVector,
    val accent: Color,
    val accentBg: Color,
    val name: String,
    val desc: String,
    val status: String,
    val statusOk: Boolean
)

@Composable
private fun CoreComponentsSection(
    appVersion: String,
    terminalReady: Boolean,
    proxyRunning: Boolean,
    proxyPort: Int,
    webViewVersion: String,
    ac: AboutColors
) {
    Column(
        modifier = Modifier.padding(horizontal = Spacing.lg),
        verticalArrangement = Arrangement.spacedBy(Spacing.md)
    ) {
        AppSectionHeader(title = stringResource(R.string.about_core_components))
        Text(
            text = stringResource(R.string.about_core_components_subtitle),
            style = MaterialTheme.typography.bodySmall,
            color = ac.desc,
            modifier = Modifier.padding(start = Spacing.lg + 4.dp, bottom = Spacing.xs)
        )

        val cores = listOf(
            CoreItem(
                icon = Icons.Rounded.Smartphone,
                accent = ac.blueAccent,
                accentBg = ac.blueBg,
                name = stringResource(R.string.about_host_core),
                desc = stringResource(R.string.about_host_core_desc, appVersion),
                status = stringResource(R.string.about_core_status_running),
                statusOk = true
            ),
            CoreItem(
                icon = Icons.Rounded.Terminal,
                accent = ac.greenAccent,
                accentBg = ac.greenBg,
                name = stringResource(R.string.about_terminal_core),
                desc = stringResource(R.string.about_terminal_core_desc),
                status = if (terminalReady) {
                    stringResource(R.string.about_core_status_ready)
                } else {
                    stringResource(R.string.about_core_status_not_installed)
                },
                statusOk = terminalReady
            ),
            CoreItem(
                icon = Icons.Rounded.Public,
                accent = ac.amberAccent,
                accentBg = ac.amberBg,
                name = stringResource(R.string.about_proxy_core),
                desc = stringResource(
                    R.string.about_proxy_core_desc,
                    ClashProxyManager.MIHOMO_VERSION
                ) + " · :$proxyPort",
                status = if (proxyRunning) {
                    stringResource(R.string.about_core_status_running)
                } else {
                    stringResource(R.string.about_core_status_stopped)
                },
                statusOk = proxyRunning
            ),
            CoreItem(
                icon = Icons.Rounded.Language,
                accent = ac.skyAccent,
                accentBg = ac.skyBg,
                name = stringResource(R.string.about_browser_core),
                desc = stringResource(R.string.about_browser_core_desc, webViewVersion),
                status = stringResource(R.string.about_core_status_ready),
                statusOk = true
            )
        )

        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(Spacing.sm)
        ) {
            CoreComponentCard(item = cores[0], modifier = Modifier.weight(1f), ac = ac)
            CoreComponentCard(item = cores[1], modifier = Modifier.weight(1f), ac = ac)
        }
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(Spacing.sm)
        ) {
            CoreComponentCard(item = cores[2], modifier = Modifier.weight(1f), ac = ac)
            CoreComponentCard(item = cores[3], modifier = Modifier.weight(1f), ac = ac)
        }
    }
}

@Composable
private fun CoreComponentCard(item: CoreItem, modifier: Modifier = Modifier, ac: AboutColors) {
    Card(
        modifier = modifier,
        shape = RoundedCornerShape(LocalCornerRadius.current.lg),
        colors = CardDefaults.cardColors(containerColor = ac.card),
        border = BorderStroke(0.8.dp, ac.border),
        elevation = CardDefaults.cardElevation(defaultElevation = 0.dp)
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(Spacing.md),
            verticalArrangement = Arrangement.spacedBy(Spacing.sm)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Box(
                    modifier = Modifier
                        .size(38.dp)
                        .clip(RoundedCornerShape(LocalCornerRadius.current.md))
                        .background(item.accentBg),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        imageVector = item.icon,
                        contentDescription = null,
                        tint = item.accent,
                        modifier = Modifier.size(20.dp)
                    )
                }
                Spacer(Modifier.weight(1f))
                StatusPill(text = item.status, ok = item.statusOk, ac = ac)
            }

            Text(
                text = item.name,
                style = MaterialTheme.typography.titleSmall,
                fontWeight = FontWeight.SemiBold,
                color = ac.title
            )
            Text(
                text = item.desc,
                style = MaterialTheme.typography.labelSmall,
                color = ac.desc,
                maxLines = 2
            )
        }
    }
}

@Composable
private fun StatusPill(text: String, ok: Boolean, ac: AboutColors) {
    Row(
        modifier = Modifier
            .clip(RoundedCornerShape(LocalCornerRadius.current.pill))
            .background(if (ok) ac.greenBg else ac.statGrayBg)
            .padding(horizontal = 8.dp, vertical = 3.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(4.dp)
    ) {
        Box(
            modifier = Modifier
                .size(6.dp)
                .clip(CircleShape)
                .background(if (ok) ac.greenAccent else ac.faint)
        )
        Text(
            text = text,
            style = MaterialTheme.typography.labelSmall,
            fontWeight = FontWeight.SemiBold,
            color = if (ok) ac.greenText else ac.desc
        )
    }
}

// ============================================================
// 3. 使用统计（独立卡片，大数字 + 渐变强调）
// ============================================================

@Composable
private fun UsageStatsSection(stats: UsageStats, ac: AboutColors) {
    Column(
        modifier = Modifier.padding(horizontal = Spacing.lg),
        verticalArrangement = Arrangement.spacedBy(Spacing.sm)
    ) {
        AppSectionHeader(title = stringResource(R.string.about_stats))
        Text(
            text = stringResource(R.string.about_stats_subtitle),
            style = MaterialTheme.typography.bodySmall,
            color = ac.desc,
            modifier = Modifier.padding(start = Spacing.lg + 4.dp, bottom = Spacing.xs)
        )

        AppCard {
            Column(
                modifier = Modifier.padding(Spacing.md),
                verticalArrangement = Arrangement.spacedBy(Spacing.sm)
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(Spacing.sm)
                ) {
                    UsageStatCell(
                        icon = Icons.Rounded.ChatBubble,
                        label = stringResource(R.string.about_sessions),
                        value = stats.totalSessions.toString(),
                        unit = stringResource(R.string.about_stats_unit_sessions),
                        modifier = Modifier.weight(1f),
                        ac = ac
                    )
                    UsageStatCell(
                        icon = Icons.Rounded.Tag,
                        label = stringResource(R.string.about_messages),
                        value = stats.totalMessages.toString(),
                        unit = stringResource(R.string.about_stats_unit_messages),
                        modifier = Modifier.weight(1f),
                        ac = ac
                    )
                    UsageStatCell(
                        icon = Icons.Rounded.CalendarMonth,
                        label = stringResource(R.string.about_active_days),
                        value = stats.activeDays.toString(),
                        unit = stringResource(R.string.about_stats_unit_days),
                        modifier = Modifier.weight(1f),
                        highlighted = true,
                        ac = ac
                    )
                }

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(Spacing.sm)
                ) {
                    UsageStatCell(
                        icon = Icons.Rounded.CloudUpload,
                        label = stringResource(R.string.about_input_tokens),
                        value = compactNumber(stats.totalInputTokens),
                        unit = stringResource(R.string.about_stats_unit_tokens),
                        modifier = Modifier.weight(1f),
                        ac = ac
                    )
                    UsageStatCell(
                        icon = Icons.Rounded.CloudDownload,
                        label = stringResource(R.string.about_output_tokens),
                        value = compactNumber(stats.totalOutputTokens),
                        unit = stringResource(R.string.about_stats_unit_tokens),
                        modifier = Modifier.weight(1f),
                        ac = ac
                    )
                    UsageStatCell(
                        icon = Icons.Rounded.Schedule,
                        label = stringResource(R.string.about_first_used),
                        value = if (stats.firstUsedMs > 0L) formatShortDate(stats.firstUsedMs) else "--",
                        unit = if (stats.firstUsedMs > 0L) stringResource(R.string.about_stats_unit_since) else "",
                        modifier = Modifier.weight(1f),
                        compactValue = true,
                        gradient = false,
                        ac = ac
                    )
                }
            }
        }
    }
}

@Composable
private fun UsageStatCell(
    icon: ImageVector,
    label: String,
    value: String,
    unit: String,
    modifier: Modifier = Modifier,
    compactValue: Boolean = false,
    highlighted: Boolean = false,
    gradient: Boolean = true,
    ac: AboutColors
) {
    Card(
        modifier = modifier,
        shape = RoundedCornerShape(LocalCornerRadius.current.lg),
        colors = CardDefaults.cardColors(
            containerColor = if (highlighted) ac.selectedBg else ac.card
        ),
        border = BorderStroke(
            0.8.dp,
            if (highlighted) ac.selectedBorder else ac.border
        ),
        elevation = CardDefaults.cardElevation(defaultElevation = 0.dp)
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = Spacing.sm, vertical = Spacing.md),
            verticalArrangement = Arrangement.spacedBy(Spacing.xs)
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(6.dp)
            ) {
                Box(
                    modifier = Modifier
                        .size(18.dp)
                        .clip(CircleShape)
                        .background(if (highlighted) ac.chipBg else ac.statGrayBg),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        imageVector = icon,
                        contentDescription = null,
                        tint = if (highlighted) ac.selectedText else ac.iconGray,
                        modifier = Modifier.size(11.dp)
                    )
                }
                Text(
                    text = label,
                    style = MaterialTheme.typography.labelSmall,
                    color = ac.desc,
                    fontWeight = FontWeight.Medium,
                    maxLines = 1
                )
            }

            Spacer(Modifier.height(2.dp))

            Row(
                verticalAlignment = Alignment.Bottom,
                horizontalArrangement = Arrangement.spacedBy(3.dp)
            ) {
                val baseStyle = if (compactValue) {
                    MaterialTheme.typography.titleMedium
                } else {
                    MaterialTheme.typography.headlineSmall
                }
                Text(
                    text = value,
                    style = baseStyle.copy(
                        brush = if (gradient) {
                            Brush.horizontalGradient(listOf(ac.blueAccent, ac.skyAccent))
                        } else {
                            Brush.horizontalGradient(listOf(ac.title, ac.title))
                        },
                        fontWeight = FontWeight.ExtraBold
                    ),
                    maxLines = 1
                )
                if (unit.isNotEmpty()) {
                    Text(
                        text = unit,
                        style = MaterialTheme.typography.labelMedium,
                        fontWeight = FontWeight.SemiBold,
                        color = ac.desc,
                        modifier = Modifier.padding(bottom = 2.dp)
                    )
                }
            }
        }
    }
}

private fun compactNumber(n: Long): String = when {
    n >= 1_000_000_000 -> "%.1fB".format(n / 1_000_000_000.0)
    n >= 1_000_000 -> "%.1fM".format(n / 1_000_000.0)
    n >= 10_000 -> "%.1fK".format(n / 1_000.0)
    else -> "%,d".format(n)
}

private fun formatShortDate(ms: Long): String {
    val fmt = java.text.SimpleDateFormat("yy/MM/dd", java.util.Locale.getDefault())
    return fmt.format(java.util.Date(ms))
}

// ============================================================
// 4. 版本更新入口（跳转独立双 Tab 页面，不再在此弹窗）
// ============================================================

@Composable
private fun UpdateEntryCard(
    appInfo: AppInfo,
    availability: UpdateAvailability,
    onClick: () -> Unit,
    ac: AboutColors
) {
    Column(
        modifier = Modifier.padding(horizontal = Spacing.lg),
        verticalArrangement = Arrangement.spacedBy(Spacing.md)
    ) {
        AppCard {
            AppListItem(
                icon = Icons.Rounded.SystemUpdate,
                title = stringResource(R.string.update_title),
                subtitle = stringResource(R.string.update_entry_subtitle, appInfo.name),
                onClick = onClick,
                showDivider = false,
                trailing = {
                    when (availability) {
                        is UpdateAvailability.UpdateAvailable -> {
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(Spacing.xs),
                            ) {
                                // 红点
                                Box(
                                    modifier = Modifier
                                        .size(8.dp)
                                        .clip(CircleShape)
                                        .background(ac.greenAccent)
                                )
                                Text(
                                    text = stringResource(R.string.update_badge_new),
                                    style = MaterialTheme.typography.labelMedium,
                                    fontWeight = FontWeight.Bold,
                                    color = ac.greenText,
                                )
                            }
                        }
                        else -> Icon(
                            imageVector = Icons.Rounded.History,
                            contentDescription = null,
                            tint = ac.faint,
                        )
                    }
                }
            )
        }
    }
}

// ============================================================
// 5. Open Source Credits Section（扁平 chip）
// ============================================================

@Composable
private fun OpenSourceCreditsSection(ac: AboutColors) {
    AppSectionHeader(title = stringResource(R.string.about_credits))

    Column(
        modifier = Modifier.padding(horizontal = Spacing.lg),
        verticalArrangement = Arrangement.spacedBy(Spacing.md)
    ) {
        Text(
            text = stringResource(R.string.about_credits_subtitle),
            style = MaterialTheme.typography.bodySmall,
            color = ac.desc,
            modifier = Modifier.padding(start = 4.dp)
        )

        val credits = listOf(
            R.string.about_credit_kotlin to "JetBrains",
            R.string.about_credit_compose to "Google",
            R.string.about_credit_coroutines to "JetBrains",
            R.string.about_credit_hilt to "Google",
            R.string.about_credit_room to "Google",
            R.string.about_credit_material to "Google",
            R.string.about_credit_okhttp to "Square",
            R.string.about_credit_ktor to "JetBrains"
        )

        AppCard {
            FlowRow(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(Spacing.sm),
                horizontalArrangement = Arrangement.spacedBy(Spacing.sm),
                verticalArrangement = Arrangement.spacedBy(Spacing.sm)
            ) {
                credits.forEach { (nameRes, author) ->
                    CreditChip(
                        name = stringResource(nameRes),
                        author = author,
                        ac = ac
                    )
                }
            }
        }
    }
}

@Composable
private fun CreditChip(name: String, author: String, ac: AboutColors) {
    Row(
        modifier = Modifier
            .clip(RoundedCornerShape(LocalCornerRadius.current.md))
            .background(ac.statGrayBg)
            .border(
                border = BorderStroke(0.8.dp, ac.border),
                shape = RoundedCornerShape(LocalCornerRadius.current.md)
            )
            .padding(horizontal = Spacing.sm, vertical = 6.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(6.dp)
    ) {
        Box(
            modifier = Modifier
                .size(6.dp)
                .clip(CircleShape)
                .background(ac.faint)
        )
        Text(
            text = name,
            style = MaterialTheme.typography.labelMedium,
            fontWeight = FontWeight.SemiBold,
            color = ac.title
        )
        Text(
            text = "· $author",
            style = MaterialTheme.typography.labelSmall,
            color = ac.desc
        )
    }
}

// ============================================================
// Helper: load app icon bitmap
// ============================================================

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
    val minSdk: Int
)
