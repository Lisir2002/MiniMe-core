package com.mini.me_core.newui.designsystem.component.molecule

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkVertically
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
import androidx.compose.material.icons.rounded.CheckCircle
import androidx.compose.material.icons.rounded.ErrorOutline
import androidx.compose.material.icons.rounded.KeyboardArrowDown
import androidx.compose.material.icons.rounded.Terminal
import androidx.compose.material.icons.rounded.Warning
import androidx.compose.material3.CircularProgressIndicator
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
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.mini.me_core.newui.designsystem.token.generated.AppColor
import com.mini.me_core.newui.designsystem.token.generated.AppRadius
import com.mini.me_core.newui.designsystem.token.generated.AppSpacing
import java.util.Locale

/**
 * 工具调用生命周期状态：参数流式生成 → 执行中 → 待人工审批 / 成功 / 失败。
 */
enum class AppToolCallState { Streaming, Running, AwaitingApproval, Success, Error }

/**
 * 工具调用卡（分子组 · AppToolCallCard）：AI 对话流中工具执行的透明化展示层，
 * 对齐 Vercel AI SDK / shadcn Tool / LobeHub Inspector 六大 surface 范式：
 *
 * - 头部一句话（Inspector）：图标块 + 工具名 + 目标摘要（如 "执行命令 · ./gradlew assembleDebug"），
 *   不裸 dump 原始参数；[serverPrefix] 展示 MCP 服务器徽标（如 `github`）。
 * - 状态徽标：[AppToolCallState.Streaming]（三点脉动）/ [AppToolCallState.Running]（旋转）/
 *   [AppToolCallState.AwaitingApproval]（琥珀警示）/ [AppToolCallState.Success]（绿勾）/
 *   [AppToolCallState.Error]（红叉 + 红色描边）。
 * - 诚实耗时：完成态经 [durationMs] 展示 "· 1.6s"。
 * - 实时输出（Streaming）：[streamOutput] 在 Running 态直接内联展示等宽终端块 + 闪烁光标，
 *   用于 Shell 命令 stdout 等需要"看着它跑"的场景。
 * - 人工审批（Intervention）：[AppToolCallState.AwaitingApproval] 态在卡片尾部渲染
 *   允许 / 拒绝操作行（[onApprove] / [onReject]，附 [approvalHint] 说明），
 *   对齐 assistant-ui 的 `requires-action` 与 LobeHub humanIntervention。
 * - 可展开入参 / 结果：默认折叠，点击展开（旋转箭头 + 垂直展开动画）。
 *
 * 建议用法：作为消息流 marker 层的一项（与 [AppChatMarker] 并列），
 * 连续多个工具调用按时间顺序逐张渲染，不用一个"工具伞"包裹。
 */
