package com.mini.me_core.core.theme.components

import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material.icons.rounded.Search
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.mini.me_core.core.theme.tokens.LocalAppTheme
import com.mini.me_core.core.theme.tokens.PrimitiveSpacing

/**
 * 统一顶栏组件（Phase 2 Token 版）。
 *
 * **API 与现有 [com.mini.me_core.core.theme.AppTopAppBar] 完全兼容**，
 * 参数签名一致，仅内部颜色从 MaterialTheme 迁移到 SemanticColors Token。
 *
 * 现有 28 处调用点无需修改（仍引用旧版），Phase 3 逐步替换 import 即可。
 *
 * 与 ChatHeader 结构一致：44dp 高度、图标 20dp、导航按钮 40dp。
 *
 * @param title 标题文字
 * @param onNavigateBack 返回回调；非空时显示导航图标按钮
 * @param navigationIcon 导航图标（默认 ArrowBack）
 * @param navigationContentDescription 导航图标内容描述
 * @param actions 右侧操作区
 */
@Composable
fun AppTopAppBar(
    title: String,
    onNavigateBack: (() -> Unit)? = null,
    navigationIcon: ImageVector? = null,
    navigationContentDescription: String? = null,
    actions: @Composable () -> Unit = {},
) {
    val colors = LocalAppTheme.current.colors

    Surface(
        color = colors.surfaceCard,
        modifier = Modifier
            .fillMaxWidth()
            .statusBarsPadding(),
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .height(44.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            if (onNavigateBack != null && navigationIcon != null) {
                IconButton(
                    onClick = onNavigateBack,
                    modifier = Modifier.size(40.dp),
                ) {
                    Icon(
                        imageVector = navigationIcon,
                        contentDescription = navigationContentDescription,
                        tint = colors.textSecondary,
                        modifier = Modifier.size(20.dp),
                    )
                }
            } else {
                Spacer(Modifier.width(PrimitiveSpacing.Md))
            }
            Text(
                text = title,
                fontWeight = FontWeight.SemiBold,
                color = colors.textPrimary,
                maxLines = 1,
                modifier = Modifier.weight(1f),
            )
            actions()
            Spacer(Modifier.width(PrimitiveSpacing.Sm))
        }
    }
}

// ──────────────────────────────────────────────
// Previews
// ──────────────────────────────────────────────

/** Preview：带返回按钮的顶栏。 */
@androidx.compose.ui.tooling.preview.Preview(showBackground = true, widthDp = 400, heightDp = 120)
@Composable
private fun AppTopAppBarPreview() {
    com.mini.me_core.core.theme.AIEditorTheme(darkTheme = false) {
        AppTopAppBar(
            title = "设置",
            onNavigateBack = {},
            navigationIcon = Icons.AutoMirrored.Rounded.ArrowBack,
            actions = {
                IconButton(onClick = {}) {
                    Icon(
                        imageVector = Icons.Rounded.Search,
                        contentDescription = "Search",
                        tint = LocalAppTheme.current.colors.textSecondary,
                    )
                }
            },
        )
    }
}

/** Preview：无返回按钮的顶栏。 */
@androidx.compose.ui.tooling.preview.Preview(showBackground = true, widthDp = 400, heightDp = 120)
@Composable
private fun AppTopAppBarNoBackPreview() {
    com.mini.me_core.core.theme.AIEditorTheme(darkTheme = false) {
        AppTopAppBar(title = "主页")
    }
}
