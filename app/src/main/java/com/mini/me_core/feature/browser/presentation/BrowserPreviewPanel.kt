package com.mini.me_core.feature.browser.presentation

import android.graphics.BitmapFactory
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material.icons.rounded.ExpandMore
import androidx.compose.material.icons.rounded.ExpandLess
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.mini.me_core.feature.browser.domain.PreviewFrame

/**
 * 可折叠的浏览器实时预览面板。
 *
 * 布局行为：
 *  - 默认显示为右下角小缩略图（120dp 宽），点击展开为较大预览（300dp 宽）；
 *  - 展开时显示当前 URL 和页面标题；
 *  - 有「关闭」按钮可完全隐藏预览；
 *  - 仅在模型操作浏览器时（agentStatus.active=true）由父组件控制可见性。
 *
 * @param previewFrame 最新预览帧（JPEG 字节 + URL + 标题）
 * @param onDismiss 关闭预览面板的回调
 */
@Composable
fun BrowserPreviewPanel(
    previewFrame: PreviewFrame?,
    onDismiss: () -> Unit,
    modifier: Modifier = Modifier
) {
    var expanded by remember { mutableStateOf(false) }

    val panelWidth = if (expanded) 300.dp else 120.dp
    val panelHeight = if (expanded) 400.dp else 90.dp

    Surface(
        shape = RoundedCornerShape(12.dp),
        color = MaterialTheme.colorScheme.surface,
        shadowElevation = 8.dp,
        modifier = modifier
            .width(panelWidth)
            .height(panelHeight)
    ) {
        Column {
            // 顶部标题栏（展开时显示 URL + 标题）
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .background(MaterialTheme.colorScheme.surfaceVariant)
                    .padding(horizontal = 8.dp, vertical = 4.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                // 展开/折叠按钮
                IconButton(
                    onClick = { expanded = !expanded },
                    modifier = Modifier.size(20.dp)
                ) {
                    Icon(
                        if (expanded) Icons.Rounded.ExpandLess else Icons.Rounded.ExpandMore,
                        contentDescription = if (expanded) "折叠预览" else "展开预览",
                        tint = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.size(16.dp)
                    )
                }
                Column(
                    modifier = Modifier.weight(1f)
                ) {
                    if (expanded) {
                        Text(
                            text = previewFrame?.title?.takeIf { it.isNotBlank() }
                                ?: previewFrame?.url?.takeIf { it.isNotBlank() }
                                ?: "预览中",
                            style = MaterialTheme.typography.labelMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                    }
                    Text(
                        text = if (expanded) {
                            previewFrame?.url ?: ""
                        } else {
                            "AI 浏览中"
                        },
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.7f),
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                }
                // 关闭按钮
                IconButton(
                    onClick = onDismiss,
                    modifier = Modifier.size(20.dp)
                ) {
                    Icon(
                        Icons.Rounded.Close,
                        contentDescription = "关闭预览",
                        tint = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.size(14.dp)
                    )
                }
            }
            // 预览截图区域
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .weight(1f)
                    .background(MaterialTheme.colorScheme.surface),
                contentAlignment = Alignment.Center
            ) {
                previewFrame?.let { frame ->
                    val bitmap = remember(frame) {
                        BitmapFactory.decodeByteArray(frame.jpegBytes, 0, frame.jpegBytes.size)
                    }
                    bitmap?.let { bmp ->
                        Image(
                            bitmap = bmp.asImageBitmap(),
                            contentDescription = "浏览器实时预览",
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(4.dp)
                                .clip(RoundedCornerShape(8.dp)),
                            contentScale = ContentScale.Fit
                        )
                    }
                }
            }
        }
    }
}
