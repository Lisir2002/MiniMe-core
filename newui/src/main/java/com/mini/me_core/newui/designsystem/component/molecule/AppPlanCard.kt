package com.mini.me_core.newui.designsystem.component.molecule

import com.mini.me_core.newui.designsystem.theme.appPalette
import androidx.compose.foundation.background
import androidx.compose.foundation.border
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
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.Assignment
import androidx.compose.material.icons.rounded.CheckCircle
import androidx.compose.material.icons.rounded.ErrorOutline
import androidx.compose.material.icons.rounded.RadioButtonUnchecked
import androidx.compose.material.icons.rounded.Warning
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.mini.me_core.newui.designsystem.token.generated.AppColor
import com.mini.me_core.newui.designsystem.token.generated.AppRadius
import com.mini.me_core.newui.designsystem.token.generated.AppSizing
import com.mini.me_core.newui.designsystem.token.generated.AppSpacing
import com.mini.me_core.newui.designsystem.token.generated.AppStroke

/**
 * 计划步骤单步状态（对齐 plan 工具 `[ {text, status} ]` 的 status 取值）：
 * [Pending] 待办 / [InProgress] 进行中 / [Done] 完成 / [Failed] 失败。
 */
enum class AppPlanStepStatus { Pending, InProgress, Done, Failed }

/** 计划步骤：文本 + 状态。 */
data class AppPlanStep(
    val text: String,
    val status: AppPlanStepStatus = AppPlanStepStatus.Pending,
)

/**
 * 计划审批卡（分子组 · AppPlanCard）：AI 在 PLAN 模式产出实施计划后的展示 + 审批层，
 * 对接真实链路 [PlanTool]（title + steps）与 [PlanApprovalManager.awaitApproval]：
 *
 * - 计划呈现：头部图标块 + 计划标题 + 状态徽标（待审批琥珀警示 / 执行中旋转 / 已批准绿勾 / 已放弃灰）。
 * - 步骤清单：[AppPlanStep] 列表逐行渲染，按状态区分指示器——完成（绿勾）/ 进行中（琥珀脉动）/
 *   待办（空心圆）/ 失败（红叉），对齐 Claude Code / Cursor 的 plan 面板范式。
 * - 待定选择：[pendingSelection] 非空时以琥珀信息行呈现（用户批准前每轮注入模型请求的待决策项）。
 * - 审批操作：[AppPlanState.AwaitingApproval] 渲染「批准并执行 / 继续细化」双按钮，
 *   对齐 [PlanApprovalChoice.APPROVE / REFINE]（REFINE 回滚模式到 PLAN 的语义由上层处理）。
 * - 审批理由：[reason] 承载 [PlanApprovalRequest.reason]（如「切到 BUILD 执行前需你确认」）。
 *
 * 建议用法：作为消息流 marker 层的一项（与 [AppChatMarker] 并列），
 * 计划未被批准/被细化时逐轮展示最新版计划卡。
 *
 * @since 0.1.0-experimental
 */
