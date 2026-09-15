package com.mini.me_core.newui.designsystem.component.molecule

import androidx.compose.animation.animateContentSize
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
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
import com.mini.me_core.newui.designsystem.token.generated.AppColor
import com.mini.me_core.newui.designsystem.token.generated.AppRadius
import com.mini.me_core.newui.designsystem.token.generated.AppSpacing
import kotlinx.coroutines.delay

/** 日志等级：决定终端的渲染颜色与左侧状态点。 */
enum class LogLevel { Info, Success, Warning, Danger }

/** 一条终端日志：时间戳 + 等级 + 正文。 */
data class TerminalLogLine(
    val text: String,
    val level: LogLevel = LogLevel.Info,
    val at: String,
)

private fun logLevelTint(level: LogLevel): Color = when (level) {
    LogLevel.Info -> AppColor.OnDarkSecondaryLabel
    LogLevel.Success -> AppColor.StatusSuccess
    LogLevel.Warning -> AppColor.StatusWarning
    LogLevel.Danger -> AppColor.StatusDanger
}

/**
 * 终端日志面板（分子组 · AppTerminalLog）：iOS 简约风格的仿真终端卡片，
 * 替代单行跑马灯，以"控制台"形态连续投递日志。
 *
 * 视觉对齐 iOS 简约规范：
 *  - **深色控制台**：正文采用 [AppColor.OnDarkSurface] 深底 + 等宽字体，
 *    在浅色页面下形成高对比 "terminal" 区块（类 iOS 深色系统背景）。
 *  - **状态点**：标题栏一枚呼吸态绿色圆点 + 标题文本；正文按 [LogLevel]
 *    染 [AppColor.StatusSuccess]/[AppColor.StatusWarning]/[AppColor.StatusDanger]。
 *  - **卡片**：圆角 + 统一深色标题栏分隔，内容超出自动滚动到底部，
 *    尾部一枚等宽光标方块示意"仍在线"。
 */
@Composable
fun AppTerminalLog(
    modifier: Modifier = Modifier,
    title: String = "终端日志",
    height: Dp = 148.dp,
    maxLines: Int = 12,
) {
    val osc = rememberScrollState()
    var lines by remember { mutableStateOf(initialScript()) }
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

    // 循环投递一条模拟命令回显，超出 maxLines 收尾滚动
    LaunchedEffect(Unit) {
        val stream = logStream()
        var i = 0
        while (true) {
            val (entry, wait) = stream[i % stream.size]
            lines = (lines + entry).takeLast(maxLines)
            i++
            delay(wait)
        }
    }
    // 新日志出现自动滚到底部
    LaunchedEffect(lines.size) {
        if (osc.canScrollForward) osc.animateScrollTo(osc.maxValue)
    }

    Box(
        modifier = modifier
            .fillMaxWidth()
            .height(height)
            .clip(RoundedCornerShape(AppRadius.Md))
            .background(AppColor.OnDarkSurface),
    ) {
        Column(Modifier.fillMaxSize()) {
            // 标题栏：呼吸状态点 + 标题 + 右缘"运行中"胶囊
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .background(AppColor.OnDarkSurfaceRaised)
                    .padding(horizontal = AppSpacing.Lg, vertical = AppSpacing.Sm),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                val pulseColor by animateColorAsState(
                    targetValue = AppColor.StatusSuccess.copy(alpha = pulse.coerceIn(0.3f, 1f)),
                    label = "statusPulseColor",
                )
                Box(
                    Modifier
                        .size(AppSpacing.Sm)
                        .clip(CircleShape)
                        .background(pulseColor),
                )
                Spacer(Modifier.size(AppSpacing.Sm))
                Text(
                    text = title,
                    style = MaterialTheme.typography.bodyMedium,
                    color = AppColor.OnDarkInk,
                    fontWeight = FontWeight.SemiBold,
                )
                Spacer(Modifier.weight(1f))
                Text(
                    text = "● 运行中",
                    style = MaterialTheme.typography.labelSmall,
                    color = AppColor.OnDarkSecondaryLabel,
                    fontFamily = FontFamily.Monospace,
                )
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
                lines.forEach { line ->
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text(
                            text = line.at,
                            style = MaterialTheme.typography.labelSmall,
                            color = AppColor.OnDarkSecondaryLabel.copy(alpha = 0.72f),
                            fontFamily = FontFamily.Monospace,
                        )
                        Spacer(Modifier.size(AppSpacing.Sm))
                        Text(
                            text = line.text,
                            style = MaterialTheme.typography.bodySmall,
                            color = logLevelTint(line.level),
                            fontFamily = FontFamily.Monospace,
                            fontWeight = if (line.level == LogLevel.Danger) FontWeight.SemiBold else FontWeight.Normal,
                        )
                    }
                }
                // 闪烁光标
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
                            1f to AppColor.OnDarkSurface.copy(alpha = 0.55f),
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

/** 循环回显脚本：日志 + 下一条间隔(millis)。 */
private fun logStream(): List<Pair<TerminalLogLine, Long>> = listOf(
    TerminalLogLine("$  gradle :app:assembleDebug", LogLevel.Info, "[00:00:03]") to 900,
    TerminalLogLine("> task compileDebugKotlin", LogLevel.Info, "[00:00:04]") to 1100,
    TerminalLogLine("> build 成功，1200ms", LogLevel.Success, "[00:00:05]") to 1600,
    TerminalLogLine("$  ./mcp_server --port 8899", LogLevel.Info, "[00:00:06]") to 900,
    TerminalLogLine("MCP Server 已就绪 · Bearer 鉴权开启", LogLevel.Success, "[00:00:07]") to 1800,
    TerminalLogLine("$  ssh agent@host quick-test", LogLevel.Info, "[00:00:08]") to 1000,
    TerminalLogLine("warning: 远程端口 backlog 偏大", LogLevel.Warning, "[00:00:09]") to 1500,
    TerminalLogLine("$  git push origin main", LogLevel.Info, "[00:00:10]") to 1200,
    TerminalLogLine("error: 提交信息不符合规范", LogLevel.Danger, "[00:00:11]") to 2000,
    TerminalLogLine("→ 已终止，待重写提交信息…", LogLevel.Info, "[00:00:12]") to 1800,
)