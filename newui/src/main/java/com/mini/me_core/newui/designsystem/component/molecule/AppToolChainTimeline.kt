package com.mini.me_core.newui.designsystem.component.molecule

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Check
import androidx.compose.material.icons.rounded.ErrorOutline
import androidx.compose.material.icons.rounded.Hub
import androidx.compose.material.icons.rounded.Warning
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.mini.me_core.newui.designsystem.token.generated.AppColor
import com.mini.me_core.newui.designsystem.token.generated.AppRadius
import com.mini.me_core.newui.designsystem.token.generated.AppSpacing
import java.util.Locale

/** 工具链步骤状态（对齐 [AppToolCallState] 语义子集，供时间线紧凑呈现）。 */
enum class AppToolChainStepState { Running, AwaitingApproval, Success, Error }

/** 工具链步骤数据模型（分子组 · AppToolChainTimeline）。 */
data class AppToolChainStep(
    val title: String,
    val summary: String? = null,
    val serverPrefix: String? = null,
    val state: AppToolChainStepState = AppToolChainStepState.Success,
    val durationMs: Long? = null,
)

/**
 * 工具链时间线（分子组 · AppToolChainTimeline）：一次任务内**连续多次工具调用**的
 * 串联视图——节点状态色点 + 连接线 + 每步标题/服务器徽标/耗时，头部汇总
 * 「N 次调用 · 总耗时」。
 *
 * 与 [AppToolCallCard] 的分工：单次调用用完整卡（可展开入参/结果/审批），
 * **已完成的工具链**用本时间线做一行一条的紧凑复盘，避免 N 张全卡堆叠刷屏
 * （对齐 LangChain / Vercel AI SDK 的 step 时间线范式）。步骤仍可按需展开明细：
 * 真实链路中每步点击可回退到完整工具卡。
 */
@Composable
fun AppToolChainTimeline(
    steps: List<AppToolChainStep>,
    modifier: Modifier = Modifier,
    label: String = "工具链",
) {
    val cardShape = RoundedCornerShape(AppRadius.Md)
    val totalMs = steps.mapNotNull { it.durationMs }.sum()

    Column(
        modifier = modifier
            .clip(cardShape)
            .background(AppColor.BrandCard)
            .border(1.dp, AppColor.SeparatorOnLight, cardShape),
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = AppSpacing.Md, vertical = AppSpacing.Sm),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Box(
                modifier = Modifier
                    .size(28.dp)
                    .clip(RoundedCornerShape(AppRadius.Sm))
                    .background(AppColor.BrandPrimary.copy(alpha = 0.12f)),
                contentAlignment = Alignment.Center,
            ) {
                Icon(
                    imageVector = Icons.Rounded.Hub,
                    contentDescription = null,
                    tint = AppColor.BrandPrimary,
                    modifier = Modifier.size(16.dp),
                )
            }
            Spacer(Modifier.width(AppSpacing.Sm))
            Column(Modifier.weight(1f)) {
                Text(
                    text = label,
                    style = MaterialTheme.typography.bodyMedium,
                    fontWeight = FontWeight.Medium,
                    color = AppColor.BrandInk,
                )
                val meta = listOfNotNull(
                    "${steps.size} 次调用",
                    if (totalMs > 0) "总耗时 ${formatChainDuration(totalMs)}" else null,
                ).joinToString(" · ")
                Text(
                    text = meta,
                    style = MaterialTheme.typography.labelSmall,
                    color = AppColor.LabelSecondary,
                )
            }
            Spacer(Modifier.width(AppSpacing.Sm))
            ChainStateBadge(steps)
        }

        Box(
            Modifier
                .fillMaxWidth()
                .height(1.dp)
                .background(AppColor.SeparatorOnLight),
        )

        Column(
            modifier = Modifier.padding(horizontal = AppSpacing.Md, vertical = AppSpacing.Sm),
            verticalArrangement = Arrangement.spacedBy(AppSpacing.Sm),
        ) {
            steps.forEachIndexed { index, step ->
                ChainStepRow(step = step, isLast = index == steps.lastIndex)
            }
        }
    }
}

