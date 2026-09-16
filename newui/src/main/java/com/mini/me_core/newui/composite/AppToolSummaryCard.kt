package com.mini.me_core.newui.composite

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
import androidx.compose.material.icons.rounded.KeyboardArrowDown
import androidx.compose.material.icons.rounded.Summarize
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.mini.me_core.newui.designsystem.token.generated.AppColor
import com.mini.me_core.newui.designsystem.token.generated.AppRadius
import com.mini.me_core.newui.designsystem.token.generated.AppSizing
import com.mini.me_core.newui.designsystem.token.generated.AppSpacing
import com.mini.me_core.newui.designsystem.token.generated.AppStroke

/** 工具结果摘要状态：正在总结（流式三点） / 完成（定格文本）。 */
enum class AppToolSummaryState { Summarizing, Done }

/**
 * 工具结果摘要卡（分子组 · AppToolSummaryCard）：一次工具链执行完毕后，
 * Agent **回填正文前**对 N 个工具结果做意图归纳的过渡卡（对齐 Claude Code 的
 * "tool result 折叠后一句话"模式，防止 N 张工具卡的原始输出直接刷进正文）。
 *
 * - [AppToolSummaryState.Summarizing]：标题行 + 「正在总结 N 个工具结果…」+ [AppTypingIndicator]，
 *   视觉上承接工具链收尾，给正文一句过渡。
 * - [AppToolSummaryState.Done]：摘要正文（[text]），超过 [maxLines] 自动折叠可展开；
 *   头部保留 `N 个结果` 计数徽标，暗示"下面这屏来自哪些工具"。
 *
 * 真实链路：Summary 由 agent 的总结文本（toolResult 回填前的 thinking/summary 事件）驱动，
 * 本卡与 [AppToolChainTimeline] 搭配：先时间线复盘 → 再摘要 → 再正文。
 *
 * @since 0.1.0-experimental
 */
@Composable
fun AppToolSummaryCard(
    text: String,
    modifier: Modifier = Modifier,
    state: AppToolSummaryState = AppToolSummaryState.Done,
    toolCount: Int = 1,
    maxLines: Int = 4,
) {
    val cardShape = RoundedCornerShape(AppRadius.Md)
    Column(
        modifier = modifier
            .clip(cardShape)
            .background(appPalette().card)
            .border(AppStroke.Thin, appPalette().separator, cardShape),
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = AppSpacing.Md, vertical = AppSpacing.Sm),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Box(
                modifier = Modifier
                    .size(AppSizing.IconXl)
                    .clip(RoundedCornerShape(AppRadius.Sm))
                    .background(appPalette().primary.copy(alpha = 0.12f)),
                contentAlignment = Alignment.Center,
            ) {
                // 装饰图标：旁侧已有文字/语义，跳过无障碍
                Icon(
                    imageVector = Icons.Rounded.Summarize,
                    contentDescription = null,
                    tint = appPalette().primary,
                    modifier = Modifier.size(AppSizing.IconXs),
                )
            }
            Spacer(Modifier.width(AppSpacing.Sm))
            Text(
                text = "工具结果摘要",
                style = MaterialTheme.typography.bodyMedium,
                fontWeight = FontWeight.Medium,
                color = appPalette().ink,
                modifier = Modifier.weight(1f),
            )
            Text(
                text = "$toolCount 个结果",
                style = MaterialTheme.typography.labelSmall,
                color = appPalette().primary,
                maxLines = 1,
                modifier = Modifier
                    .clip(RoundedCornerShape(AppRadius.Sm))
                    .background(appPalette().primary.copy(alpha = 0.10f))
                    .padding(horizontal = AppSpacing.Xs, vertical = 1.dp),
            )
        }

        Box(
            Modifier
                .fillMaxWidth()
                .height(AppStroke.Thin)
                .background(appPalette().separator),
        )

        when (state) {
            AppToolSummaryState.Summarizing -> Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = AppSpacing.Md, vertical = AppSpacing.Sm),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                AppTypingIndicator(dotColor = appPalette().primary, dotSize = 4.dp)
                Text(
                    text = "正在总结工具结果…",
                    style = MaterialTheme.typography.bodySmall,
                    color = appPalette().labelSecondary,
                    modifier = Modifier.padding(start = AppSpacing.Sm),
                )
            }

            AppToolSummaryState.Done -> SummaryTextBlock(text = text, maxLines = maxLines)
        }
    }
}

/** 摘要正文：超过 [maxLines] 行折叠，点击展开/收起。 */
@Composable
private fun SummaryTextBlock(text: String, maxLines: Int) {
    var expanded by remember { mutableStateOf(false) }
    val lineCount = text.count { it == '\n' } + 1
    val foldable = lineCount > maxLines

    Column(Modifier.fillMaxWidth()) {
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .clickable(enabled = foldable) { expanded = !expanded }
                .padding(horizontal = AppSpacing.Md, vertical = AppSpacing.Sm),
        ) {
            Text(
                text = text,
                style = MaterialTheme.typography.bodySmall,
                color = appPalette().ink,
                maxLines = if (expanded) Int.MAX_VALUE else maxLines,
                overflow = TextOverflow.Ellipsis,
            )
        }
        if (foldable) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .clickable { expanded = !expanded }
                    .padding(horizontal = AppSpacing.Md, vertical = AppSpacing.Xs),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(
                    text = if (expanded) "收起" else "展开全部 · $lineCount 行",
                    style = MaterialTheme.typography.labelSmall,
                    color = appPalette().labelSecondary,
                    modifier = Modifier.weight(1f),
                )
                // 装饰图标：旁侧已有文字/语义，跳过无障碍
                Icon(
                    imageVector = Icons.Rounded.KeyboardArrowDown,
                    contentDescription = null,
                    tint = appPalette().labelSecondary,
                    modifier = Modifier
                        .size(AppSizing.IconXs)
                        .rotate(if (expanded) 180f else 0f),
                )
            }
        }
    }
}
