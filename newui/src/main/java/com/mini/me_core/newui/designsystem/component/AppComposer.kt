package com.mini.me_core.newui.designsystem.component

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Add
import androidx.compose.material.icons.rounded.Article
import androidx.compose.material.icons.rounded.Build
import androidx.compose.material.icons.rounded.CameraAlt
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material.icons.rounded.Image
import androidx.compose.material.icons.rounded.KeyboardArrowUp
import androidx.compose.material.icons.rounded.Psychology
import androidx.compose.material.icons.rounded.Stop
import androidx.compose.material3.Icon
import androidx.compose.material3.LocalTextStyle
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
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.mini.me_core.newui.designsystem.theme.appPalette
import com.mini.me_core.newui.designsystem.token.generated.AppColor
import com.mini.me_core.newui.designsystem.token.generated.AppRadius
import com.mini.me_core.newui.designsystem.token.generated.AppSizing
import com.mini.me_core.newui.designsystem.token.generated.AppSpacing
import com.mini.me_core.newui.designsystem.token.generated.AppStroke

/** 对话行为模式（与生产 AgentMode 同构，避免跨模块依赖）。 */
enum class AppComposerMode(val label: String) {
    BUILD("构建"),
    PLAN("计划"),
    AUTO("自动");

    fun next(): AppComposerMode = entries[(ordinal + 1) % entries.size]
}

/** 思考强度（与生产 ReasoningEffort 同构）。 */
enum class AppComposerReasoning(val label: String) {
    LOW("低"),
    MEDIUM("中"),
    HIGH("高");

    fun next(): AppComposerReasoning = entries[(ordinal + 1) % entries.size]
}

/** 斜杠命令条目。 */
data class AppComposerSlashCommand(val trigger: String, val description: String)

/**
 * 对话输入框（composer）—— newui iOS 简约风。
 *
 * 与生产 ChatInputBar 同构契约：只持有输入文本与开关状态，附件/模式/模型/发送等全部以回调上抛，
 * 由上层注入真实数据与行为。不暴露"联网开关"之类无接入口的摆设。
 */
