package com.mini.me_core.feature.agent.presentation.component

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material.icons.rounded.Star
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.mini.me_core.R

/** 一个对比面板的数据。 */
data class ComparePanel(
    val modelName: String,
    val content: String,
)

/**
 * MiniMe 多模型对比视图（F2.9）。
 *
 * 输入框旁开启对比模式后，选择多个模型（最多 3）发送，界面变为左右分屏（2 个）
 * 或三栏（3 个），每个模型独立流式输出、独立滚动。顶栏提供「退出对比」与
 * 「同步滚动」开关；每个面板顶部显示模型名 + 关闭按钮，底部提供 1-5 星评分
 * 与「使用此回答」。
 */
@Composable
fun ChatCompareView(
    panels: List<ComparePanel>,
    onExitCompare: () -> Unit,
    onClosePanel: (Int) -> Unit,
    onUseAnswer: (Int) -> Unit,
    modifier: Modifier = Modifier,
) {
    var syncScroll by remember { mutableStateOf(false) }
    val scrollStates = panels.map { rememberScrollState() }

    Column(modifier.fillMaxSize()) {
        // 顶栏：退出对比 + 同步滚动
        Row(
            modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 8.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween,
        ) {
            TextButton(onClick = onExitCompare) {
                Text(stringResource(R.string.compare_exit))
            }
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(stringResource(R.string.compare_sync_scroll), style = MaterialTheme.typography.labelMedium)
                Spacer(Modifier.width(8.dp))
                Switch(checked = syncScroll, onCheckedChange = { syncScroll = it })
            }
        }
        HorizontalDivider()
        // 分屏栏
        Row(
            modifier = Modifier.fillMaxSize().padding(8.dp),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            panels.forEachIndexed { index, panel ->
                ComparePanelColumn(
                    panel = panel,
                    scrollState = scrollStates.getOrElse(index) { rememberScrollState() },
                    syncScroll = syncScroll,
                    onClose = { onClosePanel(index) },
                    onUseAnswer = { onUseAnswer(index) },
                    modifier = Modifier.weight(1f),
                )
            }
        }
    }
}

@Composable
private fun ComparePanelColumn(
    panel: ComparePanel,
    scrollState: androidx.compose.foundation.ScrollState,
    syncScroll: Boolean,
    onClose: () -> Unit,
    onUseAnswer: () -> Unit,
    modifier: Modifier = Modifier,
) {
    var rating by remember { mutableIntStateOf(0) }
    Surface(
        modifier = modifier.fillMaxHeight(),
        shape = MaterialTheme.shapes.medium,
        color = MaterialTheme.colorScheme.surfaceVariant,
    ) {
        Column {
            // 面板头：模型名 + 关闭
            Row(
                modifier = Modifier.fillMaxWidth().padding(8.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween,
            ) {
                Text(panel.modelName, style = MaterialTheme.typography.labelMedium, maxLines = 1)
                IconButton(onClick = onClose, modifier = Modifier.padding(0.dp)) {
                    Icon(Icons.Rounded.Close, contentDescription = null, modifier = Modifier.width(16.dp).height(16.dp))
                }
            }
            HorizontalDivider()
            // 独立滚动内容
            Box(Modifier.weight(1f).verticalScroll(scrollState)) {
                Text(
                    text = panel.content.ifBlank { stringResource(R.string.compare_waiting) },
                    modifier = Modifier.padding(12.dp),
                    style = MaterialTheme.typography.bodyMedium,
                )
            }
            // 评分 + 使用
            Column(Modifier.padding(8.dp)) {
                Row(horizontalArrangement = Arrangement.spacedBy(2.dp)) {
                    repeat(5) { star ->
                        IconButton(onClick = { rating = star + 1 }, modifier = Modifier.padding(0.dp).width(28.dp).height(28.dp)) {
                            Icon(
                                Icons.Rounded.Star,
                                contentDescription = stringResource(R.string.compare_score_hint),
                                tint = if (star < rating) MaterialTheme.colorScheme.tertiary
                                else MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.3f),
                                modifier = Modifier.width(20.dp).height(20.dp),
                            )
                        }
                    }
                }
                OutlinedButton(onClick = onUseAnswer, modifier = Modifier.fillMaxWidth()) {
                    Text(stringResource(R.string.compare_use_answer))
                }
            }
        }
    }
}
