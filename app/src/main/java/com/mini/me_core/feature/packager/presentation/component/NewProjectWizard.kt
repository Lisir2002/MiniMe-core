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
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.filled.Code
import androidx.compose.material.icons.filled.Language
import androidx.compose.material.icons.filled.PhoneAndroid
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
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
 * 新建项目页（单页简化版）
 *
 * 只包含：选择分类、应用名称、包名、版本号
 * 创建后跳转到项目详情页进行深度配置
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun NewProjectWizard(
    viewModel: PackagerViewModel = hiltViewModel(),
    onNavigateBack: () -> Unit,
    onProjectCreated: (String) -> Unit
) {
    val wizardState by viewModel.newProjectState.collectAsState()
    val canCreate = wizardState.appName.isNotBlank() &&
            wizardState.packageName.isNotBlank() &&
            wizardState.packageNameError == null &&
            !wizardState.isCreating

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("新建项目") },
                navigationIcon = {
                    IconButton(onClick = onNavigateBack) {
                        Icon(Icons.Default.ArrowBack, contentDescription = "返回")
                    }
                }
            )
        },
        bottomBar = {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(16.dp)
            ) {
                Button(
                    onClick = {
                        runBlocking {
                            val project = viewModel.createProject()
                            if (project != null) {
                                onProjectCreated(project.id)
                            }
                        }
                    },
                    enabled = canCreate,
                    modifier = Modifier.fillMaxWidth()
                ) {
                    if (wizardState.isCreating) {
                        CircularProgressIndicator(
                            modifier = Modifier.size(20.dp),
                            color = MaterialTheme.colorScheme.onPrimary,
                            strokeWidth = 2.dp
                        )
                        Spacer(Modifier.size(8.dp))
                        Text("创建中...")
                    } else {
                        Text("创建项目")
                    }
                }
            }
        }
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .verticalScroll(rememberScrollState())
                .padding(16.dp)
        ) {
            // 选择分类
            Text("选择分类", style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold)
            Spacer(Modifier.height(8.dp))
            Text("P0 阶段仅支持纯 HTML 项目，Vue/React 将在后续版本支持", style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
            Spacer(Modifier.height(16.dp))

            val types = listOf(
                ProjectTypeOption(ProjectType.HTML, "纯HTML", Icons.Default.Language, true),
                ProjectTypeOption(ProjectType.VUE, "Vue", Icons.Default.Code, false),
                ProjectTypeOption(ProjectType.REACT, "React", Icons.Default.PhoneAndroid, false)
            )

            Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                types.forEach { option ->
                    val isSelected = wizardState.projectType == option.type
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
                            .clickable(enabled = option.enabled) { if (option.enabled) viewModel.updateProjectType(option.type) }
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

            Spacer(Modifier.height(24.dp))

            // 基本信息
            Text("基本信息", style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold)
            Spacer(Modifier.height(16.dp))

            OutlinedTextField(
                value = wizardState.appName,
                onValueChange = { viewModel.updateAppName(it) },
                label = { Text("应用名称 *") },
                placeholder = { Text("如：我的博客") },
                modifier = Modifier.fillMaxWidth(),
                singleLine = true
            )
            Spacer(Modifier.height(16.dp))

            OutlinedTextField(
                value = wizardState.packageName,
                onValueChange = { viewModel.updatePackageName(it) },
                label = { Text("包名 *") },
                placeholder = { Text("如：com.example.myapp") },
                modifier = Modifier.fillMaxWidth(),
                singleLine = true,
                isError = wizardState.packageNameError != null,
                supportingText = {
                    val error = wizardState.packageNameError
                    if (error != null) {
                        Text(error, color = MaterialTheme.colorScheme.error)
                    } else {
                        Text("根据应用名称自动生成，可手动编辑", style = MaterialTheme.typography.bodySmall)
                    }
                }
            )
            Spacer(Modifier.height(16.dp))

            OutlinedTextField(
                value = wizardState.versionName,
                onValueChange = { viewModel.updateVersionName(it) },
                label = { Text("版本号") },
                placeholder = { Text("如：1.0.0") },
                modifier = Modifier.fillMaxWidth(),
                singleLine = true,
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number)
            )

            Spacer(Modifier.height(24.dp))

            // 提示
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .background(MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f), RoundedCornerShape(12.dp))
                    .padding(16.dp)
            ) {
                Column {
                    Text("创建后可在项目详情页进行：", style = MaterialTheme.typography.bodySmall, fontWeight = FontWeight.Medium)
                    Spacer(Modifier.height(4.dp))
                    Text("• 编辑 HTML/CSS/JS 源码", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    Text("• 配置应用权限与能力开关", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    Text("• 自定义启动页、图标与显示设置", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            }
        }
    }
}
