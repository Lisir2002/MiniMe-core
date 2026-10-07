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
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material.icons.filled.Analytics
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Error
import androidx.compose.material.icons.filled.FileDownload
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.rounded.PlayArrow
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Switch
import androidx.compose.material3.Tab
import androidx.compose.material3.TabRow
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import com.mini.me_core.core.theme.components.AppTopAppBar
import com.mini.me_core.feature.packager.domain.engine.ApkAnalyzer
import com.mini.me_core.feature.packager.domain.model.BuildRecord
import com.mini.me_core.feature.packager.domain.model.BuildStatus
import com.mini.me_core.feature.packager.domain.model.Project
import com.mini.me_core.feature.packager.presentation.PackagerViewModel
import java.io.File
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

    // 进入页面时自动加载构建记录
    LaunchedEffect(projectId) {
        viewModel.loadBuildRecords(projectId)
    }

    Scaffold(
        topBar = {
            AppTopAppBar(
                title = currentProject.name,
                onNavigateBack = onNavigateBack,
                navigationIcon = Icons.AutoMirrored.Rounded.ArrowBack,
                navigationContentDescription = "返回",
                actions = {
                    IconButton(onClick = { onStartBuild(currentProject) }) {
                        Icon(Icons.Rounded.PlayArrow, contentDescription = "构建", modifier = Modifier.size(20.dp))
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
                0 -> OverviewTab(project = currentProject, buildRecords = buildRecords, viewModel = viewModel, onStartBuild = { onStartBuild(currentProject) })
                1 -> SourceTab(project = currentProject, viewModel = viewModel)
                2 -> ConfigTab(project = currentProject, viewModel = viewModel)
                3 -> BuildsTab(buildRecords = buildRecords, onInstall = { record ->
                    record.apkPath?.let { viewModel.installApk(File(it)) }
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
    viewModel: PackagerViewModel,
    onStartBuild: () -> Unit
) {
    val apkAnalysis: ApkAnalyzer.ApkAnalysis? by viewModel.apkAnalysis.collectAsState()
    val isAnalyzing by viewModel.isAnalyzing.collectAsState()
    val latestSuccessBuild = buildRecords.firstOrNull { it.status == BuildStatus.SUCCESS && it.apkPath != null }

    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = androidx.compose.foundation.layout.PaddingValues(16.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        item {
            CardSection(title = "基本信息") {
                InfoRow("应用名称", project.name)
                InfoRow("包名", project.packageName)
                InfoRow("版本", "v${project.versionName} (${project.versionCode})")
                InfoRow("类型", project.type.displayName)
                InfoRow("创建时间", formatDateTime(project.createdAt))
            }
        }

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

        // APK 分析结果
        if (latestSuccessBuild != null) {
            item {
                CardSection(title = "APK 分析") {
                    if (isAnalyzing) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            androidx.compose.material3.CircularProgressIndicator(modifier = Modifier.size(20.dp), strokeWidth = 2.dp)
                            Spacer(Modifier.width(8.dp))
                            Text("分析中...", color = MaterialTheme.colorScheme.onSurfaceVariant)
                        }
                    } else if (apkAnalysis != null) {
                        ApkAnalysisContent(analysis = apkAnalysis!!)
                    } else {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Text("点击按钮分析 APK 详情", color = MaterialTheme.colorScheme.onSurfaceVariant)
                            Spacer(Modifier.weight(1f))
                            TextButton(onClick = {
                                latestSuccessBuild.apkPath?.let { viewModel.analyzeApk(File(it)) }
                            }) {
                                Icon(Icons.Default.Analytics, contentDescription = null, modifier = Modifier.size(18.dp))
                                Spacer(Modifier.width(4.dp))
                                Text("分析APK")
                            }
                        }
                    }
                }
            }
        }

        item {
            Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                ActionButtonFull(
                    text = "立即构建",
                    icon = Icons.Rounded.PlayArrow,
                    onClick = onStartBuild,
                    modifier = Modifier.weight(1f)
                )
            }
        }
    }
}

/**
 * APK 分析结果内容
 */
@Composable
private fun ApkAnalysisContent(analysis: ApkAnalyzer.ApkAnalysis) {
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        // 基本信息
        InfoRow("文件大小", formatSize(analysis.fileSize))
        if (analysis.minSdkVersion > 0) InfoRow("最低 SDK", "API ${analysis.minSdkVersion}")
        if (analysis.targetSdkVersion > 0) InfoRow("目标 SDK", "API ${analysis.targetSdkVersion}")

        // 组件统计
        HorizontalDivider()
        Text("组件统计", style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.Bold)
        Row(horizontalArrangement = Arrangement.spacedBy(16.dp)) {
            StatChip("Activity", analysis.activityCount)
            StatChip("Service", analysis.serviceCount)
            StatChip("Receiver", analysis.receiverCount)
            StatChip("Provider", analysis.providerCount)
        }

        // 文件统计
        Spacer(Modifier.height(4.dp))
        Row(horizontalArrangement = Arrangement.spacedBy(16.dp)) {
            StatChip("Dex", analysis.dexCount)
            StatChip("Native库", analysis.nativeLibCount)
            StatChip("资源", analysis.assetCount)
        }

        // 权限列表
        if (analysis.permissions.isNotEmpty()) {
            HorizontalDivider()
            Text("权限列表 (${analysis.permissions.size})", style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.Bold)
            analysis.permissions.take(5).forEach { perm ->
                Text(
                    perm.removePrefix("android.permission."),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
            if (analysis.permissions.size > 5) {
                Text("等 ${analysis.permissions.size} 项权限", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        }

        // 签名信息
        if (analysis.isSigned) {
            HorizontalDivider()
            Text("签名信息", style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.Bold)
            InfoRow("签名算法", analysis.signatureAlgorithm)
            InfoRow("证书主体", analysis.certificateSubject.take(40) + if (analysis.certificateSubject.length > 40) "..." else "")
            InfoRow("有效期至", analysis.certificateValidTo)
        }
    }
}

/**
 * 统计芯片
 */
@Composable
private fun StatChip(label: String, count: Int) {
    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        Text("$count", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.primary)
        Text(label, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
    }
}

/**
 * 源文件 Tab（HTML 代码编辑器）
 */
@Composable
private fun SourceTab(
    project: Project,
    viewModel: PackagerViewModel
) {
    val wwwDir = viewModel.getWwwDir(project.id)
    val indexFile = File(wwwDir, "index.html")
    var htmlContent by remember { mutableStateOf(if (indexFile.exists()) indexFile.readText() else DEFAULT_HTML) }
    var isEditing by remember { mutableStateOf(false) }
    var savedMessage by remember { mutableStateOf<String?>(null) }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(16.dp)
    ) {
        // 文件信息
        CardSection(title = "www/index.html") {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text("单文件 HTML 项目", style = MaterialTheme.typography.bodyMedium)
                Spacer(Modifier.weight(1f))
                Text(
                    if (isEditing) "编辑中" else "已保存",
                    style = MaterialTheme.typography.bodySmall,
                    color = if (isEditing) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }

        Spacer(Modifier.height(12.dp))

        // 模板选择
        CardSection(title = "快速模板") {
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                TemplateButton("空白页") {
                    htmlContent = DEFAULT_HTML
                    isEditing = true
                }
                TemplateButton("欢迎页") {
                    htmlContent = WELCOME_HTML
                    isEditing = true
                }
                TemplateButton("待办清单") {
                    htmlContent = TODO_HTML
                    isEditing = true
                }
            }
        }

        Spacer(Modifier.height(12.dp))

        // 代码编辑器
        OutlinedTextField(
            value = htmlContent,
            onValueChange = {
                htmlContent = it
                isEditing = true
                savedMessage = null
            },
            label = { Text("HTML 代码") },
            modifier = Modifier
                .fillMaxWidth()
                .height(400.dp),
            textStyle = MaterialTheme.typography.bodySmall.copy(fontFamily = FontFamily.Monospace),
            maxLines = 50
        )

        Spacer(Modifier.height(12.dp))

        // 操作按钮
        Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            ActionButtonFull(
                text = "保存",
                icon = Icons.Default.CheckCircle,
                onClick = {
                    indexFile.writeText(htmlContent)
                    isEditing = false
                    savedMessage = "已保存到 ${indexFile.absolutePath}"
                },
                modifier = Modifier.weight(1f)
            )
        }

        if (savedMessage != null) {
            Spacer(Modifier.height(8.dp))
            Text(savedMessage!!, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.primary)
        }

        Spacer(Modifier.height(16.dp))
        Text(
            "P0 阶段支持单 HTML 文件，后续版本将支持多文件/目录管理、CSS/JS 分离、实时预览",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.6f)
        )
    }
}

/**
 * 配置 Tab
 */
@Composable
private fun ConfigTab(
    project: Project,
    viewModel: PackagerViewModel
) {
    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = androidx.compose.foundation.layout.PaddingValues(16.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        // 权限管理
        item {
            CardSection(title = "权限管理") {
                PermissionGroup(
                    title = "网络权限",
                    permissions = listOf(
                        "android.permission.INTERNET" to "访问网络",
                        "android.permission.ACCESS_NETWORK_STATE" to "网络状态",
                        "android.permission.ACCESS_WIFI_STATE" to "WiFi状态"
                    ),
                    selectedPermissions = project.permissions,
                    onToggle = { perm, enabled -> viewModel.togglePermission(project.id, perm, enabled) }
                )
                Spacer(Modifier.height(12.dp))
                PermissionGroup(
                    title = "存储权限",
                    permissions = listOf(
                        "android.permission.READ_EXTERNAL_STORAGE" to "读取存储",
                        "android.permission.WRITE_EXTERNAL_STORAGE" to "写入存储"
                    ),
                    selectedPermissions = project.permissions,
                    onToggle = { perm, enabled -> viewModel.togglePermission(project.id, perm, enabled) }
                )
                Spacer(Modifier.height(12.dp))
                PermissionGroup(
                    title = "设备权限",
                    permissions = listOf(
                        "android.permission.CAMERA" to "相机",
                        "android.permission.RECORD_AUDIO" to "录音",
                        "android.permission.VIBRATE" to "震动",
                        "android.permission.BLUETOOTH" to "蓝牙"
                    ),
                    selectedPermissions = project.permissions,
                    onToggle = { perm, enabled -> viewModel.togglePermission(project.id, perm, enabled) }
                )
                Spacer(Modifier.height(12.dp))
                PermissionGroup(
                    title = "位置权限",
                    permissions = listOf(
                        "android.permission.ACCESS_FINE_LOCATION" to "精确定位",
                        "android.permission.ACCESS_COARSE_LOCATION" to "粗略定位"
                    ),
                    selectedPermissions = project.permissions,
                    onToggle = { perm, enabled -> viewModel.togglePermission(project.id, perm, enabled) }
                )
            }
        }

        // Bridge 能力开关
        item {
            CardSection(title = "JS Bridge 能力") {
                SwitchRow("UI 模块", "Toast、对话框等界面交互", project.bridgeCapabilities.ui) {
                    viewModel.updateBridgeCapability(project.id, "ui", it)
                }
                SwitchRow("设备信息", "获取设备型号、系统版本等", project.bridgeCapabilities.device) {
                    viewModel.updateBridgeCapability(project.id, "device", it)
                }
                SwitchRow("文件操作", "读写应用私有目录文件", project.bridgeCapabilities.file) {
                    viewModel.updateBridgeCapability(project.id, "file", it)
                }
                SwitchRow("网络状态", "获取网络连接类型与状态", project.bridgeCapabilities.network) {
                    viewModel.updateBridgeCapability(project.id, "network", it)
                }
                SwitchRow("数据模块", "剪贴板读写等数据操作", project.bridgeCapabilities.data) {
                    viewModel.updateBridgeCapability(project.id, "data", it)
                }
            }
        }

        // 显示设置
        item {
            CardSection(title = "显示设置") {
                SwitchRow("沉浸模式", "状态栏/导航栏透明，内容全屏", project.displaySettings.immersiveMode) {
                    viewModel.updateProject(project.copy(displaySettings = project.displaySettings.copy(immersiveMode = it)))
                }
                Spacer(Modifier.height(8.dp))
                InfoRow("屏幕方向", when (project.displaySettings.screenOrientation) {
                    "portrait" -> "竖屏锁定"
                    "landscape" -> "横屏锁定"
                    else -> "自动旋转"
                })
            }
        }

        // 启动页设置
        item {
            CardSection(title = "启动页设置") {
                SwitchRow("启用启动页", "应用启动时显示启动页", project.splashSettings.enabled) {
                    viewModel.updateProject(project.copy(splashSettings = project.splashSettings.copy(enabled = it)))
                }
                Spacer(Modifier.height(8.dp))
                InfoRow("启动延迟", "${project.splashSettings.delayMs} ms")
                InfoRow("全屏启动", if (project.splashSettings.fullscreen) "是" else "否")
            }
        }

        item {
            Text(
                "配置修改后需要重新构建才能生效",
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
private fun SwitchRow(
    title: String,
    description: String,
    checked: Boolean,
    onCheckedChange: (Boolean) -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 4.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Column(modifier = Modifier.weight(1f)) {
            Text(title, style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.Medium)
            Text(description, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
        Switch(checked = checked, onCheckedChange = onCheckedChange)
    }
}

@Composable
private fun PermissionGroup(
    title: String,
    permissions: List<Pair<String, String>>,
    selectedPermissions: List<String>,
    onToggle: (String, Boolean) -> Unit
) {
    Text(title, style = MaterialTheme.typography.bodySmall, fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.onSurfaceVariant)
    Spacer(Modifier.height(4.dp))
    permissions.forEach { (perm, label) ->
        SwitchRow(
            title = label,
            description = perm.split(".").last(),
            checked = perm in selectedPermissions,
            onCheckedChange = { onToggle(perm, it) }
        )
    }
}

@Composable
private fun TemplateButton(
    text: String,
    onClick: () -> Unit
) {
    Box(
        modifier = Modifier
            .background(MaterialTheme.colorScheme.primary.copy(alpha = 0.1f), RoundedCornerShape(8.dp))
            .clickable(onClick = onClick)
            .padding(horizontal = 12.dp, vertical = 8.dp)
    ) {
        Text(text, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.primary)
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

@Composable
private fun BuildRecordRow(
    record: BuildRecord,
    onClick: () -> Unit
) {
    val (icon, color) = when (record.status) {
        com.mini.me_core.feature.packager.domain.model.BuildStatus.SUCCESS -> Icons.Default.CheckCircle to androidx.compose.ui.graphics.Color(0xFF4CAF50)
        com.mini.me_core.feature.packager.domain.model.BuildStatus.FAILED -> Icons.Default.Error to androidx.compose.ui.graphics.Color(0xFFF44336)
        com.mini.me_core.feature.packager.domain.model.BuildStatus.BUILDING -> Icons.Rounded.PlayArrow to MaterialTheme.colorScheme.primary
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

private fun formatDateTime(timestamp: Long): String =
    SimpleDateFormat("yyyy-MM-dd HH:mm", Locale.getDefault()).format(Date(timestamp))

private fun formatSize(bytes: Long): String {
    val kb = bytes / 1024.0
    val mb = kb / 1024.0
    return when {
        mb >= 1 -> String.format("%.2f MB", mb)
        kb >= 1 -> String.format("%.1f KB", kb)
        else -> "$bytes B"
    }
}

// ========== HTML 模板 ==========

private const val DEFAULT_HTML = """<!DOCTYPE html>
<html>
<head>
    <meta charset="UTF-8">
    <meta name="viewport" content="width=device-width, initial-scale=1.0">
    <title>我的应用</title>
</head>
<body>
    <h1>Hello World</h1>
    <p>在这里开始编写你的 HTML 应用</p>
</body>
</html>"""

private const val WELCOME_HTML = """<!DOCTYPE html>
<html>
<head>
    <meta charset="UTF-8">
    <meta name="viewport" content="width=device-width, initial-scale=1.0">
    <title>欢迎使用</title>
    <style>
        body { font-family: sans-serif; display: flex; flex-direction: column; align-items: center; justify-content: center; height: 100vh; margin: 0; background: linear-gradient(135deg, #667eea 0%, #764ba2 100%); color: white; }
        h1 { font-size: 2em; margin-bottom: 0.5em; }
        p { opacity: 0.9; }
    </style>
</head>
<body>
    <h1>欢迎使用</h1>
    <p>这是一个由 MiniMe 打包的 HTML 应用</p>
</body>
</html>"""

private const val TODO_HTML = """<!DOCTYPE html>
<html>
<head>
    <meta charset="UTF-8">
    <meta name="viewport" content="width=device-width, initial-scale=1.0">
    <title>待办清单</title>
    <style>
        body { font-family: sans-serif; max-width: 500px; margin: 0 auto; padding: 20px; }
        h1 { color: #333; }
        .input-row { display: flex; gap: 10px; margin-bottom: 20px; }
        input { flex: 1; padding: 10px; border: 1px solid #ddd; border-radius: 4px; }
        button { padding: 10px 20px; background: #667eea; color: white; border: none; border-radius: 4px; cursor: pointer; }
        ul { list-style: none; padding: 0; }
        li { padding: 12px; background: #f5f5f5; margin-bottom: 8px; border-radius: 4px; display: flex; justify-content: space-between; }
        .done { text-decoration: line-through; opacity: 0.5; }
        .delete { color: #e74c3c; cursor: pointer; }
    </style>
</head>
<body>
    <h1>待办清单</h1>
    <div class="input-row">
        <input type="text" id="taskInput" placeholder="输入新任务...">
        <button onclick="addTask()">添加</button>
    </div>
    <ul id="taskList"></ul>
    <script>
        function addTask() {
            const input = document.getElementById('taskInput');
            const text = input.value.trim();
            if (!text) return;
            const li = document.createElement('li');
            li.innerHTML = '<span onclick="toggleTask(this)">' + text + '</span><span class="delete" onclick="deleteTask(this)">删除</span>';
            document.getElementById('taskList').appendChild(li);
            input.value = '';
        }
        function toggleTask(el) { el.classList.toggle('done'); }
        function deleteTask(el) { el.parentElement.remove(); }
    </script>
</body>
</html>"""
