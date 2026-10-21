@file:Suppress("FunctionNaming", "LongMethod") // Compose 命名/长组件约定

package com.mini.me_core.feature.themegallery.presentation

import androidx.activity.compose.BackHandler
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
        // 窄屏抽屉显隐：由宿主控制（引擎 GalleryDrawer 无 open 参数）。
        var drawerOpen by remember { mutableStateOf(false) }
        // 系统返回优先关抽屉，其次才退出演示页（交还 NavHost 返回栈）。
        BackHandler(enabled = drawerOpen) { drawerOpen = false }

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