@Composable
fun AppToolCallCard(
    title: String,
    modifier: Modifier = Modifier,
    summary: String? = null,
    state: AppToolCallState = AppToolCallState.Success,
    serverPrefix: String? = null,
    durationMs: Long? = null,
    input: String? = null,
    output: String? = null,
    streamOutput: String? = null,
    approvalHint: String? = null,
    onApprove: (() -> Unit)? = null,
    onReject: (() -> Unit)? = null,
    leadingIcon: ImageVector = Icons.Rounded.Terminal,
) {
    var inputExpanded by remember { mutableStateOf(false) }
    var outputExpanded by remember { mutableStateOf(false) }
    val statusColor = when (state) {
        AppToolCallState.Streaming, AppToolCallState.Running -> AppColor.BrandPrimary
        AppToolCallState.AwaitingApproval -> AppColor.StatusWarning
        AppToolCallState.Success -> AppColor.StatusSuccess
        AppToolCallState.Error -> AppColor.StatusDanger
    }
    val cardShape = RoundedCornerShape(AppRadius.Md)
    val isError = state == AppToolCallState.Error

    Column(
        modifier = modifier
            .clip(cardShape)
            .background(AppColor.BrandCard)
            .border(
                width = 1.dp,
                color = if (isError) {
                    AppColor.StatusDanger.copy(alpha = 0.45f)
                } else {
                    AppColor.SeparatorOnLight
                },
                shape = cardShape,
            ),
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = AppSpacing.Md, vertical = AppSpacing.Sm),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            // 图标块：状态色浅底 + 状态色图标
            Box(
                modifier = Modifier
                    .size(28.dp)
                    .clip(RoundedCornerShape(AppRadius.Sm))
                    .background(statusColor.copy(alpha = 0.12f)),
                contentAlignment = Alignment.Center,
            ) {
                Icon(
                    imageVector = leadingIcon,
                    contentDescription = null,
                    tint = statusColor,
                    modifier = Modifier.size(16.dp),
                )
            }
            Spacer(Modifier.width(AppSpacing.Sm))
            Column(Modifier.weight(1f)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        text = title,
                        style = MaterialTheme.typography.bodyMedium,
                        fontWeight = FontWeight.Medium,
                        color = AppColor.BrandInk,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                    if (serverPrefix != null) {
                        Spacer(Modifier.width(AppSpacing.Xs))
                        ServerChip(prefix = serverPrefix)
                    }
                }
                val meta = listOfNotNull(summary, durationMs?.let { formatDuration(it) })
                    .joinToString(" · ")
                if (meta.isNotEmpty()) {
                    Text(
                        text = meta,
                        style = MaterialTheme.typography.labelSmall,
                        color = if (isError) AppColor.StatusDanger else AppColor.LabelSecondary,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                }
            }
            Spacer(Modifier.width(AppSpacing.Sm))
            ToolCallStatusBadge(state = state, color = statusColor)
        }
        // Streaming：执行中实时输出，无需展开，直接"看着它跑"
        if (state == AppToolCallState.Running && streamOutput != null) {
            CardDivider()
            StreamBlock(text = streamOutput)
        }
        if (input != null) {
            CardDivider()
            ExpandableSection(
                label = "入参",
                expanded = inputExpanded,
                onToggle = { inputExpanded = !inputExpanded },
            ) {
                JsonBlock(text = input)
            }
        }
        if (output != null) {
            CardDivider()
            ExpandableSection(
                label = "结果",
                expanded = outputExpanded,
                onToggle = { outputExpanded = !outputExpanded },
            ) {
                JsonBlock(text = output)
            }
        }
        // Intervention：待人工审批 → 允许 / 拒绝操作行
        if (state == AppToolCallState.AwaitingApproval && (onApprove != null || onReject != null)) {
            CardDivider()
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = AppSpacing.Md, vertical = AppSpacing.Sm),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(AppSpacing.Sm),
            ) {
                if (approvalHint != null) {
                    Text(
                        text = approvalHint,
                        style = MaterialTheme.typography.labelSmall,
                        color = AppColor.LabelSecondary,
                        modifier = Modifier.weight(1f),
                    )
                } else {
                    Spacer(Modifier.weight(1f))
                }
                if (onReject != null) {
                    AppButton(
                        text = "拒绝",
                        onClick = onReject,
                        variant = AppButtonVariant.Outlined,
                    )
                }
                if (onApprove != null) {
                    AppButton(
                        text = "允许",
                        onClick = onApprove,
                        variant = AppButtonVariant.Primary,
                    )
                }
            }
        }
    }
}

/** 状态徽标：流式三点 / 运行中旋转 / 待审批琥珀警示 / 成功绿勾 / 失败红叉。 */
@Composable
private fun ToolCallStatusBadge(state: AppToolCallState, color: Color) {
    when (state) {
        AppToolCallState.Streaming -> AppTypingIndicator(dotColor = color, dotSize = 4.dp)
        AppToolCallState.Running -> CircularProgressIndicator(
            modifier = Modifier.size(14.dp),
            color = color,
            strokeWidth = 2.dp,
        )
        AppToolCallState.AwaitingApproval -> Icon(
            imageVector = Icons.Rounded.Warning,
            contentDescription = "待审批",
            tint = color,
            modifier = Modifier.size(16.dp),
        )
        AppToolCallState.Success -> Icon(
            imageVector = Icons.Rounded.CheckCircle,
            contentDescription = "成功",
            tint = color,
            modifier = Modifier.size(16.dp),
        )
        AppToolCallState.Error -> Icon(
            imageVector = Icons.Rounded.ErrorOutline,
            contentDescription = "失败",
            tint = color,
            modifier = Modifier.size(16.dp),
        )
    }
}

