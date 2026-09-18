package com.mini.me_core.newui.designsystem.component

import com.mini.me_core.newui.designsystem.theme.appPalette
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
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.dp
import com.mini.me_core.newui.designsystem.token.generated.AppColor
import com.mini.me_core.newui.designsystem.token.generated.AppRadius
import com.mini.me_core.newui.designsystem.token.generated.AppSizing
import com.mini.me_core.newui.designsystem.token.generated.AppSpacing
import com.mini.me_core.newui.designsystem.token.generated.AppStroke
import java.util.Locale

/**
 * 工具调用生命周期状态：参数流式生成 → 执行中 → 待人工审批 / 成功 / 失败。
 */
enum class AppToolCallState { Streaming, Running, AwaitingApproval, Success, Error }

/** 入参 / 结果文本块超过此行数即默认折叠为前若干行 + 展开入口。 */
private const val JSON_FOLD_LINES = 10

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
 *
 * @since 0.1.0-experimental
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
    approvalExpired: Boolean = false,
    approvalRemembered: Boolean = false,
    leadingIcon: ImageVector = Icons.Rounded.Terminal,
    defaultCollapsed: Boolean = true,
) {
    // 整卡折叠收纳：默认只留头部一行；点按头部展开 命令/入参/结果。
    // 仅 Success / Error 两个终态可折叠；审批 / 流式 / 运行中强制展开（要看输出与交互）。
    // 失败默认展开（对齐 ToolCallGroupBlock：组内失败即展开标红），成功默认折叠。
    var cardExpanded by remember(defaultCollapsed, state) {
        mutableStateOf(!defaultCollapsed || state == AppToolCallState.Error)
    }
    val collapsible = state == AppToolCallState.Success || state == AppToolCallState.Error
    val cardOpen = cardExpanded || !collapsible
    val statusColor = when (state) {
        AppToolCallState.Streaming, AppToolCallState.Running -> appPalette().primary
        AppToolCallState.AwaitingApproval -> AppColor.StatusWarning
        AppToolCallState.Success -> AppColor.StatusSuccess
        AppToolCallState.Error -> AppColor.StatusDanger
    }
    val cardShape = RoundedCornerShape(AppRadius.Md)
    val isError = state == AppToolCallState.Error
    val cardChevronRotation by animateFloatAsState(
        targetValue = if (cardOpen) 180f else 0f,
        label = "cardChevron",
    )

    Column(
        modifier = modifier
            .clip(cardShape)
            .background(appPalette().card)
            .border(
                width = AppStroke.Thin,
                color = if (isError) {
                    AppColor.StatusDanger.copy(alpha = 0.45f)
                } else {
                    appPalette().separator
                },
                shape = cardShape,
            ),
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .then(if (collapsible) Modifier.clickable { cardExpanded = !cardExpanded } else Modifier)
                .padding(horizontal = AppSpacing.Lg, vertical = AppSpacing.Xs),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            // 图标块：状态色浅底 + 状态色图标
            Box(
                modifier = Modifier
                    .size(AppSizing.IconXl)
                    .clip(RoundedCornerShape(AppRadius.Sm))
                    .background(statusColor.copy(alpha = 0.12f)),
                contentAlignment = Alignment.Center,
            ) {
                // 装饰图标：旁侧已有文字/语义，跳过无障碍
                Icon(
                    imageVector = leadingIcon,
                    contentDescription = null,
                    tint = statusColor,
                    modifier = Modifier.size(AppSizing.IconXs),
                )
            }
            Spacer(Modifier.width(AppSpacing.Sm))
            Column(Modifier.weight(1f)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        text = title,
                        style = MaterialTheme.typography.bodyMedium,
                        fontWeight = FontWeight.Medium,
                        color = appPalette().ink,
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
                        color = if (isError) AppColor.StatusDanger else appPalette().labelSecondary,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                }
            }
            Spacer(Modifier.width(AppSpacing.Sm))
            ToolCallStatusBadge(state = state, color = statusColor)
            if (collapsible) {
                Spacer(Modifier.width(AppSpacing.Xs))
                Icon(
                    imageVector = Icons.Rounded.KeyboardArrowDown,
                    contentDescription = if (cardOpen) "收起详情" else "展开详情",
                    tint = appPalette().labelSecondary,
                    modifier = Modifier
                        .size(AppSizing.IconXs)
                        .rotate(cardChevronRotation),
                )
            }
        }
        // 展开态：命令 / 入参 / 结果 / 无结果 / 审批 各区块整体收纳在一层垂直展开动画里。
        AnimatedVisibility(
            visible = cardOpen,
            enter = expandVertically() + fadeIn(),
            exit = shrinkVertically() + fadeOut(),
        ) {
            Column(Modifier.fillMaxWidth()) {
        // Streaming：执行中实时输出，无需展开，直接"看着它跑"（多行截断可展开）
        if (state == AppToolCallState.Running && streamOutput != null) {
            CardDivider()
            StreamBlock(text = streamOutput, maxLines = streamMaxLines)
        }
        if (command != null) {
            CardDivider()
            ExpandableSection(
                label = "命令",
                expanded = cardOpen,
                onToggle = {},
                interactive = false,
            ) {
                CommandBlock(text = command)
            }
        }
        if (input != null) {
            CardDivider()
            ExpandableSection(
                label = "入参",
                expanded = cardOpen,
                onToggle = {},
                interactive = false,
            ) {
                FoldingJsonText(text = input)
            }
        }
        if (output.isNullOrBlank()) {
            // 空结果占位：统一文案，避免结果区空着。
            CardDivider()
            Text(
                text = "［无结果］",
                style = MaterialTheme.typography.bodySmall,
                color = appPalette().labelTertiary,
                modifier = Modifier.padding(horizontal = AppSpacing.Lg, vertical = AppSpacing.Xs),
            )
        } else {
            CardDivider()
            ExpandableSection(
                label = "结果",
                expanded = cardOpen,
                onToggle = {},
                interactive = false,
            ) {
                FoldingJsonText(text = output)
            }
        }
        // Intervention：待人工审批 → 三档 / 二档操作行；超时降级 / 记忆放行 仅展示态
        if (
            state == AppToolCallState.AwaitingApproval &&
            (onChoice != null || onApprove != null || onReject != null || approvalExpired || approvalRemembered)
        ) {
            CardDivider()
            when {
                approvalRemembered -> Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = AppSpacing.Lg, vertical = AppSpacing.Sm),
                    verticalArrangement = Arrangement.spacedBy(AppSpacing.Xs),
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(
                            imageVector = Icons.Rounded.CheckCircle,
                            contentDescription = "已记住",
                            tint = AppColor.StatusSuccess,
                            modifier = Modifier.size(AppSizing.IconXs),
                        )
                        Text(
                            text = "已记住 · 始终允许，不再询问",
                            style = MaterialTheme.typography.labelSmall,
                            color = AppColor.StatusSuccess,
                            modifier = Modifier.padding(start = AppSpacing.Xs),
                        )
                    }
                }

                approvalExpired -> Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = AppSpacing.Lg, vertical = AppSpacing.Sm),
                    verticalArrangement = Arrangement.spacedBy(AppSpacing.Xs),
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(
                            imageVector = Icons.Rounded.Warning,
                            contentDescription = "审批超时",
                            tint = AppColor.StatusWarning,
                            modifier = Modifier.size(AppSizing.IconXs),
                        )
                        Text(
                            text = "审批超时，已按默认策略拒绝执行",
                            style = MaterialTheme.typography.labelSmall,
                            color = AppColor.StatusWarning,
                            modifier = Modifier.padding(start = AppSpacing.Xs),
                        )
                    }
                    if (approvalHint != null) {
                        Text(
                            text = approvalHint,
                            style = MaterialTheme.typography.labelSmall,
                            color = appPalette().labelSecondary,
                            maxLines = 2,
                        )
                    }
                }

                else -> Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = AppSpacing.Lg, vertical = AppSpacing.Sm),
                    verticalArrangement = Arrangement.spacedBy(AppSpacing.Xs),
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(AppSpacing.Sm)) {
                        if (approvalHint != null) {
                            Text(
                                text = approvalHint,
                                style = MaterialTheme.typography.labelSmall,
                                color = appPalette().labelSecondary,
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
                            color = appPalette().labelSecondary,
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
            modifier = Modifier.size(AppSizing.IconXs),
        )
        AppToolCallState.Success -> Icon(
            imageVector = Icons.Rounded.CheckCircle,
            contentDescription = "成功",
            tint = color,
            modifier = Modifier.size(AppSizing.IconXs),
        )
        AppToolCallState.Error -> Icon(
            imageVector = Icons.Rounded.ErrorOutline,
            contentDescription = "失败",
            tint = color,
            modifier = Modifier.size(AppSizing.IconXs),
        )
    }
}

