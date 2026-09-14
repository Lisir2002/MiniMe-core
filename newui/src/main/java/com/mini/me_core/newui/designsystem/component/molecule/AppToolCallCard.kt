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
 * 工具权限审批三档选择（对齐真实权限引擎 [PermissionChoice 的 REJECT / ONCE / ALWAYS]）：
 * [Reject] 拒绝、[Once] 本次放行、[Always] 始终允许。
 */
enum class AppApprovalChoice { Reject, Once, Always }

/**
 * 工具调用卡（分子组 · AppToolCallCard）：AI 对话流中工具执行的透明化展示层，
 * 对齐 Vercel AI SDK / shadcn Tool / LobeHub Inspector 六大 surface 范式：
 *
 * - 头部一句话（Inspector）：图标块 + 工具名 + 目标摘要（如 "执行命令 · ./gradlew assembleDebug"），
 *   不裸 dump 原始参数；[serverPrefix] 展示 MCP 服务器徽标（如 `github`），
 *   真实工具名 `mcp__github__search_code` 由上层解析后传入。
 * - 状态徽标：[AppToolCallState.Streaming]（三点脉动）/ [AppToolCallState.Running]（旋转）/
 *   [AppToolCallState.AwaitingApproval]（琥珀警示）/ [AppToolCallState.Success]（绿勾）/
 *   [AppToolCallState.Error]（红叉 + 红色描边）。
 * - 诚实耗时：完成态经 [durationMs] 展示 "· 1.6s"。
 * - 错误分类：[errorCode] 在失败态渲染错误码徽标（如 MCP 的 `TOOL_NOT_FOUND` / `PERMISSION_DENIED`），
 *   [denyReason] 在审批态渲染策略拦截说明（如沙箱模式拒绝原因）。
 * - 实时输出（Streaming）：[streamOutput] 在 Running 态直接内联等宽终端块 + 闪烁光标，
 *   超 [streamMaxLines] 行自动折叠并可展开（Shell stdout 常见长输出）。
 * - 命令高亮：[command] 承载 Bash/terminal 的命令参数，展开区以等宽命令行形式呈现
 *   （优于 JSON 块的可读性）。
 * - 人工审批（Intervention）：[AppToolCallState.AwaitingApproval] 态渲染审批操作行。
 *   优先用三档 [onChoice]（拒绝 / 本次 / 始终允许），[alwaysDisabled] + [alwaysDisabledReason]
 *   用于「始终允许」不可记忆（含命令替换/管道）时的置灰说明；旧二档 [onApprove]/[onReject] 兼容保留。
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
    errorCode: String? = null,
    durationMs: Long? = null,
    input: String? = null,
    command: String? = null,
    output: String? = null,
    streamOutput: String? = null,
    streamMaxLines: Int = 3,
    approvalHint: String? = null,
    denyReason: String? = null,
    onApprove: (() -> Unit)? = null,
    onReject: (() -> Unit)? = null,
    onChoice: ((AppApprovalChoice) -> Unit)? = null,
    alwaysDisabled: Boolean = false,
    alwaysDisabledReason: String? = null,
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
                    if (isError && errorCode != null) {
                        Spacer(Modifier.width(AppSpacing.Xs))
                        ErrorCodeChip(code = errorCode)
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
        // Streaming：执行中实时输出，无需展开，直接"看着它跑"（多行截断可展开）
        if (state == AppToolCallState.Running && streamOutput != null) {
            CardDivider()
            StreamBlock(text = streamOutput, maxLines = streamMaxLines)
        }
        if (command != null) {
            CardDivider()
            ExpandableSection(
                label = "命令",
                expanded = inputExpanded,
                onToggle = { inputExpanded = !inputExpanded },
            ) {
                CommandBlock(text = command)
            }
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
        // Intervention：待人工审批 → 三档 / 二档操作行
        if (state == AppToolCallState.AwaitingApproval && (onChoice != null || onApprove != null || onReject != null)) {
            CardDivider()
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = AppSpacing.Md, vertical = AppSpacing.Sm),
                verticalArrangement = Arrangement.spacedBy(AppSpacing.Xs),
            ) {
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(AppSpacing.Sm)) {
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
                        AppButton(text = "拒绝", onClick = onReject, variant = AppButtonVariant.Outlined)
                    }
                    if (onChoice != null) {
                        AppButton(
                            text = "本次",
                            onClick = { onChoice(AppApprovalChoice.Once) },
                            variant = AppButtonVariant.Outlined,
                        )
                        AppButton(
                            text = "始终允许",
                            onClick = { onChoice(AppApprovalChoice.Always) },
                            variant = AppButtonVariant.Primary,
                            enabled = !alwaysDisabled,
                        )
                    } else if (onApprove != null) {
                        AppButton(text = "允许", onClick = onApprove, variant = AppButtonVariant.Primary)
                    }
                }
                if (alwaysDisabled && alwaysDisabledReason != null) {
                    Text(
                        text = alwaysDisabledReason,
                        style = MaterialTheme.typography.labelSmall,
                        color = AppColor.LabelSecondary,
                        maxLines = 2,
                    )
                }
                if (denyReason != null) {
                    Text(
                        text = denyReason,
                        style = MaterialTheme.typography.labelSmall,
                        color = AppColor.StatusDanger,
                        maxLines = 3,
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

/** 错误分类徽标：失败态的 MCP 错误码（如 `TOOL_NOT_FOUND`）。 */
@Composable
private fun ErrorCodeChip(code: String) {
    Text(
        text = code,
        style = MaterialTheme.typography.labelSmall,
        color = AppColor.StatusDanger,
        maxLines = 1,
        modifier = Modifier
            .clip(RoundedCornerShape(AppRadius.Sm))
            .background(AppColor.StatusDanger.copy(alpha = 0.10f))
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

/** 命令块：Bash/terminal 命令的等宽命令行呈现，品牌色前置 `$`。 */
@Composable
private fun CommandBlock(text: String) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(AppRadius.Sm))
            .background(AppColor.BrandSurfaceDim)
            .padding(AppSpacing.Sm),
    ) {
        Text(
            text = "$ ",
            style = MaterialTheme.typography.bodySmall.copy(fontFamily = FontFamily.Monospace),
            color = AppColor.BrandPrimary,
        )
        Text(
            text = text,
            style = MaterialTheme.typography.bodySmall.copy(fontFamily = FontFamily.Monospace),
            color = AppColor.BrandInk,
        )
    }
}