@OptIn(ExperimentalLayoutApi::class)
@Composable
fun AppComposer(
    value: String,
    onValueChange: (String) -> Unit,
    modifier: Modifier = Modifier,
    // 附件 / 排队
    attachments: List<String> = emptyList(),
    onRemoveAttachment: (Int) -> Unit = {},
    queued: List<String> = emptyList(),
    onRemoveQueued: (Int) -> Unit = {},
    // 模式 / 思考
    mode: AppComposerMode = AppComposerMode.BUILD,
    onCycleMode: () -> Unit = {},
    reasoning: AppComposerReasoning = AppComposerReasoning.MEDIUM,
    onCycleReasoning: () -> Unit = {},
    // 模型 / 技能
    modelLabel: String = "",
    onPickModel: () -> Unit = {},
    onOpenSkills: () -> Unit = {},
    // 上下文进度 0f..1f，<=0 不显示
    tokenProgress: Float = 0f,
    // 附件面板动作
    onPickFile: () -> Unit = {},
    onPickImage: () -> Unit = {},
    onTakePhoto: () -> Unit = {},
    // 斜杠命令
    slashCommands: List<AppComposerSlashCommand> = emptyList(),
    onRunSlash: (AppComposerSlashCommand) -> Unit = {},
    // 发送
    streaming: Boolean = false,
    onSend: () -> Unit = {},
    onStop: () -> Unit = {},
) {
    val shape = RoundedCornerShape(AppRadius.Lg)
    var showAttachmentSheet by remember { mutableStateOf(false) }
    val showSlashMenu = slashCommands.isNotEmpty() && value.startsWith("/") && !streaming
    val matchedSlash = slashCommands.filter { value.length == 1 || it.trigger.startsWith(value) }
    val sendEnabled = streaming || value.isNotBlank() || attachments.isNotEmpty()

    Column(
        modifier = modifier
            .fillMaxWidth()
            .clip(shape)
            .background(appPalette().card)
            .border(AppStroke.Thin, appPalette().separator, shape)
            .padding(AppSpacing.Sm),
    ) {
        // 顶部：附件 chips + 排队请求 chips。
        FlowRow(
            horizontalArrangement = Arrangement.spacedBy(AppSpacing.Xs),
            verticalArrangement = Arrangement.spacedBy(AppSpacing.Xs),
        ) {
            attachments.forEachIndexed { i, name ->
                RemoveChip(label = name, tone = ChipTone.File, onRemove = { onRemoveAttachment(i) })
            }
            queued.forEachIndexed { i, q ->
                RemoveChip(label = q, tone = ChipTone.Queued, onRemove = { onRemoveQueued(i) })
            }
        }

        if (attachments.isNotEmpty() || queued.isNotEmpty()) Spacer(Modifier.size(AppSpacing.Xs))

        // 斜杠命令浮层：输入 "/" 时展开候选。
        AnimatedVisibility(visible = showSlashMenu && matchedSlash.isNotEmpty()) {
            Column(
                Modifier
                    .fillMaxWidth()
                    .padding(bottom = AppSpacing.Xs)
                    .clip(RoundedCornerShape(AppRadius.Md))
                    .background(appPalette().surface),
            ) {
                matchedSlash.take(6).forEach { cmd ->
                    Row(
                        Modifier
                            .fillMaxWidth()
                            .clickable { onRunSlash(cmd) }
                            .padding(horizontal = AppSpacing.Sm, vertical = AppSpacing.Xs),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Text(
                            cmd.trigger,
                            style = MaterialTheme.typography.labelMedium,
                            color = appPalette().primary,
                            fontWeight = FontWeight.Medium,
                        )
                        Spacer(Modifier.width(AppSpacing.Sm))
                        Text(
                            cmd.description,
                            style = MaterialTheme.typography.labelSmall,
                            color = appPalette().labelTertiary,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                        )
                    }
                }
            }
        }

        // 文本区。
        BasicTextField(
            value = value,
            onValueChange = onValueChange,
            textStyle = LocalTextStyle.current.copy(color = appPalette().ink),
            cursorBrush = SolidColor(appPalette().primary),
            modifier = Modifier
                .fillMaxWidth()
                .heightIn(min = AppSizing.IconXl + AppSpacing.Md, max = 120.dp)
                .padding(horizontal = AppSpacing.Xs, vertical = AppSpacing.Xs),
            decorationBox = { inner ->
                if (value.isEmpty()) {
                    Text(
                        "给 Agent 下指令，输入 / 查看命令…",
                        style = MaterialTheme.typography.bodyLarge,
                        color = appPalette().labelTertiary,
                    )
                }
                inner()
            },
        )

        Spacer(Modifier.size(AppSpacing.Xs))

        // 工具行。
        Row(verticalAlignment = Alignment.CenterVertically) {
            CircleIconBtn(icon = Icons.Rounded.Add, contentDescription = "附件", onClick = { showAttachmentSheet = !showAttachmentSheet })
            Spacer(Modifier.width(AppSpacing.Xs))
            CircleIconBtn(icon = Icons.Rounded.Build, contentDescription = "技能", onClick = onOpenSkills)
            Spacer(Modifier.width(AppSpacing.Sm))
            ModeChip(mode = mode, onClick = onCycleMode)
            Spacer(Modifier.width(AppSpacing.Xs))
            ReasoningChip(reasoning = reasoning, onClick = onCycleReasoning)

            Spacer(Modifier.weight(1f))

            if (tokenProgress > 0f) {
                TokenHint(progress = tokenProgress)
                Spacer(Modifier.width(AppSpacing.Xs))
            }
            if (modelLabel.isNotBlank()) {
                ModelChip(label = modelLabel, onClick = onPickModel)
                Spacer(Modifier.width(AppSpacing.Xs))
            }
            SendOrStopButton(streaming = streaming, enabled = sendEnabled) {
                if (streaming) onStop() else onSend()
            }
        }

        // 附件面板展开。
        AnimatedVisibility(visible = showAttachmentSheet) {
            Column(Modifier.padding(top = AppSpacing.Sm)) {
                AttachmentRow(icon = Icons.Rounded.Article, label = "选择文件") { showAttachmentSheet = false; onPickFile() }
                AttachmentRow(icon = Icons.Rounded.Image, label = "选择图片") { showAttachmentSheet = false; onPickImage() }
                AttachmentRow(icon = Icons.Rounded.CameraAlt, label = "拍照") { showAttachmentSheet = false; onTakePhoto() }
            }
        }
    }
}

private enum class ChipTone { File, Queued }

@Composable
private fun RemoveChip(label: String, tone: ChipTone, onRemove: () -> Unit) {
    val accent = if (tone == ChipTone.Queued) appPalette().primary else appPalette().labelSecondary
    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = Modifier
            .clip(RoundedCornerShape(AppRadius.Pill))
            .background(appPalette().surface)
            .border(AppStroke.Thin, appPalette().separator, RoundedCornerShape(AppRadius.Pill))
            .padding(horizontal = AppSpacing.Sm, vertical = AppSpacing.Tiny),
    ) {
        if (tone == ChipTone.Queued) {
            Text("排队", style = MaterialTheme.typography.labelSmall, color = appPalette().primary, fontWeight = FontWeight.Medium)
            Spacer(Modifier.width(AppSpacing.Xs))
        }
        Text(
            label,
            style = MaterialTheme.typography.labelSmall,
            color = accent,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.widthIn(max = 120.dp),
        )
        Spacer(Modifier.width(AppSpacing.Xs))
        Icon(
            Icons.Rounded.Close,
            contentDescription = "移除",
            tint = appPalette().labelTertiary,
            modifier = Modifier.size(12.dp).clickable { onRemove() },
        )
    }
}

