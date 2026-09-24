package com.mini.logs.ui.crash

import android.widget.Toast
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import com.mini.logs.data.CrashGroup
import com.mini.logs.util.FormatUtils
import com.mini.me_core.core.theme.Spacing
import com.mini.me_core.core.theme.components.AppButton
import com.mini.me_core.core.theme.components.AppButtonColor
import com.mini.me_core.core.theme.components.AppButtonSize
import com.mini.me_core.core.theme.components.AppButtonVariant
import com.mini.me_core.core.theme.components.AppCard
import com.mini.me_core.core.theme.components.AppCardVariant
import com.mini.me_core.core.theme.components.AppSectionHeader
import com.mini.me_core.core.theme.components.AppTopAppBar
import com.mini.me_core.core.theme.tokens.LocalAppTheme
import com.mini.me_core.core.util.LogLevel

/** 可点击堆栈行：at <pkg>.<Class>.<method>(<File>:<Line>) */
private val STACK_AT_PATTERN = Regex(
    """^\s*at\s+[A-Za-z_][A-Za-z0-9_.$]*\([^)]*\)\s*$"""
)

/**
 * 崩溃详情全屏覆盖（Dialog 铺满屏幕）。
 * 完整堆栈 + 发生记录 + 底部操作栏。
 */
@Composable
fun CrashDetailSheet(
    crash: CrashGroup,
    onDismiss: () -> Unit,
    onCopy: (String) -> Unit,
    onExport: (title: String, text: String) -> Unit,
    onToggleFixed: () -> Unit,
) {
    val colors = LocalAppTheme.current.colors
    val context = LocalContext.current
    val clipboard = LocalClipboardManager.current

    Dialog(
        onDismissRequest = onDismiss,
        properties = DialogProperties(usePlatformDefaultWidth = false),
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .background(colors.surfacePage),
        ) {
            AppTopAppBar(
                title = "崩溃详情",
                onNavigateBack = onDismiss,
                navigationIcon = Icons.AutoMirrored.Rounded.ArrowBack,
            )

            Column(
                modifier = Modifier
                    .weight(1f)
                    .verticalScroll(rememberScrollState())
                    .padding(Spacing.lg),
            ) {
                Text(
                    text = crash.exceptionType,
                    fontSize = 20.sp,
                    fontWeight = FontWeight.Bold,
                    color = colors.error,
                )
                Spacer(Modifier.height(Spacing.sm))
                Text(
                    text = "发生 ${crash.occurrences} 次 · 最近: ${FormatUtils.formatDateTime(crash.lastOccurrence)}" +
                        "\n首次: ${FormatUtils.formatDateTime(crash.firstOccurrence)}" +
                        "\n涉及 Tag: ${crash.tags.joinToString(", ")}",
                    fontSize = 13.sp,
                    color = colors.textSecondary,
                )

                Spacer(Modifier.height(Spacing.lg))
                AppSectionHeader(title = "完整堆栈")
                AppCard(variant = AppCardVariant.Sunken) {
                    Column(
                        modifier = Modifier
                            .horizontalScroll(rememberScrollState())
                            .padding(Spacing.md),
                    ) {
                        val stackText = crash.fullStackTrace.ifBlank { crash.message }
                        stackText.split('\n').forEach { line ->
                            val isClickable = STACK_AT_PATTERN.matches(line)
                            Text(
                                text = line,
                                fontSize = 12.sp,
                                fontFamily = FontFamily.Monospace,
                                color = if (isClickable) colors.brandPrimary else colors.textPrimary,
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .let { m ->
                                        if (isClickable) m.then(
                                            Modifier.padding(vertical = 1.dp).clickable {
                                                clipboard.setText(AnnotatedString(line))
                                                Toast.makeText(context, "已复制堆栈行", Toast.LENGTH_SHORT).show()
                                            }
                                        ) else m.padding(vertical = 1.dp)
                                    },
                            )
                        }
                    }
                }

                Spacer(Modifier.height(Spacing.lg))
                AppSectionHeader(title = "发生记录")
                val records = remember(crash) {
                    crash.entries
                        .filter { it.level == LogLevel.ERROR || it.level == LogLevel.FATAL }
                        .distinctBy { it.timestamp to it.tag }
                        .sortedByDescending { it.timestamp }
                }
                if (records.isEmpty()) {
                    Text(
                        "仅有聚合统计，无逐次时间记录",
                        fontSize = 12.sp,
                        color = colors.textTertiary,
                    )
                } else {
                    records.forEach { rec ->
                        val recordText = "${FormatUtils.formatDateTime(rec.timestamp)}  ·  ${rec.tag.ifBlank { "-" }}"
                        Text(
                            text = recordText,
                            fontSize = 12.sp,
                            fontFamily = FontFamily.Monospace,
                            color = colors.textSecondary,
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(vertical = Spacing.xs)
                                .clickable {
                                    clipboard.setText(AnnotatedString(recordText))
                                    Toast.makeText(context, "已复制发生记录", Toast.LENGTH_SHORT).show()
                                },
                        )
                    }
                }
            }

            // 底部固定操作栏
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .background(colors.surfaceCard)
                    .padding(Spacing.md),
                horizontalArrangement = Arrangement.spacedBy(Spacing.sm),
            ) {
                AppButton(
                    text = "复制堆栈",
                    onClick = { onCopy(crash.fullStackTrace) },
                    variant = AppButtonVariant.Tonal,
                    size = AppButtonSize.Small,
                    modifier = Modifier.weight(1f),
                )
                AppButton(
                    text = "导出",
                    onClick = {
                        onExport(
                            crash.exceptionType,
                            buildString {
                                append(crash.exceptionType).append('\n')
                                append(crash.fullStackTrace)
                            },
                        )
                    },
                    variant = AppButtonVariant.Tonal,
                    size = AppButtonSize.Small,
                    modifier = Modifier.weight(1f),
                )
                AppButton(
                    text = if (crash.isFixed) "取消修复" else "标记已修复",
                    onClick = onToggleFixed,
                    variant = AppButtonVariant.Filled,
                    buttonColor = if (crash.isFixed) AppButtonColor.Neutral else AppButtonColor.Success,
                    size = AppButtonSize.Small,
                    modifier = Modifier.weight(1f),
                )
            }
        }
    }
}
