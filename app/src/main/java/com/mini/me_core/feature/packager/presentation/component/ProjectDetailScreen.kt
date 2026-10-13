@file:Suppress("FunctionNaming", "LongMethod", "CyclomaticComplexMethod", "CognitiveComplexMethod")

package com.mini.me_core.feature.packager.presentation.component

import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.border
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
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.Error
import androidx.compose.material.icons.filled.FileDownload
import androidx.compose.material.icons.filled.Folder
import androidx.compose.material.icons.rounded.Visibility
import androidx.compose.material.icons.rounded.VisibilityOff
import androidx.compose.material.icons.filled.InsertPhoto
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material.icons.rounded.PlayArrow
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SegmentedButton
import androidx.compose.material3.SegmentedButtonDefaults
import androidx.compose.material3.SingleChoiceSegmentedButtonRow
import androidx.compose.material3.Switch
import androidx.compose.material3.Tab
import androidx.compose.material3.TabRow
import androidx.compose.material3.Slider
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import com.mini.me_core.core.theme.components.AppTopAppBar
import com.mini.me_core.core.theme.components.FileBrowserItem
import com.mini.me_core.core.theme.components.fileIconVisual
import com.mini.me_core.feature.packager.domain.model.BuildRecord
import com.mini.me_core.feature.packager.domain.model.BuildStatus
import com.mini.me_core.feature.packager.domain.model.Project
import com.mini.me_core.feature.packager.domain.model.normalizedColorOrNull
import com.mini.me_core.feature.packager.presentation.PackagerViewModel
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File
import kotlin.math.roundToInt
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/**
 * 项目详情页（3个Tab：概览/源文件/配置）
 *
 * 构建功能通过顶栏按钮触发，以流程式弹窗展示，不再单独占用Tab页。
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ProjectDetailScreen(
    projectId: String,
    viewModel: PackagerViewModel = hiltViewModel(),
    onNavigateBack: () -> Unit,
    onOpenFile: (String) -> Unit = {},
) {
    val projects by viewModel.projects.collectAsState()
    val project = projects.find { it.id == projectId }
    val buildRecords by viewModel.buildRecords.collectAsState()
    val buildState by viewModel.buildState.collectAsState()

    var selectedTab by remember { mutableStateOf(0) }
    val tabs = listOf("概览", "源文件", "配置")
    var showBuildDialog by remember { mutableStateOf(false) }

    if (project == null) {
        Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
            Text("项目不存在", color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
        return
    }
    val currentProject = project

    LaunchedEffect(projectId) {
        viewModel.loadBuildRecords(projectId)
    }

    // 构建完成后自动显示弹窗
    LaunchedEffect(buildState.isBuilding) {
        if (!buildState.isBuilding && (buildState.resultApk != null || buildState.error != null)) {
            showBuildDialog = true
        }
    }

    Scaffold(
        topBar = {
            AppTopAppBar(
                title = currentProject.name,
                onNavigateBack = onNavigateBack,
                navigationIcon = Icons.AutoMirrored.Rounded.ArrowBack,
                navigationContentDescription = "返回",
                actions = {
                    IconButton(onClick = {
                        showBuildDialog = true
                        viewModel.startBuild(currentProject)
                    }) {
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
                0 -> OverviewTab(
                    project = currentProject,
                    buildRecords = buildRecords,
                    viewModel = viewModel,
                    onStartBuild = {
                        showBuildDialog = true
                        viewModel.startBuild(currentProject)
                    }
                )
                1 -> SourceTab(
                    project = currentProject,
                    viewModel = viewModel,
                    onOpenFile = onOpenFile,
                )
                2 -> ConfigTab(project = currentProject, viewModel = viewModel)
            }
        }
    }

    // 构建进度弹窗
    if (showBuildDialog) {
        BuildProgressDialog(
            onDismiss = {
                showBuildDialog = false
                viewModel.resetBuildState()
            },
            onRetry = { viewModel.startBuild(currentProject) }
        )
    }
}

/**
 * 概览 Tab（基本信息内联编辑 + 更换图标 + 最近构建）
 */
