package com.mini.me_core.newui.designsystem.component

import androidx.compose.animation.animateContentSize
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.mini.me_core.newui.designsystem.theme.appPalette
import com.mini.me_core.newui.designsystem.token.generated.AppColor
import com.mini.me_core.newui.designsystem.token.generated.AppRadius
import com.mini.me_core.newui.designsystem.token.generated.AppSpacing
import kotlinx.coroutines.delay

/** 日志等级：保留以兼容历史 [TerminalLogLine] 构造与 demo 脚本；新代码请用 [TerminalLineKind]。 */
enum class LogLevel { Info, Success, Warning, Danger }

/**
 * 一行终端日志的语义类型：
 * - [Command]：被执行的命令行（`$` 前缀 + 命令名主色 + 参数次要色三级高亮）；
 * - [Stdout]：普通标准输出（ink 正文）；
 * - [Stderr]：错误输出（StatusDanger 文字 + 错误色浅底高亮整行）；
 * - [Warning]：告警（StatusWarning 文字）。
 */
enum class TerminalLineKind { Command, Stdout, Stderr, Warning }

/** 一条终端日志：时间戳 + 类型/等级 + 正文。 */
data class TerminalLogLine(
    val text: String,
    val level: LogLevel = LogLevel.Info,
    val at: String,
    // 行类型，默认 [TerminalLineKind.Stdout]，保证历史 demo 脚本不传新参数也能编译。
    val kind: TerminalLineKind = TerminalLineKind.Stdout,
) {
    companion object {
        /** 便捷构造一条命令行：[command] 为不含 `$` 前缀的命令本体，渲染时自动加 `$` 高亮前缀。 */
        fun command(command: String, at: String): TerminalLogLine =
            TerminalLogLine(text = command, kind = TerminalLineKind.Command, at = at)
    }
}

/** 历史 [LogLevel] 与新 [TerminalLineKind] 的归一：未显式标 kind 的旧行按 level 上色。 */
private fun effectiveKind(line: TerminalLogLine): TerminalLineKind = when {
    line.kind != TerminalLineKind.Stdout -> line.kind
    line.level == LogLevel.Danger -> TerminalLineKind.Stderr
    line.level == LogLevel.Warning -> TerminalLineKind.Warning
    else -> TerminalLineKind.Stdout
}

/**
 * 终端日志面板（分子组 · AppTerminalLog）：iOS 简约风格的仿真终端卡片，
 * 替代单行跑马灯，以"控制台"形态连续投递日志。
 *
 * 视觉对齐 iOS 简约规范：
 *  - **深色控制台**：正文采用 [appPalette]().surface 深底 + 等宽字体，
 *    在浅色页面下形成高对比 "terminal" 区块（类 iOS 深色系统背景）。
 *  - **状态点**：标题栏一枚呼吸态绿色圆点 + 标题文本；正文按 [LogLevel]
 *    染 [AppColor.StatusSuccess]/[AppColor.StatusWarning]/[AppColor.StatusDanger]。
 *  - **卡片**：圆角 + 统一深色标题栏分隔，内容超出自动滚动到底部，
 *    尾部一枚等宽光标方块示意"仍在线"。
 *
 * @since 0.1.0-experimental
 */