/**
 * 实时输出块：深色终端底 + 等宽文字 + 闪烁方块光标。
 * 超过 [maxLines] 行时折叠为前 [maxLines] 行，可点击展开完整输出。
 */
@Composable
private fun StreamBlock(text: String, maxLines: Int) {
    var expanded by remember { mutableStateOf(false) }
    val lines = text.lines()
    val folded = lines.size > maxLines
    val shown = if (folded && !expanded) lines.take(maxLines) else lines
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
        Column(
            modifier = Modifier
                .weight(1f, fill = false)
                .clip(RoundedCornerShape(AppRadius.Sm))
                .background(AppColor.BrandPrimary.copy(alpha = 0.06f))
                .padding(horizontal = AppSpacing.Sm, vertical = 4.dp)
                .then(
                    if (folded) Modifier.clickable { expanded = !expanded } else Modifier,
                ),
        ) {
            shown.forEachIndexed { index, line ->
                Text(
                    text = line,
                    style = MaterialTheme.typography.bodySmall.copy(fontFamily = FontFamily.Monospace),
                    color = AppColor.BrandPrimary,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
                if (index != shown.lastIndex) {
                    Spacer(Modifier.height(2.dp))
                }
            }
            if (folded) {
                Spacer(Modifier.height(2.dp))
                Text(
                    text = if (expanded) "收起 · ${lines.size} 行" else "展开全部 · ${lines.size} 行",
                    style = MaterialTheme.typography.labelSmall,
                    color = AppColor.LabelSecondary,
                )
            }
        }
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
