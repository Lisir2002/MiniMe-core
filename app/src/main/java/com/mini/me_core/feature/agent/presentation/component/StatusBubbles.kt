package com.mini.me_core.feature.agent.presentation.component
import com.mini.me_core.core.theme.tokens.LocalComponentTokens
import com.mini.me_core.core.theme.tokens.LocalCornerRadius

import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.StartOffset
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.keyframes
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Icon
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
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.mini.me_core.R
import com.mini.me_core.core.theme.Brand
import com.mini.me_core.core.theme.Radius
import com.mini.me_core.core.theme.Spacing
import com.mini.me_core.core.theme.LocalAppDarkMode
import com.mini.me_core.core.theme.tokens.LocalAppTheme
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.KeyboardArrowDown
import androidx.compose.material.icons.rounded.KeyboardArrowUp
import androidx.compose.material.icons.rounded.Star

@Composable
internal fun ThinkingBubble() {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.Start
    ) {
        Surface(
            shape = RoundedCornerShape(LocalCornerRadius.current.lg, LocalCornerRadius.current.lg, LocalCornerRadius.current.lg, LocalCornerRadius.current.xs),
            color = MaterialTheme.colorScheme.surface,
            border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant)
        ) {
            Box(
                modifier = Modifier.padding(horizontal = Spacing.sm, vertical = Spacing.xs),
                contentAlignment = Alignment.Center
            ) {
                TypingDots(color = MaterialTheme.colorScheme.primary)
            }
        }
    }
}

/** 上下文压缩期间的临时状态气泡，不落库。 */
@Composable
internal fun CompactionProgressBubble() {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.Start
    ) {
        Surface(
            shape = RoundedCornerShape(LocalCornerRadius.current.lg, LocalCornerRadius.current.lg, LocalCornerRadius.current.lg, LocalCornerRadius.current.xs),
            color = MaterialTheme.colorScheme.surface,
            border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant)
        ) {
            Row(
                modifier = Modifier.padding(horizontal = Spacing.sm, vertical = Spacing.xs),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(Spacing.sm)
            ) {
                Text(
                    text = stringResource(R.string.chat_compressing_context),
                    color = MaterialTheme.colorScheme.onSurface,
                    style = MaterialTheme.typography.bodyMedium
                )
                TypingDots(color = MaterialTheme.colorScheme.primary)
            }
        }
    }
}

/** 网络重试期间的临时状态气泡，不落库。 */
@Composable
internal fun RetryingBubble(attempt: Int, maxRetries: Int) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.Start
    ) {
        Surface(
            shape = RoundedCornerShape(LocalCornerRadius.current.lg, LocalCornerRadius.current.lg, LocalCornerRadius.current.lg, LocalCornerRadius.current.xs),
            color = MaterialTheme.colorScheme.surface,
            border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant)
        ) {
            Row(
                modifier = Modifier.padding(horizontal = Spacing.sm, vertical = Spacing.xs),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(Spacing.sm)
            ) {
                Text(
                    text = stringResource(R.string.chat_retrying, attempt, maxRetries),
                    color = MaterialTheme.colorScheme.onSurface,
                    style = MaterialTheme.typography.bodyMedium
                )
                TypingDots(color = MaterialTheme.colorScheme.primary)
            }
        }
    }
}

/**
 * 模型流式吐字时的实时气泡：左对齐、与助手气泡同款。
 * 尾部带三个跳动的点表示仍在生成。本轮结束后由落库的助手气泡接管。
 *
 * 流式阶段也渲染 Markdown，但使用采样文本降低解析频率；最终落库消息再走常规缓存渲染。
 */
@Composable
internal fun StreamingBubble(text: String) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.Start
    ) {
        Surface(
            shape = RoundedCornerShape(LocalCornerRadius.current.lg, LocalCornerRadius.current.lg, LocalCornerRadius.current.lg, LocalCornerRadius.current.xs),
            color = MaterialTheme.colorScheme.surface,
            border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant),
            modifier = Modifier.fillMaxWidth()
        ) {
            Column(modifier = Modifier.padding(horizontal = Spacing.sm, vertical = Spacing.xs)) {
                MarkdownContent(
                    text = text,
                    color = MaterialTheme.colorScheme.onSurface
                )
                Spacer(Modifier.height(Spacing.xs))
                // F2.4：末尾闪烁打字光标「|」，primary 色，500ms 周期
                BlinkingCursor(color = MaterialTheme.colorScheme.primary)
            }
        }
    }
}

