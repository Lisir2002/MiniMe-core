package com.mini.me_core.newui.designsystem.component

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
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Add
import androidx.compose.material.icons.rounded.AttachFile
import androidx.compose.material.icons.rounded.KeyboardArrowUp
import androidx.compose.material.icons.rounded.Psychology
import androidx.compose.material.icons.rounded.Search
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
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.mini.me_core.newui.designsystem.theme.appPalette
import com.mini.me_core.newui.designsystem.token.generated.AppColor
import com.mini.me_core.newui.designsystem.token.generated.AppRadius
import com.mini.me_core.newui.designsystem.token.generated.AppSizing
import com.mini.me_core.newui.designsystem.token.generated.AppSpacing
import com.mini.me_core.newui.designsystem.token.generated.AppStroke

/**
 * 对话输入框（composer）：iOS 简约风，卡片式圆角容器。
 *
 * 分层约定：本组件只持有输入文本与开关状态，附件/模型/发送等动作全部以回调上抛，
 * 由上层（Agent / ViewModel）注入实际数据与行为。语音输入暂不内置。
 *
 * @param attachments 已附加的文件名，顶部以 chip 横排展示。
 * @param streaming true 时发送按钮变为停止按钮。
 */
@OptIn(ExperimentalLayoutApi::class)
@Composable
fun AppComposer(
    value: String,
    onValueChange: (String) -> Unit,
    modifier: Modifier = Modifier,
    attachments: List<String> = emptyList(),
    onRemoveAttachment: (Int) -> Unit = {},
    modelLabel: String = "深度",
    onPickModel: () -> Unit = {},
    webSearch: Boolean = false,
    onToggleWeb: () -> Unit = {},
    deepMode: Boolean = false,
    onToggleDeep: () -> Unit = {},
    streaming: Boolean = false,
    onSend: () -> Unit = {},
    onStop: () -> Unit = {},
    onAddAttachment: () -> Unit = {},
    onMention: () -> Unit = {},
) {
    val shape = RoundedCornerShape(AppRadius.Lg)
    Column(
        modifier = modifier
            .fillMaxWidth()
            .clip(shape)
            .background(appPalette().card)
            .border(AppStroke.Thin, appPalette().separator, shape)
            .padding(AppSpacing.Sm),
    ) {
        if (attachments.isNotEmpty()) {
            FlowRow(
                horizontalArrangement = Arrangement.spacedBy(AppSpacing.Xs),
                verticalArrangement = Arrangement.spacedBy(AppSpacing.Xs),
                modifier = Modifier.padding(bottom = AppSpacing.Xs),
            ) {
                attachments.forEachIndexed { i, name ->
                    AttachmentChip(name = name, onRemove = { onRemoveAttachment(i) })
                }
            }
        }

        // 文本区：多行自适应，最多约 5 行后内部滚动。
        BasicTextField(
            value = value,
            onValueChange = onValueChange,
            textStyle = LocalTextStyle.current.copy(color = appPalette().ink),
            cursorBrush = androidx.compose.ui.graphics.SolidColor(appPalette().primary),
            keyboardOptions = KeyboardOptions(imeAction = ImeAction.Default),
            modifier = Modifier
                .fillMaxWidth()
                .heightIn(min = AppSizing.IconXl + AppSpacing.Md, max = 120.dp)
                .padding(horizontal = AppSpacing.Xs, vertical = AppSpacing.Xs),
            decorationBox = { inner ->
                if (value.isEmpty()) {
                    Text(
                        "给 Agent 下指令…",
                        style = MaterialTheme.typography.bodyLarge,
                        color = appPalette().labelTertiary,
                    )
                }
                inner()
            },
        )

        Spacer(Modifier.width(AppSpacing.Xs))

        // 工具行：左侧操作，右侧模型 + 发送/停止。
        Row(verticalAlignment = Alignment.CenterVertically) {
            CircleIconBtn(icon = Icons.Rounded.Add, contentDescription = "更多", onClick = onMention)
            Spacer(Modifier.width(AppSpacing.Xs))
            CircleIconBtn(icon = Icons.Rounded.AttachFile, contentDescription = "附件", onClick = onAddAttachment)
            Spacer(Modifier.width(AppSpacing.Sm))
            ToggleChip(active = webSearch, label = "联网", onClick = onToggleWeb)
            Spacer(Modifier.width(AppSpacing.Xs))
            ToggleChip(active = deepMode, label = "深度", onClick = onToggleDeep)

            Spacer(Modifier.weight(1f))

            ModelChip(label = modelLabel, onClick = onPickModel)
            Spacer(Modifier.width(AppSpacing.Xs))
            SendOrStopButton(streaming = streaming, onClick = {
                if (streaming) onStop() else onSend()
            }, enabled = streaming || value.isNotBlank())
        }
    }
}

