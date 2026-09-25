package com.mini.me_core.feature.agent.presentation.component

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
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
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
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.mini.me_core.R

/**
 * MiniMe Token 消耗指示器（F2.6）。
 *
 * 紧凑模式：`当前 / 上下文窗口` + 3dp 高、60dp 宽迷你进度条；点击展开详情
 * （输入/输出/缓存命中/估算费用/本轮消耗）。进度条颜色：
 *  <50% primary / 50-80% tertiary / >80% error。
 *
 * 数据优先取 API usage 字段；无 usage 时由宿主按字符数估算并标注「估算」。
 */
@Composable
fun TokenUsageIndicator(
    inputTokens: Int,
    outputTokens: Int,
    contextWindow: Int,
    modifier: Modifier = Modifier,
    cachedTokens: Int = 0,
    thisTurnTokens: Int = 0,
    estimated: Boolean = false,
) {
    var expanded by remember { mutableStateOf(false) }
    val ratio = if (contextWindow > 0) inputTokens.toFloat() / contextWindow else 0f
    val barColor = when {
        ratio >= 0.8f -> MaterialTheme.colorScheme.error
        ratio >= 0.5f -> MaterialTheme.colorScheme.tertiary
        else -> MaterialTheme.colorScheme.primary
    }

    Column(modifier) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier.clickable { expanded = !expanded }
        ) {
            Text(
                text = "${formatTokenCountShort(inputTokens)} / ${formatTokenCountShort(contextWindow)}" +
                    if (estimated) " " + stringResource(R.string.token_usage_estimated) else "",
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Spacer(Modifier.width(6.dp))
            // 迷你进度条 3dp 高、60dp 宽
            Box(
                Modifier
                    .width(60.dp)
                    .height(3.dp)
                    .clip(RoundedCornerShape(2.dp))
                    .background(MaterialTheme.colorScheme.surfaceVariant)
            ) {
                Box(
                    Modifier
                        .fillMaxWidth(ratio.coerceIn(0f, 1f))
                        .height(3.dp)
                        .background(barColor)
                )
            }
        }
        if (expanded) {
            Surface(
                shape = RoundedCornerShape(8.dp),
                color = MaterialTheme.colorScheme.surfaceVariant,
                modifier = Modifier.padding(top = 4.dp)
            ) {
                Column(Modifier.padding(8.dp), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                    Text(stringResource(R.string.token_usage_title), style = MaterialTheme.typography.labelMedium)
                    Text(stringResource(R.string.token_usage_input, formatTokenCountShort(inputTokens)), style = MaterialTheme.typography.bodySmall)
                    Text(stringResource(R.string.token_usage_output, formatTokenCountShort(outputTokens)), style = MaterialTheme.typography.bodySmall)
                    if (cachedTokens > 0) Text(stringResource(R.string.token_usage_cache, formatTokenCountShort(cachedTokens)), style = MaterialTheme.typography.bodySmall)
                    if (thisTurnTokens > 0) Text(stringResource(R.string.token_usage_this_turn, formatTokenCountShort(thisTurnTokens)), style = MaterialTheme.typography.bodySmall)
                }
            }
        }
    }
}

/** 紧凑格式化：2300 -> 2.3K，128000 -> 128K。 */
internal fun formatTokenCountShort(tokens: Int): String = when {
    tokens >= 1_000_000 -> "%.1fM".format(tokens / 1_000_000.0)
    tokens >= 1_000 -> "%.0fK".format(tokens / 1_000.0)
    else -> tokens.toString()
}
