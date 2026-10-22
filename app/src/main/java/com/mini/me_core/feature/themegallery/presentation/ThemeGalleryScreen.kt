@file:Suppress("FunctionNaming", "LongMethod") // Compose 命名/长组件约定

package com.mini.me_core.feature.themegallery.presentation

import android.content.Context
import android.net.Uri
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.Spring
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.mini.me_core.core.theme.ScaledAnimation
import org.minime.core.api.MiniMeEngine
import org.minime.core.ui.MiniMeTheme as MiniMeEngineTheme
import org.minime.core.ui.MiniMeTokens
import org.minime.core.ui.gallery.canvasColor
import org.minime.core.ui.gallery.nav.GalleryDrawer
import org.minime.core.ui.gallery.nav.GallerySidebar
import org.minime.core.ui.gallery.nav.GalleryTopBar
import org.minime.core.ui.gallery.pages.SectionContent

/**
 * 新主题引擎演示页（R4 接入期第一批，侧边栏「新主题引擎演示」入口）。
 *
 * 完整展示自研主题引擎（theme/ 子树，org.minime.*）的 Design Gallery，
 * 与主程序旧主题引擎**完全隔离、相互不影响**：
 *
 * - **主题作用域隔离**：整页由引擎入口 [MiniMeEngineTheme] 包裹——十个
 *   `Local*Tokens` 与 M3 MaterialTheme 的覆写只作用于本子树，返回即恢复主程序
 *   旧主题；旧引擎切换（设置页预设/模式）也不影响本页（快照由演示页 ViewModel
 *   独享的 ThemeManager 提供，与旧引擎零共享）；
 * - **存储隔离**：引擎选择持久化在 SharedPreferences「minime_theme」，
 *   与旧引擎 KVStore 无交集（VM 文档已述）。
 *
 * 布局（引擎无独立壳，宿主组装 chrome）：
 * - 宽屏（≥840dp，Material expanded 断点，与引擎 Robolectric 测试屏一致）：
 *   [GallerySidebar](280dp) 常驻 + 内容列（顶栏 + 分区内容）；
 * - 窄屏：引擎 compact 顶栏（自带菜单钮开抽屉）+ 分区内容，
 *   分区导航走 [GalleryDrawer]，由宿主 `AnimatedVisibility` 控制显隐
 *   （库抽屉无 open 参数且 R3 起即无动画——动效规格归 R5 动效层；壳级按
 *   「宿主组装 chrome」模式用主程序 [ScaledAnimation] 补齐滑入/淡出，
 *   尊重全局动效缩放，不改引擎库）。
 *
 * 返回：演示页为工具型页面，无应用内返回钮——退出走系统返回键（与聊天主屏
 * 一致，由 NavHost 返回栈消费）；抽屉展开时系统返回优先关抽屉
 * （[BackHandler]），其次才交还返回栈。
 */
