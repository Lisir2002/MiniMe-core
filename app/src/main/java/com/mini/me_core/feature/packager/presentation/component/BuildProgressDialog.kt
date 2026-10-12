@file:Suppress("FunctionNaming", "LongMethod", "CyclomaticComplexMethod", "CognitiveComplexMethod")

package com.mini.me_core.feature.packager.presentation.component

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowDownward
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.ContentCopy
import androidx.compose.material.icons.filled.Error
import androidx.compose.material.icons.filled.FileDownload
import androidx.compose.material.icons.filled.RadioButtonUnchecked
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import com.mini.me_core.feature.packager.domain.engine.ApkAnalyzer
import com.mini.me_core.feature.packager.domain.engine.ApkBuilder
import com.mini.me_core.feature.packager.presentation.PackagerViewModel
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

/**
 * 构建进度弹窗（流程式）。
 *
 * 布局：固定标题栏 + 进度条 + 步骤流程区 + 日志区 + 底部结果区。
 * 日志区支持平滑自动滚动、滚离底部时暂停并显示悬浮回到底部按钮、按级别着色。
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun BuildProgressDialog(
    onDismiss: () -> Unit,
    onRetry: () -> Unit = {},
    viewModel: PackagerViewModel = hiltViewModel()
) {
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    val buildState by viewModel.buildState.collectAsState()
    val apkAnalysis by viewModel.apkAnalysis.collectAsState()
    val isAnalyzing by viewModel.isAnalyzing.collectAsState()
    val steps = ApkBuilder.BuildStep.entries.toTypedArray()
    val logListState = rememberLazyListState()
    val clipboard = LocalClipboardManager.current
    val scope = rememberCoroutineScope()

    var autoScroll by remember { mutableStateOf(true) }

    // 根据可视布局推导是否已经位于日志底部
    val isAtBottom by remember {
        derivedStateOf {
            val info = logListState.layoutInfo
            val total = info.totalItemsCount
            if (total == 0) return@derivedStateOf true
            val lastVisibleIndex = info.visibleItemsInfo.lastOrNull()?.index ?: 0
            lastVisibleIndex >= total - 1
        }
    }

    // 实时时钟：用于构建中总耗时与当前步骤耗时
    var now by remember { mutableStateOf(System.currentTimeMillis()) }
    LaunchedEffect(buildState.isBuilding) {
        if (buildState.isBuilding) {
            while (true) {
                now = System.currentTimeMillis()
                delay(500)
            }
        }
    }

    // 用户向上滚离底部时暂停自动滚动；滚回底部时恢复
    LaunchedEffect(logListState) {
        snapshotFlow { isAtBottom }
            .collect { atBottom ->
                autoScroll = atBottom
            }
    }

    // 日志追加时若自动滚动开启则平滑滚动到底部
    LaunchedEffect(buildState.logs.size, autoScroll) {
        if (autoScroll && buildState.logs.isNotEmpty()) {
            logListState.animateScrollToItem(buildState.logs.size - 1)
        }
    }

    // 构建失败时强制滚动到最后一行
    LaunchedEffect(buildState.error != null) {
        if (buildState.error != null && buildState.logs.isNotEmpty()) {
            autoScroll = true
            logListState.animateScrollToItem(buildState.logs.size - 1)
        }
    }

    // 标题文案、主题色与前置图标
    val (titleText, titleColor, titleIcon) = when {
        buildState.isBuilding -> Triple(
            "构建中...",
            MaterialTheme.colorScheme.primary,
            null
        )
        buildState.error != null -> Triple(
            "构建失败",
            Color(0xFFF44336),
            Icons.Default.Close
        )
        buildState.resultApk != null -> Triple(
            "构建成功",
            Color(0xFF4CAF50),
            Icons.Default.Check
        )
        else -> Triple(
            "构建完成",
            MaterialTheme.colorScheme.onSurface,
            null
        )
    }

    val totalElapsed: Long = when {
        buildState.isBuilding && buildState.buildStartTime > 0 -> now - buildState.buildStartTime
        buildState.buildEndTime > 0 && buildState.buildStartTime > 0 -> buildState.buildEndTime - buildState.buildStartTime
        else -> 0L
    }

    ModalBottomSheet(
        onDismissRequest = { if (!buildState.isBuilding) onDismiss() },
        sheetState = sheetState,
        modifier = Modifier.fillMaxSize()
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(horizontal = 16.dp)
        ) {
            // ===== 标题栏 =====
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically
            ) {
                if (buildState.isBuilding) {
                    CircularProgressIndicator(
                        modifier = Modifier.size(18.dp),
                        strokeWidth = 2.dp,
                        color = titleColor
                    )
                    Spacer(Modifier.width(8.dp))
                } else if (titleIcon != null) {
                    Icon(
                        titleIcon,
                        contentDescription = null,
                        tint = titleColor,
                        modifier = Modifier.size(20.dp)
                    )
                    Spacer(Modifier.width(8.dp))
                }
                Text(
                    titleText,
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold,
                    color = titleColor
                )
                Spacer(Modifier.weight(1f))
                if (totalElapsed > 0L) {
                    Text(
                        formatDuration(totalElapsed),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Spacer(Modifier.width(12.dp))
                }
                if (!buildState.isBuilding) {
                    IconButton(onClick = onDismiss) {
                        Icon(Icons.Default.Close, contentDescription = "关闭")
                    }
                }
            }

            Spacer(Modifier.height(8.dp))

            // ===== 进度条（按状态着色）=====
            when {
                buildState.isBuilding -> LinearProgressIndicator(
                    progress = {
                        if (buildState.totalSteps > 0)
                            (buildState.currentStepIndex + 0.5f) / buildState.totalSteps
                        else 0f
                    },
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(4.dp),
                    color = MaterialTheme.colorScheme.primary,
                    trackColor = MaterialTheme.colorScheme.primary.copy(alpha = 0.2f)
                )
                buildState.error != null -> Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(4.dp)
                        .background(Color(0xFFF44336))
                )
                buildState.resultApk != null -> Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(4.dp)
                        .background(Color(0xFF4CAF50))
                )
            }

            Spacer(Modifier.height(6.dp))

            // ===== 状态摘要行 =====
            val summary = when {
                buildState.isBuilding && buildState.totalSteps > 0 ->
                    "步骤 ${(buildState.currentStepIndex + 1).coerceAtMost(buildState.totalSteps)}/${buildState.totalSteps}"
                buildState.error != null -> "构建失败"
                buildState.resultApk != null -> "构建成功"
                else -> ""
            }
            if (summary.isNotEmpty()) {
                Text(
                    summary,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                Spacer(Modifier.height(6.dp))
            }

            // ===== 步骤流程区（最大高度 180dp，超出可滚动）=====
            LazyColumn(
                modifier = Modifier
                    .fillMaxWidth()
                    .heightIn(max = 180.dp),
                verticalArrangement = Arrangement.spacedBy(4.dp)
            ) {
                items(steps) { step ->
                    val index = steps.indexOf(step)
                    val isCurrent = buildState.currentStep == step && buildState.isBuilding
                    val isCompleted = buildState.isBuilding && index < buildState.currentStepIndex
                    val isSuccess = !buildState.isBuilding && buildState.error == null
                    val isFailed = !buildState.isBuilding && buildState.error != null && index >= buildState.currentStepIndex
                    val durationMs = buildState.stepDurations[step]
                    val liveElapsed = if (isCurrent) {
                        buildState.stepStartTimes[step]?.let { now - it }
                    } else null

                    StepRow(
                        step = step,
                        isCurrent = isCurrent,
                        isCompleted = isCompleted,
                        isSuccess = isSuccess,
                        isFailed = isFailed,
                        durationMs = durationMs,
                        liveElapsedMs = liveElapsed
                    )
                }
            }

            Spacer(Modifier.height(8.dp))

            // ===== 日志工具栏 =====
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    "日志 · ${buildState.logs.size} 行",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                Spacer(Modifier.weight(1f))
                Text(
                    "自动滚动",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                Switch(
                    checked = autoScroll,
                    onCheckedChange = { autoScroll = it }
                )
                IconButton(
                    onClick = {
                        clipboard.setText(AnnotatedString(buildState.logs.joinToString("\n")))
                    },
                    enabled = buildState.logs.isNotEmpty()
                ) {
                    Icon(Icons.Default.ContentCopy, contentDescription = "复制全部日志")
                }
            }

            Spacer(Modifier.height(4.dp))

            // ===== 日志区（最大高度 300dp，避免弹窗过高）=====
            Box(
                modifier = Modifier
                    .weight(1f)
                    .fillMaxWidth()
                    .heightIn(max = 300.dp)
                    .background(Color(0xFF1A1A2E), RoundedCornerShape(8.dp))
                    .padding(8.dp)
            ) {
                if (buildState.logs.isEmpty()) {
                    Text(
                        "等待日志输出...",
                        style = MaterialTheme.typography.bodySmall,
                        fontFamily = FontFamily.Monospace,
                        color = Color(0xFF606070),
                        modifier = Modifier.align(Alignment.Center)
                    )
                } else {
                    LazyColumn(
                        state = logListState,
                        modifier = Modifier.fillMaxSize()
                    ) {
                        items(buildState.logs) { log ->
                            Text(
                                text = log,
                                style = MaterialTheme.typography.bodySmall,
                                fontFamily = FontFamily.Monospace,
                                color = logColor(log),
                                fontWeight = if (logEmphasis(log)) FontWeight.Bold else FontWeight.Normal
                            )
                        }
                    }

                    // 不在底部时显示悬浮回到底部按钮
                    if (!isAtBottom) {
                        Icon(
                            imageVector = Icons.Default.ArrowDownward,
                            contentDescription = "滚动到底部",
                            tint = Color.White,
                            modifier = Modifier
                                .align(Alignment.BottomEnd)
                                .size(32.dp)
                                .background(MaterialTheme.colorScheme.primary, CircleShape)
                                .clickable {
                                    autoScroll = true
                                    scope.launch {
                                        if (buildState.logs.isNotEmpty()) {
                                            logListState.animateScrollToItem(buildState.logs.size - 1)
                                        }
                                    }
                                }
                        )
                    }
                }
            }

            // ===== 结果区（构建结束后显示）=====
            if (!buildState.isBuilding) {
                Spacer(Modifier.height(8.dp))
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .heightIn(max = 220.dp)
                        .verticalScroll(rememberScrollState())
                ) {
                    val error = buildState.error
                    val resultApk = buildState.resultApk
                    when {
                        error != null -> BuildErrorCard(
                            error = error,
                            onRetry = onRetry
                        )
                        resultApk != null -> {
                            BuildSuccessCard(
                                apkPath = resultApk.absolutePath,
                                apkSize = buildState.resultApkSize,
                                onInstall = { viewModel.installApk(resultApk) }
                            )
                            if (apkAnalysis != null || isAnalyzing) {
                                Spacer(Modifier.height(8.dp))
                                Text(
                                    "APK 分析",
                                    style = MaterialTheme.typography.titleSmall,
                                    fontWeight = FontWeight.Bold
                                )
                                Spacer(Modifier.height(6.dp))
                                if (isAnalyzing) {
                                    Row(verticalAlignment = Alignment.CenterVertically) {
                                        CircularProgressIndicator(
                                            modifier = Modifier.size(16.dp),
                                            strokeWidth = 2.dp
                                        )
                                        Spacer(Modifier.width(8.dp))
                                        Text(
                                            "分析中...",
                                            style = MaterialTheme.typography.bodySmall,
                                            color = MaterialTheme.colorScheme.onSurfaceVariant
                                        )
                                    }
                                } else if (apkAnalysis != null) {
                                    ApkAnalysisSummary(analysis = apkAnalysis!!)
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}

/**
 * 步骤行
 */
