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
import androidx.compose.material.icons.rounded.CheckCircle
import androidx.compose.material.icons.rounded.ErrorOutline
import androidx.compose.material.icons.rounded.KeyboardArrowDown
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
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.mini.me_core.newui.designsystem.token.generated.AppColor
import com.mini.me_core.newui.designsystem.token.generated.AppRadius
import com.mini.me_core.newui.designsystem.token.generated.AppSizing
import com.mini.me_core.newui.designsystem.token.generated.AppSpacing
import com.mini.me_core.newui.designsystem.token.generated.AppStroke
import java.util.Locale

/**
 * 技能调用生命周期状态：运行中 → 成功 / 失败。
 */
enum class AppSkillCallState { Running, Success, Error }

/**
 * 技能调用卡（分子组 · AppSkillCallCard）：技能 / 斜杠命令调用的终端风格展示，
 * 对齐 Claude Desktop / Claude Code CLI 的紧凑徽章范式：
 *
 * - 一行记录 `▸ skill-name [args]`（等宽字体，`▸` 点缀蓝），不内联展开完整 SKILL.md 正文。
 * - 状态徽标：[AppSkillCallState.Running]（三点脉动）/ 成功（绿勾）/ 失败（红叉）。
 * - 可选 [description]（SKILL.md 摘要）：点击整行展开/收起，副文本展示技能用途。
 *
 * 与 [AppToolCallCard] 的差异：工具卡强调"可审计的参数 + 结果"，技能卡强调"命令式触发"的轻量记录。
 *
 * @since 0.1.0-experimental
 */
@Composable
fun AppSkillCallCard(
    name: String,
    modifier: Modifier = Modifier,
    args: String? = null,
    state: AppSkillCallState = AppSkillCallState.Running,
    description: String? = null,
    durationMs: Long? = null,
) {
    var expanded by remember { mutableStateOf(false) }
    val statusColor = when (state) {
        AppSkillCallState.Running -> appPalette().primary
        AppSkillCallState.Success -> AppColor.StatusSuccess
        AppSkillCallState.Error -> AppColor.StatusDanger
    }
    val cardShape = RoundedCornerShape(AppRadius.Md)
    val meta = durationMs?.takeIf { state == AppSkillCallState.Success }?.let { formatSkillDuration(it) }

    Column(
        modifier = modifier
            .clip(cardShape)
            .background(appPalette().surface)
            .border(
                width = AppStroke.Thin,
                color = if (state == AppSkillCallState.Error) {
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
                .then(if (description != null) Modifier.clickable { expanded = !expanded } else Modifier)
                .padding(horizontal = AppSpacing.Md, vertical = AppSpacing.Sm),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            // 终端风格前缀 ▸
            Text(
                text = "▸",
                style = MaterialTheme.typography.bodyMedium,
                fontWeight = FontWeight.Bold,
                color = appPalette().accent,
            )
            Spacer(Modifier.width(AppSpacing.Xs))
            Text(
                text = name,
                style = MaterialTheme.typography.bodyMedium,
                fontWeight = FontWeight.Medium,
                color = appPalette().ink,
                fontFamily = FontFamily.Monospace,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            if (args != null) {
                Spacer(Modifier.width(AppSpacing.Xs))
                Text(
                    text = args,
                    style = MaterialTheme.typography.bodyMedium,
                    color = appPalette().labelSecondary,
                    fontFamily = FontFamily.Monospace,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }
            if (meta != null) {
                Spacer(Modifier.width(AppSpacing.Xs))
                Text(
                    text = meta,
                    style = MaterialTheme.typography.labelSmall,
                    color = appPalette().labelSecondary,
                    maxLines = 1,
                )
            }
            Spacer(Modifier.weight(1f))
            when (state) {
                AppSkillCallState.Running -> AppTypingIndicator(dotColor = statusColor, dotSize = 4.dp)
                AppSkillCallState.Success -> Icon(
                    imageVector = Icons.Rounded.CheckCircle,
                    contentDescription = "成功",
                    tint = statusColor,
                    modifier = Modifier.size(AppSizing.IconXs),
                )
                AppSkillCallState.Error -> Icon(
                    imageVector = Icons.Rounded.ErrorOutline,
                    contentDescription = "失败",
                    tint = statusColor,
                    modifier = Modifier.size(AppSizing.IconXs),
                )
            }
            if (description != null) {
                Spacer(Modifier.width(AppSpacing.Xs))
                val rotation by animateFloatAsState(
                    targetValue = if (expanded) 180f else 0f,
                    label = "skillChevron",
                )
                Icon(
                    imageVector = Icons.Rounded.KeyboardArrowDown,
                    contentDescription = if (expanded) "收起技能说明" else "展开技能说明",
                    tint = appPalette().labelTertiary,
                    modifier = Modifier
                        .size(AppSizing.IconXs)
                        .rotate(rotation),
                )
            }
        }
        AnimatedVisibility(
            visible = expanded && description != null,
            enter = expandVertically() + fadeIn(),
            exit = shrinkVertically() + fadeOut(),
        ) {
            Text(
                text = description.orEmpty(),
                style = MaterialTheme.typography.bodySmall,
                color = appPalette().labelSecondary,
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(start = AppSpacing.Md, end = AppSpacing.Md, bottom = AppSpacing.Md),
            )
        }
    }
}

/** 技能耗时格式化："850ms" / "1.6s"。 */
private fun formatSkillDuration(ms: Long): String {
    return if (ms < 1000) {
        String.format(Locale.US, "%dms", ms)
    } else {
        String.format(Locale.US, "%.1fs", ms / 1000.0)
    }
}