@Composable
private fun OverviewTab(
    project: Project,
    buildRecords: List<BuildRecord>,
    viewModel: PackagerViewModel,
    onStartBuild: () -> Unit
) {
    val scope = rememberCoroutineScope()
    var isSaving by remember { mutableStateOf(false) }
    var showSavedCheck by remember { mutableStateOf(false) }
    // 仅在切换项目时用项目数据初始化输入框，之后以用户输入为准
    var editName by remember(project.id) { mutableStateOf(project.name) }
    var editPackage by remember(project.id) { mutableStateOf(project.packageName) }
    var editVersion by remember(project.id) { mutableStateOf(project.versionName) }

    val trimmedName = editName.trim()
    val trimmedPackage = editPackage.trim()
    val trimmedVersion = editVersion.trim()

    // 实时校验（只提示不阻断输入）
    val nameError = when {
        editName.isBlank() -> "应用名称不能为空"
        editName.length > 30 -> "应用名称不能超过 30 个字符"
        else -> null
    }
    val packageError =
        if (!Project.isValidPackageName(editPackage)) "包名格式不正确" else null
    val versionError =
        if (!versionRegex.matches(trimmedVersion)) "版本号格式应为 x.y.z（如 1.0.0）" else null

    val hasChanges = trimmedName != project.name ||
        trimmedPackage != project.packageName ||
        trimmedVersion != project.versionName
    val allValid = nameError == null && packageError == null && versionError == null
    val canSave = allValid && hasChanges && !isSaving

    // 停止输入 500ms 后自动保存；每次键入都会取消上一次计时
    LaunchedEffect(editName, editPackage, editVersion) {
        delay(DEBOUNCE_SAVE_MS)
        if (allValid && hasChanges) {
            runCatching {
                viewModel.saveBasicInfo(project.id, trimmedName, trimmedPackage, trimmedVersion)
            }.onSuccess { showSavedCheck = true }
        }
    }

    // 保存成功对勾 1 秒后自动消失
    LaunchedEffect(showSavedCheck) {
        if (showSavedCheck) {
            delay(SAVED_CHECK_MS)
            showSavedCheck = false
        }
    }

    fun manualSave() {
        if (!canSave) return
        isSaving = true
        scope.launch {
            runCatching {
                viewModel.saveBasicInfo(project.id, trimmedName, trimmedPackage, trimmedVersion)
            }.onSuccess { showSavedCheck = true }
            isSaving = false
        }
    }

    // 图片选择器
    val context = androidx.compose.ui.platform.LocalContext.current
    val iconPicker = rememberLauncherForActivityResult(ActivityResultContracts.GetContent()) { uri: Uri? ->
        if (uri != null) {
            scope.launch {
                val savedPath = withContext(Dispatchers.IO) {
                    saveIconToProject(context, project.id, uri)
                }
                if (savedPath != null) {
                    viewModel.updateProject(project.copy(iconPath = savedPath))
                }
            }
        }
    }

    // 启动图选择器（选中后复制进项目目录 splash.png，复用图标选择器的 GetContent 模式）
    val splashPicker = rememberLauncherForActivityResult(ActivityResultContracts.GetContent()) { uri: Uri? ->
        if (uri != null) {
            scope.launch {
                val savedPath = withContext(Dispatchers.IO) {
                    saveImageToProject(context, project.id, uri, "splash.png")
                }
                if (savedPath != null) {
                    viewModel.updateProject(
                        project.copy(splashSettings = project.splashSettings.copy(customImagePath = savedPath))
                    )
                }
            }
        }
    }

    // 图标预览：换图会覆盖同一路径文件，用 lastModified 变化驱动重新解码，
    // 避免路径字符串不变导致 Bitmap 缓存不刷新
    val iconFile = project.iconPath?.let { File(it) }.takeIf { it?.exists() == true }
    val iconLastModified = iconFile?.lastModified() ?: 0L
    val bitmap = remember(project.iconPath, iconLastModified) {
        project.iconPath?.let { path ->
            android.graphics.BitmapFactory.decodeFile(path)?.asImageBitmap()
        }
    }

    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = androidx.compose.foundation.layout.PaddingValues(16.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        // 应用图标 + 基本信息
        item {
            CardSection(title = "基本信息") {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    // 应用图标
                    Box(
                        modifier = Modifier
                            .size(64.dp)
                            .clip(RoundedCornerShape(16.dp))
                            .background(MaterialTheme.colorScheme.primary.copy(alpha = 0.1f))
                            .clickable { iconPicker.launch("image/*") },
                        contentAlignment = Alignment.Center
                    ) {
                        if (bitmap != null) {
                            androidx.compose.foundation.Image(
                                bitmap = bitmap,
                                contentDescription = "应用图标",
                                modifier = Modifier.fillMaxSize(),
                                contentScale = androidx.compose.ui.layout.ContentScale.Crop
                            )
                        } else {
                            Icon(
                                Icons.Default.InsertPhoto,
                                contentDescription = "更换图标",
                                tint = MaterialTheme.colorScheme.primary,
                                modifier = Modifier.size(28.dp)
                            )
                        }
                    }
                    Spacer(Modifier.width(16.dp))
                    Column(modifier = Modifier.weight(1f)) {
                        OutlinedTextField(
                            value = editName,
                            onValueChange = { editName = it },
                            label = { Text("应用名称") },
                            modifier = Modifier.fillMaxWidth(),
                            singleLine = true,
                            isError = nameError != null,
                            supportingText = nameError?.let { { Text(it) } },
                            trailingIcon = { SavedCheckIcon(visible = showSavedCheck) },
                        )
                        Spacer(Modifier.height(8.dp))
                        OutlinedTextField(
                            value = editPackage,
                            onValueChange = { editPackage = it },
                            label = { Text("包名") },
                            modifier = Modifier.fillMaxWidth(),
                            singleLine = true,
                            isError = packageError != null,
                            supportingText = packageError?.let { { Text(it) } },
                            trailingIcon = { SavedCheckIcon(visible = showSavedCheck) },
                        )
                        Spacer(Modifier.height(8.dp))
                        OutlinedTextField(
                            value = editVersion,
                            onValueChange = { editVersion = it },
                            label = { Text("版本号") },
                            modifier = Modifier.fillMaxWidth(),
                            singleLine = true,
                            isError = versionError != null,
                            supportingText = {
                                val msg = versionError
                                if (msg != null) {
                                    Text(msg)
                                } else {
                                    Text("versionCode: ${Project.parseVersionCode(trimmedVersion)}")
                                }
                            },
                            trailingIcon = { SavedCheckIcon(visible = showSavedCheck) },
                        )
                    }
                }

                Spacer(Modifier.height(12.dp))
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.End,
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    TextButton(onClick = { iconPicker.launch("image/*") }) {
                        Icon(Icons.Default.InsertPhoto, contentDescription = "更换图标", modifier = Modifier.size(18.dp))
                        Spacer(Modifier.width(4.dp))
                        Text("更换图标")
                    }
                    TextButton(onClick = { manualSave() }, enabled = canSave) {
                        if (isSaving) {
                            CircularProgressIndicator(
                                modifier = Modifier.size(18.dp),
                                strokeWidth = 2.dp,
                            )
                        } else {
                            Icon(Icons.Default.CheckCircle, contentDescription = null, modifier = Modifier.size(18.dp))
                        }
                        Spacer(Modifier.width(4.dp))
                        Text("保存")
                    }
                }
            }
        }

        // 启动动画配置
        item {
            SplashConfigCard(
                project = project,
                onPickImage = { splashPicker.launch("image/*") },
                onChange = { updated -> viewModel.updateProject(updated) }
            )
        }

        // 最近构建
        item {
            CardSection(title = "最近构建") {
                if (buildRecords.isEmpty()) {
                    Text("暂无构建记录", color = MaterialTheme.colorScheme.onSurfaceVariant)
                } else {
                    buildRecords.take(3).forEach { record ->
                        BuildRecordRow(record = record, onClick = {
                            record.apkPath?.let { viewModel.installApk(File(it)) }
                        })
                    }
                }
            }
        }

        // 构建按钮
        item {
            ActionButtonFull(
                text = "立即构建",
                icon = Icons.Rounded.PlayArrow,
                onClick = onStartBuild,
                modifier = Modifier.fillMaxWidth()
            )
        }

        item {
            Text(
                "点击图标可更换应用图标；直接修改上方信息后停止输入 0.5 秒自动保存",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.6f)
            )
        }
    }
}