@Composable
fun AppPlanCard(
    title: String,
    modifier: Modifier = Modifier,
    steps: List<AppPlanStep> = emptyList(),
    state: AppPlanState = AppPlanState.AwaitingApproval,
    pendingSelection: String? = null,
    reason: String? = null,
    onApprove: (() -> Unit)? = null,
    onRefine: (() -> Unit)? = null,
) {
    val statusColor = when (state) {
        AppPlanState.AwaitingApproval -> AppColor.StatusWarning
        AppPlanState.InProgress -> appPalette().primary
        AppPlanState.Approved -> AppColor.StatusSuccess
        AppPlanState.Abandoned -> appPalette().labelTertiary
    }
    val cardShape = RoundedCornerShape(AppRadius.Md)

    Column(
        modifier = modifier
            .fillMaxWidth()
            .clip(cardShape)
            .background(appPalette().card)
            .border(
                width = AppStroke.Thin,
                color = if (state == AppPlanState.AwaitingApproval) {
                    AppColor.StatusWarning.copy(alpha = 0.55f)
                } else {
                    appPalette().separator
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
            Box(
                modifier = Modifier
                    .size(AppSizing.IconXl)
                    .clip(RoundedCornerShape(AppRadius.Sm))
                    .background(statusColor.copy(alpha = 0.12f)),
                contentAlignment = Alignment.Center,
            ) {
                // 装饰图标：旁侧已有文字/语义，跳过无障碍
                Icon(
                    imageVector = Icons.AutoMirrored.Rounded.Assignment,
                    contentDescription = null,
                    tint = statusColor,
                    modifier = Modifier.size(AppSizing.IconXs),
                )
            }
            Spacer(Modifier.width(AppSpacing.Sm))
            Text(
                text = title,
                style = MaterialTheme.typography.bodyMedium,
                fontWeight = FontWeight.Medium,
                color = appPalette().ink,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.weight(1f),
            )
            Spacer(Modifier.width(AppSpacing.Sm))
            PlanStatusBadge(state = state, color = statusColor)
        }

        if (reason != null) {
            Text(
                text = reason,
                style = MaterialTheme.typography.labelSmall,
                color = appPalette().labelSecondary,
                modifier = Modifier.padding(horizontal = AppSpacing.Md),
            )
        }

        if (steps.isNotEmpty()) {
            Column(
                modifier = Modifier.padding(
                    start = AppSpacing.Md,
                    end = AppSpacing.Md,
                    top = AppSpacing.Sm,
                    bottom = AppSpacing.Sm,
                ),
                verticalArrangement = Arrangement.spacedBy(AppSpacing.Xs),
            ) {
                steps.forEach { step ->
                    PlanStepRow(step = step)
                }
            }
        }

        if (pendingSelection != null) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = AppSpacing.Md, vertical = AppSpacing.Xs),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(AppSpacing.Xs),
            ) {
                // 装饰图标：旁侧已有文字/语义，跳过无障碍
                Icon(
                    imageVector = Icons.Rounded.Warning,
                    contentDescription = null,
                    tint = AppColor.StatusWarning,
                    modifier = Modifier.size(14.dp),
                )
                Text(
                    text = pendingSelection,
                    style = MaterialTheme.typography.labelSmall,
                    color = appPalette().labelSecondary,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.weight(1f),
                )
            }
        }

        // 审批操作行：批准并执行 / 继续细化（对齐 PlanApprovalChoice.APPROVE / REFINE）
        if (state == AppPlanState.AwaitingApproval && (onApprove != null || onRefine != null)) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = AppSpacing.Md, vertical = AppSpacing.Sm),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(AppSpacing.Sm),
            ) {
                Spacer(Modifier.weight(1f))
                if (onRefine != null) {
                    AppButton(text = "继续细化", onClick = onRefine, variant = AppButtonVariant.Outlined)
                }
                if (onApprove != null) {
                    AppButton(text = "批准并执行", onClick = onApprove, variant = AppButtonVariant.Primary)
                }
            }
        }
    }
}

/** 计划生命周期状态：待审批 → 执行中 / 已批准 / 已放弃。 */
enum class AppPlanState { AwaitingApproval, InProgress, Approved, Abandoned }

/** 状态徽标：待审批琥珀警示 / 执行中旋转 / 已批准绿勾 / 已放弃灰点。 */
@Composable
private fun PlanStatusBadge(state: AppPlanState, color: Color) {
    when (state) {
        AppPlanState.AwaitingApproval -> Icon(
            imageVector = Icons.Rounded.Warning,
            contentDescription = "待审批",
            tint = color,
            modifier = Modifier.size(AppSizing.IconXs),
        )
        AppPlanState.InProgress -> AppTypingIndicator(dotColor = color, dotSize = 4.dp)
        AppPlanState.Approved -> Icon(
            imageVector = Icons.Rounded.CheckCircle,
            contentDescription = "已批准",
            tint = color,
            modifier = Modifier.size(AppSizing.IconXs),
        )
        AppPlanState.Abandoned -> Box(
            modifier = Modifier
                .size(AppSizing.IconXs)
                .clip(CircleShape)
                .background(color.copy(alpha = 0.5f)),
        )
    }
}

/** 单步行：状态指示器 + 步骤文本。 */
@Composable
private fun PlanStepRow(step: AppPlanStep) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(AppSpacing.Sm),
    ) {
        when (step.status) {
            AppPlanStepStatus.Done -> Icon(
                imageVector = Icons.Rounded.CheckCircle,
                contentDescription = "完成",
                tint = AppColor.StatusSuccess,
                modifier = Modifier.size(AppSizing.IconXs),
            )
            AppPlanStepStatus.InProgress -> AppTypingIndicator(dotColor = AppColor.StatusWarning, dotSize = 3.dp)
            AppPlanStepStatus.Failed -> Icon(
                imageVector = Icons.Rounded.ErrorOutline,
                contentDescription = "失败",
                tint = AppColor.StatusDanger,
                modifier = Modifier.size(AppSizing.IconXs),
            )
            AppPlanStepStatus.Pending -> Icon(
                imageVector = Icons.Rounded.RadioButtonUnchecked,
                contentDescription = "待办",
                tint = appPalette().labelTertiary,
                modifier = Modifier.size(AppSizing.IconXs),
            )
        }
        Text(
            text = step.text,
            style = MaterialTheme.typography.bodySmall,
            color = when (step.status) {
                AppPlanStepStatus.Pending -> appPalette().labelSecondary
                AppPlanStepStatus.Done -> appPalette().labelSecondary
                AppPlanStepStatus.InProgress -> appPalette().ink
                AppPlanStepStatus.Failed -> AppColor.StatusDanger
            },
            fontWeight = if (step.status == AppPlanStepStatus.InProgress) FontWeight.Medium else FontWeight.Normal,
            maxLines = 2,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.weight(1f),
        )
    }
}