@Composable
private fun StepRow(
    step: ApkBuilder.BuildStep,
    isCurrent: Boolean,
    isCompleted: Boolean,
    isSuccess: Boolean,
    isFailed: Boolean,
    durationMs: Long?,
    liveElapsedMs: Long?
) {
    val durationLabel = when {
        durationMs != null -> formatDuration(durationMs)
        liveElapsedMs != null -> "${formatDuration(liveElapsedMs)} ..."
        else -> ""
    }

    val textColor = when {
        isCurrent -> MaterialTheme.colorScheme.primary
        isFailed -> Color(0xFFF44336)
        isCompleted || isSuccess -> Color(0xFF4CAF50)
        else -> MaterialTheme.colorScheme.onSurfaceVariant
    }

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .background(
                if (isCurrent) MaterialTheme.colorScheme.primary.copy(alpha = 0.1f)
                else MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.3f),
                RoundedCornerShape(8.dp)
            )
            .padding(horizontal = 12.dp, vertical = 6.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        when {
            isCurrent -> CircularProgressIndicator(
                modifier = Modifier.size(16.dp),
                strokeWidth = 2.dp,
                color = MaterialTheme.colorScheme.primary
            )
            isCompleted || isSuccess -> Icon(
                Icons.Default.Check,
                contentDescription = null,
                tint = Color(0xFF4CAF50),
                modifier = Modifier.size(16.dp)
            )
            isFailed -> Icon(
                Icons.Default.Close,
                contentDescription = null,
                tint = Color(0xFFF44336),
                modifier = Modifier.size(16.dp)
            )
            else -> Icon(
                Icons.Default.RadioButtonUnchecked,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.5f),
                modifier = Modifier.size(16.dp)
            )
        }
        Spacer(Modifier.width(8.dp))
        Text(
            step.displayName,
            style = MaterialTheme.typography.bodySmall,
            fontWeight = if (isCurrent) FontWeight.Bold else FontWeight.Normal,
            color = textColor,
            modifier = Modifier.weight(1f)
        )
        if (durationLabel.isNotEmpty()) {
            Text(
                durationLabel,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    }
}