/** MCP 服务器徽标：品牌色浅底小胶囊（如 `github`）。 */
@Composable
private fun ServerChip(prefix: String) {
    Text(
        text = prefix,
        style = MaterialTheme.typography.labelSmall,
        color = appPalette().primary,
        maxLines = 1,
        modifier = Modifier
            .clip(RoundedCornerShape(AppRadius.Sm))
            .background(appPalette().primary.copy(alpha = 0.10f))
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

/** 可展开区：label 行 + 旋转箭头 + 垂直展开动画内容。[interactive]=false 时仅作静态标签（无箭头、不可点）。 */
@Composable
private fun ExpandableSection(
    label: String,
    expanded: Boolean,
    onToggle: () -> Unit,
    interactive: Boolean = true,
    content: @Composable () -> Unit,
) {
    Column(Modifier.fillMaxWidth()) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .then(if (interactive) Modifier.clickable(onClick = onToggle) else Modifier)
                .padding(horizontal = AppSpacing.Lg, vertical = AppSpacing.Xs),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(
                text = label,
                style = MaterialTheme.typography.labelSmall,
                color = appPalette().labelSecondary,
                modifier = Modifier.weight(1f),
            )
            if (interactive) {
                val rotation by animateFloatAsState(
                    targetValue = if (expanded) 180f else 0f,
                    label = "expandChevron",
                )
                Icon(
                    imageVector = Icons.Rounded.KeyboardArrowDown,
                    contentDescription = if (expanded) "收起$label" else "展开$label",
                    tint = appPalette().labelSecondary,
                    modifier = Modifier
                        .size(AppSizing.IconXs)
                        .rotate(rotation),
                )
            }
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

/** 入参 / 结果内容块：等宽字体，单层卡内纯文本；超过 [JSON_FOLD_LINES] 行默认折叠为前若干行 + 展开入口。 */
@Composable
private fun FoldingJsonText(text: String) {
    val lines = text.lines()
    val folded = lines.size > JSON_FOLD_LINES
    var expanded by remember(text) { mutableStateOf(false) }
    val shown = if (folded && !expanded) lines.take(JSON_FOLD_LINES) else lines
    Column(Modifier.fillMaxWidth()) {
        shown.forEachIndexed { index, line ->
            Text(
                text = line,
                style = MaterialTheme.typography.bodySmall.copy(fontFamily = FontFamily.Monospace),
                color = appPalette().ink,
            )
            if (index != shown.lastIndex) {
                Spacer(Modifier.height(AppSpacing.Tiny))
            }
        }
        if (folded) {
            Spacer(Modifier.height(AppSpacing.Tiny))
            Text(
                text = if (expanded) "收起 · ${lines.size} 行" else "展开全部 · ${lines.size} 行",
                style = MaterialTheme.typography.labelSmall,
                color = appPalette().labelSecondary,
                modifier = Modifier.clickable { expanded = !expanded },
            )
        }
    }
}

/**
 * 命令块：Bash/terminal 命令的等宽命令行呈现（单层卡内纯文本，不叠子块底）。
 * 精度高亮：`$ ` 提示符主色；首个 token（命令名）主色加粗，与后续参数做主次区分；
 * 参数 / 续行走次要色，降低视觉噪声。
 */
@Composable
private fun CommandBlock(text: String) {
    val mono = MaterialTheme.typography.bodySmall.copy(fontFamily = FontFamily.Monospace)
    val palette = appPalette()
    val firstSpace = text.indexOfFirst { it.isWhitespace() }
    val (cmd, args) = if (firstSpace == -1) text to "" else text.substring(0, firstSpace) to text.substring(firstSpace)
    val annotated = buildAnnotatedString {
        withStyle(SpanStyle(color = palette.primary)) { append("$ ") }
        withStyle(SpanStyle(color = palette.primary, fontWeight = FontWeight.SemiBold)) { append(cmd) }
        if (args.isNotEmpty()) {
            withStyle(SpanStyle(color = palette.labelSecondary)) { append(args) }
        }
    }
    Text(text = annotated, style = mono)
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
            .padding(horizontal = AppSpacing.Lg, vertical = AppSpacing.Sm),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(
            modifier = Modifier
                .weight(1f, fill = false)
                .padding(horizontal = AppSpacing.Sm, vertical = AppSpacing.Xs)
                .then(
                    if (folded) Modifier.clickable { expanded = !expanded } else Modifier,
                ),
        ) {
            shown.forEachIndexed { index, line ->
                val isErr = isErrorLine(line)
                Text(
                    text = line,
                    style = MaterialTheme.typography.bodySmall.copy(fontFamily = FontFamily.Monospace),
                    color = if (isErr) AppColor.StatusDanger else appPalette().primary,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = if (isErr) {
                        Modifier
                            .clip(RoundedCornerShape(AppRadius.Sm))
                            .background(AppColor.StatusDanger.copy(alpha = 0.10f))
                            .padding(horizontal = AppSpacing.Xs)
                    } else {
                        Modifier
                    },
                )
                if (index != shown.lastIndex) {
                    Spacer(Modifier.height(AppSpacing.Tiny))
                }
            }
            if (folded) {
                Spacer(Modifier.height(AppSpacing.Tiny))
                Text(
                    text = if (expanded) "收起 · ${lines.size} 行" else "展开全部 · ${lines.size} 行",
                    style = MaterialTheme.typography.labelSmall,
                    color = appPalette().labelSecondary,
                )
            }
        }
        Spacer(Modifier.width(AppSpacing.Xs))
        Box(
            modifier = Modifier
                .size(width = 6.dp, height = 12.dp)
                .graphicsLayer { alpha = cursorAlpha }
                .background(appPalette().primary, RoundedCornerShape(1.dp)),
        )
    }
}

@Composable
private fun CardDivider() {
    Box(
        Modifier
            .fillMaxWidth()
            .height(AppStroke.Thin)
            .background(appPalette().separator),
    )
}

/** 实时输出中识别 stderr / 错误行的启发式：编译错误前缀、异常 / 失败关键字等。 */
private val ERROR_LINE_REGEX = Regex(
    "(?i)^.*(\\be:\\s|error|exception|fatal|failure|failed|traceback|panic|stderr|" +
        "no such (file|command)|not found|permission denied|cannot |undefined symbol|stack trace).*",
)

/** 是否为 stderr / 错误行（用于红色 + 浅红高亮）。 */
private fun isErrorLine(line: String): Boolean = ERROR_LINE_REGEX.matches(line)

/** 耗时格式化："850ms" / "1.6s"。 */
private fun formatDuration(ms: Long): String {
    return if (ms < 1000) {
        String.format(Locale.US, "%dms", ms)
    } else {
        String.format(Locale.US, "%.1fs", ms / 1000.0)
    }
}
