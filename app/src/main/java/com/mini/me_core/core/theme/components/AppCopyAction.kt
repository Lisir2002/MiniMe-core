package com.mini.me_core.core.theme.components

import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Check
import androidx.compose.material.icons.rounded.ContentCopy
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.unit.dp
import com.mini.me_core.R
import com.mini.me_core.core.theme.tokens.LocalAppTheme
import kotlinx.coroutines.delay

/**
 * 复制动作按钮：点击复制文本到剪贴板，图标在 1.5 秒内变为对勾（success 色）作为反馈。
 *
 * 原型为 CodeBlock 的复制按钮（代码块顶栏）与 AboutSection 的剪贴板辅助函数
 * （全项目 31 处文件存在 ClipboardManager 手搓代码）。剪贴板写入、反馈态、
 * 自动复位全部内聚在本组件，调用方只需关心 [onCopied]（如弹 Toast）。
 *
 * @param textToCopy 待复制文本
 * @param onCopied 复制完成回调（用于 Toast 等附加反馈）
 * @param modifier 修饰符
 * @param clipLabel 剪贴板标签（ClipData 的 label，部分系统剪贴板 UI 会展示）
 * @param tint 默认图标着色；null 时用 textSecondary
 * @param resetDelayMillis 对勾反馈持续时长（毫秒）
 */
@Composable
fun AppCopyAction(
    textToCopy: String,
    onCopied: (() -> Unit)? = null,
    modifier: Modifier = Modifier,
    clipLabel: String = "",
    tint: Color? = null,
    resetDelayMillis: Long = 1500,
) {
    val colors = LocalAppTheme.current.colors
    val clipboard = LocalClipboardManager.current
    var copied by remember(textToCopy) { mutableStateOf(false) }

    LaunchedEffect(copied) {
        if (copied) {
            delay(resetDelayMillis)
            copied = false
        }
    }

    IconButton(
        onClick = {
            clipboard.setText(AnnotatedString(textToCopy))
            copied = true
            onCopied?.invoke()
        },
        modifier = modifier,
    ) {
        Icon(
            imageVector = if (copied) Icons.Rounded.Check else Icons.Rounded.ContentCopy,
            contentDescription = stringResource(
                if (copied) R.string.chat_copied else R.string.chat_copy
            ),
            tint = if (copied) colors.success else tint ?: colors.textSecondary,
            modifier = Modifier
                .size(20.dp)
                .padding(2.dp),
        )
    }
}

// ──────────────────────────────────────────────
// Previews
// ──────────────────────────────────────────────

/** Preview：复制按钮。 */
@androidx.compose.ui.tooling.preview.Preview(showBackground = true, widthDp = 400, heightDp = 200)
@Composable
private fun AppCopyActionPreview() {
    com.mini.me_core.core.theme.MiniMeTheme(darkTheme = false) {
        AppCopyAction(
            textToCopy = "https://github.com/Lisir2002/MiniMe-core",
            clipLabel = "repo_url",
        )
    }
}
