package com.mini.me_core.feature.settings.presentation.component

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.KeyboardArrowDown
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import com.mini.me_core.R
import com.mini.me_core.core.theme.tokens.PrimitiveSpacing

/**
 * 底部状态栏：左侧显示行数、中间日期范围/文件数、右侧「跳至」菜单。
 * 设计文档 §5.5。
 *
 * @param onNextError 点击「下一个 ERROR」（由父组件滚动定位）
 */
@Composable
fun LogBottomStatusBar(
    shownLines: Int,
    totalLines: Int,
    dateRangeLabel: String,
    fileCount: Int,
    liveTailEnabled: Boolean,
    onNextError: () -> Unit,
    modifier: Modifier = Modifier,
) {
    var jumpMenuExpanded by remember { mutableStateOf(false) }
    Column(modifier = modifier) {
        HorizontalDivider()
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = PrimitiveSpacing.Sm, vertical = PrimitiveSpacing.Xxs),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween,
        ) {
            Text(
                text = stringResource(R.string.log_status_shown, shownLines.toString(), totalLines.toString()),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Text(
                text = "$dateRangeLabel · ${stringResource(R.string.log_status_files, fileCount)}",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Row(verticalAlignment = Alignment.CenterVertically) {
                if (liveTailEnabled) {
                    Text(
                        text = stringResource(R.string.log_live_tail) + " ●",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.error,
                    )
                }
                Box {
                    TextButton(onClick = { jumpMenuExpanded = true }) {
                        Text(stringResource(R.string.log_jump), style = MaterialTheme.typography.bodySmall)
                        Icon(Icons.Rounded.KeyboardArrowDown, contentDescription = null)
                    }
                    DropdownMenu(expanded = jumpMenuExpanded, onDismissRequest = { jumpMenuExpanded = false }) {
                        DropdownMenuItem(
                            text = { Text(stringResource(R.string.log_jump_next_error)) },
                            onClick = { jumpMenuExpanded = false; onNextError() },
                        )
                    }
                }
            }
        }
    }
}

@androidx.compose.ui.tooling.preview.Preview(showBackground = true, widthDp = 380, heightDp = 80)
@Composable
private fun LogBottomStatusBarPreview() {
    com.mini.me_core.core.theme.MiniMeTheme(darkTheme = false) {
        LogBottomStatusBar(
            shownLines = 45,
            totalLines = 234,
            dateRangeLabel = "近 3 天",
            fileCount = 3,
            liveTailEnabled = true,
            onNextError = {},
        )
    }
}
