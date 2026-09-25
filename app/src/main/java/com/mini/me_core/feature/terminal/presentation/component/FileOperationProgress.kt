package com.mini.me_core.feature.terminal.presentation.component

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.mini.me_core.R

/**
 * P2 模块8：文件操作进度状态（纯数据，由 ViewModel/调用方持有）。
 */
data class FileOperationState(
    val running: Boolean = false,
    val currentFile: String = "",
    val processed: Int = 0,
    val total: Int = 0,
) {
    companion object {
        val Idle = FileOperationState()
    }
}

/**
 * P2 模块8：文件操作进度浮层。在复制/移动/删除大量文件时显示当前文件名与进度条。
 * 后台协程执行不阻塞浏览；完成后由调用方弹 Snackbar。
 */
@Composable
fun FileOperationProgress(
    state: FileOperationState,
    modifier: Modifier = Modifier,
) {
    if (!state.running) return
    Column(
        modifier = modifier
            .fillMaxWidth()
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        LinearProgressIndicator(
            progress = { if (state.total > 0) state.processed.toFloat() / state.total else 0f },
            modifier = Modifier.fillMaxWidth()
        )
        Text(
            text = stringResource(R.string.fm_operation_progress, state.processed, state.total),
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
        if (state.currentFile.isNotBlank()) {
            Text(
                text = state.currentFile,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 1
            )
        }
    }
}
