package com.mini.me_core.feature.packager.presentation.component

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
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Code
import androidx.compose.material.icons.filled.Language
import androidx.compose.material.icons.filled.PhoneAndroid
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import com.mini.me_core.feature.packager.domain.model.ProjectType
import com.mini.me_core.feature.packager.presentation.PackagerViewModel
import kotlinx.coroutines.runBlocking

/** 项目类型选项 */
private data class ProjectTypeOption(
    val type: ProjectType,
    val name: String,
    val icon: androidx.compose.ui.graphics.vector.ImageVector,
    val enabled: Boolean
)

/**
 * 新建项目向导
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun NewProjectWizard(
    viewModel: PackagerViewModel = hiltViewModel(),
    onNavigateBack: () -> Unit,
    onProjectCreated: (String) -> Unit
) {
    val wizardState by viewModel.newProjectState.collectAsState()

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("新建项目 (${wizardState.step + 1}/5)") },
                navigationIcon = {
                    IconButton(onClick = {
                        if (wizardState.step > 0) viewModel.prevStep() else onNavigateBack()
                    }) {
                        Icon(Icons.Default.ArrowBack, contentDescription = "返回")
                    }
                }
            )
        },
        bottomBar = {
            WizardBottomBar(
                currentStep = wizardState.step,
                canNext = when (wizardState.step) {
                    0 -> true
                    1 -> wizardState.appName.isNotBlank() && wizardState.packageNameError == null && wizardState.packageName.isNotBlank()
                    2 -> true
                    3 -> true
                    4 -> true
                    else -> false
                },
                onPrev = { viewModel.prevStep() },
                onNext = {
                    if (wizardState.step < 4) {
                        viewModel.nextStep()
                    } else {
                        // 创建项目
                        runBlocking {
                            val project = viewModel.createProject()
                            if (project != null) {
                                onProjectCreated(project.id)
                            }
                        }
                    }
                }
            )
        }
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .verticalScroll(rememberScrollState())
                .padding(16.dp)
        ) {
            // 步骤指示器
            StepIndicator(currentStep = wizardState.step)
            Spacer(Modifier.height(24.dp))

            when (wizardState.step) {
                0 -> StepProjectType(
                    selectedType = wizardState.projectType,
                    onSelect = { viewModel.updateProjectType(it) }
                )
                1 -> StepBasicInfo(
                    appName = wizardState.appName,
                    packageName = wizardState.packageName,
                    packageNameError = wizardState.packageNameError,
                    versionName = wizardState.versionName,
                    onAppNameChange = { viewModel.updateAppName(it) },
                    onPackageNameChange = { viewModel.updatePackageName(it) },
                    onVersionNameChange = { viewModel.updateVersionName(it) }
                )
                2 -> StepSourceFile(
                    htmlContent = wizardState.htmlContent,
                    onContentChange = { viewModel.updateHtmlContent(it) }
                )
                3 -> StepCapabilityConfig()
                4 -> StepConfirm(
                    appName = wizardState.appName,
                    packageName = wizardState.packageName,
                    versionName = wizardState.versionName,
                    projectType = wizardState.projectType
                )
            }
        }
    }
}

/**
 * 步骤指示器
 */
