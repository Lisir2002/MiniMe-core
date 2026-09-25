package com.mini.me_core.feature.settings.presentation.component

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material.icons.rounded.Add
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.mini.me_core.R
import com.mini.me_core.core.theme.Spacing
import com.mini.me_core.core.theme.components.AppSegmentedControl
import com.mini.me_core.feature.settings.domain.model.AIProviderConfig
import com.mini.me_core.feature.settings.presentation.SettingsViewModel

/**
 * 模型管理页面：合并「模型服务商」和「默认模型」两个入口，通过顶栏 TabRow 切换。
 *
 * - Tab 0「服务商」：显示供应商列表，顶栏提供「+」添加按钮
 * - Tab 1「默认模型」：显示识图模型 / 压缩模型配置
 *
 * 复用现有 [ProvidersSection] 和 [DefaultModelsSection] 组件，不重写其内部逻辑。
 */
@Composable
internal fun ModelManagementScreen(
    viewModel: SettingsViewModel,
    onNavigateBack: () -> Unit,
    onEditProvider: (AIProviderConfig) -> Unit
) {
    // 收集 ViewModel 状态
    val providers by viewModel.providers.collectAsStateWithLifecycle()
    val activeProvider by viewModel.activeProvider.collectAsStateWithLifecycle()
    val visionProviderId by viewModel.visionProviderId.collectAsStateWithLifecycle()
    val visionModel by viewModel.visionModel.collectAsStateWithLifecycle()
    val compactionProviderId by viewModel.compactionProviderId.collectAsStateWithLifecycle()
    val compactionModel by viewModel.compactionModel.collectAsStateWithLifecycle()
    val modelMetadata by viewModel.modelMetadata.collectAsStateWithLifecycle()

    // 当前选中的 Tab：0 = 服务商，1 = 默认模型
    var selectedTab by remember { mutableStateOf(0) }

    // 添加供应商弹窗状态（本页面内部管理）
    var showAddProviderSheet by remember { mutableStateOf(false) }

    Scaffold(
        containerColor = MaterialTheme.colorScheme.background,
        contentColor = MaterialTheme.colorScheme.onBackground,
        topBar = {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .statusBarsPadding()
            ) {
                // ── 顶栏行：返回按钮 + 标题 + 操作按钮 ──
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = Spacing.xs, vertical = Spacing.xs),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    IconButton(
                        onClick = onNavigateBack,
                        modifier = Modifier.size(40.dp)
                    ) {
                        Icon(
                            Icons.AutoMirrored.Rounded.ArrowBack,
                            contentDescription = stringResource(R.string.common_back),
                            modifier = Modifier.size(20.dp)
                        )
                    }
                    Spacer(Modifier.width(Spacing.xs))
                    Text(
                        text = "模型管理",
                        style = MaterialTheme.typography.titleLarge,
                        color = MaterialTheme.colorScheme.onBackground,
                        modifier = Modifier.weight(1f)
                    )
                    // 仅在服务商 Tab 显示添加按钮
                    if (selectedTab == 0) {
                        IconButton(
                            onClick = { showAddProviderSheet = true },
                            modifier = Modifier.size(40.dp)
                        ) {
                            Icon(
                                Icons.Rounded.Add,
                                contentDescription = stringResource(R.string.settings_add_provider),
                                modifier = Modifier.size(20.dp)
                            )
                        }
                    }
                }

                // ── 顶栏 Tab：服务商 / 默认模型 ──
                AppSegmentedControl(
                    tabs = listOf(
                        stringResource(R.string.model_management_tab_providers),
                        stringResource(R.string.settings_default_models)
                    ),
                    selectedIndex = selectedTab,
                    onSelect = { selectedTab = it }
                )
            }
        }
    ) { padding ->
        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
        ) {
            when (selectedTab) {
                // Tab 0：服务商列表
                0 -> ProvidersSection(
                    providers = providers,
                    activeProviderId = activeProvider?.id,
                    onEdit = onEditProvider,
                    onSetActive = { viewModel.setActiveProvider(it) },
                    onToggleEnabled = { id, enabled -> viewModel.setProviderEnabled(id, enabled) },
                    onDuplicate = { viewModel.duplicateProvider(it) }
                )
                // Tab 1：默认模型配置
                1 -> DefaultModelsSection(
                    providers = providers,
                    visionProviderId = visionProviderId,
                    visionModel = visionModel,
                    compactionProviderId = compactionProviderId,
                    compactionModel = compactionModel,
                    modelMetadata = modelMetadata,
                    onLoadMetadata = { viewModel.loadAllModelMetadata() },
                    onSelectVisionModel = { pid, m -> viewModel.setVisionModel(pid, m) },
                    onClearVisionModel = { viewModel.clearVisionModel() },
                    onSelectCompactionModel = { pid, m -> viewModel.setCompactionModel(pid, m) },
                    onClearCompactionModel = { viewModel.clearCompactionModel() }
                )
            }
        }
    }

    // 添加供应商弹窗
    if (showAddProviderSheet) {
        AddProviderSheet(
            viewModel = viewModel,
            onDismiss = { showAddProviderSheet = false },
            onSave = { provider ->
                viewModel.saveProvider(provider)
                showAddProviderSheet = false
            }
        )
    }
}
