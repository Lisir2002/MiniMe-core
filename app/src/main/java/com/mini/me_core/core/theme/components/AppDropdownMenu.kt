package com.mini.me_core.core.theme.components

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.mini.me_core.core.theme.tokens.LocalCornerRadius

/**
 * 统一应用下拉菜单组件。
 *
 * 封装 Material3 DropdownMenu，提供统一的视觉样式：
 * - 圆角裁剪（跟随全局圆角令牌）
 * - 卡片背景色
 * - 4dp 阴影
 *
 * 使用方式与 Material3 DropdownMenu 完全一致，直接替换即可。
 * 触发按钮与菜单需放在同一 Box 中以确保弹出位置正确。
 */
@Composable
fun AppDropdownMenu(
    expanded: Boolean,
    onDismissRequest: () -> Unit,
    modifier: Modifier = Modifier,
    content: @Composable ColumnScope.() -> Unit,
) {
    DropdownMenu(
        expanded = expanded,
        onDismissRequest = onDismissRequest,
        modifier = modifier
            .clip(RoundedCornerShape(LocalCornerRadius.current.md))
            .background(MaterialTheme.colorScheme.surface)
            .shadow(elevation = 4.dp, shape = RoundedCornerShape(LocalCornerRadius.current.md)),
        content = content,
    )
}

/**
 * 统一下拉菜单项，带前导图标。
 *
 * @param text 菜单项文字
 * @param onClick 点击回调
 * @param leadingIcon 前导图标（可选）
 * @param isError 是否为危险操作（文字和图标显示为错误色）
 */
@Composable
fun AppDropdownMenuItem(
    text: String,
    onClick: () -> Unit,
    leadingIcon: ImageVector? = null,
    isError: Boolean = false,
) {
    val color = if (isError) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.onSurface
    DropdownMenuItem(
        text = {
            Text(
                text = text,
                style = MaterialTheme.typography.bodyMedium,
                color = color,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        },
        leadingIcon = leadingIcon?.let {
            {
                androidx.compose.material3.Icon(
                    imageVector = it,
                    contentDescription = null,
                    tint = color,
                    modifier = Modifier.size(20.dp),
                )
            }
        },
        onClick = onClick,
    )
}