@Composable
private fun AttachmentChip(name: String, onRemove: () -> Unit) {
    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = Modifier
            .clip(RoundedCornerShape(AppRadius.Pill))
            .background(appPalette().surface)
            .border(AppStroke.Thin, appPalette().separator, RoundedCornerShape(AppRadius.Pill))
            .padding(horizontal = AppSpacing.Sm, vertical = AppSpacing.Tiny),
    ) {
        Text(
            name,
            style = MaterialTheme.typography.labelSmall,
            color = appPalette().labelSecondary,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.width(96.dp),
        )
        Spacer(Modifier.width(AppSpacing.Xs))
        Text("✕", color = appPalette().labelTertiary, style = MaterialTheme.typography.labelSmall,
            modifier = Modifier.clickable { onRemove() })
    }
}

@Composable
private fun CircleIconBtn(
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    contentDescription: String,
    onClick: () -> Unit,
) {
    Box(
        modifier = Modifier
            .size(AppSizing.IconXl)
            .clip(CircleShape)
            .background(appPalette().surface)
            .clickable { onClick() },
        contentAlignment = Alignment.Center,
    ) {
        Icon(icon, contentDescription = contentDescription, tint = appPalette().labelSecondary, modifier = Modifier.size(AppSizing.IconM))
    }
}

@Composable
private fun ToggleChip(active: Boolean, label: String, onClick: () -> Unit) {
    val bg = if (active) appPalette().primary.copy(alpha = 0.15f) else appPalette().surface
    val fg = if (active) appPalette().primary else appPalette().labelSecondary
    Text(
        label,
        style = MaterialTheme.typography.labelMedium,
        fontWeight = if (active) FontWeight.Medium else FontWeight.Normal,
        color = fg,
        modifier = Modifier
            .clip(RoundedCornerShape(AppRadius.Pill))
            .background(bg)
            .border(
                AppStroke.Thin,
                if (active) appPalette().primary.copy(alpha = 0.35f) else appPalette().separator,
                RoundedCornerShape(AppRadius.Pill),
            )
            .clickable { onClick() }
            .padding(horizontal = AppSpacing.Sm, vertical = AppSpacing.Tiny),
    )
}

@Composable
private fun ModelChip(label: String, onClick: () -> Unit) {
    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = Modifier
            .clip(RoundedCornerShape(AppRadius.Pill))
            .clickable { onClick() }
            .padding(horizontal = AppSpacing.Sm, vertical = AppSpacing.Tiny),
    ) {
        Text(label, style = MaterialTheme.typography.labelSmall, color = appPalette().labelSecondary)
    }
}

@Composable
private fun SendOrStopButton(streaming: Boolean, onClick: () -> Unit, enabled: Boolean) {
    val bg = when {
        !enabled -> appPalette().surface
        streaming -> AppColor.StatusDanger
        else -> appPalette().primary
    }
    val fg = when {
        !enabled -> appPalette().labelTertiary
        else -> appPalette().onPrimary
    }
    Box(
        modifier = Modifier
            .size(AppSizing.IconXl)
            .clip(CircleShape)
            .background(bg)
            .clickable(enabled = enabled) { onClick() },
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
