@file:Suppress("FunctionNaming", "LongMethod") // Compose 命名/长组件约定

package com.mini.me_core.core.ui

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.ui.graphics.RectangleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.AccountTree
import androidx.compose.material.icons.rounded.Android
import androidx.compose.material.icons.rounded.Public
import androidx.compose.material.icons.rounded.Settings
import androidx.compose.material.icons.rounded.Terminal
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.mini.me_core.R
import com.mini.me_core.core.theme.LocalAnimationScale
import com.mini.me_core.core.theme.Spacing
import com.mini.me_core.core.theme.components.AppNavIcon
import com.mini.me_core.core.theme.tokens.LocalCornerRadius

/**
 * 右侧贴边工具抽屉。
 *
 * 从主界面顶栏「工具」按钮呼出，把终端 / 浏览器 / 打包器 / Git 管理 / 设置等
 * 入口集中收纳；只复用现有路由 navigate，不新建、不删除任何功能页面。
 *
 * 设计要点：
 * - 面板宽度与左侧抽屉一致：手机竖屏 300dp；宽屏（≥600dp）按屏宽 0.32 折半并夹在 260–300dp。
 * - 形状、底色、elevation 复用左侧 ModalDrawerSheet 的令牌：RectangleShape、surface、0dp。
 * - 间距统一走 [Spacing]（md/lg/sm），动效时长乘 [LocalAnimationScale] 支持全局关闭/调慢。
 * - 半透明 scrim 点击收起；面板从右滑入/滑出，scrim 淡入淡出。
 * - 分组列表：工具 / 开发 / 其他，每组有小标题；颜色全部取自 [MaterialTheme.colorScheme]。
 * - 菜单项图标块与左侧抽屉共用设计系统组件 [AppNavIcon]（36dp 彩色圆角块 + 白色图标，
 *   日夜双色），整行点击热区与排版令牌（clip md / 垂直 sm / bodyLarge Medium）也与左侧一致；
 *   同一目的地的图标配色与左侧抽屉按钮保持同色（设置=天蓝、打包器=紫）。
 * - 返回键由宿主（AppNavigation 里的 BackHandler）在展开时优先关闭本抽屉。
 *
 * @param open 当前是否展开
 * @param onDismissRequest 点击 scrim / 返回键时请求收起
 * @param onNavigate 跳转已有路由并由宿主负责收起
 * @param modifier 修饰符
 */
@Composable
fun RightSidebar(
    open: Boolean,
    onDismissRequest: () -> Unit,
    onNavigate: (String) -> Unit,
    modifier: Modifier = Modifier,
) {
    BoxWithConstraints(modifier = modifier.fillMaxSize()) {
        // 面板宽度与左侧抽屉一致：手机竖屏 300dp；宽屏按屏宽 0.32 折半并夹在 260–300dp。
        val panelWidth = if (maxWidth >= 600.dp) {
            (maxWidth * 0.32f).coerceIn(260.dp, 300.dp)
        } else {
            300.dp
        }

        // 动效时长跟随全局动效缩放（用户可在设置中关闭/调慢动效）。
        val animationScale = LocalAnimationScale.current
        val motionDuration = (220 * animationScale).toInt().coerceAtLeast(0)

        // 半透明遮罩：点击收起，淡入淡出。
        AnimatedVisibility(
            visible = open,
            modifier = Modifier.matchParentSize(),
            enter = fadeIn(animationSpec = tween(motionDuration)),
            exit = fadeOut(animationSpec = tween(motionDuration)),
        ) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .background(Color.Black.copy(alpha = 0.4f))
                    .clickable(
                        interactionSource = remember { MutableInteractionSource() },
                        indication = null,
                        onClick = onDismissRequest,
                    )
            )
        }

        // 右侧面板：从右滑入/滑出；形状、底色、elevation 与左侧 ModalDrawerSheet 保持一致。
        AnimatedVisibility(
            visible = open,
            modifier = Modifier.align(Alignment.CenterEnd),
            enter = slideInHorizontally(
                animationSpec = tween(motionDuration),
                initialOffsetX = { fullWidth -> fullWidth },
            ) + fadeIn(animationSpec = tween(motionDuration)),
            exit = slideOutHorizontally(
                animationSpec = tween(motionDuration),
                targetOffsetX = { fullWidth -> fullWidth },
            ) + fadeOut(animationSpec = tween(motionDuration)),
        ) {
            Surface(
                modifier = Modifier
                    .width(panelWidth)
                    .fillMaxHeight(),
                color = MaterialTheme.colorScheme.surface,
                tonalElevation = 0.dp,
                shape = RectangleShape,
            ) {
                Column(
                    modifier = Modifier
                        .fillMaxSize()
                        .verticalScroll(rememberScrollState())
                        .statusBarsPadding()
                        .padding(vertical = Spacing.md),
                ) {
                    groups().forEach { group ->
                        Spacer(Modifier.height(Spacing.md))
                        Text(
                            text = stringResource(group.titleRes),
                            style = MaterialTheme.typography.labelMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.padding(horizontal = Spacing.lg, vertical = Spacing.sm),
                        )
                        group.entries.forEach { entry ->
                            SidebarItem(
                                icon = entry.icon,
                                iconBgLight = entry.iconBgLight,
                                iconBgDark = entry.iconBgDark,
                                label = stringResource(entry.labelRes),
                                onClick = { onNavigate(entry.route) },
                            )
                        }
                    }
                }
            }
        }
    }
}

