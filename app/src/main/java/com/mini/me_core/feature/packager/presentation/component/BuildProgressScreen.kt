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
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Error
import androidx.compose.material.icons.filled.FileDownload
import androidx.compose.material.icons.filled.HourglassEmpty
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import com.mini.me_core.feature.packager.domain.engine.ApkBuilder
import com.mini.me_core.feature.packager.presentation.PackagerViewModel

/**
 * 构建过程页
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun BuildProgressScreen(
    viewModel: PackagerViewModel = hiltViewModel(),
    onNavigateBack: () -> Unit
) {
    val buildUiState by viewModel.buildState.collectAsState()
    val steps = ApkBuilder.BuildStep.entries.toTypedArray()

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(if (buildUiState.isBuilding) "构建中..." else "构建结果") },
                navigationIcon = {
                    IconButton(onClick = onNavigateBack) {
                        Icon(Icons.Default.ArrowBack, contentDescription = "返回")
                    }
                }
            )
        }
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
        ) {
            // 进度条
            if (buildUiState.isBuilding) {
                LinearProgressIndicator(
                    progress = {
                        if (buildUiState.totalSteps > 0)
                            (buildUiState.currentStepIndex + 0.5f) / buildUiState.totalSteps
                        else 0f
                    },
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(4.dp)
                )
            } else if (buildUiState.error != null) {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(4.dp)
                        .background(androidx.compose.ui.graphics.Color(0xFFF44336))
                )
            } else if (buildUiState.resultApk != null) {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(4.dp)
                        .background(androidx.compose.ui.graphics.Color(0xFF4CAF50))
                )
            }

            // 步骤列表
            LazyColumn(
                modifier = Modifier
                    .weight(1f)
                    .padding(16.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                items(steps) { step ->
                    val index = steps.indexOf(step)
                    val isCurrent = buildUiState.currentStep == step && buildUiState.isBuilding
                    val isCompleted = buildUiState.isBuilding && index < buildUiState.currentStepIndex
                    val isSuccess = !buildUiState.isBuilding && buildUiState.error == null
                    val isFailed = !buildUiState.isBuilding && buildUiState.error != null

                    StepRow(
                        step = step,
                        isCurrent = isCurrent,
                        isCompleted = isCompleted,
                        isSuccess = isSuccess,
                        isFailed = isFailed && index >= buildUiState.currentStepIndex
                    )
                }

                // 构建结果
                if (!buildUiState.isBuilding) {
                    val error = buildUiState.error
                    val resultApk = buildUiState.resultApk
                    item {
                        Spacer(Modifier.height(16.dp))
                        if (error != null) {
                            BuildErrorCard(error = error)
                        } else if (resultApk != null) {
                            BuildSuccessCard(
                                apkPath = resultApk.absolutePath,
                                apkSize = buildUiState.resultApkSize,
                                onInstall = { viewModel.installApk(resultApk) }
                            )
                        }
                    }
                }

                // 日志
                if (buildUiState.logs.isNotEmpty()) {
                    item {
                        Spacer(Modifier.height(16.dp))
                        Text("构建日志", style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.Bold)
                        Spacer(Modifier.height(8.dp))
                        Box(
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(200.dp)
                                .background(androidx.compose.ui.graphics.Color(0xFF1A1A2E), RoundedCornerShape(8.dp))
                                .padding(12.dp)
                        ) {
                            LazyColumn {
                                items(buildUiState.logs.takeLast(100)) { log ->
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
            .padding(horizontal = 16.dp, vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Icon(icon, contentDescription = null, tint = color, modifier = Modifier.size(20.dp))
        Spacer(Modifier.width(12.dp))
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
            Icon(Icons.Default.CheckCircle, contentDescription = null, tint = androidx.compose.ui.graphics.Color(0xFF4CAF50), modifier = Modifier.size(28.dp))
            Spacer(Modifier.width(12.dp))
            Text("构建成功", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold, color = androidx.compose.ui.graphics.Color(0xFF4CAF50))
        }
        Spacer(Modifier.height(12.dp))
        Text("APK 路径: $apkPath", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        Spacer(Modifier.height(4.dp))
        Text("APK 大小: ${formatSize(apkSize)}", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        Spacer(Modifier.height(16.dp))
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .background(MaterialTheme.colorScheme.primary, RoundedCornerShape(8.dp))
                .clickable(onClick = onInstall)
                .padding(vertical = 12.dp),
            horizontalArrangement = Arrangement.Center,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Icon(Icons.Default.FileDownload, contentDescription = null, tint = androidx.compose.ui.graphics.Color.White, modifier = Modifier.size(20.dp))
            Spacer(Modifier.width(8.dp))
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
            Icon(Icons.Default.Error, contentDescription = null, tint = androidx.compose.ui.graphics.Color(0xFFF44336), modifier = Modifier.size(28.dp))
            Spacer(Modifier.width(12.dp))
            Text("构建失败", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold, color = androidx.compose.ui.graphics.Color(0xFFF44336))
        }
        Spacer(Modifier.height(12.dp))
        Text(error, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurface)
    }
}

private fun formatSize(bytes: Long): String {
    val kb = bytes / 1024.0
    val mb = kb / 1024.0
    return when {
        mb >= 1 -> String.format("%.2f MB", mb)
        kb >= 1 -> String.format("%.1f KB", kb)
        else -> "$bytes B"
    }
}