/**
 * 保存成功后显示在输入框尾部的小对勾（1 秒后由调用方状态驱动消失）
 */
@Composable
private fun SavedCheckIcon(visible: Boolean) {
    if (visible) {
        Icon(
            Icons.Default.Check,
            contentDescription = "已保存",
            tint = MaterialTheme.colorScheme.primary,
            modifier = Modifier.size(18.dp)
        )
    }
}

/**
 * 保存图标到项目目录
 */
@Suppress("TooGenericExceptionCaught", "SwallowedException")
private suspend fun saveIconToProject(context: android.content.Context, projectId: String, uri: Uri): String? {
    return try {
        val projectDir = File(context.filesDir, "packager/projects/$projectId")
        val iconFile = File(projectDir, "icon.png")
        context.contentResolver.openInputStream(uri)?.use { input ->
            iconFile.outputStream().use { output ->
                input.copyTo(output)
            }
        }
        iconFile.absolutePath
    } catch (e: Exception) {
        null
    }
}

/**
 * 把图片选择器返回的 uri 保存为项目目录下的指定文件名，返回绝对路径。
 * 与图标保存逻辑一致：固定落在项目根目录，仅存文件名语义。
 */
@Suppress("TooGenericExceptionCaught", "SwallowedException")
private suspend fun saveImageToProject(
    context: android.content.Context,
    projectId: String,
    uri: Uri,
    fileName: String
): String? {
    return try {
        val projectDir = File(context.filesDir, "packager/projects/$projectId")
        val target = File(projectDir, fileName)
        context.contentResolver.openInputStream(uri)?.use { input ->
            target.outputStream().use { output -> input.copyTo(output) }
        }
        target.absolutePath
    } catch (e: Exception) {
        null
    }
}