@Composable
fun AppTerminalLog(
    modifier: Modifier = Modifier,
    title: String = "终端日志",
    height: Dp = 148.dp,
    maxLines: Int = 12,
    // 传入真实日志行时进入静态模式（不跑 demo 循环）；null 保留原有 demo 演示。
    lines: List<TerminalLogLine>? = null,
    // 是否仍在运行（控制"运行中"胶囊与光标）。
    running: Boolean = true,
    // 本次命令是否成功：null=运行中呼吸绿（默认）；true=稳态绿点；false=稳态红点（不呼吸）。
    succeeded: Boolean? = null,
    // 失败时卡片右下角的"重跑"按钮；null 不渲染。
    onRerun: (() -> Unit)? = null,
    // 可空颜色槽位：终端卡本次固定浅色（LightPalette surface/ink），留槽位后续接 newui AppUiMode 日夜。
    surfaceColor: Color = com.mini.me_core.newui.designsystem.theme.LightPalette.surface,
    inkColor: Color = com.mini.me_core.newui.designsystem.theme.LightPalette.ink,
) {
    val osc = rememberScrollState()
    // 静态输出超过此行数默认折叠（复用工具卡 >10 行折叠交互）。
    val collapseThreshold = 10
    var expanded by remember(lines) { mutableStateOf((lines?.size ?: 0) <= collapseThreshold) }
    val shownLines = remember(lines, expanded) {
        if (lines == null) null else if (expanded) lines else lines.take(collapseThreshold)
    }
    var demoLines by remember { mutableStateOf(initialScript()) }
    val cursor = rememberInfiniteTransition(label = "terminalCursor")
    val blink by cursor.animateFloat(
        initialValue = 1f,
        targetValue = 0f,
        animationSpec = infiniteRepeatable(animation = tween(600, easing = LinearEasing)),
        label = "cursorBlink",
    )
    val pulse by cursor.animateFloat(
        initialValue = 0.45f,
        targetValue = 1f,
        animationSpec = infiniteRepeatable(animation = tween(1100, easing = LinearEasing)),
        label = "statusPulse",
    )

    // 循环投递一条模拟命令回显，超出 maxLines 收尾滚动（仅 demo 模式）
    LaunchedEffect(lines) {
        if (lines != null) return@LaunchedEffect
        val stream = logStream()
        var i = 0
        while (true) {
            val (entry, wait) = stream[i % stream.size]
            demoLines = (demoLines + entry).takeLast(maxLines)
            i++
            delay(wait)
        }
    }
    // 新日志出现自动滚到底部
    LaunchedEffect(shownLines?.size) {
        if (osc.canScrollForward) osc.animateScrollTo(osc.maxValue)
    }

    Box(
        modifier = modifier
            .fillMaxWidth()
            .height(height)
            .clip(RoundedCornerShape(AppRadius.Md))
            .background(surfaceColor),
    ) {
        Column(Modifier.fillMaxSize()) {
            // 标题栏：呼吸状态点 + 标题 + 右缘"运行中"胶囊
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .background(com.mini.me_core.newui.designsystem.theme.LightPalette.surfaceDim)
                    .padding(horizontal = AppSpacing.Lg, vertical = AppSpacing.Sm),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                // 状态点：succeeded=null 时呼吸绿（运行中）；true 稳态绿；false 稳态红（不呼吸）。
                val dotBase = when (succeeded) {
                    true -> AppColor.StatusSuccess
                    false -> AppColor.StatusDanger
                    null -> AppColor.StatusSuccess
                }
                val dotAlpha = if (succeeded == null) pulse else 1f
                Box(
                    Modifier
                        .size(AppSpacing.Sm)
                        .clip(CircleShape)
                        .background(dotBase.copy(alpha = dotAlpha.coerceIn(0.3f, 1f))),
                )
                Spacer(Modifier.size(AppSpacing.Sm))
                Text(
                    text = title,
                    style = MaterialTheme.typography.bodyMedium,
                    color = inkColor,
                    fontWeight = FontWeight.SemiBold,
                )
                Spacer(Modifier.weight(1f))
                if (running) {
                    Text(
                        text = "● 运行中",
                        style = MaterialTheme.typography.labelSmall,
                        color = appPalette().labelSecondary,
                        fontFamily = FontFamily.Monospace,
                    )
                }
            }

            // 正文：等宽字体日志，自动滚动；底部预留 scrim 等高内边距，
            // 否则滚到底时最后一行 / 光标被底部渐隐层盖住（看似被裁切）。
            Column(
                modifier = Modifier
                    .weight(1f)
                    .verticalScroll(osc)
                    .animateContentSize()
                    .padding(
                        start = AppSpacing.Lg,
                        end = AppSpacing.Lg,
                        top = AppSpacing.Sm,
                        bottom = AppSpacing.Xl,
                    ),
                verticalArrangement = Arrangement.spacedBy(AppSpacing.Xs),
            ) {
                (shownLines ?: demoLines).forEach { line ->
                    val kind = effectiveKind(line)
                    // stderr 整行错误色浅底高亮 + 小圆角；其余行透明背景。
                    val rowMod = if (kind == TerminalLineKind.Stderr) {
                        Modifier
                            .clip(RoundedCornerShape(AppRadius.Sm))
                            .background(AppColor.StatusDanger.copy(alpha = 0.08f))
                            .padding(horizontal = AppSpacing.Xs)
                    } else {
                        Modifier
                    }
                    Row(
                        modifier = rowMod,
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        // 空时间戳（真实流 at=""）时跳过时间戳列与 Spacer，避免命令行整体右缩进错位。
                        if (line.at.isNotBlank()) {
                            Text(
                                text = line.at,
                                style = MaterialTheme.typography.labelSmall,
                                color = appPalette().labelSecondary.copy(alpha = 0.72f),
                                fontFamily = FontFamily.Monospace,
                            )
                            Spacer(Modifier.size(AppSpacing.Sm))
                        }
                        when (kind) {
                            // 命令行：$ 主色前缀 + 命令名 ink SemiBold 等宽 + 参数 labelSecondary 等宽。
                            TerminalLineKind.Command -> {
                                Text(
                                    text = "$ ",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = appPalette().primary,
                                    fontFamily = FontFamily.Monospace,
                                )
                                val tokens = line.text.split(Regex("\\s+"), limit = 2)
                                Text(
                                    text = tokens.firstOrNull().orEmpty(),
                                    style = MaterialTheme.typography.bodySmall,
                                    color = inkColor,
                                    fontFamily = FontFamily.Monospace,
                                    fontWeight = FontWeight.SemiBold,
                                )
                                if (tokens.size > 1 && tokens[1].isNotBlank()) {
                                    Text(
                                        text = " " + tokens[1],
                                        style = MaterialTheme.typography.bodySmall,
                                        color = appPalette().labelSecondary,
                                        fontFamily = FontFamily.Monospace,
                                    )
                                }
                            }
                            TerminalLineKind.Stdout -> Text(
                                text = line.text,
                                style = MaterialTheme.typography.bodySmall,
                                color = inkColor,
                                fontFamily = FontFamily.Monospace,
                            )
                            TerminalLineKind.Stderr -> Text(
                                text = line.text,
                                style = MaterialTheme.typography.bodySmall,
                                color = AppColor.StatusDanger,
                                fontFamily = FontFamily.Monospace,
                                fontWeight = FontWeight.SemiBold,
                            )
                            TerminalLineKind.Warning -> Text(
                                text = line.text,
                                style = MaterialTheme.typography.bodySmall,
                                color = AppColor.StatusWarning,
                                fontFamily = FontFamily.Monospace,
                            )
                        }
                    }
                }
                // 静态输出超阈值：折叠展开切换
                if (lines != null && lines.size > collapseThreshold) {
                    Text(
                        text = if (expanded) "收起" else "展开（共 ${lines.size} 行）",
                        style = MaterialTheme.typography.labelSmall,
                        color = appPalette().primary,
                        modifier = Modifier.clip(RoundedCornerShape(AppRadius.Sm))
                            .clickable { expanded = !expanded }
                            .padding(horizontal = AppSpacing.Sm, vertical = AppSpacing.Xs),
                    )
                }
                // 失败时"重跑"按钮占位
                if (onRerun != null && lines?.any { effectiveKind(it) == TerminalLineKind.Stderr } == true) {
                    Spacer(Modifier.size(AppSpacing.Xs))
                    Text(
                        text = "重跑",
                        style = MaterialTheme.typography.labelMedium,
                        fontWeight = FontWeight.SemiBold,
                        color = appPalette().onPrimary,
                        modifier = Modifier.clip(RoundedCornerShape(AppRadius.Sm))
                            .background(appPalette().primary)
                            .clickable { onRerun() }
                            .padding(horizontal = AppSpacing.Md, vertical = AppSpacing.Xs),
                    )
                }
                // 闪烁光标（仅运行中）
                if (running) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Box(
                            Modifier
                                .padding(bottom = AppSpacing.Xs)
                                .size(AppSpacing.Sm)
                                .alpha(blink)
                                .background(AppColor.StatusSuccess),
                        )
                    }
                }
            }
        }

        // 底部渐隐 scrim：贴近 iOS 终端/控制台的底部纵向收尾（BoxScope 直接子级覆盖）
        Box(
            Modifier
                .align(Alignment.BottomCenter)
                .fillMaxWidth()
                .height(AppSpacing.Xl)
                .background(
                    Brush.verticalGradient(
                        colorStops = arrayOf(
                            0f to Color.Transparent,
                            1f to appPalette().surface.copy(alpha = 0.55f),
                        ),
                    ),
                ),
        )
    }
}

