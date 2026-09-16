package com.mini.me_core.newui.designsystem.component

import com.mini.me_core.newui.designsystem.theme.appPalette
import androidx.compose.foundation.background
import androidx.compose.foundation.border
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
import androidx.compose.material.icons.automirrored.rounded.OpenInNew
import androidx.compose.material.icons.rounded.Description
import androidx.compose.material.icons.rounded.Image
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.mini.me_core.newui.designsystem.token.generated.AppColor
import com.mini.me_core.newui.designsystem.token.generated.AppRadius
import com.mini.me_core.newui.designsystem.token.generated.AppSizing
import com.mini.me_core.newui.designsystem.token.generated.AppSpacing
import com.mini.me_core.newui.designsystem.token.generated.AppStroke
import java.util.Locale

/**
 * 附件卡（分子组 · AppAttachmentCard）：对话流中的附件引用展示层，
 * 对接真实数据 [AgentAttachment]（fileName / mimeType / sizeBytes / isImage / containerPath）
 * 与 `ToolCallFinished.attachments`（sendFile 等展示型工具的产物）：
 *
 * - 类型识别：[isImage] 时图标块用图片图标（未来可扩 [thumbnail] 直接渲染缩略图），否则文件图标。
 * - 元信息行：mimeType + 格式化大小（B / KB / MB）+ 容器路径（`containerPath`，短截断）。
 * - 交互：[onClick] 非空时整卡可点并渲染"打开"箭头（上层接预览 / 定位文件）。
 *
 * 建议用法：作为消息行正文区域的一部分（工具消息气泡尾部、用户上传附件区），
 * 多附件按列表顺序逐张渲染。
 *
 * @since 0.1.0-experimental
 */
@Composable
fun AppAttachmentCard(
    fileName: String,
    modifier: Modifier = Modifier,
    mimeType: String? = null,
    sizeBytes: Long? = null,
    containerPath: String? = null,
    isImage: Boolean = false,
    thumbnail: ImageBitmap? = null,
    onClick: (() -> Unit)? = null,
) {
    val shape = RoundedCornerShape(AppRadius.Md)

    Row(
        modifier = modifier
            .fillMaxWidth()
            .clip(shape)
            .background(appPalette().surface)
            .border(AppStroke.Thin, appPalette().separator, shape)
            .then(if (onClick != null) Modifier.clickable(onClick = onClick) else Modifier)
            .padding(horizontal = AppSpacing.Md, vertical = AppSpacing.Sm),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        // 缩略图优先，否则类型图标块
        if (thumbnail != null) {
            Box(
                modifier = Modifier
                    .size(36.dp)
                    .clip(RoundedCornerShape(AppRadius.Sm))
                    .background(appPalette().surfaceDim),
                contentAlignment = Alignment.Center,
            ) {
                // 占位：缩略图绘制由上层以 Image composable 注入（此组件保持纯数据层）
                // 装饰图标：旁侧已有文字/语义，跳过无障碍
                Icon(
                    imageVector = if (isImage) Icons.Rounded.Image else Icons.Rounded.Description,
                    contentDescription = null,
                    tint = appPalette().labelSecondary,
                    modifier = Modifier.size(AppSizing.IconS),
                )
            }
        } else {
            Box(
                modifier = Modifier
                    .size(36.dp)
                    .clip(RoundedCornerShape(AppRadius.Sm))
                    .background(appPalette().primary.copy(alpha = 0.10f)),
                contentAlignment = Alignment.Center,
            ) {
                // 装饰图标：旁侧已有文字/语义，跳过无障碍
                Icon(
                    imageVector = if (isImage) Icons.Rounded.Image else Icons.Rounded.Description,
                    contentDescription = null,
                    tint = appPalette().primary,
                    modifier = Modifier.size(AppSizing.IconS),
                )
            }
        }
        Spacer(Modifier.width(AppSpacing.Sm))
        Column(Modifier.weight(1f)) {
            Text(
                text = fileName,
                style = MaterialTheme.typography.bodySmall,
                fontWeight = FontWeight.Medium,
                color = appPalette().ink,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            val meta = listOfNotNull(
                mimeType,
                sizeBytes?.let { formatBytes(it) },
            ).joinToString(" · ")
            if (meta.isNotEmpty()) {
                Spacer(Modifier.height(AppSpacing.Tiny))
                Text(
                    text = meta,
                    style = MaterialTheme.typography.labelSmall,
                    color = appPalette().labelSecondary,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }
            if (containerPath != null) {
                Text(
                    text = containerPath,
                    style = MaterialTheme.typography.labelSmall,
                    color = appPalette().labelTertiary,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }
        }
        if (onClick != null) {
            Spacer(Modifier.width(AppSpacing.Xs))
            Icon(
                imageVector = Icons.AutoMirrored.Rounded.OpenInNew,
                contentDescription = "打开附件",
                tint = appPalette().labelSecondary,
                modifier = Modifier.size(AppSizing.IconXs),
            )
        }
    }
}

/** 字节格式化："850 B" / "2.4 KB" / "1.3 MB"。 */
private fun formatBytes(bytes: Long): String {
    if (bytes < 1024) return String.format(Locale.US, "%d B", bytes)
    val kb = bytes / 1024.0
    if (kb < 1024) return String.format(Locale.US, "%.1f KB", kb)
    return String.format(Locale.US, "%.1f MB", kb / 1024.0)
}