/**
 * 构建成功卡片
 */
@Composable
private fun BuildSuccessCard(
    apkPath: String,
    apkSize: Long,
    onInstall: () -> Unit
) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .background(Color(0xFF4CAF50).copy(alpha = 0.1f), RoundedCornerShape(12.dp))
            .padding(16.dp)
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Icon(Icons.Default.CheckCircle, contentDescription = null, tint = Color(0xFF4CAF50), modifier = Modifier.size(24.dp))
            Spacer(Modifier.width(10.dp))
            Text("构建成功", style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.Bold, color = Color(0xFF4CAF50))
        }
        Spacer(Modifier.height(8.dp))
        Text("大小: ${formatSize(apkSize)}", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        Text(
            "路径: $apkPath",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis
        )
        Spacer(Modifier.height(12.dp))
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .background(MaterialTheme.colorScheme.primary, RoundedCornerShape(8.dp))
                .clickable(onClick = onInstall)
                .padding(vertical = 10.dp),
            horizontalArrangement = Arrangement.Center,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Icon(Icons.Default.FileDownload, contentDescription = null, tint = Color.White, modifier = Modifier.size(18.dp))
            Spacer(Modifier.width(6.dp))
            Text("安装 APK", style = MaterialTheme.typography.labelLarge, color = Color.White, fontWeight = FontWeight.Bold)
        }
    }
}