/** 入场即显示的若干条"已就绪"日志，营造已运行一段的状态。 */
private fun initialScript(): List<TerminalLogLine> = listOf(
    TerminalLogLine("MiniMe-core · 终端启动", LogLevel.Info, "[00:00:00]"),
    TerminalLogLine("PRoot 容器已挂载", LogLevel.Success, "[00:00:01]"),
    TerminalLogLine("SSH 会话握手成功", LogLevel.Success, "[00:00:02]"),
)

/** 循环回显脚本：日志 + 下一条间隔(millis)。命令行走 [TerminalLogLine.command] 展示三级高亮。 */
private fun logStream(): List<Pair<TerminalLogLine, Long>> = listOf(
    TerminalLogLine.command("gradle :app:assembleDebug", "[00:00:03]") to 900,
    TerminalLogLine("> task compileDebugKotlin", LogLevel.Info, "[00:00:04]") to 1100,
    TerminalLogLine("build 成功，1200ms", LogLevel.Success, "[00:00:05]") to 1600,
    TerminalLogLine.command("./mcp_server --port 8899", "[00:00:06]") to 900,
    TerminalLogLine("MCP Server 已就绪 · Bearer 鉴权开启", LogLevel.Success, "[00:00:07]") to 1800,
    TerminalLogLine.command("ssh agent@host quick-test", "[00:00:08]") to 1000,
    TerminalLogLine("warning: 远程端口 backlog 偏大", LogLevel.Warning, "[00:00:09]") to 1500,
    TerminalLogLine.command("git push origin main", "[00:00:10]") to 1200,
    TerminalLogLine("error: 提交信息不符合规范", LogLevel.Danger, "[00:00:11]") to 2000,
    TerminalLogLine("→ 已终止，待重写提交信息…", LogLevel.Info, "[00:00:12]") to 1800,
)