@Composable
fun ThemeGalleryScreen(
    viewModel: ThemeGalleryViewModel = hiltViewModel(),
) {
    MiniMeEngineTheme(manager = viewModel.manager) {
        val state by viewModel.uiState.collectAsStateWithLifecycle()
        val snapshot by viewModel.manager.snapshot.collectAsStateWithLifecycle()
        val context = LocalContext.current
        // 窄屏抽屉显隐：由宿主控制（引擎 GalleryDrawer 无 open 参数）。
        var drawerOpen by remember { mutableStateOf(false) }
        // 系统返回优先关抽屉，其次才退出演示页（交还 NavHost 返回栈）。
        BackHandler(enabled = drawerOpen) { drawerOpen = false }

        // ---------- R4.3 diff 主题包传输（SAF 零权限；设计 §8.4） ----------
        // 导出：工坊 onSaveDiff(json, name) → CREATE_DOCUMENT → 用户选定位置后回写
        var pendingExportJson by remember { mutableStateOf<String?>(null) }
        // 导入：OPEN_DOCUMENT → 读文本（KB 级直读；>1MB 拒绝）→ 回填工坊弹确认对话框
        var pendingImportJson by remember { mutableStateOf<String?>(null) }
        var pendingImportError by remember { mutableStateOf<String?>(null) }
        val createDiffLauncher = rememberLauncherForActivityResult(
            ActivityResultContracts.CreateDocument(DIFF_MIME_TYPE),
        ) { uri ->
            val json = pendingExportJson
            pendingExportJson = null
            if (uri != null && json != null) {
                // 写失败（存储满/权限回收）静默降级：用户刚授权过该 URI，重走导出即可
                runCatching {
                    context.contentResolver.openOutputStream(uri)?.use {
                        it.write(json.toByteArray(Charsets.UTF_8))
                    }
                }
            }
        }
        val openDiffLauncher = rememberLauncherForActivityResult(
            ActivityResultContracts.OpenDocument(),
        ) { uri ->
            if (uri == null) return@rememberLauncherForActivityResult
            val (text, error) = runCatching { readDiffText(context, uri) }
                .getOrElse { null to "读取失败：${it.message}" }
            pendingImportJson = text
            pendingImportError = error
        }

        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(MiniMeTokens.LocalColor.current.canvasColor),
        ) {
            BoxWithConstraints(
                modifier = Modifier
                    .fillMaxSize()
                    .statusBarsPadding()
                    .navigationBarsPadding(),
            ) {
                if (maxWidth >= WIDE_BREAKPOINT) {
                    Row(modifier = Modifier.fillMaxSize()) {
                        GallerySidebar(
                            current = state.section,
                            meta = snapshot.meta,
                            engineVersion = MiniMeEngine.VERSION,
                            onSelect = viewModel::selectSection,
                            modifier = Modifier.width(SIDEBAR_WIDTH),
                        )
                        Column(modifier = Modifier.weight(1f)) {
                            GalleryTopBar(
                                title = state.section.title,
                                subtitle = state.section.subtitle,
                                meta = snapshot.meta,
                                compact = false,
                                onToggleDark = viewModel::toggleDark,
                                // 宽屏侧栏常驻，无抽屉可开。
                                onOpenDrawer = {},
                            )
                            SectionContent(
                                section = state.section,
                                manager = viewModel.manager,
                                onSaveDiff = { json, suggestedName ->
                                    pendingExportJson = json
                                    createDiffLauncher.launch(suggestedName)
                                },
                                onPickDiff = {
                                    openDiffLauncher.launch(DIFF_INPUT_TYPES)
                                },
                                pendingImportJson = pendingImportJson,
                                pendingImportError = pendingImportError,
                                onPendingImportConsumed = {
                                    pendingImportJson = null
                                    pendingImportError = null
                                },
                                modifier = Modifier.weight(1f),
                            )
                        }
                    }
                } else {
                    Column(modifier = Modifier.fillMaxSize()) {
                        GalleryTopBar(
                            title = state.section.title,
                            subtitle = state.section.subtitle,
                            meta = snapshot.meta,
                            compact = true,
                            onToggleDark = viewModel::toggleDark,
                            onOpenDrawer = { drawerOpen = true },
                        )
                        SectionContent(
                            section = state.section,
                            manager = viewModel.manager,
                            onSaveDiff = { json, suggestedName ->
                                pendingExportJson = json
                                createDiffLauncher.launch(suggestedName)
                            },
                            onPickDiff = {
                                openDiffLauncher.launch(DIFF_INPUT_TYPES)
                            },
                            pendingImportJson = pendingImportJson,
                            pendingImportError = pendingImportError,
                            onPendingImportConsumed = {
                                pendingImportJson = null
                                pendingImportError = null
                            },
                            modifier = Modifier.weight(1f),
                        )
                    }
                    // 抽屉动画（R3 引擎无动画——动效规格归 R5；壳级用主程序
                    // ScaledAnimation 补齐，滑入走 spring、滑出走 tween，
                    // 与右侧工具抽屉同手感）。AnimatedVisibility 作为
                    // BoxWithConstraints（Box）直接子节点，浮于内容列之上。
                    AnimatedVisibility(
                        visible = drawerOpen,
                        enter = slideInHorizontally(
                            animationSpec = ScaledAnimation.finiteSpring<IntOffset>(
                                dampingRatio = Spring.DampingRatioNoBouncy,
                                stiffness = Spring.StiffnessMediumLow,
                            ),
                            initialOffsetX = { fullWidth -> fullWidth },
                        ) + fadeIn(animationSpec = ScaledAnimation.tween(DRAWER_FADE_MS)),
                        exit = slideOutHorizontally(
                            animationSpec = ScaledAnimation.tween(DRAWER_SLIDE_MS),
                            targetOffsetX = { fullWidth -> fullWidth },
                        ) + fadeOut(animationSpec = ScaledAnimation.tween(DRAWER_FADE_MS)),
                    ) {
                        GalleryDrawer(
                            current = state.section,
                            meta = snapshot.meta,
                            engineVersion = MiniMeEngine.VERSION,
                            onSelect = {
                                viewModel.selectSection(it)
                                drawerOpen = false
                            },
                            onDismiss = { drawerOpen = false },
                        )
                    }
                }
            }
        }
    }
}

/** 宽屏断点：840dp = Material expanded 断点，与引擎 Robolectric 测试屏（w840dp）一致。 */
private val WIDE_BREAKPOINT = 840.dp

/** 宽屏常驻侧栏宽度（与引擎参考用法的 280dp 一致）。 */
private val SIDEBAR_WIDTH = 280.dp

/** 抽屉滑出时长（毫秒，会再乘全局动效缩放）。 */
private const val DRAWER_SLIDE_MS = 220

/** 抽屉遮罩淡入淡出时长（毫秒，会再乘全局动效缩放）。 */
private const val DRAWER_FADE_MS = 180

// ---------- R4.3 diff 主题包传输（SAF） ----------

/** diff 包 MIME（DTCG 媒体类型，设计 §8.4） */
private const val DIFF_MIME_TYPE = "application/design-tokens+json"

/** 文件选择器输入类型（专有 MIME 优先，application/json 兜底——部分文件管理器不认 +json 后缀） */
private val DIFF_INPUT_TYPES = arrayOf(DIFF_MIME_TYPE, "application/json")

/** diff 包体积上限（KB 级；超过即非 diff 包，拒绝并提示——设计 §8.4） */
private const val MAX_DIFF_BYTES = 1_000_000

/**
 * 读 diff 文本：URI → (文本, 错误)。diff 包 KB 级，主线程直读可接受；
 * 超过 1MB 或读取失败返回错误信息（由工坊确认对话框以错误态呈现）。
 */
private fun readDiffText(context: Context, uri: Uri): Pair<String?, String?> =
    context.contentResolver.openInputStream(uri)?.use { stream ->
        val bytes = stream.readBytes()
        if (bytes.size > MAX_DIFF_BYTES) {
            null to "文件超过 1MB（${bytes.size / 1024}KB），不是 diff 主题包（diff 包为 KB 级）"
        } else {
            bytes.toString(Charsets.UTF_8) to null
        }
    } ?: (null to "无法打开所选文件")
