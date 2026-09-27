package com.mini.me_core.core.theme.components

import androidx.compose.foundation.background
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp

/**
 * 通用文件浏览器列表项：带背景色块的图标 + 文件名 + 副标题。
 *
 * 代码浏览器（只读）与容器文件管理器（多选/排序）共用本组件，视觉一致：
 * - 图标背景 40dp，圆角 10dp，内部图标 22dp
 * - 横向 padding 12dp，纵向 padding 10dp，图标与文字间距 12dp
 * - 文件名为 bodyLarge，副标题为 bodySmall（onSurfaceVariant）
 * - 选中态：primaryContainer 背景
 *
 * @param name 文件名。
 * @param subtitle 副标题（可配置：目录子项数 / 文件大小·时间 等）。
 * @param icon 文件类型图标（由 [fileIconVisual] 提供）。
 * @param iconBg 图标背景色。
 * @param iconFg 图标前景色。
 * @param selected 是否选中。
 * @param onClick 单击回调。
 * @param onLongClick 长按回调。
 * @param trailing 尾部槽位（可选，如多选选中态的对勾、更多操作按钮）。
 */
@Composable
fun FileBrowserItem(
    name: String,
    subtitle: String,
    icon: ImageVector,
    iconBg: Color,
    iconFg: Color,
    modifier: Modifier = Modifier,
    selected: Boolean = false,
    onClick: () -> Unit = {},
    onLongClick: () -> Unit = {},
    trailing: @Composable (() -> Unit)? = null,
) {
    Row(
        modifier = modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(10.dp))
            .background(
                if (selected) MaterialTheme.colorScheme.primaryContainer
                else MaterialTheme.colorScheme.surface
            )
            .combinedClickable(onClick = onClick, onLongClick = onLongClick)
            .padding(horizontal = 12.dp, vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(
            modifier = Modifier
                .size(40.dp)
                .clip(RoundedCornerShape(10.dp))
                .background(iconBg),
            contentAlignment = Alignment.Center,
        ) {
            Icon(
                imageVector = icon,
                contentDescription = null,
                tint = iconFg,
                modifier = Modifier.size(22.dp),
            )
        }
        Spacer(Modifier.width(12.dp))
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = name,
                style = MaterialTheme.typography.bodyLarge,
                color = MaterialTheme.colorScheme.onSurface,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            Text(
                text = subtitle,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        }
        trailing?.invoke()
    }
}