@Composable
private fun CircleIconBtn(icon: ImageVector, contentDescription: String, onClick: () -> Unit) {
    Box(
        modifier = Modifier.size(AppSizing.IconXl).clip(CircleShape).background(appPalette().surface).clickable { onClick() },
        contentAlignment = Alignment.Center,
    ) {
        Icon(icon, contentDescription = contentDescription, tint = appPalette().labelSecondary, modifier = Modifier.size(AppSizing.IconM))
    }
}

@Composable
private fun ModeChip(mode: AppComposerMode, onClick: () -> Unit) {
    val active = mode != AppComposerMode.BUILD
    Row(
        Modifier
            .clip(RoundedCornerShape(AppRadius.Pill))
            .background(if (active) appPalette().primary.copy(alpha = 0.15f) else appPalette().surface)
            .border(
                AppStroke.Thin,
                if (active) appPalette().primary.copy(alpha = 0.35f) else appPalette().separator,
                RoundedCornerShape(AppRadius.Pill),
            )
            .clickable { onClick() }
            .padding(horizontal = AppSpacing.Sm, vertical = AppSpacing.Tiny),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            "模式·${mode.label}",
            style = MaterialTheme.typography.labelMedium,
            color = if (active) appPalette().primary else appPalette().labelSecondary,
            fontWeight = if (active) FontWeight.Medium else FontWeight.Normal,
        )
    }
}

@Composable
private fun ReasoningChip(reasoning: AppComposerReasoning, onClick: () -> Unit) {
    Row(
        Modifier
            .clip(RoundedCornerShape(AppRadius.Pill))
            .background(appPalette().surface)
            .border(AppStroke.Thin, appPalette().separator, RoundedCornerShape(AppRadius.Pill))
            .clickable { onClick() }
            .padding(horizontal = AppSpacing.Sm, vertical = AppSpacing.Tiny),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Icon(Icons.Rounded.Psychology, contentDescription = null, tint = appPalette().labelSecondary, modifier = Modifier.size(14.dp))
        Spacer(Modifier.width(AppSpacing.Xs))
        Text("思考·${reasoning.label}", style = MaterialTheme.typography.labelMedium, color = appPalette().labelSecondary)
    }
}

@Composable
private fun TokenHint(progress: Float) {
    val over = progress > 0.9f
    Text(
        if (over) "上下文快满" else "${(progress * 100).toInt()}%",
        style = MaterialTheme.typography.labelSmall,
        color = if (over) AppColor.StatusDanger else appPalette().labelTertiary,
    )
}

@Composable
private fun ModelChip(label: String, onClick: () -> Unit) {
    Row(
        Modifier.clip(RoundedCornerShape(AppRadius.Pill)).clickable { onClick() }.padding(horizontal = AppSpacing.Sm, vertical = AppSpacing.Tiny),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            label,
            style = MaterialTheme.typography.labelSmall,
            color = appPalette().labelSecondary,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.widthIn(max = 90.dp),
        )
    }
}

@Composable
private fun SendOrStopButton(streaming: Boolean, enabled: Boolean, onClick: () -> Unit) {
    val bg = when {
        !enabled -> appPalette().surface
        streaming -> AppColor.StatusDanger
        else -> appPalette().primary
    }
    val fg = if (enabled) appPalette().onPrimary else appPalette().labelTertiary
    Box(
        Modifier.size(AppSizing.IconXl).clip(CircleShape).background(bg).clickable(enabled = enabled) { onClick() },
        contentAlignment = Alignment.Center,
    ) {
        Icon(
            imageVector = if (streaming) Icons.Rounded.Stop else Icons.Rounded.KeyboardArrowUp,
            contentDescription = if (streaming) "停止" else "发送",
            tint = fg,
            modifier = Modifier.size(AppSizing.IconM),
        )
    }
}

@Composable
private fun AttachmentRow(icon: ImageVector, label: String, onClick: () -> Unit) {
    Row(
        Modifier.fillMaxWidth().clip(RoundedCornerShape(AppRadius.Sm)).clickable { onClick() }.padding(horizontal = AppSpacing.Sm, vertical = AppSpacing.Sm),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Icon(icon, contentDescription = null, tint = appPalette().labelSecondary, modifier = Modifier.size(AppSizing.IconM))
        Spacer(Modifier.width(AppSpacing.Sm))
        Text(label, style = MaterialTheme.typography.bodyMedium, color = appPalette().ink)
    }
}
