package com.mini.me_core.core.download

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.Alignment
import androidx.compose.ui.draw.clip
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import com.mini.me_core.R
import com.mini.me_core.core.theme.Spacing
import com.mini.me_core.core.theme.tokens.LocalAppTheme
import com.mini.me_core.core.theme.tokens.LocalComponentTokens
import com.mini.me_core.core.theme.tokens.LocalCornerRadius
import com.mini.me_core.core.theme.tokens.PrimitiveSpacing

/**
 * 通用下载进度窗口。
 *
 * 根据 [task.status] 自动切换三种视图：
 *  - [DownloadStatus.DOWNLOADING]：文件名 + 进度条 + 百分比 + 已下载/总大小 + 速度 + 剩余时间 + 取消按钮；
 *    当 totalBytes <= 0 时使用不确定进度条（Indeterminate），供远程同步等无进度回调的场景使用。
 *  - [DownloadStatus.COMPLETED]：「下载完成」+「打开文件」/「关闭」。
 *  - [DownloadStatus.FAILED]：「下载失败」+ 错误信息 +「重试」/「关闭」。
 *
 * 样式跟随 AppDialog / AppButton，使用 Semantic Token 颜色，无硬编码颜色，无 emoji。
 *
 * @param task 当前下载任务
 * @param onCancel 取消下载回调（仅下载中/暂停时显示）
 * @param onRetry 重试回调（仅失败时显示；null 表示不显示）
 * @param onOpen 打开已下载文件回调（仅完成时显示；null 表示不显示）
 * @param onDismiss 关闭弹窗回调（完成/失败时关闭弹窗本身）
 * @param onPause 暂停回调（仅下载中且非 null 时显示「暂停」按钮）
 * @param onResume 继续回调（仅暂停中且非 null 时显示「继续」按钮）
 */
@Composable
fun DownloadProgressDialog(
    task: DownloadTask,
    onCancel: () -> Unit,
    onRetry: (() -> Unit)? = null,
    onOpen: (() -> Unit)? = null,
    onDismiss: () -> Unit,
    onPause: (() -> Unit)? = null,
    onResume: (() -> Unit)? = null,
) {
    val colors = LocalAppTheme.current.colors
    val indeterminate = task.status == DownloadStatus.DOWNLOADING && task.totalBytes <= 0L

    AlertDialog(
        onDismissRequest = onDismiss,
        title = {
            val titleText = when (task.status) {
                DownloadStatus.COMPLETED -> stringResource(R.string.download_completed_title)
                DownloadStatus.FAILED -> stringResource(R.string.download_failed_title)
                DownloadStatus.PAUSED -> stringResource(R.string.download_paused_title)
                else -> stringResource(R.string.download_progress_title)
            }
            Text(
                text = titleText,
                fontSize = LocalComponentTokens.current.text.titleMediumFontSize,
                fontWeight = FontWeight.SemiBold,
                color = colors.textPrimary,
            )
        },
        text = {
            when (task.status) {
                DownloadStatus.COMPLETED -> CompletedContent(task)
                DownloadStatus.FAILED -> FailedContent(task)
                else -> DownloadingContent(task, indeterminate)
            }
        },
        confirmButton = {
            Row(horizontalArrangement = Arrangement.End, verticalAlignment = Alignment.CenterVertically) {
                when (task.status) {
                    DownloadStatus.COMPLETED -> {
                        TextButton(onClick = onDismiss) {
                            Text(
                                text = stringResource(R.string.download_close),
                                color = colors.textSecondary,
                            )
                        }
                        if (onOpen != null) {
                            Spacer(Modifier.width(PrimitiveSpacing.Sm))
                            TextButton(onClick = onOpen) {
                                Text(
                                    text = stringResource(R.string.download_completed_open),
                                    color = colors.brandPrimary,
                                    fontWeight = FontWeight.SemiBold,
                                )
                            }
                        }
                    }
                    DownloadStatus.FAILED -> {
                        TextButton(onClick = onDismiss) {
                            Text(
                                text = stringResource(R.string.download_close),
                                color = colors.textSecondary,
                            )
                        }
                        if (onRetry != null) {
                            Spacer(Modifier.width(PrimitiveSpacing.Sm))
                            TextButton(onClick = onRetry) {
                                Text(
                                    text = stringResource(R.string.download_failed_retry),
                                    color = colors.brandPrimary,
                                    fontWeight = FontWeight.SemiBold,
                                )
                            }
                        }
                    }
                    else -> {
                        // 暂停中：显示「继续」
                        if (task.status == DownloadStatus.PAUSED && onResume != null) {
                            TextButton(onClick = onResume) {
                                Text(
                                    text = stringResource(R.string.download_resume),
                                    color = colors.brandPrimary,
                                    fontWeight = FontWeight.SemiBold,
                                )
                            }
                            Spacer(Modifier.width(PrimitiveSpacing.Sm))
                        }
                        // 下载中：显示「暂停」
                        if (task.status == DownloadStatus.DOWNLOADING && onPause != null) {
                            TextButton(onClick = onPause) {
                                Text(
                                    text = stringResource(R.string.download_pause),
                                    color = colors.brandPrimary,
                                )
                            }
                            Spacer(Modifier.width(PrimitiveSpacing.Sm))
                        }
                        // 取消按钮
                        TextButton(onClick = onCancel) {
                            Text(
                                text = stringResource(R.string.download_progress_cancel),
                                color = colors.error,
                            )
                        }
                    }
                }
            }
        },
        shape = RoundedCornerShape(LocalCornerRadius.current.xl),
        containerColor = colors.surfaceOverlay,
    )
}