/**
 * 构建失败卡片
 */
@Composable
private fun BuildErrorCard(
    error: String,
    onRetry: () -> Unit
) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .background(Color(0xFFF44336).copy(alpha = 0.1f), RoundedCornerShape(12.dp))
            .padding(16.dp)
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Icon(Icons.Default.Error, contentDescription = null, tint = Color(0xFFF44336), modifier = Modifier.size(24.dp))
            Spacer(Modifier.width(10.dp))
            Text("构建失败", style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.Bold, color = Color(0xFFF44336))
        }
        Spacer(Modifier.height(8.dp))
        Text(error, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurface)
        Spacer(Modifier.height(12.dp))
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .background(MaterialTheme.colorScheme.primary, RoundedCornerShape(8.dp))
                .clickable(onClick = onRetry)
                .padding(vertical = 10.dp),
            horizontalArrangement = Arrangement.Center,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Icon(Icons.Default.Refresh, contentDescription = null, tint = Color.White, modifier = Modifier.size(18.dp))
            Spacer(Modifier.width(6.dp))
            Text("重试", style = MaterialTheme.typography.labelLarge, color = Color.White, fontWeight = FontWeight.Bold)
        }
    }
}

/**
 * APK 分析摘要
 */
@Composable
private fun ApkAnalysisSummary(analysis: ApkAnalyzer.ApkAnalysis) {
    Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
        InfoRow("包名", analysis.packageName)
        if (analysis.versionName.isNotEmpty()) InfoRow("版本", "v${analysis.versionName}")
        if (analysis.minSdkVersion > 0) InfoRow("最低 SDK", "API ${analysis.minSdkVersion}")
        InfoRow("文件大小", formatSize(analysis.fileSize))
        Row(horizontalArrangement = Arrangement.spacedBy(16.dp)) {
            StatChip("Activity", analysis.activityCount)
            StatChip("Service", analysis.serviceCount)
            StatChip("权限", analysis.permissions.size)
            StatChip("Dex", analysis.dexCount)
        }
        if (analysis.isSigned) {
            InfoRow("签名", analysis.signatureAlgorithm)
        }
    }
}

