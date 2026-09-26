package com.mini.me_core.core.download

import androidx.compose.runtime.Composable
import androidx.compose.ui.res.stringResource
import com.mini.me_core.R
import com.mini.me_core.core.theme.components.AppConfirmDialog

/**
 * 通用下载确认弹窗。
 *
 * 展示文件名/标题、文件大小（未知时占位）、来源 URL（截断），提供「取消」/「确认下载」按钮。
 * 统一使用 [AppConfirmDialog] 基座，样式跟随 Semantic Token，无硬编码颜色，无 emoji。
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
    val sizeLabel = fileSizeText ?: stringResource(R.string.download_confirm_size_unknown)
    val message = stringResource(
        R.string.download_confirm_message,
        fileName,
        sizeLabel,
        DownloadFormatters.truncateUrl(sourceUrl),
    )

    AppConfirmDialog(
        title = stringResource(R.string.download_confirm_title),
        message = message,
        confirmText = stringResource(R.string.download_confirm_button),
        cancelText = stringResource(R.string.common_cancel),
        onConfirm = onConfirm,
        onDismiss = onDismiss,
    )
}