/** 头部汇总徽标：全部成功显示绿勾「完成」，否则显示进行/警示态。 */
@Composable
private fun ChainStateBadge(steps: List<AppToolChainStep>) {
    val allDone = steps.isNotEmpty() && steps.all { it.state == AppToolChainStepState.Success }
    val hasError = steps.any { it.state == AppToolChainStepState.Error }
    val (tint, label) = when {
        hasError -> AppColor.StatusDanger to "有失败"
        allDone -> AppColor.StatusSuccess to "完成"
        else -> AppColor.StatusWarning to "进行中"
    }
    Row(verticalAlignment = Alignment.CenterVertically) {
        Icon(
            imageVector = when {
                hasError -> Icons.Rounded.ErrorOutline
                allDone -> Icons.Rounded.Check
                else -> Icons.Rounded.Warning
            },
            contentDescription = label,
            tint = tint,
            modifier = Modifier.size(14.dp),
        )
        Text(
            text = label,
            style = MaterialTheme.typography.labelSmall,
            color = tint,
            modifier = Modifier.padding(start = AppSpacing.Xs),
        )
    }
}

@Composable
private fun ChainStepRow(step: AppToolChainStep, isLast: Boolean) {
    val tone = when (step.state) {
        AppToolChainStepState.Running -> AppColor.BrandPrimary
        AppToolChainStepState.AwaitingApproval -> AppColor.StatusWarning
        AppToolChainStepState.Success -> AppColor.StatusSuccess
        AppToolChainStepState.Error -> AppColor.StatusDanger
    }
    Row(modifier = Modifier.fillMaxWidth(), verticalAlignment = Alignment.Top) {
        // 左侧轨道：状态节点 + 连接线
        Box(
            modifier = Modifier
                .width(26.dp)
                .fillMaxHeight(),
            contentAlignment = Alignment.TopCenter,
        ) {
            Box(
                modifier = Modifier
                    .padding(top = 2.dp)
                    .size(20.dp)
                    .clip(CircleShape)
                    .background(tone.copy(alpha = 0.14f)),
                contentAlignment = Alignment.Center,
            ) {
                when (step.state) {
                    AppToolChainStepState.Running -> CircularProgressIndicator(
                        modifier = Modifier.size(10.dp),
                        color = tone,
                        strokeWidth = 1.5.dp,
                    )
                    AppToolChainStepState.AwaitingApproval -> Icon(
                        imageVector = Icons.Rounded.Warning,
                        contentDescription = "待审批",
                        tint = tone,
                        modifier = Modifier.size(11.dp),
                    )
                    AppToolChainStepState.Success -> Icon(
                        imageVector = Icons.Rounded.Check,
                        contentDescription = "成功",
                        tint = tone,
                        modifier = Modifier.size(11.dp),
                    )
                    AppToolChainStepState.Error -> Icon(
                        imageVector = Icons.Rounded.ErrorOutline,
                        contentDescription = "失败",
                        tint = tone,
                        modifier = Modifier.size(11.dp),
                    )
                }
            }
            if (!isLast) {
                Box(
                    modifier = Modifier
                        .padding(top = 22.dp)
                        .width(2.dp)
                        .fillMaxHeight()
                        .background(AppColor.SeparatorOnLight),
                )
            }
        }
        Column(
            modifier = Modifier
                .weight(1f)
                .padding(start = AppSpacing.Sm),
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    text = step.title,
                    style = MaterialTheme.typography.bodySmall,
                    fontWeight = FontWeight.Medium,
                    color = AppColor.BrandInk,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.weight(1f, fill = false),
                )
                if (step.serverPrefix != null) {
                    Spacer(Modifier.width(AppSpacing.Xs))
                    ChainServerChip(prefix = step.serverPrefix)
                }
                if (step.durationMs != null) {
                    Spacer(Modifier.width(AppSpacing.Xs))
                    Text(
                        text = formatChainDuration(step.durationMs),
                        style = MaterialTheme.typography.labelSmall,
                        color = AppColor.LabelSecondary,
                    )
                }
            }
            if (step.summary != null) {
                Text(
                    text = step.summary,
                    style = MaterialTheme.typography.labelSmall,
                    color = AppColor.LabelSecondary,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.padding(top = 1.dp),
                )
            }
        }
    }
}

/** 步骤内 MCP 服务器小胶囊（复用工具卡视觉）。 */
@Composable
private fun ChainServerChip(prefix: String) {
    Text(
        text = prefix,
        style = MaterialTheme.typography.labelSmall,
        color = AppColor.BrandPrimary,
        maxLines = 1,
        modifier = Modifier
            .clip(RoundedCornerShape(AppRadius.Sm))
            .background(AppColor.BrandPrimary.copy(alpha = 0.10f))
            .padding(horizontal = AppSpacing.Xs, vertical = 1.dp),
    )
}

/** 耗时格式化（复用工具卡规则）："850ms" / "1.6s"。 */
private fun formatChainDuration(ms: Long): String {
    return if (ms < 1000) {
        String.format(Locale.US, "%dms", ms)
    } else {
        String.format(Locale.US, "%.1fs", ms / 1000.0)
    }
}
