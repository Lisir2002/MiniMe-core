package com.mini.logs.ui.logs

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import com.mini.logs.data.HighlightColor

/**
 * 长按日志行弹出的操作菜单。
 */
@Composable
fun LogActionMenu(
    expanded: Boolean,
    onDismiss: () -> Unit,
    onCopyFull: () -> Unit,
    onCopyMessage: () -> Unit,
    onCopyTimestamp: () -> Unit,
    onCopyTag: () -> Unit,
    onHighlight: (Int) -> Unit,
    onBookmark: () -> Unit,
    onViewContext: () -> Unit,
    onNextError: () -> Unit,
) {
    var showHighlightSubmenu by remember { mutableStateOf(false) }

    DropdownMenu(
        expanded = expanded,
        onDismissRequest = { onDismiss(); showHighlightSubmenu = false },
    ) {
        if (!showHighlightSubmenu) {
            DropdownMenuItem(text = { Text("复制全文") }, onClick = { onCopyFull(); onDismiss() })
            DropdownMenuItem(text = { Text("复制消息") }, onClick = { onCopyMessage(); onDismiss() })
            DropdownMenuItem(text = { Text("复制时间戳") }, onClick = { onCopyTimestamp(); onDismiss() })
            DropdownMenuItem(text = { Text("复制 Tag") }, onClick = { onCopyTag(); onDismiss() })
            HorizontalDivider()
            DropdownMenuItem(text = { Text("高亮标记 ▶") }, onClick = { showHighlightSubmenu = true })
            DropdownMenuItem(text = { Text("添加书签+备注") }, onClick = { onBookmark(); onDismiss() })
            HorizontalDivider()
            DropdownMenuItem(text = { Text("查看前后上下文") }, onClick = { onViewContext(); onDismiss() })
            DropdownMenuItem(text = { Text("下一个 ERROR") }, onClick = { onNextError(); onDismiss() })
        } else {
            DropdownMenuItem(text = { Text("◀ 返回") }, onClick = { showHighlightSubmenu = false })
            HorizontalDivider()
            HighlightColor.entries.forEach { hc ->
                DropdownMenuItem(
                    text = {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Box(
                                modifier = Modifier
                                    .padding(end = 12.dp)
                                    .size(20.dp)
                                    .background(Color(hc.argb), RoundedCornerShape(4.dp)),
                            )
                            Text(
                                when (hc) {
                                    HighlightColor.RED -> "红色"
                                    HighlightColor.YELLOW -> "黄色"
                                    HighlightColor.GREEN -> "绿色"
                                    HighlightColor.BLUE -> "蓝色"
                                    HighlightColor.PURPLE -> "紫色"
                                },
                            )
                        }
                    },
                    onClick = { onHighlight(hc.index); onDismiss(); showHighlightSubmenu = false },
                )
            }
            DropdownMenuItem(
                text = { Text("清除高亮") },
                onClick = { onHighlight(-1); onDismiss(); showHighlightSubmenu = false },
            )
        }
    }
}
