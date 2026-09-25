package com.mini.me_core.feature.agent.presentation.component.markdown

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Check
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

/**
 * MiniMe 列表组件（F2.1）。
 * - 无序列表：圆点 primary 色，直径 6dp，与文字间距 8dp；
 * - 有序列表：数字 primary 色粗体，等宽字体；
 * - 任务列表：复选框 24dp，已完成项文字删除线 + onSurfaceVariant 色。
 */
@Composable
fun MiniMeBulletList(
    items: List<List<MdInline>>,
    color: Color,
    onOpenUrl: ((String) -> Unit)?,
    modifier: Modifier = Modifier,
) {
    Column(modifier = modifier.padding(start = 16.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
        items.forEach { inlines ->
            Row(verticalAlignment = Alignment.Top) {
                Box(
                    Modifier
                        .padding(top = 8.dp, end = 8.dp)
                        .size(6.dp)
                        .clip(RoundedCornerShape(50))
                        .background(MaterialTheme.colorScheme.primary)
                )
                LinkableInlines(
                    inlines = inlines,
                    baseStyle = MaterialTheme.typography.bodyMedium.copy(
                        color = color, fontSize = 14.sp, lineHeight = 20.sp
                    ),
                    onOpenUrl = onOpenUrl,
                    modifier = Modifier.width(0.dp).weight(1f),
                )
            }
        }
    }
}

@Composable
fun MiniMeOrderedList(
    items: List<List<MdInline>>,
    start: Int,
    color: Color,
    onOpenUrl: ((String) -> Unit)?,
    modifier: Modifier = Modifier,
) {
    Column(modifier = modifier.padding(start = 16.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
        items.forEachIndexed { idx, inlines ->
            Row(verticalAlignment = Alignment.Top) {
                Text(
                    text = "${idx + start}.",
                    style = TextStyle(
                        color = MaterialTheme.colorScheme.primary,
                        fontWeight = FontWeight.Bold,
                        fontFamily = FontFamily.Monospace,
                        fontSize = 14.sp,
                        lineHeight = 20.sp,
                    ),
                    modifier = Modifier.padding(end = 8.dp),
                )
                LinkableInlines(
                    inlines = inlines,
                    baseStyle = MaterialTheme.typography.bodyMedium.copy(
                        color = color, fontSize = 14.sp, lineHeight = 20.sp
                    ),
                    onOpenUrl = onOpenUrl,
                    modifier = Modifier.width(0.dp).weight(1f),
                )
            }
        }
    }
}

@Composable
fun MiniMeTaskList(
    items: List<TaskItem>,
    color: Color,
    onOpenUrl: ((String) -> Unit)?,
    modifier: Modifier = Modifier,
) {
    Column(modifier = modifier.padding(start = 16.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
        items.forEach { item ->
            Row(verticalAlignment = Alignment.Top) {
                // 复选框 24dp
                Box(
                    modifier = Modifier
                        .padding(top = 2.dp, end = 8.dp)
                        .size(24.dp)
                        .clip(RoundedCornerShape(6.dp))
                        .background(
                            if (item.checked) MaterialTheme.colorScheme.primary else Color.Transparent
                        )
                        .then(
                            if (item.checked) Modifier
                            else Modifier.border(
                                BorderStroke(1.5.dp, MaterialTheme.colorScheme.onSurfaceVariant),
                                RoundedCornerShape(6.dp),
                            )
                        ),
                    contentAlignment = Alignment.Center,
                ) {
                    if (item.checked) {
                        Icon(
                            Icons.Rounded.Check,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.onPrimary,
                            modifier = Modifier.size(16.dp),
                        )
                    }
                }
                val textColor = if (item.checked) MaterialTheme.colorScheme.onSurfaceVariant else color
                LinkableInlines(
                    inlines = item.inlines,
                    baseStyle = MaterialTheme.typography.bodyMedium.copy(
                        color = textColor,
                        fontSize = 14.sp,
                        lineHeight = 20.sp,
                        textDecoration = if (item.checked) TextDecoration.LineThrough else null,
                    ),
                    onOpenUrl = onOpenUrl,
                    modifier = Modifier.width(0.dp).weight(1f),
                )
            }
        }
    }
}
