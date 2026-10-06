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
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Tab
import androidx.compose.material3.TabRow
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import com.mini.me_core.feature.packager.domain.model.BuildRecord
import com.mini.me_core.feature.packager.domain.model.Project
import com.mini.me_core.feature.packager.presentation.PackagerViewModel
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/**
 * 项目详情页（4个Tab：概览/源文件/配置/构建）
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ProjectDetailScreen(
    projectId: String,
    viewModel: PackagerViewModel = hiltViewModel(),
    onNavigateBack: () -> Unit,
    onStartBuild: (Project) -> Unit
) {
    val projects by viewModel.projects.collectAsState()
    val project = projects.find { it.id == projectId }
    val buildRecords by viewModel.buildRecords.collectAsState()

    var selectedTab by remember { mutableStateOf(0) }
    val tabs = listOf("概览", "源文件", "配置", "构建")

    if (project == null) {
        Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
            Text("项目不存在", color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
        return
    }
    val currentProject = project

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(currentProject.name, maxLines = 1, overflow = TextOverflow.Ellipsis) },
                navigationIcon = {
                    IconButton(onClick = onNavigateBack) {
                        Icon(Icons.Default.ArrowBack, contentDescription = "返回")
                    }
                },
                actions = {
                    IconButton(onClick = { onStartBuild(currentProject) }) {
                        Icon(Icons.Default.PlayArrow, contentDescription = "构建")
                    }
                }
            )
        }
    ) { padding ->
        Column(modifier = Modifier.padding(padding)) {
            TabRow(selectedTabIndex = selectedTab) {
                tabs.forEachIndexed { index, title ->
                    Tab(
                        selected = selectedTab == index,
                        onClick = { selectedTab = index },
                        text = { Text(title) }
                    )
                }
            }

            when (selectedTab) {
                0 -> OverviewTab(project = currentProject, buildRecords = buildRecords, onStartBuild = { onStartBuild(currentProject) })
                1 -> SourceTab(project = currentProject)
                2 -> ConfigTab(project = currentProject)
                3 -> BuildsTab(buildRecords = buildRecords, onInstall = { record ->
                    record.apkPath?.let { viewModel.installApk(java.io.File(it)) }
                })
            }
        }
    }
}

/**
 * 概览 Tab
 */
@Composable
private fun OverviewTab(
    project: Project,
    buildRecords: List<BuildRecord>,
    onStartBuild: () -> Unit
) {
    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = androidx.compose.foundation.layout.PaddingValues(16.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        // 基本信息卡片
        item {
            CardSection(title = "基本信息") {
                InfoRow("应用名称", project.name)
                InfoRow("包名", project.packageName)
                InfoRow("版本", "v${project.versionName} (${project.versionCode})")
                InfoRow("类型", project.type.displayName)
                InfoRow("创建时间", formatDateTime(project.createdAt))
            }
        }

        // 最近构建
        item {
            CardSection(title = "最近构建") {
                if (buildRecords.isEmpty()) {
                    Text("暂无构建记录", color = MaterialTheme.colorScheme.onSurfaceVariant)
                } else {
                    buildRecords.take(3).forEach { record ->
                        BuildRecordRow(record = record, onClick = {})
                    }
                }
            }
        }

        // 快捷操作
        item {
            Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                ActionButtonFull(
                    text = "立即构建",
                    icon = Icons.Default.PlayArrow,
                    onClick = onStartBuild,
                    modifier = Modifier.weight(1f)
                )
            }
        }
    }
}

/**
 * 源文件 Tab
 */
