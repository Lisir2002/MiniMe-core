package com.mini.me_core.newui.designsystem.component.molecule

import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Refresh
import androidx.compose.material3.Icon
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
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.mini.me_core.newui.designsystem.token.generated.AppColor
import com.mini.me_core.newui.designsystem.token.generated.AppRadius
import com.mini.me_core.newui.designsystem.token.generated.AppSpacing

/**
 * 消息状态机：发送中 → 流式输出 → 完成 / 失败（失败可重试）。
 */
enum class AppChatMessageState { Pending, Streaming, Complete, Error }

/**
 * 聊天气泡（分子组 · AppChatBubble）：用户侧品牌色胶囊 / AI 侧表面卡片奶白描边，
 * 支撑 AI 对话 / Agent 审批等高信息密度会话流。
 *
 * 状态机 [AppChatMessageState]：发送中 → 流式输出（尾部光标闪烁）→ 完成 / 失败（失败附重试）。
 * 文本经 [AppMarkdownText] 轻量渲染（段落 / 粗体 / 行内代码 / 代码块 / 列表），颜色全系 AppColor 令牌。
 */
@Composable
fun AppChatBubble(
    text: String,
    modifier: Modifier = Modifier,
    state: AppChatMessageState = AppChatMessageState.Complete,
    isUser: Boolean = false,
    accent: Color = AppColor.BrandPrimary,
    onRetry: (() -> Unit)? = null,
) {
    var entered by remember { mutableStateOf(false) }
    LaunchedEffect(Unit) { entered = true }
    val progress by animateFloatAsState(
        targetValue = if (entered) 1f else 0f,
        animationSpec = spring(dampingRatio = Spring.DampingRatioNoBouncy, stiffness = Spring.StiffnessMediumLow),
        label = "chatBubble",
    )
    val radius = if (isUser) AppRadius.Sm else AppRadius.Md
    val shape = RoundedCornerShape(
        topStart = if (isUser) AppRadius.Lg else radius,
        topEnd = if (isUser) radius else AppRadius.Lg,
        bottomStart = AppRadius.Lg,
        bottomEnd = AppRadius.Lg,
    )
    val bg = if (isUser) accent else AppColor.BrandCard
    val borderColor = when {
        state == AppChatMessageState.Error -> AppColor.StatusDanger
        !isUser -> AppColor.SeparatorOnLight
        else -> Color.Transparent
    }
    val contentColor = if (isUser) Color.White else AppColor.BrandInk

    Box(
        modifier = modifier
            .graphicsLayer {
                val s = 0.92f + 0.08f * progress
                scaleX = s
                scaleY = s
                alpha = 0.5f + 0.5f * progress
            }
            .clip(shape)
            .background(bg)
            .then(if (borderColor != Color.Transparent) Modifier.border(1.dp, borderColor, shape) else Modifier)
            .padding(horizontal = AppSpacing.Lg, vertical = AppSpacing.Md),
    ) {
        when (state) {
            AppChatMessageState.Pending -> Row(verticalAlignment = Alignment.CenterVertically) {
                AppTypingIndicator(
                    dotColor = if (isUser) Color.White.copy(alpha = 0.85f) else AppColor.BrandPrimary,
                    dotSize = 6.dp,
                )
            }
            AppChatMessageState.Streaming -> Row(verticalAlignment = Alignment.Bottom) {
                AppMarkdownText(
                    text = text,
                    style = MaterialTheme.typography.bodyMedium,
                    color = contentColor,
                    codeBlockBackground = if (isUser) Color.White.copy(alpha = 0.14f) else AppColor.BrandSurfaceDim,
                    inlineCodeColor = if (isUser) Color.White else AppColor.BrandPrimary,
                    inlineCodeBackground = if (isUser) Color.White.copy(alpha = 0.14f) else AppColor.BrandSurfaceDim,
                )
                StreamingCaret(color = if (isUser) Color.White else AppColor.BrandPrimary)
            }
            AppChatMessageState.Error -> Column {
                AppMarkdownText(
                    text = text,
                    style = MaterialTheme.typography.bodyMedium,
                    color = contentColor,
                    codeBlockBackground = if (isUser) Color.White.copy(alpha = 0.14f) else AppColor.BrandSurfaceDim,
                    inlineCodeColor = if (isUser) Color.White else AppColor.BrandPrimary,
                    inlineCodeBackground = if (isUser) Color.White.copy(alpha = 0.14f) else AppColor.BrandSurfaceDim,
                )
                if (onRetry != null) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(top = AppSpacing.Sm),
                        horizontalArrangement = Arrangement.End,
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        RetryLabel(color = if (isUser) Color.White else AppColor.StatusDanger, onClick = onRetry)
                    }
                }
            }
            AppChatMessageState.Complete -> AppMarkdownText(
                text = text,
                style = MaterialTheme.typography.bodyMedium,
                color = contentColor,
                fontWeight = if (isUser) FontWeight.Medium else FontWeight.Normal,
                codeBlockBackground = if (isUser) Color.White.copy(alpha = 0.14f) else AppColor.BrandSurfaceDim,
                inlineCodeColor = if (isUser) Color.White else AppColor.BrandPrimary,
                inlineCodeBackground = if (isUser) Color.White.copy(alpha = 0.14f) else AppColor.BrandSurfaceDim,
            )
        }
    }
}

/** 流式输出光标：文本末尾闪烁的 ▍。 */
@Composable
private fun StreamingCaret(color: Color) {
    val transition = rememberInfiniteTransition(label = "caret")
    val alpha by transition.animateFloat(
        initialValue = 1f,
        targetValue = 0.1f,
        animationSpec = infiniteRepeatable(
            animation = tween(durationMillis = 480, easing = LinearEasing),
            repeatMode = RepeatMode.Reverse,
        ),
        label = "caretAlpha",
    )
    Text(
        text = "▍",
        style = MaterialTheme.typography.bodyMedium,
        color = color,
        modifier = Modifier.graphicsLayer { this.alpha = alpha },
    )
}

/** 失败态重试入口：图标 + 文字小胶囊。 */
@Composable
private fun RetryLabel(color: Color, onClick: () -> Unit) {
    Row(
        modifier = Modifier
            .clip(RoundedCornerShape(AppRadius.Pill))
            .clickable(onClick = onClick)
            .padding(horizontal = AppSpacing.Sm, vertical = 2.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Icon(
            imageVector = Icons.Rounded.Refresh,
            contentDescription = null,
            tint = color,
            modifier = Modifier.size(14.dp),
        )
        Text(
            text = "重试",
            style = MaterialTheme.typography.labelSmall,
            color = color,
            modifier = Modifier.padding(start = 2.dp),
        )
    }
}