@Composable
private fun InfoRow(label: String, value: String) {
    Row(modifier = Modifier.padding(vertical = 2.dp)) {
        Text(label, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.width(70.dp))
        Text(value, style = MaterialTheme.typography.bodySmall, fontWeight = FontWeight.Medium)
    }
}

@Composable
private fun StatChip(label: String, count: Int) {
    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        Text("$count", style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.primary)
        Text(label, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
    }
}

/**
 * 日志行按级别着色：错误红、警告橙、成功绿、分隔线蓝加粗、普通灰色
 */
private fun logColor(text: String): Color {
    val lower = text.lowercase()
    return when {
        lower.contains("error") ||
            lower.contains("failure") ||
            lower.contains("exception") ||
            text.contains("失败") -> Color(0xFFFF6B6B)
        lower.contains("warning") ||
            lower.contains("note:") ||
            text.contains("警告") -> Color(0xFFFFB74D)
        lower.contains("success") ||
            text.contains("完成") ||
            text.contains("成功") -> Color(0xFF81C784)
        text.contains("=====") || text.contains("---") -> Color(0xFF82AAFF)
        else -> Color(0xFFB0B0C0)
    }
}

private fun logEmphasis(text: String): Boolean =
    text.contains("=====") || text.contains("---")

private fun formatSize(bytes: Long): String {
    val kb = bytes / 1024.0
    val mb = kb / 1024.0
    return when {
        mb >= 1 -> String.format(java.util.Locale.US, "%.2f MB", mb)
        kb >= 1 -> String.format(java.util.Locale.US, "%.1f KB", kb)
        else -> "$bytes B"
    }
}

private fun formatDuration(ms: Long): String {
    if (ms <= 0) return ""
    val seconds = ms / 1000.0
    return String.format(java.util.Locale.US, "%.1fs", seconds)
}