@Composable
private fun StepIndicator(currentStep: Int) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween
    ) {
        repeat(5) { index ->
            val isActive = index == currentStep
            val isCompleted = index < currentStep
            Box(
                modifier = Modifier
                    .size(32.dp)
                    .background(
                        color = when {
                            isCompleted -> MaterialTheme.colorScheme.primary
                            isActive -> MaterialTheme.colorScheme.primary.copy(alpha = 0.3f)
                            else -> MaterialTheme.colorScheme.surfaceVariant
                        },
                        shape = RoundedCornerShape(16.dp)
                    ),
                contentAlignment = Alignment.Center
            ) {
                if (isCompleted) {
                    Icon(Icons.Default.Check, contentDescription = null, tint = androidx.compose.ui.graphics.Color.White, modifier = Modifier.size(18.dp))
                } else {
                    Text(
                        text = "${index + 1}",
                        style = MaterialTheme.typography.labelLarge,
                        color = if (isActive) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
            if (index < 4) {
                Box(
                    modifier = Modifier
                        .height(2.dp)
                        .weight(1f)
                        .padding(top = 15.dp)
                        .background(
                            if (index < currentStep) MaterialTheme.colorScheme.primary
                            else MaterialTheme.colorScheme.surfaceVariant
                        )
                )
            }
        }
    }
}

/**
 * 步骤1：选择项目类型
 */
@Composable
private fun StepProjectType(
    selectedType: ProjectType,
    onSelect: (ProjectType) -> Unit
) {
    Text("选择项目类型", style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold)
    Spacer(Modifier.height(8.dp))
    Text("P0 阶段仅支持纯 HTML 项目，Vue/React 将在后续版本支持", style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
    Spacer(Modifier.height(24.dp))

    val types = listOf(
        ProjectTypeOption(ProjectType.HTML, "纯HTML", Icons.Default.Language, true),
        ProjectTypeOption(ProjectType.VUE, "Vue", Icons.Default.Code, false),
        ProjectTypeOption(ProjectType.REACT, "React", Icons.Default.PhoneAndroid, false)
    )

    Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
        types.forEach { option ->
            val isSelected = selectedType == option.type
            Column(
                modifier = Modifier
                    .weight(1f)
                    .background(
                        if (isSelected) MaterialTheme.colorScheme.primary.copy(alpha = 0.15f)
                        else MaterialTheme.colorScheme.surfaceVariant,
                        RoundedCornerShape(12.dp)
                    )
                    .border(
                        width = if (isSelected) 2.dp else 0.dp,
                        color = if (isSelected) MaterialTheme.colorScheme.primary else androidx.compose.ui.graphics.Color.Transparent,
                        shape = RoundedCornerShape(12.dp)
                    )
                    .clickable(enabled = option.enabled) { if (option.enabled) onSelect(option.type) }
                    .padding(vertical = 24.dp),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                Icon(option.icon, contentDescription = null, tint = if (option.enabled) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.4f), modifier = Modifier.size(32.dp))
                Spacer(Modifier.height(8.dp))
                Text(option.name, style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.Medium, color = if (option.enabled) MaterialTheme.colorScheme.onSurface else MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.4f))
                if (!option.enabled) {
                    Spacer(Modifier.height(4.dp))
                    Text("敬请期待", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.4f))
                }
            }
        }
    }
}

/**
 * 步骤2：基本信息
 */
@Composable
private fun StepBasicInfo(
    appName: String,
    packageName: String,
    packageNameError: String?,
    versionName: String,
    onAppNameChange: (String) -> Unit,
    onPackageNameChange: (String) -> Unit,
    onVersionNameChange: (String) -> Unit
) {
    Text("基本信息", style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold)
    Spacer(Modifier.height(24.dp))

    OutlinedTextField(
        value = appName,
        onValueChange = onAppNameChange,
        label = { Text("应用名称") },
        placeholder = { Text("如：我的博客") },
        modifier = Modifier.fillMaxWidth(),
        singleLine = true
    )
    Spacer(Modifier.height(16.dp))

    OutlinedTextField(
        value = packageName,
        onValueChange = onPackageNameChange,
        label = { Text("包名") },
        placeholder = { Text("如：com.example.myapp") },
        modifier = Modifier.fillMaxWidth(),
        singleLine = true,
        isError = packageNameError != null,
        supportingText = {
            if (packageNameError != null) {
                Text(packageNameError, color = MaterialTheme.colorScheme.error)
            } else {
                Text("包名一旦创建不可修改，格式：com.xxx.xxx", style = MaterialTheme.typography.bodySmall)
            }
        }
    )
    Spacer(Modifier.height(16.dp))

    OutlinedTextField(
        value = versionName,
        onValueChange = onVersionNameChange,
        label = { Text("版本号") },
        placeholder = { Text("如：1.0.0") },
        modifier = Modifier.fillMaxWidth(),
        singleLine = true,
        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number)
    )
}

