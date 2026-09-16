package com.mini.me_core.newui.designsystem.layout

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.mini.me_core.newui.designsystem.token.generated.AppSizing
import com.mini.me_core.newui.designsystem.token.generated.AppSpacing

/**
 * 顶栏风格：compact=44dp（iOS HIG 紧凑顶栏，默认），standard=64dp（M3 默认顶栏高度）。
 */
enum class AppTopBarStyle { Compact, Standard }

/**
 * 统一页面骨架壳（§2.3 收敛落地）：把顶栏 / 顶栏 Tab / 内容区 / 底栏 / 侧栏五个标准槽位
 * 固化为一份声明式装配，统一窗口 insets（状态栏 / 导航栏）处理。
 *
 * - **compact 移动端壳**：不引入断点 / 停靠 / 多栏等理论，只服务 Android 真机的单栏紧凑布局。
 * - **顶栏内建紧凑规格**：44dp 内容行 + `statusBarsPadding` + 40dp 图标钮（对齐 app 侧既成事实，
 *   此处成为该规格唯一事实源，彻底替代 M3 默认 64dp 顶栏）。
 * - **sideRail / topTabs / bottomBar 均为可选插槽**：不传则不渲染、不占布局。
 *
 * `content` 收 `PaddingValues`：调用方用 `Modifier.padding(inner)` 消费，避免自带 insets 双算。
 *
 * @param topBarStyle 顶栏高度风格：Compact=44dp（默认，iOS HIG），Standard=64dp（M3 默认）。
 */
@Composable
fun AppShell(
    title: String = "",
    onNavigateBack: (() -> Unit)? = null,
    navigationIcon: ImageVector? = Icons.AutoMirrored.Filled.ArrowBack,
    topBarActions: @Composable RowScope.() -> Unit = {},
    topTabs: (@Composable () -> Unit)? = null,
    bottomBar: (@Composable () -> Unit)? = null,
    sideRail: (@Composable () -> Unit)? = null,
    topBarStyle: AppTopBarStyle = AppTopBarStyle.Compact,
    content: @Composable (PaddingValues) -> Unit,
) {
    // 内容区固定占主位；侧栏仅存在时左排（compact 移动端几乎不用，保留槽位契约）。
    if (sideRail != null) {
        Row(Modifier.fillMaxSize()) {
            Surface(Modifier.fillMaxSize()) {
                Column(Modifier.fillMaxSize().statusBarsPadding().navigationBarsPadding()) { sideRail() }
            }
            Column(Modifier.weight(1f).fillMaxSize()) {
                ShellContent(
                    title = title,
                    onNavigateBack = onNavigateBack,
                    navigationIcon = navigationIcon,
                    topBarActions = topBarActions,
                    topTabs = topTabs,
                    bottomBar = bottomBar,
                    topBarStyle = topBarStyle,
                    content = content,
                )
            }
        }
    } else {
        ShellContent(
            title = title,
            onNavigateBack = onNavigateBack,
            navigationIcon = navigationIcon,
            topBarActions = topBarActions,
            topTabs = topTabs,
            bottomBar = bottomBar,
            topBarStyle = topBarStyle,
            content = content,
        )
    }
}

@Composable
private fun ShellContent(
    title: String,
    onNavigateBack: (() -> Unit)?,
    navigationIcon: ImageVector?,
    topBarActions: @Composable RowScope.() -> Unit,
    topTabs: (@Composable () -> Unit)?,
    bottomBar: (@Composable () -> Unit)?,
    topBarStyle: AppTopBarStyle,
    content: @Composable (PaddingValues) -> Unit,
) {
    Column(Modifier.fillMaxSize()) {
        // 顶栏：背景逼近状态栏，insets 内移内容
        ShellTopBar(
            title = title,
            onNavigateBack = onNavigateBack,
            navigationIcon = navigationIcon,
            actions = topBarActions,
            topBarStyle = topBarStyle,
        )
        if (topTabs != null) topTabs()
        // 内容区占剩余空间；bottomBar 在内容下方挤压布局，不再覆盖内容。
        Column(Modifier.weight(1f).fillMaxWidth()) {
            content(PaddingValues(0.dp))
        }
        if (bottomBar != null) {
            Surface(
                color = MaterialTheme.colorScheme.surface,
                modifier = Modifier.fillMaxWidth(),
            ) {
                Column(Modifier.fillMaxWidth().navigationBarsPadding()) {
                    bottomBar()
                }
            }
        }
    }
}

/**
 * 紧凑顶栏（统一规格事实源）：surface 背景延伸到状态栏（Surface 本体不加 inset），
 * `statusBarsPadding` 内移内容行，内容行高度由 [topBarStyle] 决定：
 * Compact=44dp（iOS HIG 触摸目标），Standard=64dp（M3 默认）。
 * 返回 / 操作图标钮 40dp、图标 20dp。
 */
@Composable
private fun ShellTopBar(
    title: String,
    onNavigateBack: (() -> Unit)?,
    navigationIcon: ImageVector?,
    actions: @Composable RowScope.() -> Unit,
    topBarStyle: AppTopBarStyle,
) {
    val topBarHeight = when (topBarStyle) {
        AppTopBarStyle.Compact -> AppSizing.TouchTarget
        AppTopBarStyle.Standard -> 64.dp
    }
    Surface(
        color = MaterialTheme.colorScheme.surface,
        modifier = Modifier.fillMaxWidth(),
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .statusBarsPadding()
                .height(topBarHeight),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            if (onNavigateBack != null && navigationIcon != null) {
                IconButton(
                    onClick = onNavigateBack,
                    modifier = Modifier.size(AppSizing.IconButton),
                ) {
                    Icon(
                        imageVector = navigationIcon,
                        contentDescription = "返回",
                        tint = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.size(AppSizing.IconM),
                    )
                }
            } else {
                Spacer(Modifier.width(AppSpacing.Md))
            }
            Text(
                text = title,
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.SemiBold,
                color = MaterialTheme.colorScheme.onBackground,
                maxLines = 1,
                modifier = Modifier.weight(1f),
            )
            actions()
            Spacer(Modifier.width(AppSpacing.Sm))
        }
    }
}