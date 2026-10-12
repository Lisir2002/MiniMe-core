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
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.AccountTree
import androidx.compose.material.icons.rounded.Android
import androidx.compose.material.icons.rounded.Extension
import androidx.compose.material.icons.rounded.Folder
import androidx.compose.material.icons.rounded.Info
import androidx.compose.material.icons.rounded.Public
import androidx.compose.material.icons.rounded.Settings
import androidx.compose.material.icons.rounded.Terminal
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.mini.me_core.R

/**
 * 右侧贴边工具抽屉。
 *
 * 从主界面顶栏「工具」按钮呼出，把终端 / 浏览器 / 打包器 / 版本控制 / 设置等
 * 原本散落在聊天顶栏与抽屉里的功能入口集中收纳；只复用现有路由 navigate，
 * 不新建、不删除任何功能页面。
 *
 * 设计要点：
 * - 宽屏（平板 / 横屏，屏宽 ≥ 600dp）按屏宽 0.32 折半并夹在 260–300dp；手机竖屏固定 280dp。
 * - 半透明 scrim 点击收起；面板从右滑入/滑出，scrim 淡入淡出。
 * - 分组列表：工具 / 开发 / 其他，每组有小标题；颜色全部取自 [MaterialTheme.colorScheme]。
 * - 返回键由宿主（AppNavigation 里的 BackHandler）在展开时优先关闭本抽屉。
 *
 * @param open 当前是否展开
 * @param onDismissRequest 点击 scrim / 返回键时请求收起
 * @param onNavigate 跳转已有路由并由宿主负责收起
 * @param onPlaceholder 点击「无独立页面」占位项时提示（保持抽屉展开）
 */
@Composable
fun RightSidebar(
    open: Boolean,
    onDismissRequest: () -> Unit,
    onNavigate: (String) -> Unit,
    onPlaceholder: () -> Unit,
    modifier: Modifier = Modifier,
) {
    BoxWithConstraints(modifier = modifier.fillMaxSize()) {
        // 宽屏占比约 0.32 并设上下限；手机竖屏固定 280dp。
        val panelWidth = if (maxWidth >= 600.dp) {
            (maxWidth * 0.32f).coerceIn(260.dp, 300.dp)
        } else {
            280.dp
        }

        // 半透明遮罩：点击收起，淡入淡出。
        AnimatedVisibility(
            visible = open,
            modifier = Modifier.matchParentSize(),
            enter = fadeIn(animationSpec = tween(220)),
            exit = fadeOut(animationSpec = tween(220)),
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

        // 右侧面板：从右滑入/滑出。
        AnimatedVisibility(
            visible = open,
            modifier = Modifier.align(Alignment.CenterEnd),
            enter = slideInHorizontally(
                animationSpec = tween(220),
                initialOffsetX = { fullWidth -> fullWidth },
            ) + fadeIn(animationSpec = tween(220)),
            exit = slideOutHorizontally(
                animationSpec = tween(220),
                targetOffsetX = { fullWidth -> fullWidth },
            ) + fadeOut(animationSpec = tween(220)),
        ) {
            Surface(
                modifier = Modifier
                    .width(panelWidth)
                    .fillMaxHeight(),
                color = MaterialTheme.colorScheme.surface,
                tonalElevation = 3.dp,
                shape = RoundedCornerShape(topStart = 16.dp, bottomStart = 16.dp),
            ) {
                Column(
                    modifier = Modifier
                        .fillMaxSize()
                        .verticalScroll(rememberScrollState())
                        .statusBarsPadding()
                        .padding(vertical = 12.dp),
                ) {
                    groups().forEach { group ->
                        Spacer(Modifier.height(12.dp))
                        Text(
                            text = stringResource(group.titleRes),
                            style = MaterialTheme.typography.labelMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.padding(horizontal = 20.dp, vertical = 6.dp),
                        )
                        group.entries.forEach { entry ->
                            SidebarItem(
                                icon = entry.icon,
                                label = stringResource(entry.labelRes),
                                onClick = {
                                    val route = entry.route
                                    if (route != null) onNavigate(route) else onPlaceholder()
                                },
                            )
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun SidebarItem(
    icon: ImageVector,
    label: String,
    onClick: () -> Unit,
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .padding(horizontal = 20.dp, vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(16.dp),
    ) {
        Icon(
            imageVector = icon,
            contentDescription = label,
            tint = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.size(22.dp),
        )
        Text(
            text = label,
            style = MaterialTheme.typography.bodyLarge,
            color = MaterialTheme.colorScheme.onSurface,
        )
    }
}

private data class SidebarEntry(
    val icon: ImageVector,
    val labelRes: Int,
    val route: String? = null,
)

private data class SidebarGroup(
    val titleRes: Int,
    val entries: List<SidebarEntry>,
)

/**
 * 分组与路由：全部复用 NavHost 中已存在的 route；
 * 「文件管理」「关于」暂无独立页面，作为占位项走 [RightSidebar.onPlaceholder]。
 */
@Composable
private fun groups(): List<SidebarGroup> = listOf(
    SidebarGroup(
        titleRes = R.string.right_sidebar_group_tools,
        entries = listOf(
            SidebarEntry(Icons.Rounded.Terminal, R.string.right_sidebar_terminal, route = "terminal"),
            SidebarEntry(Icons.Rounded.Public, R.string.right_sidebar_browser, route = "browser"),
            SidebarEntry(Icons.Rounded.Android, R.string.right_sidebar_packager, route = "packager"),
        ),
    ),
    SidebarGroup(
        titleRes = R.string.right_sidebar_group_dev,
        entries = listOf(
            SidebarEntry(Icons.Rounded.AccountTree, R.string.right_sidebar_git, route = "git"),
            SidebarEntry(Icons.Rounded.Folder, R.string.right_sidebar_files),
        ),
    ),
    SidebarGroup(
        titleRes = R.string.right_sidebar_group_other,
        entries = listOf(
            SidebarEntry(Icons.Rounded.Settings, R.string.right_sidebar_settings, route = "settings"),
            SidebarEntry(Icons.Rounded.Extension, R.string.right_sidebar_capability, route = "capability_center"),
            SidebarEntry(Icons.Rounded.Info, R.string.right_sidebar_about),
        ),
    ),
)