/**
 * 步骤3：导入源文件
 */
@Composable
private fun StepSourceFile(
    htmlContent: String,
    onContentChange: (String) -> Unit
) {
    Text("网页内容", style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold)
    Spacer(Modifier.height(8.dp))
    Text("直接编写或粘贴 HTML 代码，将作为应用首页", style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
    Spacer(Modifier.height(16.dp))

    OutlinedTextField(
        value = htmlContent,
        onValueChange = onContentChange,
        label = { Text("index.html") },
        placeholder = { Text("<!DOCTYPE html>\n<html>\n<head>\n    <title>我的应用</title>\n</head>\n<body>\n    <h1>Hello World</h1>\n</body>\n</html>") },
        modifier = Modifier
            .fillMaxWidth()
            .height(300.dp),
        maxLines = 20
    )
    Spacer(Modifier.height(8.dp))
    Text("留空将使用模版默认欢迎页", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.6f))
}

/**
 * 步骤4：能力配置（简化版）
 */
@Composable
private fun StepCapabilityConfig() {
    Text("能力配置", style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold)
    Spacer(Modifier.height(8.dp))
    Text("P0 阶段使用默认配置，后续版本可自定义", style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
    Spacer(Modifier.height(24.dp))

    val capabilities = listOf(
        "JS Bridge 能力" to "UI/设备/文件/网络/数据 5大模块",
        "权限声明" to "网络/存储/相机/定位等常用权限",
        "沉浸模式" to "状态栏/导航栏透明，内容全屏",
        "硬件加速" to "WebView 硬件加速，流畅渲染"
    )

    Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
        capabilities.forEach { (title, desc) ->
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .background(MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f), RoundedCornerShape(12.dp))
                    .padding(16.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Icon(Icons.Default.Check, contentDescription = null, tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(20.dp))
                Spacer(Modifier.width(12.dp))
                Column {
                    Text(title, style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.Medium)
                    Text(desc, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            }
        }
    }
}

/**
 * 步骤5：确认创建
 */
@Composable
private fun StepConfirm(
    appName: String,
    packageName: String,
    versionName: String,
    projectType: ProjectType
) {
    Text("确认创建", style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold)
    Spacer(Modifier.height(24.dp))

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .background(MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f), RoundedCornerShape(12.dp))
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        ConfirmRow("应用名称", appName)
        ConfirmRow("包名", packageName)
        ConfirmRow("版本号", "v$versionName")
        ConfirmRow("项目类型", projectType.displayName)
    }

    Spacer(Modifier.height(24.dp))
    Text(
        "点击「创建项目」后将生成项目配置，可在项目详情页进行构建",
        style = MaterialTheme.typography.bodySmall,
        color = MaterialTheme.colorScheme.onSurfaceVariant
    )
}

@Composable
private fun ConfirmRow(label: String, value: String) {
    Row {
        Text(label, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.width(80.dp))
        Text(value, style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.Medium)
    }
}

/**
 * 向导底部操作栏
 */
@Composable
private fun WizardBottomBar(
    currentStep: Int,
    canNext: Boolean,
    onPrev: () -> Unit,
    onNext: () -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(16.dp),
        horizontalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        if (currentStep > 0) {
            TextButton(onClick = onPrev, modifier = Modifier.weight(1f)) {
                Text("上一步")
            }
        }
        Button(
            onClick = onNext,
            enabled = canNext,
            modifier = Modifier.weight(1f),
            colors = ButtonDefaults.buttonColors()
        ) {
            Text(if (currentStep < 4) "下一步" else "创建项目")
        }
    }
}
