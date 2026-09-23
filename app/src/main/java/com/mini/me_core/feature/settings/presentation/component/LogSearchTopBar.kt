package com.mini.me_core.feature.settings.presentation.component

import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material.icons.rounded.EditCalendar
import androidx.compose.material.icons.rounded.SaveAlt
import androidx.compose.material.icons.rounded.Search
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.unit.dp
import com.mini.me_core.R
import com.mini.me_core.core.theme.tokens.LocalAppTheme
import com.mini.me_core.core.theme.tokens.PrimitiveSpacing

/**
 * 日志查看器顶栏。
 *
 * 常规模式：返回 | 运行日志 | 🔍 | ⚙ | 📤
 * 搜索展开模式：[←] [搜索框] [×]，下方一行显示匹配数 + 上一处/下一处。
 * 设计文档 §5.1 / §8。
 */
@Composable
fun LogSearchTopBar(
    searchExpanded: Boolean,
    searchQuery: String,
    totalMatches: Int,
    currentMatchIndex: Int,
    onNavigateBack: () -> Unit,
    onToggleSearch: (Boolean) -> Unit,
    onSearchQuery: (String) -> Unit,
    onPrevMatch: () -> Unit,
    onNextMatch: () -> Unit,
    onOpenFilter: () -> Unit,
    onExport: () -> Unit,
) {
    val colors = LocalAppTheme.current.colors
    Surface(color = colors.surfacePage, modifier = Modifier.fillMaxWidth().statusBarsPadding()) {
        if (!searchExpanded) {
            Row(
                modifier = Modifier.fillMaxWidth().height(44.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                IconButton(onClick = onNavigateBack, modifier = Modifier.size(40.dp)) {
                    Icon(Icons.AutoMirrored.Rounded.ArrowBack, contentDescription = stringResource(R.string.common_back), tint = colors.textSecondary)
                }
                Text(
                    text = stringResource(R.string.log_viewer_title),
                    fontWeight = androidx.compose.ui.text.font.FontWeight.SemiBold,
                    color = colors.textPrimary,
                    modifier = Modifier.weight(1f),
                )
                IconButton(onClick = { onToggleSearch(true) }, modifier = Modifier.size(40.dp)) {
                    Icon(Icons.Rounded.Search, contentDescription = stringResource(R.string.log_search_expanded_hint), tint = colors.textSecondary)
                }
                IconButton(onClick = onOpenFilter, modifier = Modifier.size(40.dp)) {
                    Icon(Icons.Rounded.EditCalendar, contentDescription = stringResource(R.string.log_filter_sheet_title), tint = colors.textSecondary)
                }
                IconButton(onClick = onExport, modifier = Modifier.size(40.dp)) {
                    Icon(Icons.Rounded.SaveAlt, contentDescription = stringResource(R.string.logs_export_action), tint = colors.textSecondary)
                }
                Spacer(Modifier.width(PrimitiveSpacing.Sm))
            }
        } else {
            // 搜索展开模式
            Row(
                modifier = Modifier.fillMaxWidth().padding(vertical = PrimitiveSpacing.Xxs),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                IconButton(onClick = { onToggleSearch(false) }, modifier = Modifier.size(40.dp)) {
                    Icon(Icons.AutoMirrored.Rounded.ArrowBack, contentDescription = stringResource(R.string.common_back), tint = colors.textSecondary)
                }
                OutlinedTextField(
                    value = searchQuery,
                    onValueChange = onSearchQuery,
                    modifier = Modifier.weight(1f),
                    placeholder = { Text(stringResource(R.string.log_search_expanded_hint)) },
                    singleLine = true,
                    keyboardOptions = KeyboardOptions(imeAction = ImeAction.Search),
                    trailingIcon = {
                        if (searchQuery.isNotEmpty()) {
                            IconButton(onClick = { onSearchQuery("") }) {
                                Icon(Icons.Rounded.Close, contentDescription = stringResource(R.string.common_close), modifier = Modifier.size(18.dp))
                            }
                        }
                    },
                )
            }
            // 匹配计数 + 上一处/下一处
            Row(
                modifier = Modifier.fillMaxWidth().padding(horizontal = PrimitiveSpacing.Sm),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(
                    text = if (totalMatches > 0) stringResource(R.string.log_match_count, totalMatches) else stringResource(R.string.log_match_none),
                    style = MaterialTheme.typography.bodySmall,
                    color = colors.textSecondary,
                    modifier = Modifier.weight(1f),
                )
                TextButton(onClick = onPrevMatch, enabled = totalMatches > 0) {
                    Text(stringResource(R.string.log_match_prev))
                }
                TextButton(onClick = onNextMatch, enabled = totalMatches > 0) {
                    Text(stringResource(R.string.log_match_next))
                }
            }
        }
    }
}
