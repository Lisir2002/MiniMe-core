package com.mini.me_core.core.theme.components

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Info
import androidx.compose.material.icons.rounded.Settings
import androidx.compose.material3.Surface
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.mini.me_core.core.theme.tokens.LocalAppTheme
import com.mini.me_core.core.theme.tokens.LocalCornerRadius
import com.mini.me_core.core.theme.tokens.PrimitiveSpacing

/**
 * 统一分组背景容器组件。
 *
 * 基于现有 AppSectionGroup（AppComponents.kt）归纳，使用 Semantic Token 颜色。
 * 用于设置页分组：浅色背景块 + 圆角，内部子项无边框，靠间距区分。
 *
 * @param modifier 修饰符
 * @param content 分组内容
 */
@Composable
fun AppSectionGroup(
    modifier: Modifier = Modifier,
    content: @Composable () -> Unit,
) {
    val colors = LocalAppTheme.current.colors

    Surface(
        modifier = modifier
            .fillMaxWidth()
            .padding(horizontal = PrimitiveSpacing.Lg),
        shape = RoundedCornerShape(LocalCornerRadius.current.lg),
        color = colors.surfaceSunken,
        tonalElevation = 0.dp,
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(vertical = PrimitiveSpacing.Xs),
            verticalArrangement = Arrangement.spacedBy(0.dp),
        ) {
            content()
        }
    }
}

// ──────────────────────────────────────────────
// Previews
// ──────────────────────────────────────────────

/** Preview：分组容器示例。 */
@androidx.compose.ui.tooling.preview.Preview(showBackground = true, widthDp = 400, heightDp = 300)
@Composable
private fun AppSectionGroupPreview() {
    com.mini.me_core.core.theme.AIEditorTheme(darkTheme = false) {
        Column(
            modifier = Modifier.padding(vertical = 16.dp),
        ) {
            AppSectionHeader(title = "基础设置")
            AppSectionGroup {
                AppListItem(
                    icon = Icons.Rounded.Settings,
                    title = "通用",
                    subtitle = "基础偏好设置",
                    onClick = {},
                )
                AppListItem(
                    icon = Icons.Rounded.Info,
                    title = "关于",
                    subtitle = "版本与信息",
                    onClick = {},
                )
            }
        }
    }
}
