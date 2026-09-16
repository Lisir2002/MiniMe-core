package com.mini.me_core.newui.designsystem.component

import com.mini.me_core.newui.designsystem.theme.appPalette
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.CheckCircle
import androidx.compose.material.icons.rounded.ErrorOutline
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import com.mini.me_core.newui.designsystem.token.generated.AppColor
import com.mini.me_core.newui.designsystem.token.generated.AppRadius
import com.mini.me_core.newui.designsystem.token.generated.AppSpacing
import com.mini.me_core.newui.designsystem.token.generated.AppStroke

/** 消息流标记类型：日期分隔 / 系统提示 / 工具调用卡。 */
enum class AppChatMarkerKind { Date, System, Tool }

/**
 * 消息流标记（分子组 · AppChatMarker）：穿插在消息行之间的时间/系统/工具信息层。
 *
 * - [AppChatMarkerKind.Date]：居中浅底胶囊（"今天 · 09:41"），标记会话分段。
 * - [AppChatMarkerKind.System]：居中三级文字，系统级轻提示（模型切换等）。
 * - [AppChatMarkerKind.Tool]：工具调用卡（⚙ 工具名 + 运行中三点 / 成功 / 失败状态），
 *   [running] 为 true 时展示 [AppTypingIndicator] 运行动画，否则按 [tone] 展示结果图标。
 *
 * @since 0.1.0-experimental
 */
@Composable
fun AppChatMarker(
    text: String,
    modifier: Modifier = Modifier,
    kind: AppChatMarkerKind = AppChatMarkerKind.Date,
    tone: Color = AppColor.StatusSuccess,
    running: Boolean = false,
) {
    when (kind) {
        AppChatMarkerKind.Date -> DateMarker(text, modifier)
        AppChatMarkerKind.System -> SystemMarker(text, modifier)
        AppChatMarkerKind.Tool -> ToolMarker(text, modifier, tone, running)
    }
}

@Composable
private fun DateMarker(text: String, modifier: Modifier) {
    Box(modifier.fillMaxWidth(), contentAlignment = Alignment.Center) {
        Text(
            text = text,
            style = MaterialTheme.typography.labelSmall,
            color = appPalette().labelSecondary,
            modifier = Modifier
                .background(appPalette().surfaceDim, RoundedCornerShape(AppRadius.Pill))
                .padding(horizontal = AppSpacing.Md, vertical = 3.dp),
        )
    }
}

@Composable
private fun SystemMarker(text: String, modifier: Modifier) {
    Box(modifier.fillMaxWidth(), contentAlignment = Alignment.Center) {
        Text(
            text = text,
            style = MaterialTheme.typography.labelSmall,
            color = appPalette().labelTertiary,
        )
    }
}

@Composable
private fun ToolMarker(text: String, modifier: Modifier, tone: Color, running: Boolean) {
    Row(
        modifier = modifier
            .background(appPalette().surface, RoundedCornerShape(AppRadius.Pill))
            .border(AppStroke.Thin, appPalette().separator, RoundedCornerShape(AppRadius.Pill))
            .padding(horizontal = AppSpacing.Md, vertical = AppSpacing.Xs),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        if (running) {
            AppTypingIndicator(dotColor = appPalette().primary, dotSize = 4.dp)
        } else {
            // 装饰图标：旁侧已有文字/语义，跳过无障碍
            Icon(
                imageVector = if (tone == AppColor.StatusDanger) Icons.Rounded.ErrorOutline else Icons.Rounded.CheckCircle,
                contentDescription = null,
                tint = tone,
                modifier = Modifier.size(12.dp),
            )
        }
        Text(
            text = text,
            style = MaterialTheme.typography.labelSmall,
            color = appPalette().labelSecondary,
            modifier = Modifier.padding(start = AppSpacing.Sm),
        )
    }
}
