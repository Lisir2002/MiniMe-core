package com.mini.me_core.newui.designsystem.component.molecule

import com.mini.me_core.newui.designsystem.theme.appPalette
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkVertically
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.KeyboardArrowDown
import androidx.compose.material.icons.rounded.Lightbulb
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
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.mini.me_core.newui.designsystem.token.generated.AppColor
import com.mini.me_core.newui.designsystem.token.generated.AppRadius
import com.mini.me_core.newui.designsystem.token.generated.AppSizing
import com.mini.me_core.newui.designsystem.token.generated.AppSpacing
import com.mini.me_core.newui.designsystem.token.generated.AppStroke

/**
 * 思考过程折叠块（分子组 · AppThinkingBlock）：AI 消息正文前/后的 reasoning 展示层，
 * 对齐 OpenAI/DeepSeek `reasoning_content` 与 Anthropic extended thinking 的 UI 惯例：
 * 思考与正式回复**视觉分离**（浅灰底 + 弱化文字），默认折叠避免刷屏。
 *
 * - 可折叠：[label] 标题栏（灯泡图标 + 行数元信息 + 旋转箭头），点击展开/收起正文。
 * - 行数阈值自动折叠：正文超过 [collapseLineLimit] 行视为"过长"，除非用户手动展开，
 *   一律默认折叠（对齐 app 侧 `ReasoningBubble` 的 `REASONING_COLLAPSE_LINE_LIMIT` 语义）。
 * - 流式实时追加：[isStreaming] 时标题栏头部展示三点脉动（对应 `AgentEvent.ReasoningDelta`），
 *   折叠态下新内容持续累积、不刷屏，用户可随时点开看最新。
 * - 弱化呈现：正文用 [appPalette().labelSecondary] + 浅灰底，与主回复（白底墨色）形成层级差。
 *
 * 建议用法：作为消息行正文之前的一个独立块（user 气泡对齐 Start），
 * 同一助手消息若有思考 + 正文，两者并列渲染而非嵌套。
 */
@Composable
fun AppThinkingBlock(
    text: String,
    modifier: Modifier = Modifier,
    label: String = "思考过程",
    initiallyExpanded: Boolean = true,
    isStreaming: Boolean = false,
    collapseLineLimit: Int = 8,
) {
    var userToggled by remember { mutableStateOf(false) }
    var expanded by remember { mutableStateOf(initiallyExpanded) }
    val lineCount = remember(text) { text.count { it == '\n' } + 1 }
    val overThreshold = lineCount > collapseLineLimit
    // 自动折叠仅在用户未手动 toggle 过时生效；手动展开/折叠后以用户选择为准
    val effectiveExpanded = if (userToggled) expanded else (initiallyExpanded && !overThreshold)
    val shape = RoundedCornerShape(AppRadius.Md)

    Column(
        modifier = modifier
            .fillMaxWidth()
            .clip(shape)
            .background(appPalette().surface)
            .border(AppStroke.Thin, appPalette().separator, shape),
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .clickable {
                    userToggled = true
                    expanded = !expanded
                }
                .padding(horizontal = AppSpacing.Md, vertical = AppSpacing.Sm),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            // 装饰图标：旁侧已有文字/语义，跳过无障碍
            Icon(
                imageVector = Icons.Rounded.Lightbulb,
                contentDescription = null,
                tint = appPalette().accent,
                modifier = Modifier.size(AppSizing.IconXs),
            )
            Spacer(Modifier.width(AppSpacing.Sm))
            Text(
                text = label,
                style = MaterialTheme.typography.labelMedium,
                color = appPalette().labelSecondary,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.weight(1f, fill = false),
            )
            // 元信息：流式脉动 + 行数
            if (isStreaming) {
                Spacer(Modifier.width(AppSpacing.Sm))
                AppTypingIndicator(dotColor = appPalette().accent, dotSize = 3.dp)
            }
            if (overThreshold) {
                Spacer(Modifier.width(AppSpacing.Sm))
                Text(
                    text = "$lineCount 行",
                    style = MaterialTheme.typography.labelSmall,
                    color = appPalette().labelTertiary,
                    maxLines = 1,
                )
            }
            Spacer(Modifier.width(AppSpacing.Sm))
            val rotation by animateFloatAsState(
                targetValue = if (effectiveExpanded) 180f else 0f,
                label = "thinkingChevron",
            )
            Icon(
                imageVector = Icons.Rounded.KeyboardArrowDown,
                contentDescription = if (effectiveExpanded) "收起$label" else "展开$label",
                tint = appPalette().labelTertiary,
                modifier = Modifier
                    .size(AppSizing.IconXs)
                    .rotate(rotation),
            )
        }
        AnimatedVisibility(
            visible = effectiveExpanded,
            enter = expandVertically() + fadeIn(),
            exit = shrinkVertically() + fadeOut(),
        ) {
            AppMarkdownText(
                text = text,
                style = MaterialTheme.typography.bodySmall,
                color = appPalette().labelSecondary,
                modifier = Modifier.padding(
                    start = AppSpacing.Md,
                    end = AppSpacing.Md,
                    bottom = AppSpacing.Md,
                ),
            )
        }
    }
}