/**
 * 启动动画配置卡片：启动图、展示时长、过渡效果、背景色、点击跳过。
 * 所有改动经 [onChange] 回调到 ViewModel 持久化（复用 updateProject 链路）。
 */
@Composable
private fun SplashConfigCard(
    project: Project,
    onPickImage: () -> Unit,
    onChange: (Project) -> Unit
) {
    val splash = project.splashSettings

    // 时长与 hex 用本地状态承载，停止拖动/输入后再持久化，避免拖动过程频繁写盘
    var duration by remember(project.id, splash.delayMs) {
        mutableIntStateOf(splash.delayMs.coerceIn(500, 5000))
    }
    var hexInput by remember(project.id, splash.backgroundColor) {
        mutableStateOf(splash.backgroundColor)
    }

    LaunchedEffect(duration) {
        if (duration != splash.delayMs) {
            onChange(project.copy(splashSettings = splash.copy(delayMs = duration)))
        }
    }

    fun persist(newSettings: com.mini.me_core.feature.packager.domain.model.SplashSettings) {
        onChange(project.copy(splashSettings = newSettings))
    }

    CardSection(title = "启动动画") {
        // 启用开关
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text("启用启动页", modifier = Modifier.weight(1f))
            Switch(checked = splash.enabled, onCheckedChange = { persist(splash.copy(enabled = it)) })
        }

        Spacer(Modifier.height(8.dp))

        // 模式选择：图片 / 纯色 / HTML
        val modeOptions = listOf(
            Triple(com.mini.me_core.feature.packager.domain.model.SplashMode.IMAGE, "图片", "用户启动图"),
            Triple(com.mini.me_core.feature.packager.domain.model.SplashMode.COLOR, "纯色", "底色+加载点"),
            Triple(com.mini.me_core.feature.packager.domain.model.SplashMode.HTML, "HTML", "自定义页面")
        )
        Text("启动模式", style = MaterialTheme.typography.bodyMedium)
        Spacer(Modifier.height(4.dp))
        SingleChoiceSegmentedButtonRow(modifier = Modifier.fillMaxWidth()) {
            modeOptions.forEachIndexed { index, (mode, label, _) ->
                SegmentedButton(
                    selected = splash.mode == mode,
                    onClick = { persist(splash.copy(mode = mode)) },
                    shape = SegmentedButtonDefaults.itemShape(index = index, count = modeOptions.size)
                ) {
                    Text(label)
                }
            }
        }

        Spacer(Modifier.height(12.dp))

        // 自定义启动图（可选）：仅图片模式显示
        if (splash.mode == com.mini.me_core.feature.packager.domain.model.SplashMode.IMAGE) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Box(
                modifier = Modifier
                    .size(56.dp)
                    .clip(RoundedCornerShape(8.dp))
                    .background(MaterialTheme.colorScheme.primary.copy(alpha = 0.1f))
                    .clickable { onPickImage() },
                contentAlignment = Alignment.Center
            ) {
                val splashFile = splash.customImagePath?.let { File(it) }?.takeIf { it.exists() }
                val preview = splashFile?.let { f ->
                    remember(f.absolutePath, f.lastModified()) {
                        android.graphics.BitmapFactory.decodeFile(f.absolutePath)?.asImageBitmap()
                    }
                }
                if (preview != null) {
                    androidx.compose.foundation.Image(
                        bitmap = preview,
                        contentDescription = "自定义启动图",
                        modifier = Modifier.fillMaxSize(),
                        contentScale = androidx.compose.ui.layout.ContentScale.Crop
                    )
                } else {
                    Icon(
                        Icons.Default.InsertPhoto,
                        contentDescription = "选择启动图",
                        tint = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.size(22.dp)
                    )
                }
            }
            Spacer(Modifier.width(12.dp))
            Column(modifier = Modifier.weight(1f)) {
                Text("自定义启动图（可选）", style = MaterialTheme.typography.bodyMedium)
                Text(
                    if (splash.customImagePath != null) "已选择启动图" else "不选则仅用背景色",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
            TextButton(onClick = onPickImage) { Text("选择") }
            if (splash.customImagePath != null) {
                TextButton(onClick = { persist(splash.copy(customImagePath = null)) }) { Text("清除") }
            }
        }
        }

        Spacer(Modifier.height(12.dp))

        // 展示时长滑块
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text("展示时长", style = MaterialTheme.typography.bodyMedium, modifier = Modifier.weight(1f))
            Text("${duration} ms", style = MaterialTheme.typography.bodyMedium)
        }
        Slider(
            value = duration.toFloat(),
            onValueChange = { duration = it.roundToHundred() },
            valueRange = 500f..5000f
        )

        Spacer(Modifier.height(8.dp))

        // 过渡效果
        Text("过渡效果", style = MaterialTheme.typography.bodyMedium)
        Spacer(Modifier.height(8.dp))
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            listOf("fade" to "淡入", "zoom" to "缩放", "slide" to "滑动", "dissolve" to "溶解")
                .forEach { (value, label) ->
                    FilterChip(
                        selected = splash.transition == value,
                        onClick = { persist(splash.copy(transition = value)) },
                        label = { Text(label) }
                    )
                }
        }

        Spacer(Modifier.height(12.dp))

        // 背景色：预设色块 + 自定义 hex
        Text("背景色", style = MaterialTheme.typography.bodyMedium)
        Spacer(Modifier.height(8.dp))
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            listOf("#FFFFFF", "#000000", "#1A1A2E", "#16213E", "#F5F5F5").forEach { colorHex ->
                val selected = splash.backgroundColor.equals(colorHex, ignoreCase = true)
                Box(
                    modifier = Modifier
                        .size(28.dp)
                        .clip(RoundedCornerShape(6.dp))
                        .androidBgColor(colorHex)
                        .clickable {
                            hexInput = colorHex
                            persist(splash.copy(backgroundColor = colorHex))
                        }
                        .then(
                            if (selected) Modifier.border(
                                2.dp,
                                MaterialTheme.colorScheme.primary,
                                RoundedCornerShape(6.dp)
                            ) else Modifier
                        )
                )
            }
        }
        Spacer(Modifier.height(8.dp))
        OutlinedTextField(
            value = hexInput,
            onValueChange = { input ->
                hexInput = input
                if (input.normalizedColorOrNull() != null) {
                    persist(splash.copy(backgroundColor = input))
                }
            },
            label = { Text("背景色 (#RRGGBB)") },
            singleLine = true,
            modifier = Modifier.fillMaxWidth()
        )

        Spacer(Modifier.height(8.dp))

        // 点击跳过
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text("点击启动页跳过", modifier = Modifier.weight(1f))
            Switch(
                checked = splash.skipOnTap,
                onCheckedChange = { persist(splash.copy(skipOnTap = it)) }
            )
        }
    }
}

