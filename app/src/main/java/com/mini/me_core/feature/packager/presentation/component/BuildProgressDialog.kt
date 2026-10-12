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
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Error
import androidx.compose.material.icons.filled.FileDownload
import androidx.compose.material.icons.filled.HourglassEmpty
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Text
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import com.mini.me_core.feature.packager.domain.engine.ApkAnalyzer
import com.mini.me_core.feature.packager.domain.engine.ApkBuilder
import com.mini.me_core.feature.packager.presentation.PackagerViewModel

/**
 * 构建进度弹窗（流程式）。
 *
 * 以底部弹窗形式展示构建全过程：步骤流程 + 实时日志 + 构建结果 + APK 分析。
 * 替代原独立的 BuildProgressScreen，避免页面跳转打断操作流。
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun BuildProgressDialog(
    onDismiss: () -> Unit,
    viewModel: PackagerViewModel = hiltViewModel()
) {
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    val buildState by viewModel.buildState.collectAsState()
    val apkAnalysis by viewModel.apkAnalysis.collectAsState()
    val isAnalyzing by viewModel.isAnalyzing.collectAsState()
    val steps = ApkBuilder.BuildStep.entries.toTypedArray()
    val logListState = rememberLazyListState()

    // 日志自动滚动到底部
    LaunchedEffect(buildState.logs.size) {
        if (buildState.logs.isNotEmpty()) {
            logListState.animateScrollToItem(buildState.logs.size - 1)
        }
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
            // 标题栏
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    if (buildState.isBuilding) "构建中..."
                    else if (buildState.error != null) "构建失败"
                    else "构建完成",
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold
                )
                Spacer(Modifier.weight(1f))
                if (!buildState.isBuilding) {
                    IconButton(onClick = onDismiss) {
                        Icon(Icons.Default.Close, contentDescription = "关闭")
                    }
                }
            }

            Spacer(Modifier.height(8.dp))

            // 进度条
            if (buildState.isBuilding) {
                LinearProgressIndicator(
                    progress = {
                        if (buildState.totalSteps > 0)
                            (buildState.currentStepIndex + 0.5f) / buildState.totalSteps
                        else 0f
                    },
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(4.dp)
                )
            } else if (buildState.error != null) {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(4.dp)
                        .background(androidx.compose.ui.graphics.Color(0xFFF44336))
                )
            } else if (buildState.resultApk != null) {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(4.dp)
                        .background(androidx.compose.ui.graphics.Color(0xFF4CAF50))
                )
            }

            Spacer(Modifier.height(12.dp))

            // 内容区：步骤流程 + 日志/结果
            LazyColumn(
                modifier = Modifier.weight(1f),
                state = if (buildState.logs.isNotEmpty()) logListState else rememberLazyListState(),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                // 步骤流程
                items(steps) { step ->
                    val index = steps.indexOf(step)
                    val isCurrent = buildState.currentStep == step && buildState.isBuilding
                    val isCompleted = buildState.isBuilding && index < buildState.currentStepIndex
                    val isSuccess = !buildState.isBuilding && buildState.error == null
                    val isFailed = !buildState.isBuilding && buildState.error != null && index >= buildState.currentStepIndex

                    StepRow(
                        step = step,
                        isCurrent = isCurrent,
                        isCompleted = isCompleted,
                        isSuccess = isSuccess,
                        isFailed = isFailed
                    )
                }

                // 构建结果
                if (!buildState.isBuilding) {
                    item {
                        Spacer(Modifier.height(8.dp))
                        val error = buildState.error
                        val resultApk = buildState.resultApk
                        if (error != null) {
                            BuildErrorCard(error = error)
                        } else if (resultApk != null) {
                            BuildSuccessCard(
                                apkPath = resultApk.absolutePath,
                                apkSize = buildState.resultApkSize,
                                onInstall = { viewModel.installApk(resultApk) }
                            )
                        }
                    }
                }

                // APK 分析结果（构建成功后自动分析）
                if (!buildState.isBuilding && buildState.resultApk != null) {
                    item {
                        Spacer(Modifier.height(12.dp))
                        Text("APK 分析", style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.Bold)
                        Spacer(Modifier.height(8.dp))
                        if (isAnalyzing) {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                androidx.compose.material3.CircularProgressIndicator(
                                    modifier = Modifier.size(16.dp),
                                    strokeWidth = 2.dp
                                )
                                Spacer(Modifier.width(8.dp))
                                Text("分析中...", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                            }
                        } else if (apkAnalysis != null) {
                            ApkAnalysisSummary(analysis = apkAnalysis!!)
                        }
                    }
                }

                // 构建日志
                if (buildState.logs.isNotEmpty()) {
                    item {
                        Spacer(Modifier.height(12.dp))
                        Text("构建日志", style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.Bold)
                        Spacer(Modifier.height(8.dp))
                        Box(
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(200.dp)
                                .background(androidx.compose.ui.graphics.Color(0xFF1A1A2E), RoundedCornerShape(8.dp))
                                .padding(12.dp)
                        ) {
                            LazyColumn(state = logListState) {
                                items(buildState.logs.takeLast(200)) { log ->
                                    Text(
                                        text = log,
                                        style = MaterialTheme.typography.bodySmall,
                                        fontFamily = FontFamily.Monospace,
                                        color = androidx.compose.ui.graphics.Color(0xFFB0B0C0)
                                    )
                                }
                            }
                        }
                    }
                }

                item { Spacer(Modifier.height(16.dp)) }
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
    isFailed: Boolean
) {
    val (icon, color) = when {
        isCurrent -> Icons.Default.HourglassEmpty to MaterialTheme.colorScheme.primary
        isCompleted || isSuccess -> Icons.Default.CheckCircle to androidx.compose.ui.graphics.Color(0xFF4CAF50)
        isFailed -> Icons.Default.Error to androidx.compose.ui.graphics.Color(0xFFF44336)
        else -> Icons.Default.HourglassEmpty to MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.4f)
    }

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .background(
                if (isCurrent) MaterialTheme.colorScheme.primary.copy(alpha = 0.1f)
                else MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.3f),
                RoundedCornerShape(8.dp)
            )
            .padding(horizontal = 16.dp, vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Icon(icon, contentDescription = null, tint = color, modifier = Modifier.size(18.dp))
        Spacer(Modifier.width(10.dp))
        Text(
            step.displayName,
            style = MaterialTheme.typography.bodyMedium,
            fontWeight = if (isCurrent) FontWeight.Bold else FontWeight.Normal,
            color = if (isCurrent) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurface
        )
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
            .background(androidx.compose.ui.graphics.Color(0xFF4CAF50).copy(alpha = 0.1f), RoundedCornerShape(12.dp))
            .padding(16.dp)
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Icon(Icons.Default.CheckCircle, contentDescription = null, tint = androidx.compose.ui.graphics.Color(0xFF4CAF50), modifier = Modifier.size(24.dp))
            Spacer(Modifier.width(10.dp))
            Text("构建成功", style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.Bold, color = androidx.compose.ui.graphics.Color(0xFF4CAF50))
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
            Icon(Icons.Default.FileDownload, contentDescription = null, tint = androidx.compose.ui.graphics.Color.White, modifier = Modifier.size(18.dp))
            Spacer(Modifier.width(6.dp))
            Text("安装 APK", style = MaterialTheme.typography.labelLarge, color = androidx.compose.ui.graphics.Color.White, fontWeight = FontWeight.Bold)
        }
    }
}

/**
 * 构建失败卡片
 */
@Composable
private fun BuildErrorCard(error: String) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .background(androidx.compose.ui.graphics.Color(0xFFF44336).copy(alpha = 0.1f), RoundedCornerShape(12.dp))
            .padding(16.dp)
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Icon(Icons.Default.Error, contentDescription = null, tint = androidx.compose.ui.graphics.Color(0xFFF44336), modifier = Modifier.size(24.dp))
            Spacer(Modifier.width(10.dp))
            Text("构建失败", style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.Bold, color = androidx.compose.ui.graphics.Color(0xFFF44336))
        }
        Spacer(Modifier.height(8.dp))
        Text(error, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurface)
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

private fun formatSize(bytes: Long): String {
    val kb = bytes / 1024.0
    val mb = kb / 1024.0
    return when {
        mb >= 1 -> String.format(java.util.Locale.US, "%.2f MB", mb)
        kb >= 1 -> String.format(java.util.Locale.US, "%.1f KB", kb)
        else -> "$bytes B"
    }
}