@Composable
private fun SourceTab(project: Project) {
    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = androidx.compose.foundation.layout.PaddingValues(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        item {
            CardSection(title = "www 目录") {
                Text("index.html", style = MaterialTheme.typography.bodyMedium)
                Spacer(Modifier.height(8.dp))
                Text(
                    "P0 阶段支持单 HTML 文件，后续版本将支持多文件/目录管理",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }
    }
}

/**
 * 配置 Tab
 */
@Composable
private fun ConfigTab(project: Project) {
    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = androidx.compose.foundation.layout.PaddingValues(16.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        item {
            CardSection(title = "基本配置") {
                InfoRow("应用名称", project.name)
                InfoRow("包名", project.packageName)
                InfoRow("版本号", "v${project.versionName}")
            }
        }
        item {
            CardSection(title = "能力配置") {
                InfoRow("JS Bridge", if (project.bridgeEnabled) "已启用" else "已禁用")
                InfoRow("权限数量", "${project.permissions.size} 项")
            }
        }
        item {
            Text(
                "P0 阶段配置修改将在后续版本支持",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.6f)
            )
        }
    }
}

/**
 * 构建记录 Tab
 */
@Composable
private fun BuildsTab(
    buildRecords: List<BuildRecord>,
    onInstall: (BuildRecord) -> Unit
) {
    if (buildRecords.isEmpty()) {
        Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                Icon(Icons.Default.Info, contentDescription = null, modifier = Modifier.size(48.dp), tint = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.4f))
                Spacer(Modifier.height(12.dp))
                Text("暂无构建记录", color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        }
        return
    }

    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = androidx.compose.foundation.layout.PaddingValues(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        items(buildRecords, key = { it.id }) { record ->
            BuildRecordRow(record = record, onClick = { onInstall(record) })
        }
    }
}

/**
 * 构建记录行
 */
@Composable
private fun BuildRecordRow(
    record: BuildRecord,
    onClick: () -> Unit
) {
    val (icon, color) = when (record.status) {
        com.mini.me_core.feature.packager.domain.model.BuildStatus.SUCCESS -> Icons.Default.CheckCircle to androidx.compose.ui.graphics.Color(0xFF4CAF50)
        com.mini.me_core.feature.packager.domain.model.BuildStatus.FAILED -> Icons.Default.Error to androidx.compose.ui.graphics.Color(0xFFF44336)
        com.mini.me_core.feature.packager.domain.model.BuildStatus.BUILDING -> Icons.Default.PlayArrow to MaterialTheme.colorScheme.primary
    }

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .background(MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f), RoundedCornerShape(12.dp))
            .clickable(onClick = onClick)
            .padding(16.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Icon(icon, contentDescription = null, tint = color, modifier = Modifier.size(24.dp))
        Spacer(Modifier.width(12.dp))
        Column(modifier = Modifier.weight(1f)) {
            Text("v${record.versionName}", style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.Medium)
            Text(
                "${formatDateTime(record.startTime)} · ${record.formattedDuration}",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            if (record.status == com.mini.me_core.feature.packager.domain.model.BuildStatus.SUCCESS) {
                Text("大小: ${record.formattedApkSize}", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
            if (record.status == com.mini.me_core.feature.packager.domain.model.BuildStatus.FAILED && record.errorMessage != null) {
                Text(record.errorMessage, style = MaterialTheme.typography.bodySmall, color = androidx.compose.ui.graphics.Color(0xFFF44336), maxLines = 2, overflow = TextOverflow.Ellipsis)
            }
        }
        if (record.status == com.mini.me_core.feature.packager.domain.model.BuildStatus.SUCCESS) {
            Icon(Icons.Default.FileDownload, contentDescription = "安装", tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(20.dp))
        }
    }
}

// ========== 通用组件 ==========

@Composable
private fun CardSection(
    title: String,
    content: @Composable () -> Unit
) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .background(MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f), RoundedCornerShape(12.dp))
            .padding(16.dp)
    ) {
        Text(title, style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.Bold)
        Spacer(Modifier.height(12.dp))
        content()
    }
}

@Composable
private fun InfoRow(label: String, value: String) {
    Row(modifier = Modifier.padding(vertical = 4.dp)) {
        Text(label, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.width(90.dp))
        Text(value, style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.Medium)
    }
}

@Composable
private fun ActionButtonFull(
    text: String,
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    Row(
        modifier = modifier
            .background(MaterialTheme.colorScheme.primary, RoundedCornerShape(12.dp))
            .clickable(onClick = onClick)
            .padding(vertical = 14.dp),
        horizontalArrangement = Arrangement.Center,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Icon(icon, contentDescription = null, tint = androidx.compose.ui.graphics.Color.White, modifier = Modifier.size(20.dp))
        Spacer(Modifier.width(8.dp))
        Text(text, style = MaterialTheme.typography.labelLarge, color = androidx.compose.ui.graphics.Color.White, fontWeight = FontWeight.Bold)
    }
}

private fun formatDateTime(timestamp: Long): String =
    SimpleDateFormat("yyyy-MM-dd HH:mm", Locale.getDefault()).format(Date(timestamp))
