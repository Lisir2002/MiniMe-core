package com.mini.me_core.core.download

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import com.mini.me_core.R
import com.mini.me_core.core.theme.tokens.LocalAppTheme
import com.mini.me_core.core.theme.tokens.LocalComponentTokens
import com.mini.me_core.core.theme.tokens.LocalCornerRadius
import com.mini.me_core.core.theme.tokens.PrimitiveSpacing

/**
 * 通用下载确认弹窗。
 *
 * 展示文件名/标题、文件大小（未知时占位）、来源 URL（截断），提供「取消」/「确认下载」按钮。
 * 样式跟随 AppDialog：使用 Semantic Token 颜色，无硬编码颜色，无 emoji。
 *
 * @param fileName 文件名或下载标题
 * @param fileSizeText 已格式化的文件大小字符串；null 表示未知
 * @param sourceUrl 来源 URL（自动截断展示）
 * @param onConfirm 确认下载回调
 * @param onDismiss 取消/关闭回调
 */
@Composable
fun DownloadConfirmDialog(
    fileName: String,
    fileSizeText: String?,
    sourceUrl: String,
    onConfirm: () -> Unit,
    onDismiss: () -> Unit,
) {
    val colors = LocalAppTheme.current.colors
    val sizeLabel = fileSizeText ?: stringResource(R.string.download_confirm_size_unknown)
    val message = stringResource(
        R.string.download_confirm_message,
        fileName,
        sizeLabel,
        DownloadFormatters.truncateUrl(sourceUrl),
    )

    AlertDialog(
        onDismissRequest = onDismiss,
        title = {
            Text(
                text = stringResource(R.string.download_confirm_title),
                fontSize = LocalComponentTokens.current.text.titleMediumFontSize,
                fontWeight = FontWeight.SemiBold,
                color = colors.textPrimary,
            )
        },
        text = {
            Text(
                text = message,
                fontSize = LocalComponentTokens.current.text.bodyMediumFontSize,
                color = colors.textSecondary,
            )
        },
        confirmButton = {
            Row(horizontalArrangement = Arrangement.End) {
                TextButton(onClick = onDismiss) {
                    Text(
                        text = stringResource(R.string.common_cancel),
                        color = colors.textSecondary,
                    )
                }
                Spacer(Modifier.width(PrimitiveSpacing.Sm))
                TextButton(onClick = onConfirm) {
                    Text(
                        text = stringResource(R.string.download_confirm_button),
                        color = colors.brandPrimary,
                        fontWeight = FontWeight.SemiBold,
                    )
                }
            }
        },
        shape = RoundedCornerShape(LocalCornerRadius.current.xl),
        containerColor = colors.surfaceOverlay,
    )
}
