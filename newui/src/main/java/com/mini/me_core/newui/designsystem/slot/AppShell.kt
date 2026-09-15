package com.mini.me_core.newui.designsystem.slot

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
 * 统一页面骨架壳（§2.3 收敛落地）：把顶栏 / 顶栏 Tab / 内容区 / 底栏 / 侧栏五个标准槽位
 * 固化为一份声明式装配，统一窗口 insets（状态栏 / 导航栏）处理。
 *
 * - **compact 移动端壳**：不引入断点 / 停靠 / 多栏等理论，只服务 Android 真机的单栏紧凑布局。
 * - **顶栏内建紧凑规格**：44dp 内容行 + `statusBarsPadding` + 40dp 图标钮（对齐 app 侧既成事实，
 *   此处成为该规格唯一事实源，彻底替代 M3 默认 64dp 顶栏）。
 * - **sideRail / topTabs / bottomBar 均为可选插槽**：不传则不渲染、不占布局。
 *
 * `content` 收 `PaddingValues`：调用方用 `Modifier.padding(inner)` 消费，避免自带 insets 双算。
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
    content: @Composable (PaddingValues) -> Unit,
) {
    // 内容区固定占主位；侧栏仅存在时左排（compact 移动端几乎不用，保留槽位契约）。
    if (sideRail != null) {
        Row(Modifier.fillMaxSize()) {
            Surface(Modifier.fillMaxSize().navigationBarsPadding()) {
                Column(Modifier.fillMaxSize()) { sideRail() }
            }
            Column(Modifier.weight(1f).fillMaxSize()) {
                ShellContent(
                    title = title,
                    onNavigateBack = onNavigateBack,
                    navigationIcon = navigationIcon,
                    topBarActions = topBarActions,
                    topTabs = topTabs,
                    bottomBar = bottomBar,
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
    content: @Composable (PaddingValues) -> Unit,
) {
    Box(Modifier.fillMaxSize()) {
        Column(Modifier.fillMaxSize()) {
            // 顶栏：背景逼近状态栏，insets 内移内容
            ShellTopBar(
                title = title,
                onNavigateBack = onNavigateBack,
                navigationIcon = navigationIcon,
                actions = topBarActions,
            )
            if (topTabs != null) topTabs()
            // 系统栏已由顶栏 / 底栏各自处理，内容不再加 inset；留白交给页面自身（pageContentPadding 等）。
            content(PaddingValues(0.dp))
        }
        if (bottomBar != null) {
            Surface(
                color = MaterialTheme.colorScheme.surface,
                modifier = Modifier
                    .align(Alignment.BottomCenter)
                    .fillMaxWidth()
                    .navigationBarsPadding(),
            ) {
                bottomBar()
            }
        }
    }
}

/**
 * 紧凑顶栏（统一规格事实源）：surface 背景延伸到状态栏，`statusBarsPadding` 内移内容行，
 * 内容行固定 44dp，返回 / 操作图标钮 40dp、图标 20dp。
 */
@Composable
private fun ShellTopBar(
    title: String,
    onNavigateBack: (() -> Unit)?,
    navigationIcon: ImageVector?,
    actions: @Composable RowScope.() -> Unit,
) {
    Surface(
        color = MaterialTheme.colorScheme.surface,
        modifier = Modifier.fillMaxWidth().statusBarsPadding(),
    ) {
        Row(
            modifier = Modifier.fillMaxWidth().height(44.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            if (onNavigateBack != null && navigationIcon != null) {
                IconButton(
                    onClick = onNavigateBack,
                    modifier = Modifier.size(AppSizing.IconButton),
                ) {
                    Icon(
                        imageVector = navigationIcon,
                        contentDescription = null,
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