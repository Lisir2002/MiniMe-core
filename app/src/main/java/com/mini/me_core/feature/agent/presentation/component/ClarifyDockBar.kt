package com.mini.me_core.feature.agent.presentation.component

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.KeyboardArrowLeft
import androidx.compose.material.icons.rounded.KeyboardArrowRight
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.font.FontWeight
import com.mini.me_core.feature.agent.domain.tool.question.PendingUserQuestion
import com.mini.me_core.feature.agent.domain.tool.question.SingleAnswer
import com.mini.me_core.feature.agent.domain.tool.question.UserQuestionAnswer
import com.mini.me_core.newui.designsystem.theme.appPalette
import com.mini.me_core.newui.designsystem.token.generated.AppRadius
import com.mini.me_core.newui.designsystem.token.generated.AppSpacing

/**
 * 吸附在输入框上方的澄清/提问条（紧凑，一次只显示一个问题）。
 *
 * - 多题队列：左右箭头切换，禁用到头箭头，右上角 x/n。
 * - 单选题选完自动跳下一题；多选题点选后按「下一条」；全部答完自动收起并回传 [onSubmit]。
 * - 层级位于审批条之上、输入框之上。颜色/尺寸走 newui 令牌。
 */
@Composable
internal fun ClarifyDockBar(
    question: PendingUserQuestion,
    onSubmit: (UserQuestionAnswer) -> Unit,
    onSkip: () -> Unit,
) {
    val palette = appPalette()
    val total = question.questions.size
    var index by remember(question.id) { mutableStateOf(0) }
    val selected = remember(question.id) { mutableStateMapOf<Int, MutableList<String>>() }
    val q = question.questions[index]
    fun chosen(i: Int): MutableList<String> = selected.getOrPut(i) { mutableStateListOf() }

    fun submitAll() {
        onSubmit(
            UserQuestionAnswer(
                question.questions.mapIndexed { i, item ->
                    SingleAnswer(question = item.question, selected = chosen(i), customText = null)
                }
            )
        )
    }

    fun advance() {
        if (index < total - 1) index += 1 else submitAll()
    }

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = AppSpacing.Lg, vertical = AppSpacing.Xs)
            .clip(RoundedCornerShape(AppRadius.Md))
            .background(palette.card)
            .border(androidx.compose.foundation.BorderStroke(AppLayout_thin(), palette.separator), RoundedCornerShape(AppRadius.Md))
            .padding(horizontal = AppSpacing.Md, vertical = AppSpacing.Sm),
    ) {
        // 头部：‹  问题 …  x/n  › 关闭
        Row(verticalAlignment = Alignment.CenterVertically) {
            Icon(
                imageVector = Icons.Rounded.KeyboardArrowLeft,
                contentDescription = "上一题",
                tint = if (index == 0) palette.labelTertiary else palette.ink,
                modifier = Modifier
                    .size(AppSizingS())
                    .clickable(enabled = index > 0) { index -= 1 },
            )
            Spacer(Modifier.width(AppSpacing.Xs))
            Text(
                text = "${index + 1}/$total",
                style = MaterialTheme.typography.labelMedium,
                color = palette.labelSecondary,
                modifier = Modifier.width(AppSizingS()),
            )
            Spacer(Modifier.width(AppSpacing.Xs))
            Text(
                text = q.question,
                style = MaterialTheme.typography.labelMedium,
                color = palette.ink,
                maxLines = 2,
                modifier = Modifier.weight(1f),
            )
            Spacer(Modifier.width(AppSpacing.Xs))
            Icon(
                imageVector = Icons.Rounded.KeyboardArrowRight,
                contentDescription = "下一题",
                tint = if (index == total - 1) palette.labelTertiary else palette.ink,
                modifier = Modifier
                    .size(AppSizingS())
                    .clickable(enabled = index < total - 1) { index += 1 },
            )
            Spacer(Modifier.width(AppSpacing.Xs))
            Text(
                text = "关闭",
                style = MaterialTheme.typography.labelMedium,
                color = palette.labelSecondary,
                modifier = Modifier
                    .clip(RoundedCornerShape(AppRadius.Sm))
                    .clickable { onSkip() }
                    .padding(horizontal = AppSpacing.Sm, vertical = AppSpacing.Xs),
            )
        }
        // 选项：单行紧凑排列；单选点选即跳，多选点选后给「下一条」按钮。
        Row(
            modifier = Modifier.padding(top = AppSpacing.Sm),
            horizontalArrangement = Arrangement.spacedBy(AppSpacing.Sm),
        ) {
            q.options.forEach { opt ->
                val sel = opt.label in chosen(index)
                Text(
                    text = opt.label,
                    style = MaterialTheme.typography.labelMedium,
                    fontWeight = if (sel) FontWeight.SemiBold else FontWeight.Normal,
                    color = if (sel) palette.onPrimary else palette.ink,
                    modifier = Modifier
                        .clip(RoundedCornerShape(AppRadius.Sm))
                        .background(if (sel) palette.primary else palette.surfaceDim)
                        .clickable {
                            if (q.multiSelect) {
                                val cur = chosen(index)
                                if (sel) cur.remove(opt.label) else cur.add(opt.label)
                                selected[index] = cur
                            } else {
                                selected[index] = mutableStateListOf(opt.label)
                                advance()
                            }
                        }
                        .padding(horizontal = AppSpacing.Sm, vertical = AppSpacing.Xs),
                )
            }
            if (q.multiSelect) {
                Text(
                    text = if (index == total - 1) "完成" else "下一条",
                    style = MaterialTheme.typography.labelMedium,
                    fontWeight = FontWeight.SemiBold,
                    color = palette.onPrimary,
                    modifier = Modifier
                        .clip(RoundedCornerShape(AppRadius.Sm))
                        .background(palette.primary)
                        .clickable { advance() }
                        .padding(horizontal = AppSpacing.Sm, vertical = AppSpacing.Xs),
                )
            }
        }
    }
}

private fun AppLayout_thin() = com.mini.me_core.newui.designsystem.token.generated.AppStroke.Thin
private fun AppSizingS() = com.mini.me_core.newui.designsystem.token.generated.AppSizing.IconS