/**
 * 侧边栏菜单项：图标块 + 文字 的整块可点击区域。
 *
 * 与左侧抽屉底部导航按钮（DrawerSettingsButton / DrawerPackagerButton）共用同一套令牌：
 * [AppNavIcon] 图标块、clip md 点击热区、垂直 [Spacing.sm] 内边距、
 * bodyLarge / Medium / onSurface 文字。无障碍描述挂在行上（与左侧一致）。
 */
@Composable
private fun SidebarItem(
    icon: ImageVector,
    iconBgLight: Color,
    iconBgDark: Color,
    label: String,
    onClick: () -> Unit,
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(LocalCornerRadius.current.md))
            .clickable(onClick = onClick)
            .semantics { contentDescription = label }
            .padding(horizontal = Spacing.lg, vertical = Spacing.sm),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(Spacing.md),
    ) {
        AppNavIcon(
            icon = icon,
            iconBgLight = iconBgLight,
            iconBgDark = iconBgDark,
        )
        Text(
            text = label,
            style = MaterialTheme.typography.bodyLarge,
            color = MaterialTheme.colorScheme.onSurface,
            fontWeight = FontWeight.Medium,
        )
    }
}

private data class SidebarEntry(
    val icon: ImageVector,
    val labelRes: Int,
    val route: String,
    /** 图标块亮色模式背景（与左侧抽屉同目的地按钮保持同色）。 */
    val iconBgLight: Color,
    /** 图标块暗色模式背景。 */
    val iconBgDark: Color,
)

private data class SidebarGroup(
    val titleRes: Int,
    val entries: List<SidebarEntry>,
)

/**
 * 分组与路由：全部复用 NavHost 中已存在的 route。
 *
 * 历史变更（任务 C）：原「文件管理」「关于」为无独立页面的占位项、「能力中心」
 * 与设置页入口重复，三者一并删除；「版本控制」更名「Git 管理」——
 * 该页覆盖状态/暂存/提交、分支、提交记录、署名、凭据、仓库初始化与远程操作，
 * 命名与功能范围相符。
 */
@Composable
private fun groups(): List<SidebarGroup> = listOf(
    SidebarGroup(
        titleRes = R.string.right_sidebar_group_tools,
        entries = listOf(
            SidebarEntry(
                icon = Icons.Rounded.Terminal,
                labelRes = R.string.right_sidebar_terminal,
                route = "terminal",
                iconBgLight = Color(0xFF22C55E),
                iconBgDark = Color(0xFF15803D),
            ),
            SidebarEntry(
                icon = Icons.Rounded.Public,
                labelRes = R.string.right_sidebar_browser,
                route = "browser",
                iconBgLight = Color(0xFF06B6D4),
                iconBgDark = Color(0xFF0E7490),
            ),
            SidebarEntry(
                icon = Icons.Rounded.Android,
                labelRes = R.string.right_sidebar_packager,
                route = "packager",
                // 与左侧抽屉「应用打包器」按钮同色（紫）。
                iconBgLight = Color(0xFF8B5CF6),
                iconBgDark = Color(0xFF6D28D9),
            ),
        ),
    ),
    SidebarGroup(
        titleRes = R.string.right_sidebar_group_dev,
        entries = listOf(
            SidebarEntry(
                icon = Icons.Rounded.AccountTree,
                labelRes = R.string.right_sidebar_git,
                route = "git",
                iconBgLight = Color(0xFFF97316),
                iconBgDark = Color(0xFFC2410C),
            ),
        ),
    ),
    SidebarGroup(
        titleRes = R.string.right_sidebar_group_other,
        entries = listOf(
            SidebarEntry(
                icon = Icons.Rounded.Settings,
                labelRes = R.string.right_sidebar_settings,
                route = "settings",
                // 与左侧抽屉「设置」按钮同色（天蓝）。
                iconBgLight = Color(0xFF0EA5E9),
                iconBgDark = Color(0xFF0369A1),
            ),
        ),
    ),
)