/**
 * F2.4 打字光标：AI 流式回复末尾闪烁的竖线「|」，primary 色，500ms 亮 / 500ms 灭。
 */
@Composable
internal fun BlinkingCursor(color: androidx.compose.ui.graphics.Color) {
    val transition = rememberInfiniteTransition(label = "cursor")
    val alpha by transition.animateFloat(
        initialValue = 1f,
        targetValue = 0f,
        animationSpec = infiniteRepeatable(
            animation = keyframes { durationMillis = 1000 },
            repeatMode = RepeatMode.Reverse,
        ),
        label = "cursorAlpha"
    )
    Box(
        modifier = Modifier
            .height(16.dp)
            .width(2.dp)
            .background(color.copy(alpha = alpha))
    )
}

/** 思维链折叠阈值：超过此行数视为过长，自动折叠为前 N 行 + 「展开剩余 X 行」。 */
internal const val REASONING_COLLAPSE_LINE_LIMIT = 8

/**
 * 思考过程可折叠气泡：左对齐、浅色弱化，与正式回复区分。点击标题栏折叠/展开。
 *
 * 折叠判定按行数阈值：超过 [REASONING_COLLAPSE_LINE_LIMIT] 行视为「过长」，自动折叠为
 * 前 N 行 + 「展开剩余 X 行」。流式实时展示时，短文本边想边看，一旦长度越过阈值即自动
 * 折叠（折叠态下新内容仍持续追加，保持折叠不刷屏，用户可随时点开看最新）；落库后的历史
 * 气泡默认折叠，避免刷屏。用户手动 toggle 后以用户选择为准，不再被自动折叠覆盖。
 */
