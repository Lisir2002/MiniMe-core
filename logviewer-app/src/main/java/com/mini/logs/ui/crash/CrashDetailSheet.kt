package com.mini.logs.ui.crash

import android.net.Uri
import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.Alignment
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
import com.mini.logs.util.StackTraceParser
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

private const val HOUR_MS = 3600_000L

/**
 * 崩溃详情全屏覆盖（Dialog 铺满屏幕）。
 * 完整堆栈（语法高亮） + 发生时间分布 + 发生记录 + 相似崩溃 + 底部操作栏（含 SAF 导出）。
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

    val exportContent = remember(crash) {
        buildString {
            append(crash.exceptionType).append('\n')
            append("Message: ").append(crash.message).append('\n')
            append("Occurrences: ").append(crash.occurrences).append('\n')
            append("First: ").append(FormatUtils.formatDateTime(crash.firstOccurrence)).append('\n')
            append("Last: ").append(FormatUtils.formatDateTime(crash.lastOccurrence)).append('\n')
            append("Tags: ").append(crash.tags.joinToString(", ")).append('\n')
            append("\n─── Full Stack ───\n")
            append(crash.fullStackTrace).append('\n')
            append("\n─── Occurrences ───\n")
            crash.entries
                .filter { it.level == LogLevel.ERROR || it.level == LogLevel.FATAL }
                .distinctBy { it.timestamp to it.tag }
                .sortedByDescending { it.timestamp }
                .forEach {
                    append(FormatUtils.formatDateTime(it.timestamp))
                    append(" · ")
                    append(it.tag.ifBlank { "-" })
                    append('\n')
                }
        }
    }

    // SAF 导出文件
    val fileName = remember(crash) {
        val simpleType = crash.exceptionType.replace(Regex("[^A-Za-z0-9]"), "_")
        "crash_${simpleType}_${crash.lastOccurrence}.txt"
    }
    val createDocLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.CreateDocument("text/plain")
    ) { uri: Uri? ->
        if (uri != null) {
            runCatching {
                context.contentResolver.openOutputStream(uri)?.use { out ->
                    out.write(exportContent.toByteArray())
                }
            }.onSuccess {
                Toast.makeText(context, "已导出到文件", Toast.LENGTH_LONG).show()
            }.onFailure {
                Toast.makeText(context, "导出失败: ${it.message}", Toast.LENGTH_LONG).show()
            }
        }
    }

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

                // ── 发生时间分布（24h 柱状图） ──
                Spacer(Modifier.height(Spacing.lg))
                AppSectionHeader(title = "发生时间分布（近 24h）")
                val hourBuckets = remember(crash.key) {
                    val now = System.currentTimeMillis()
                    val start = now - 24 * HOUR_MS
                    val buckets = IntArray(24)
                    crash.entries.forEach { e ->
                        if (e.timestamp in start..now) {
                            val idx = ((e.timestamp - start) / HOUR_MS).toInt().coerceIn(0, 23)
                            buckets[idx]++
                        }
                    }
                    buckets
                }
                val maxBucket = hourBuckets.maxOrNull()?.coerceAtLeast(1) ?: 1
                AppCard(variant = AppCardVariant.Sunken) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(80.dp)
                            .padding(Spacing.sm),
                        verticalAlignment = Alignment.Bottom,
                        horizontalArrangement = Arrangement.spacedBy(2.dp),
                    ) {
                        hourBuckets.forEach { count ->
                            val weightFraction = if (count == 0) 0.04f else (count.toFloat() / maxBucket).coerceAtLeast(0.1f)
                            Box(
                                modifier = Modifier
                                    .weight(1f)
                                    .fillMaxWidth()
                                    .height((60 * weightFraction).dp)
                                    .background(if (count > 0) colors.error else colors.surfacePressed),
                            )
                        }
                    }
                    Text(
                        text = "近 24h 共 ${hourBuckets.sum()} 次",
                        fontSize = 11.sp,
                        color = colors.textTertiary,
                        modifier = Modifier.padding(start = Spacing.sm, bottom = Spacing.sm),
                    )
                }

                Spacer(Modifier.height(Spacing.lg))
                AppSectionHeader(title = "完整堆栈")
                AppCard(variant = AppCardVariant.Sunken) {
                    Column(
                        modifier = Modifier
                            .horizontalScroll(rememberScrollState())
                            .padding(Spacing.md),
                    ) {
                        val stackText = crash.fullStackTrace.ifBlank { crash.message }
                        val stackLines = remember(stackText) { stackText.split('\n') }
                        stackLines.forEachIndexed { idx, line ->
                            val isClickable = STACK_AT_PATTERN.matches(line)
                            val styled = remember(line, idx) {
                                StackTraceParser.styleLine(
                                    line = line,
                                    isFirst = idx == 0,
                                    error = colors.error,
                                    brandPrimary = colors.brandPrimary,
                                    textPrimary = colors.textPrimary,
                                    textTertiary = colors.textTertiary,
                                )
                            }
                            Text(
                                text = styled,
                                fontSize = 12.sp,
                                fontFamily = FontFamily.Monospace,
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

                // ── 相似崩溃 ──
                if (crash.similarGroupKeys.isNotEmpty()) {
                    Spacer(Modifier.height(Spacing.lg))
                    AppSectionHeader(title = "相似崩溃")
                    Text(
                        text = "还有 ${crash.similarGroupKeys.size} 个同类「${crash.exceptionType}」异常，消息不同。",
                        fontSize = 12.sp,
                        color = colors.textSecondary,
                    )
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
                    text = "分享导出",
                    onClick = {
                        onExport(crash.exceptionType, exportContent)
                    },
                    variant = AppButtonVariant.Tonal,
                    size = AppButtonSize.Small,
                    modifier = Modifier.weight(1f),
                )
                AppButton(
                    text = "导出文件",
                    onClick = { runCatching { createDocLauncher.launch(fileName) } },
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