/** Float 取整到最近的 100 的倍数（滑块步长）。 */
private fun Float.roundToHundred(): Int = ((this / 100).roundToInt() * 100).coerceIn(500, 5000)

/** 把 #RRGGBB 字符串应用为 Compose 背景色（解析失败回退透明）。 */
private fun Modifier.androidBgColor(hex: String): Modifier = this.then(
    Modifier.background(
        runCatching {
            androidx.compose.ui.graphics.Color(android.graphics.Color.parseColor(hex))
        }.getOrDefault(androidx.compose.ui.graphics.Color.Transparent)
    )
)

/**
 * 源文件 Tab（目录浏览 + 新建/重命名/删除；点击文件跳转独立代码页）
 */
@Composable
private fun SourceTab(
    project: Project,
    viewModel: PackagerViewModel,
    onOpenFile: (String) -> Unit,
) {
    val wwwDir = viewModel.getWwwDir(project.id)
    var currentPath by remember { mutableStateOf(wwwDir) }
    var pathStack by remember { mutableStateOf(listOf(wwwDir)) }
    // 操作后通过 refreshKey 触发文件列表重组
    var refreshKey by remember { mutableIntStateOf(0) }

    // 新建文件对话框
    var showNewDialog by remember { mutableStateOf(false) }
    var newFileName by remember { mutableStateOf("index.html") }
    var newFileError by remember { mutableStateOf<String?>(null) }

    // 文件操作菜单（长按弹出）
    var actionFile by remember { mutableStateOf<File?>(null) }
    // 重命名对话框
    var renameTarget by remember { mutableStateOf<File?>(null) }
    var renameText by remember { mutableStateOf("") }
    var renameError by remember { mutableStateOf<String?>(null) }
    // 删除确认
    var deleteTarget by remember { mutableStateOf<File?>(null) }

    // 是否显示以 . 开头的隐藏文件/目录（默认隐藏，如 .minime/ 等）
    var showHidden by remember { mutableStateOf(false) }

    // 文件列表（读取 refreshKey / showHidden 以触发刷新）
    val files = remember(currentPath, refreshKey, showHidden) {
        currentPath.listFiles()
            ?.filter { showHidden || !com.mini.me_core.feature.packager.domain.model.MinimeLayout.isHidden(it.name) }
            ?.sortedWith(
                compareBy<File> { !it.isDirectory }.thenBy { it.name.lowercase() }
            ) ?: emptyList()
    }

    Column(modifier = Modifier.fillMaxSize()) {
        // 面包屑 + 新建按钮
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = 8.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            TextButton(onClick = {
                pathStack = listOf(wwwDir)
                currentPath = wwwDir
            }) {
                Text("www", fontWeight = FontWeight.Bold)
            }
            pathStack.drop(1).forEachIndexed { index, dir ->
                Text("/", color = MaterialTheme.colorScheme.onSurfaceVariant)
                TextButton(onClick = {
                    pathStack = pathStack.take(index + 2)
                    currentPath = dir
                }) {
                    Text(dir.name)
                }
            }
            Spacer(Modifier.weight(1f))
            IconButton(onClick = { showHidden = !showHidden }) {
                Icon(
                    if (showHidden) Icons.Rounded.Visibility else Icons.Rounded.VisibilityOff,
                    contentDescription = if (showHidden) "隐藏文件" else "显示隐藏文件",
                    tint = if (showHidden) MaterialTheme.colorScheme.primary
                    else MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
            TextButton(onClick = {
                newFileName = "index.html"
                newFileError = null
                showNewDialog = true
            }) {
                Icon(Icons.Default.Add, contentDescription = null, modifier = Modifier.size(18.dp))
                Spacer(Modifier.width(4.dp))
                Text("新建文件")
            }
        }

        if (files.isEmpty()) {
            Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Icon(
                        Icons.Default.Folder,
                        contentDescription = null,
                        modifier = Modifier.size(48.dp),
                        tint = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.4f),
                    )
                    Spacer(Modifier.height(12.dp))
                    Text("空目录", color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            }
        } else {
            LazyColumn(
                modifier = Modifier.fillMaxSize(),
                contentPadding = androidx.compose.foundation.layout.PaddingValues(horizontal = 8.dp, vertical = 4.dp),
                verticalArrangement = Arrangement.spacedBy(2.dp),
            ) {
                items(files, key = { it.absolutePath }) { file ->
                    val visual = fileIconVisual(name = file.name, isDir = file.isDirectory)
                    val subtitle = if (file.isDirectory) {
                        "${file.listFiles()?.size ?: 0} 项"
                    } else {
                        formatFileSize(file.length())
                    }
                    FileBrowserItem(
                        name = file.name,
                        subtitle = subtitle,
                        icon = visual.icon,
                        iconBg = visual.iconBg,
                        iconFg = visual.iconFg,
                        onClick = {
                            if (file.isDirectory) {
                                pathStack = pathStack + file
                                currentPath = file
                            } else {
                                onOpenFile(file.absolutePath)
                            }
                        },
                        onLongClick = { actionFile = file },
                        trailing = {
                            IconButton(onClick = { actionFile = file }) {
                                Icon(
                                    Icons.Default.MoreVert,
                                    contentDescription = "更多操作",
                                    modifier = Modifier.size(20.dp),
                                    tint = MaterialTheme.colorScheme.onSurfaceVariant,
                                )
                            }
                        },
                    )
                }
            }
        }
    }

    // 文件操作菜单（重命名 / 删除）
    actionFile?.let { target ->
        AlertDialog(
            onDismissRequest = { actionFile = null },
            title = { Text(target.name) },
            text = {
                Column {
                    TextButton(onClick = {
                        actionFile = null
                        renameTarget = target
                        renameText = target.name
                        renameError = null
                    }) {
                        Icon(Icons.Default.Edit, contentDescription = null, modifier = Modifier.size(18.dp))
                        Spacer(Modifier.width(8.dp))
                        Text("重命名")
                    }
                    TextButton(onClick = {
                        actionFile = null
                        deleteTarget = target
                    }) {
                        Icon(Icons.Default.Delete, contentDescription = null, modifier = Modifier.size(18.dp))
                        Spacer(Modifier.width(8.dp))
                        Text("删除", color = MaterialTheme.colorScheme.error)
                    }
                }
            },
            confirmButton = {},
            dismissButton = {
                TextButton(onClick = { actionFile = null }) { Text("取消") }
            },
        )
    }

    // 新建文件对话框
    if (showNewDialog) {
        AlertDialog(
            onDismissRequest = { showNewDialog = false },
            title = { Text("新建文件") },
            text = {
                Column {
                    OutlinedTextField(
                        value = newFileName,
                        onValueChange = {
                            newFileName = it
                            newFileError = null
                        },
                        label = { Text("文件名") },
                        singleLine = true,
                        isError = newFileError != null,
                        supportingText = newFileError?.let { { Text(it) } },
                        modifier = Modifier.fillMaxWidth(),
                    )
                }
            },
            confirmButton = {
                TextButton(onClick = {
                    val name = newFileName.trim()
                    if (name.isBlank() || name.contains("/") || name.contains("\\")) {
                        newFileError = "文件名不合法"
                        return@TextButton
                    }
                    val target = File(currentPath, name)
                    if (target.exists()) {
                        newFileError = "文件已存在"
                        return@TextButton
                    }
                    val ok = runCatching {
                        if (!target.name.contains(".")) {
                            File(target.parentFile, "${target.name}.html").createNewFile()
                        } else {
                            target.createNewFile()
                        }
                    }.isSuccess
                    if (ok) {
                        showNewDialog = false
                        refreshKey++
                    } else {
                        newFileError = "创建失败"
                    }
                }) { Text("创建") }
            },
            dismissButton = {
                TextButton(onClick = { showNewDialog = false }) { Text("取消") }
            },
        )
    }

    // 重命名对话框
    renameTarget?.let { target ->
        AlertDialog(
            onDismissRequest = { renameTarget = null },
            title = { Text("重命名") },
            text = {
                OutlinedTextField(
                    value = renameText,
                    onValueChange = {
                        renameText = it
                        renameError = null
                    },
                    label = { Text("新名称") },
                    singleLine = true,
                    isError = renameError != null,
                    supportingText = renameError?.let { { Text(it) } },
                    modifier = Modifier.fillMaxWidth(),
                )
            },
            confirmButton = {
                TextButton(onClick = {
                    val name = renameText.trim()
                    if (name.isBlank() || name.contains("/") || name.contains("\\")) {
                        renameError = "名称不合法"
                        return@TextButton
                    }
                    val dest = File(target.parentFile, name)
                    if (dest.exists()) {
                        renameError = "目标已存在"
                        return@TextButton
                    }
                    val ok = runCatching { target.renameTo(dest) }.getOrDefault(false)
                    if (ok) {
                        renameTarget = null
                        refreshKey++
                    } else {
                        renameError = "重命名失败"
                    }
                }) { Text("确定") }
            },
            dismissButton = {
                TextButton(onClick = { renameTarget = null }) { Text("取消") }
            },
        )
    }

    // 删除确认对话框
    deleteTarget?.let { target ->
        AlertDialog(
            onDismissRequest = { deleteTarget = null },
            title = { Text("删除") },
            text = {
                Text(
                    if (target.isDirectory) "确定删除目录「${target.name}」及其全部内容？"
                    else "确定删除文件「${target.name}」？"
                )
            },
            confirmButton = {
                TextButton(onClick = {
                    runCatching { target.deleteRecursively() }
                    deleteTarget = null
                    refreshKey++
                }) {
                    Text("删除", color = MaterialTheme.colorScheme.error)
                }
            },
            dismissButton = {
                TextButton(onClick = { deleteTarget = null }) { Text("取消") }
            },
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
        BuildStatus.SUCCESS -> Icons.Default.CheckCircle to androidx.compose.ui.graphics.Color(0xFF4CAF50)
        BuildStatus.FAILED -> Icons.Default.Error to androidx.compose.ui.graphics.Color(0xFFF44336)
        BuildStatus.BUILDING -> Icons.Rounded.PlayArrow to MaterialTheme.colorScheme.primary
        BuildStatus.CANCELLED -> Icons.Default.Close to MaterialTheme.colorScheme.onSurfaceVariant
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
            if (record.status == BuildStatus.SUCCESS) {
                Text("大小: ${record.formattedApkSize}", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
            if (record.status == BuildStatus.FAILED && record.errorMessage != null) {
                Text(record.errorMessage, style = MaterialTheme.typography.bodySmall, color = androidx.compose.ui.graphics.Color(0xFFF44336), maxLines = 2, overflow = TextOverflow.Ellipsis)
            }
        }
        if (record.status == BuildStatus.SUCCESS) {
            Icon(Icons.Default.FileDownload, contentDescription = "安装", tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(20.dp))
        }
    }
}

private fun formatDateTime(timestamp: Long): String =
    SimpleDateFormat("yyyy-MM-dd HH:mm", Locale.getDefault()).format(Date(timestamp))

private val versionRegex = Regex("""\d+\.\d+\.\d+""")

/** 输入停止后延迟自动保存的时长 */
private const val DEBOUNCE_SAVE_MS = 500L

/** 保存成功对勾的展示时长 */
private const val SAVED_CHECK_MS = 1000L

private fun formatFileSize(bytes: Long): String {
    val kb = bytes / 1024.0
    val mb = kb / 1024.0
    return when {
        mb >= 1 -> String.format("%.2f MB", mb)
        kb >= 1 -> String.format("%.1f KB", kb)
        else -> "$bytes B"
    }
}