@Composable
private fun DownloadingContent(task: DownloadTask, indeterminate: Boolean) {
    val colors = LocalAppTheme.current.colors
    Column(verticalArrangement = Arrangement.spacedBy(PrimitiveSpacing.Sm)) {
        // 文件名
        Text(
            text = task.title,
            fontSize = LocalComponentTokens.current.text.bodyMediumFontSize,
            color = colors.textPrimary,
            maxLines = 2,
        )

        if (indeterminate) {
            LinearProgressIndicator(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(PrimitiveSpacing.Sm)),
            )
            Text(
                text = stringResource(R.string.download_indeterminate),
                fontSize = LocalComponentTokens.current.text.bodySmallFontSize,
                color = colors.textSecondary,
            )
        } else {
            val percent = if (task.totalBytes > 0) {
                ((task.downloadedBytes * 100f) / task.totalBytes).coerceIn(0f, 100f)
            } else 0f
            // 百分比 + 速度
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
            ) {
                Text(
                    text = "${percent.toInt()}%",
                    fontSize = LocalComponentTokens.current.text.bodyMediumFontSize,
                    fontWeight = FontWeight.SemiBold,
                    color = colors.textPrimary,
                )
                if (task.speedBytesPerSec > 0) {
                    Text(
                        text = stringResource(
                            R.string.download_progress_speed,
                            DownloadFormatters.formatSize(task.speedBytesPerSec),
                        ),
                        fontSize = LocalComponentTokens.current.text.bodySmallFontSize,
                        color = colors.brandPrimary,
                    )
                }
            }
            LinearProgressIndicator(
                progress = { percent / 100f },
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(PrimitiveSpacing.Sm)),
            )
            // 已下载 / 总大小
            val totalText = if (task.totalBytes > 0) {
                DownloadFormatters.formatSize(task.totalBytes)
            } else {
                stringResource(R.string.download_size_unknown)
            }
            Text(
                text = "${DownloadFormatters.formatSize(task.downloadedBytes)} / $totalText",
                fontSize = LocalComponentTokens.current.text.bodySmallFontSize,
                color = colors.textSecondary,
            )
            // 预计剩余时间
            DownloadFormatters.formatRemaining(task.downloadedBytes, task.totalBytes, task.speedBytesPerSec)?.let { rem ->
                Text(
                    text = stringResource(R.string.download_progress_remaining, rem),
                    fontSize = LocalComponentTokens.current.text.bodySmallFontSize,
                    color = colors.textSecondary,
                )
            }
        }
    }
}

@Composable
private fun CompletedContent(task: DownloadTask) {
    val colors = LocalAppTheme.current.colors
    Column(verticalArrangement = Arrangement.spacedBy(PrimitiveSpacing.Sm)) {
        Text(
            text = task.title,
            fontSize = LocalComponentTokens.current.text.bodyMediumFontSize,
            color = colors.textPrimary,
            maxLines = 2,
        )
        task.localPath?.let { path ->
            Text(
                text = path,
                fontSize = LocalComponentTokens.current.text.bodySmallFontSize,
                color = colors.textSecondary,
                maxLines = 2,
            )
        }
    }
}

@Composable
private fun FailedContent(task: DownloadTask) {
    val colors = LocalAppTheme.current.colors
    Column(verticalArrangement = Arrangement.spacedBy(PrimitiveSpacing.Sm)) {
        Text(
            text = task.title,
            fontSize = LocalComponentTokens.current.text.bodyMediumFontSize,
            color = colors.textPrimary,
            maxLines = 2,
        )
        Text(
            text = task.errorMessage ?: stringResource(R.string.download_size_unknown),
            fontSize = LocalComponentTokens.current.text.bodySmallFontSize,
            color = colors.error,
            maxLines = 4,
        )
    }
}