/** MCP 服务器徽标：品牌色浅底小胶囊（如 `github`）。 */
@Composable
private fun ServerChip(prefix: String) {
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

/** 可展开区：label 行 + 旋转箭头 + 垂直展开动画内容。 */
@Composable
private fun ExpandableSection(
    label: String,
    expanded: Boolean,
    onToggle: () -> Unit,
    content: @Composable () -> Unit,
) {
    Column(Modifier.fillMaxWidth()) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .clickable(onClick = onToggle)
                .padding(horizontal = AppSpacing.Md, vertical = AppSpacing.Xs),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(
                text = label,
                style = MaterialTheme.typography.labelSmall,
                color = AppColor.LabelSecondary,
                modifier = Modifier.weight(1f),
            )
            val rotation by animateFloatAsState(
                targetValue = if (expanded) 180f else 0f,
                label = "expandChevron",
            )
            Icon(
                imageVector = Icons.Rounded.KeyboardArrowDown,
                contentDescription = if (expanded) "收起$label" else "展开$label",
                tint = AppColor.LabelSecondary,
                modifier = Modifier
                    .size(16.dp)
                    .rotate(rotation),
            )
        }
        AnimatedVisibility(
            visible = expanded,
            enter = expandVertically() + fadeIn(),
            exit = shrinkVertically() + fadeOut(),
        ) {
            Box(
                modifier = Modifier.padding(
                    start = AppSpacing.Md,
                    end = AppSpacing.Md,
                    bottom = AppSpacing.Md,
                ),
            ) {
                content()
            }
        }
    }
}

/** 入参 / 结果内容块：等宽字体 + 浅底代码区。 */
@Composable
private fun JsonBlock(text: String) {
    Text(
        text = text,
        style = MaterialTheme.typography.bodySmall.copy(fontFamily = FontFamily.Monospace),
        color = AppColor.BrandInk,
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(AppRadius.Sm))
            .background(AppColor.BrandSurfaceDim)
            .padding(AppSpacing.Sm),
    )
}

/** 实时输出块：深色终端底 + 等宽文字 + 闪烁方块光标。 */
@Composable
private fun StreamBlock(text: String) {
    val transition = rememberInfiniteTransition(label = "streamCursor")
    val cursorAlpha by transition.animateFloat(
        initialValue = 0f,
        targetValue = 1f,
        animationSpec = infiniteRepeatable(
            animation = tween(durationMillis = 520, easing = LinearEasing),
            repeatMode = RepeatMode.Reverse,
        ),
        label = "cursorAlpha",
    )
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = AppSpacing.Md, vertical = AppSpacing.Sm),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            text = text,
            style = MaterialTheme.typography.bodySmall.copy(fontFamily = FontFamily.Monospace),
            color = AppColor.BrandPrimary,
            modifier = Modifier
                .weight(1f, fill = false)
                .clip(RoundedCornerShape(AppRadius.Sm))
                .background(AppColor.BrandPrimary.copy(alpha = 0.06f))
                .padding(horizontal = AppSpacing.Sm, vertical = 4.dp),
            maxLines = 2,
            overflow = TextOverflow.Ellipsis,
        )
        Spacer(Modifier.width(AppSpacing.Xs))
        Box(
            modifier = Modifier
                .size(width = 6.dp, height = 12.dp)
                .graphicsLayer { alpha = cursorAlpha }
                .background(AppColor.BrandPrimary, RoundedCornerShape(1.dp)),
        )
    }
}

@Composable
private fun CardDivider() {
    Box(
        Modifier
            .fillMaxWidth()
            .height(1.dp)
            .background(AppColor.SeparatorOnLight),
    )
}

/** 耗时格式化："850ms" / "1.6s"。 */
private fun formatDuration(ms: Long): String {
    return if (ms < 1000) {
        String.format(Locale.US, "%dms", ms)
    } else {
        String.format(Locale.US, "%.1fs", ms / 1000.0)
    }
}
