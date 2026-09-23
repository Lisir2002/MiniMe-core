package com.mini.me_core.feature.agent.presentation.component
import com.mini.me_core.core.theme.tokens.LocalComponentTokens

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Text
import androidx.compose.material3.TextField
import androidx.compose.material3.TextFieldDefaults
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material.icons.rounded.Fullscreen
import com.mini.me_core.R
import com.mini.me_core.core.theme.LocalAnimationScale
import com.mini.me_core.core.theme.LocalAppDarkMode
import com.mini.me_core.core.theme.Spacing
import com.mini.me_core.core.theme.tokens.LocalAppTheme

/**
 * 输入区本体：附件预览 + 多行输入框。
 * 混合模式：透明背景（外层容器由 ChatInputBar 提供），左侧 ❯ 符号。
 */
@Composable
internal fun ChatInputField(
    value: String,
    onValueChange: (String) -> Unit,
    onSend: () -> Unit,
    isBusy: Boolean,
    pendingAttachments: List<PendingUploadAttachment>,
    onRemoveAttachment: (Int) -> Unit,
    onExpandClick: () -> Unit = {},
    modifier: Modifier = Modifier
) {
    Column(modifier = modifier.fillMaxWidth()) {
        PendingAttachmentPreviewList(
            attachments = pendingAttachments,
            onRemoveAttachment = onRemoveAttachment
        )

        // v2 混合模式：左侧 ❯ 符号 + 输入框同行，垂直居中对齐
        val isDark = LocalAppDarkMode.current
        val colors = LocalAppTheme.current.colors

        // 问题16：检测单行文本是否溢出（水平截断或含换行符），决定是否显示展开按钮
        val textMeasurer = rememberTextMeasurer()
        var fieldWidthPx by remember { mutableStateOf(0) }
        var isOverflowing by remember { mutableStateOf(false) }
        val editorTextStyle = MaterialTheme.typography.bodyLarge.copy(
            fontSize = LocalComponentTokens.current.text.bodyMediumFontSize,
            lineHeight = 20.sp,
            color = colors.textPrimary
        )
        LaunchedEffect(value, fieldWidthPx) {
            isOverflowing = if (fieldWidthPx > 0 && value.isNotEmpty()) {
                val measured = textMeasurer.measure(value, style = editorTextStyle)
                measured.size.width > fieldWidthPx || value.contains("\n")
            } else false
        }

        Row(
            modifier = Modifier
                .fillMaxWidth()
                // 问题15：输入框内部上下 padding 10dp→6dp，收紧文本区与工具栏间距
                .padding(vertical = 6.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(Spacing.sm)
        ) {
            // v2 混合模式：左侧 ❯ 符号，跟随主题主色 primary，14sp 等宽加粗，与光标同行垂直居中
            Text(
                text = "❯",
                color = MaterialTheme.colorScheme.primary,
                fontSize = LocalComponentTokens.current.text.bodyMediumFontSize,
                fontWeight = FontWeight.Bold,
                fontFamily = FontFamily.Monospace
            )
            TextField(
                value = value,
                onValueChange = onValueChange,
                modifier = Modifier
                    .weight(1f)
                    .onSizeChanged { fieldWidthPx = it.width },
                // v2 混合模式：单行输入
                singleLine = true,
                placeholder = {
                    Text(
                        stringResource(if (isBusy) R.string.chat_queue_hint else R.string.chat_input_placeholder),
                        // 混合模式：placeholder 弱化色
                        color = colors.textTertiary,
                        fontSize = LocalComponentTokens.current.text.bodyMediumFontSize
                    )
                },
                // 混合模式：输入文字 14sp，行高 20sp
                textStyle = editorTextStyle,
                enabled = true,
                keyboardOptions = KeyboardOptions(imeAction = androidx.compose.ui.text.input.ImeAction.Send),
                keyboardActions = KeyboardActions(onSend = { onSend() }),
                colors = TextFieldDefaults.colors(
                    focusedContainerColor = Color.Transparent,
                    unfocusedContainerColor = Color.Transparent,
                    disabledContainerColor = Color.Transparent,
                    focusedIndicatorColor = Color.Transparent,
                    unfocusedIndicatorColor = Color.Transparent,
                    disabledIndicatorColor = Color.Transparent,
                    cursorColor = MaterialTheme.colorScheme.primary
                )
            )
            // 问题16：文本溢出时显示展开按钮，点击打开双锚点编辑面板
            if (isOverflowing) {
                IconButton(
                    onClick = onExpandClick,
                    modifier = Modifier.size(36.dp)
                ) {
                    Icon(
                        Icons.Rounded.Fullscreen,
                        contentDescription = stringResource(R.string.common_expand),
                        tint = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.size(20.dp)
                    )
                }
            }
        }
    }
}

/**
 * 问题16：长文本展开编辑面板——双锚点 ModalBottomSheet。
 *
 * 交互设计：
 * - 点击输入框右侧展开按钮后弹出，默认 HalfExpanded（半屏 50%）
 * - 顶部拖拽手柄（Material3 默认 grab handle）支持按住上滑到 Expanded（全屏），下滑收起
 * - 面板与主输入框共享同一 text state（value/onValueChange 由上层提升），编辑实时双向同步
 * - 顶部标题「编辑消息」+ 关闭按钮；中部多行编辑区随面板高度伸缩；底部固定发送按钮
 * - 所有颜色/圆角/间距走主题令牌；面板展开/收起动效由 Material3 sheet state 驱动
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun ExpandInputSheet(
    value: String,
    onValueChange: (String) -> Unit,
    onSend: () -> Unit,
    isBusy: Boolean,
    canSend: Boolean,
    tokenProgress: Float,
    onStop: () -> Unit,
    onDismiss: () -> Unit
) {
    // 双锚点：HalfExpanded（半屏）↔ Expanded（全屏），skipPartiallyExpanded=false 允许半屏锚点
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = false)
    // 动效缩放：读取用户设置，关闭动效时面板即时切换
    val animationScale = LocalAnimationScale.current
    val colors = LocalAppTheme.current.colors

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = sheetState,
        containerColor = MaterialTheme.colorScheme.surface,
        // 拖拽手柄使用 Material3 默认 grab handle（小横条），随展开进度自动偏移
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = Spacing.lg)
                .imePadding()
        ) {
            // 顶部：标题 + 关闭按钮
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = stringResource(R.string.chat_action_edit),
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.onSurface,
                    modifier = Modifier.weight(1f)
                )
                IconButton(onClick = onDismiss) {
                    Icon(
                        Icons.Rounded.Close,
                        contentDescription = stringResource(R.string.common_close),
                        tint = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }

            // 中部：大尺寸多行编辑区，weight(1f) 随面板半屏/全屏高度自动伸缩
            TextField(
                value = value,
                onValueChange = onValueChange,
                modifier = Modifier
                    .fillMaxWidth()
                    .weight(1f, fill = true)
                    .padding(vertical = Spacing.sm),
                placeholder = {
                    Text(
                        stringResource(R.string.chat_input_placeholder),
                        color = colors.textTertiary,
                        fontSize = LocalComponentTokens.current.text.bodyMediumFontSize
                    )
                },
                textStyle = MaterialTheme.typography.bodyLarge.copy(
                    fontSize = LocalComponentTokens.current.text.bodyMediumFontSize,
                    lineHeight = 22.sp,
                    color = colors.textPrimary
                ),
                keyboardOptions = KeyboardOptions.Default,
                colors = TextFieldDefaults.colors(
                    focusedContainerColor = Color.Transparent,
                    unfocusedContainerColor = Color.Transparent,
                    disabledContainerColor = Color.Transparent,
                    focusedIndicatorColor = Color.Transparent,
                    unfocusedIndicatorColor = Color.Transparent,
                    disabledIndicatorColor = Color.Transparent,
                    cursorColor = MaterialTheme.colorScheme.primary
                )
            )

            // 底部：发送按钮（右对齐），发送后自动收起面板
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.End
            ) {
                SendButton(
                    canSend = canSend,
                    isBusy = isBusy,
                    tokenProgress = tokenProgress,
                    onSend = {
                        onSend()
                        onDismiss()
                    },
                    onStop = onStop
                )
            }
        }
    }
}