@Composable
internal fun ReasoningBubble(
    text: String,
    initiallyExpanded: Boolean = true,
    cache: MarkdownRenderCache? = null,
    // 问题23：是否处于「思考仍在进行」阶段（流式思考中且正文尚未开始）。
    // 为 true 时在内容末尾显示跳动点，明确「思考仍在继续」；思考分块到达的间隙动画不消失。
    // 一旦正文开始流式（live=false），动画移交给 StreamingBubble，此处不再重复打点。
    live: Boolean = false
) {
    var userToggled by remember { mutableStateOf(false) }
    var expanded by remember { mutableStateOf(initiallyExpanded) }
    val lineCount = remember(text) { text.count { it == '\n' } + 1 }
    val overThreshold = lineCount > REASONING_COLLAPSE_LINE_LIMIT
    // 自动折叠：仅在用户尚未手动 toggle 过时生效；用户手动展开/折叠后以用户选择为准。
    // 问题19：流式输出中（initiallyExpanded=true）始终保持展开，不因超长自动折叠；
    // 流式结束后历史气泡（initiallyExpanded=false）默认折叠，超长时显示尾部 N 行。
    val effectiveExpanded = when {
        userToggled -> expanded
        initiallyExpanded -> true
        else -> !overThreshold
    }
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.Start
    ) {
        val isDark = LocalAppDarkMode.current
        // 混合模式：思考条背景 surfaceVariant（#F1F5F9 / #1E293B），圆角 8dp
        Surface(
            shape = RoundedCornerShape(LocalCornerRadius.current.md),
            color = MaterialTheme.colorScheme.surfaceVariant,
            modifier = Modifier.fillMaxWidth()
        ) {
            // 混合模式：思考条内边距 10dp 水平 / 6dp 垂直
            Column(modifier = Modifier.padding(horizontal = 10.dp, vertical = 6.dp)) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clickable {
                            userToggled = true
                            expanded = !expanded
                        },
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Icon(
                        Icons.Rounded.Star,
                        contentDescription = null,
                        // 思考条弱化色：跟随语义色 onSurfaceVariant
                        tint = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.size(14.dp)
                    )
                    Spacer(Modifier.width(Spacing.sm))
                    Text(
                        text = stringResource(R.string.chat_thinking_process),
                        // 混合模式：思考条 12sp/行高18sp
                        style = MaterialTheme.typography.labelMedium.copy(
                            fontSize = LocalComponentTokens.current.text.bodySmallFontSize,
                            lineHeight = 18.sp
                        ),
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.weight(1f)
                    )
                    Icon(
                        if (effectiveExpanded) Icons.Rounded.KeyboardArrowUp else Icons.Rounded.KeyboardArrowDown,
                        contentDescription = if (effectiveExpanded) stringResource(R.string.common_collapse) else stringResource(R.string.common_expand),
                        tint = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.size(16.dp)
                    )
                }
                if (effectiveExpanded) {
                    Spacer(Modifier.height(Spacing.xs))
                    MarkdownContent(
                        text = text,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        cache = cache,
                        compact = true,
                        modifier = Modifier.pointerInput(text) {
                            detectTapGestures(
                                onDoubleTap = {
                                    userToggled = true
                                    expanded = false
                                }
                            )
                        }
                    )
                    // 问题23：流式思考中（live=true）在内容末尾显示跳动点，
                    // 明确「思考仍在继续」；思考分块到达的间隙动画不消失，避免用户误判已结束。
                    // 正文开始流式后 live=false，动画移交给 StreamingBubble；历史消息 live=false。
                    if (live) {
                        Spacer(Modifier.height(Spacing.xs))
                        TypingDots(color = MaterialTheme.colorScheme.primary, dotSize = 5.dp)
                    }
                } else if (overThreshold) {
                    // 折叠态：显示最新内容（尾部 N 行）+「还有 X 行」
                    Spacer(Modifier.height(Spacing.xs))
                    val tailText = remember(text) {
                        text.lines().takeLast(REASONING_COLLAPSE_LINE_LIMIT).joinToString("\n")
                    }
                    Text(
                        text = tailText,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        maxLines = REASONING_COLLAPSE_LINE_LIMIT,
                        overflow = TextOverflow.Ellipsis,
                        modifier = Modifier.heightIn(min = (REASONING_COLLAPSE_LINE_LIMIT * 18).dp)
                    )
                    val hidden = lineCount - REASONING_COLLAPSE_LINE_LIMIT
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(LocalCornerRadius.current.md))
                            .clickable {
                                userToggled = true
                                expanded = true
                            }
                            .padding(vertical = Spacing.xs),
                        horizontalArrangement = Arrangement.Center,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Icon(
                            Icons.Rounded.KeyboardArrowDown,
                            contentDescription = stringResource(R.string.common_expand),
                            tint = Brand.IconGray,
                            modifier = Modifier.size(16.dp)
                        )
                        Spacer(Modifier.width(Spacing.xs))
                        Text(
                            text = stringResource(R.string.chat_expand_remaining, hidden),
                            color = MaterialTheme.colorScheme.primary,
                            style = MaterialTheme.typography.labelMedium,
                            fontWeight = FontWeight.Medium
                        )
                    }
                }
            }
        }
    }
}

/**
 * 三个循环跳动的点：通用「正在输入/生成」指示器，取代转圈 spinner。
 * 三点以固定相位差依次上下弹跳，形成波浪式律动。
 *
 * 性能优化：用 graphicsLayer { translationY } 替代 offset(y)，动画值变化在 draw 阶段
 * 处理而不触发 compose/recompose，消除无限动画导致父布局每帧重组的开销。
 * 容器高度固定，防止布局波动传递到 LazyColumn。
 */
@Composable
internal fun TypingDots(
    color: Color,
    dotSize: androidx.compose.ui.unit.Dp = 6.dp
) {
    val transition = rememberInfiniteTransition(label = "typing-dots")
    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = Modifier.height(dotSize + 10.dp)
    ) {
        repeat(3) { index ->
            val offsetY by transition.animateFloat(
                initialValue = 0f,
                targetValue = 0f,
                animationSpec = infiniteRepeatable(
                    animation = keyframes {
                        durationMillis = 900
                        0f at 0
                        -5f at 180
                        0f at 360
                        0f at 900
                    },
                    repeatMode = RepeatMode.Restart,
                    initialStartOffset = StartOffset(index * 150)
                ),
                label = "dot-$index"
            )
            Box(
                modifier = Modifier
                    .graphicsLayer { translationY = offsetY }
                    .size(dotSize)
                    .clip(CircleShape)
                    .background(color)
            )
            if (index < 2) Spacer(Modifier.width(Spacing.xs))
        }
    }
}
