package com.mini.me_core.feature.agent.presentation.component

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextField
import androidx.compose.material3.TextFieldDefaults
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.mini.me_core.R
import com.mini.me_core.core.theme.LocalAppDarkMode
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
        Row(
            modifier = Modifier
                .fillMaxWidth()
                // v2 混合模式：输入框内部上下 padding 10dp，水平 0dp（由外层容器控制）
                .padding(vertical = 10.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            // v2 混合模式：左侧 ❯ 符号，蓝色 #3B82F6/#60A5FA，14sp 等宽加粗，与光标同行垂直居中
            Text(
                text = "❯",
                color = if (isDark) Color(0xFF60A5FA) else Color(0xFF3B82F6),
                fontSize = 14.sp,
                fontWeight = FontWeight.Bold,
                fontFamily = FontFamily.Monospace
            )
            TextField(
                value = value,
                onValueChange = onValueChange,
                modifier = Modifier.weight(1f),
                // v2 混合模式：单行输入
                singleLine = true,
                placeholder = {
                    Text(
                        stringResource(if (isBusy) R.string.chat_queue_hint else R.string.chat_input_placeholder),
                        // 混合模式：placeholder 弱化色
                        color = colors.textTertiary,
                        fontSize = 14.sp
                    )
                },
                // 混合模式：输入文字 14sp，行高 20sp
                textStyle = MaterialTheme.typography.bodyLarge.copy(
                    fontSize = 14.sp,
                    lineHeight = 20.sp,
                    color = colors.textPrimary
                ),
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
                    cursorColor = if (isDark) Color(0xFF60A5FA) else Color(0xFF3B82F6)
                )
            )
        }
    }
}
