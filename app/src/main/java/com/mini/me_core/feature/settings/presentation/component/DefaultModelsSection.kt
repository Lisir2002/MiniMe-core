package com.mini.me_core.feature.settings.presentation.component

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.KeyboardArrowRight
import androidx.compose.material.icons.automirrored.rounded.KeyboardArrowRight
import androidx.compose.material.icons.outlined.KeyboardArrowDown
import androidx.compose.material.icons.rounded.Check
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material.icons.rounded.CloudOff
import androidx.compose.material.icons.rounded.Compress
import androidx.compose.material.icons.rounded.Image
import androidx.compose.material.icons.rounded.Input
import androidx.compose.material.icons.rounded.SyncAlt
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.mini.me_core.R
import com.mini.me_core.core.theme.Spacing
import com.mini.me_core.core.theme.tokens.LocalCornerRadius
import com.mini.me_core.feature.settings.domain.model.AIProviderConfig
import com.mini.me_core.feature.settings.domain.model.ModelMetadata

private enum class SelectionType { VISION, COMPACTION }

/**
 * 默认模型二级页：集中管理应用中的默认/特定用途模型设置（如识图模型、压缩模型）。
 */
@Composable
internal fun DefaultModelsSection(
    providers: List<AIProviderConfig>,
    visionProviderId: String,
    visionModel: String,
    compactionProviderId: String,
    compactionModel: String,
    modelMetadata: Map<String, ModelMetadata>,
    onLoadMetadata: () -> Unit,
    onSelectVisionModel: (providerId: String, model: String) -> Unit,
    onClearVisionModel: () -> Unit,
    onSelectCompactionModel: (providerId: String, model: String) -> Unit,
    onClearCompactionModel: () -> Unit
) {
    var showVisionSheet by remember { mutableStateOf(false) }
    var showCompactionSheet by remember { mutableStateOf(false) }

    LaunchedEffect(Unit) { onLoadMetadata() }

    val visionProviderName = providers.firstOrNull { it.id == visionProviderId }?.name
    val visionSubtitle = if (visionProviderId.isBlank() || visionModel.isBlank()) {
        stringResource(R.string.settings_vision_follow_chat)
    } else {
        if (!visionProviderName.isNullOrBlank()) {
            stringResource(R.string.settings_vision_dedicated, visionProviderName, visionModel)
        } else {
            visionModel
        }
    }
    val visionMetadata = if (visionProviderId.isBlank() || visionModel.isBlank()) null else modelMetadata[visionModel]

    val compactionProviderName = providers.firstOrNull { it.id == compactionProviderId }?.name
    val compactionSubtitle = if (compactionProviderId.isBlank() || compactionModel.isBlank()) {
        stringResource(R.string.settings_compaction_follow_chat)
    } else {
        if (!compactionProviderName.isNullOrBlank()) {
            stringResource(R.string.settings_compaction_dedicated, compactionProviderName, compactionModel)
        } else {
            compactionModel
        }
    }
    val compactionMetadata = if (compactionProviderId.isBlank() || compactionModel.isBlank()) null else modelMetadata[compactionModel]

    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(Spacing.lg),
        verticalArrangement = Arrangement.spacedBy(Spacing.sm)
    ) {
        item {
            DefaultModelMenuRow(
                icon = Icons.Rounded.Image,
                title = stringResource(R.string.settings_vision_model),
                subtitle = visionSubtitle,
                usageHint = "用于图片理解、截图分析等场景",
                previewMetadata = visionMetadata,
                selectionType = SelectionType.VISION,
                onClick = { showVisionSheet = true }
            )
        }
        item {
            DefaultModelMenuRow(
                icon = Icons.Rounded.Compress,
                title = stringResource(R.string.settings_compaction_model),
                subtitle = compactionSubtitle,
                usageHint = "对话过长时自动压缩历史消息",
                previewMetadata = compactionMetadata,
                selectionType = SelectionType.COMPACTION,
                onClick = { showCompactionSheet = true }
            )
        }
    }

    if (showVisionSheet) {
        ModelSelectionSheet(
            title = stringResource(R.string.settings_vision_model),
            followChatModelText = stringResource(R.string.vision_follow_chat_model),
            followDescText = stringResource(R.string.vision_follow_desc),
            noModelsText = stringResource(R.string.vision_no_models),
            selectionType = SelectionType.VISION,
            providers = providers,
            currentProviderId = visionProviderId,
            currentModel = visionModel,
            modelMetadata = modelMetadata,
            onSelect = { pid, model ->
                onSelectVisionModel(pid, model)
                showVisionSheet = false
            },
            onClear = {
                onClearVisionModel()
                showVisionSheet = false
            },
            onDismiss = { showVisionSheet = false }
        )
    }

    if (showCompactionSheet) {
        ModelSelectionSheet(
            title = stringResource(R.string.settings_compaction_model),
            followChatModelText = stringResource(R.string.compaction_follow_chat_model),
            followDescText = stringResource(R.string.compaction_follow_desc),
            noModelsText = stringResource(R.string.compaction_no_models),
            selectionType = SelectionType.COMPACTION,
            providers = providers,
            currentProviderId = compactionProviderId,
            currentModel = compactionModel,
            modelMetadata = modelMetadata,
            onSelect = { pid, model ->
                onSelectCompactionModel(pid, model)
                showCompactionSheet = false
            },
            onClear = {
                onClearCompactionModel()
                showCompactionSheet = false
            },
            onDismiss = { showCompactionSheet = false }
        )
    }
}

/**
 * 自定义默认模型菜单行：图标 + 标题 + 当前选择 + 使用说明 + 右侧能力预览 + 箭头。
 */
@Composable
private fun DefaultModelMenuRow(
    icon: ImageVector,
    title: String,
    subtitle: String,
    usageHint: String,
    previewMetadata: ModelMetadata?,
    selectionType: SelectionType,
    onClick: () -> Unit
) {
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .clickable { onClick() },
        shape = RoundedCornerShape(LocalCornerRadius.current.lg),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant)
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(Spacing.lg),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Icon(
                imageVector = icon,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.size(24.dp)
            )
            Spacer(Modifier.width(Spacing.md))
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = title,
                    style = MaterialTheme.typography.titleMedium,
                    color = MaterialTheme.colorScheme.onSurface
                )
                Text(
                    text = subtitle,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                Text(
                    text = usageHint,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.7f),
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
            }
            previewMetadata?.let { meta ->
                CompactCapabilityPreview(metadata = meta, selectionType = selectionType)
                Spacer(Modifier.width(Spacing.sm))
            }
            Icon(
                imageVector = Icons.AutoMirrored.Rounded.KeyboardArrowRight,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    }
}

/**
 * 菜单行右侧紧凑能力预览：14dp 图标 pill。
 */
@Composable
private fun CompactCapabilityPreview(metadata: ModelMetadata, selectionType: SelectionType) {
    when (selectionType) {
        SelectionType.VISION -> {
            if (metadata.supportsVision) {
                Surface(
                    color = MaterialTheme.colorScheme.primaryContainer,
                    shape = RoundedCornerShape(50)
                ) {
                    Row(
                        modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Icon(
                            Icons.Rounded.Image,
                            contentDescription = "识图",
                            modifier = Modifier.size(14.dp),
                            tint = MaterialTheme.colorScheme.onPrimaryContainer
                        )
                    }
                }
            } else {
                CompactInputTokenTag(metadata)
            }
        }
        SelectionType.COMPACTION -> {
            CompactInputTokenTag(metadata)
        }
    }
}

@Composable
private fun CompactInputTokenTag(metadata: ModelMetadata) {
    val tokens = metadata.inputTokens ?: metadata.contextTokens
    if (tokens > 0) {
        Surface(
            color = MaterialTheme.colorScheme.surfaceVariant,
            shape = RoundedCornerShape(50)
        ) {
            Row(
                modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Icon(
                    Icons.Rounded.Input,
                    contentDescription = "输入窗口",
                    modifier = Modifier.size(14.dp),
                    tint = MaterialTheme.colorScheme.onSurfaceVariant
                )
                Spacer(Modifier.width(3.dp))
                Text(
                    text = formatCompactTokens(tokens),
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }
    }
}

private fun formatCompactTokens(tokens: Int): String = when {
    tokens >= 1_000_000 -> "${tokens / 1_000_000}M"
    tokens >= 1_000 -> "${tokens / 1_000}K"
    else -> tokens.toString()
}

/**
 * 模型选择弹窗：风格与 [FetchModelsDialog] 保持一致（包含搜索框、Logo 图标、能力 Tag、提供商折叠分组）。
 * 识图模型与压缩模型共用此组件，仅文案与排序规则不同。
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun ModelSelectionSheet(
    title: String,
    followChatModelText: String,
    followDescText: String,
    noModelsText: String,
    selectionType: SelectionType,
    providers: List<AIProviderConfig>,
    currentProviderId: String,
    currentModel: String,
    modelMetadata: Map<String, ModelMetadata>,
    onSelect: (providerId: String, model: String) -> Unit,
    onClear: () -> Unit,
    onDismiss: () -> Unit
) {
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    var searchQuery by remember { mutableStateOf("") }
    val collapsedProviders = remember { mutableStateMapOf<String, Boolean>() }

    val configuration = LocalConfiguration.current
    val screenHeight = configuration.screenHeightDp.dp

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = sheetState,
        sheetGesturesEnabled = true,
        containerColor = Color.Transparent,
        tonalElevation = 0.dp,
        dragHandle = null
    ) {
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .fillMaxHeight(),
            contentAlignment = Alignment.BottomCenter
        ) {
            Surface(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(screenHeight * 0.85f),
                color = MaterialTheme.colorScheme.surface,
                shape = RoundedCornerShape(topStart = LocalCornerRadius.current.map(28.dp), topEnd = LocalCornerRadius.current.map(28.dp))
            ) {
                Column(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(Spacing.lg)
                        .padding(bottom = Spacing.md),
                    verticalArrangement = Arrangement.spacedBy(Spacing.md)
                ) {
                    // 标题栏：左侧标题 + 右侧关闭按钮
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            text = title,
                            style = MaterialTheme.typography.titleMedium,
                            color = MaterialTheme.colorScheme.onSurface,
                            modifier = Modifier.weight(1f)
                        )
                        IconButton(onClick = onDismiss) {
                            Icon(
                                imageVector = Icons.Rounded.Close,
                                contentDescription = "关闭",
                                tint = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }

                    OutlinedTextField(
                        value = searchQuery,
                        onValueChange = { searchQuery = it },
                        placeholder = { Text(stringResource(R.string.provider_filter_models_hint)) },
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth(),
                        shape = RoundedCornerShape(50)
                    )

                    LazyColumn(
                        modifier = Modifier
                            .fillMaxWidth()
                            .weight(1f),
                        verticalArrangement = Arrangement.spacedBy(Spacing.xs)
                    ) {
                        if (searchQuery.isBlank() || followChatModelText.contains(searchQuery, ignoreCase = true)) {
                            item(key = "header_follow_chat") {
                                Card(
                                    modifier = Modifier.fillMaxWidth(),
                                    shape = RoundedCornerShape(LocalCornerRadius.current.lg),
                                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                                    border = BorderStroke(
                                        1.dp,
                                        if (currentProviderId.isBlank()) MaterialTheme.colorScheme.primary
                                        else MaterialTheme.colorScheme.outlineVariant
                                    )
                                ) {
                                    Row(
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .clickable { onClear() }
                                            .padding(Spacing.lg),
                                        verticalAlignment = Alignment.CenterVertically
                                    ) {
                                        Icon(
                                            imageVector = Icons.Rounded.SyncAlt,
                                            contentDescription = null,
                                            tint = MaterialTheme.colorScheme.onSurfaceVariant,
                                            modifier = Modifier.size(24.dp)
                                        )
                                        Spacer(Modifier.width(Spacing.md))
                                        Column(modifier = Modifier.weight(1f)) {
                                            Text(
                                                text = followChatModelText,
                                                style = MaterialTheme.typography.titleMedium,
                                                color = MaterialTheme.colorScheme.onSurface
                                            )
                                            Text(
                                                text = followDescText,
                                                style = MaterialTheme.typography.bodySmall,
                                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                                modifier = Modifier.padding(top = Spacing.xs)
                                            )
                                        }
                                        if (currentProviderId.isBlank()) {
                                            Spacer(Modifier.width(Spacing.sm))
                                            Icon(
                                                imageVector = Icons.Rounded.Check,
                                                contentDescription = null,
                                                tint = MaterialTheme.colorScheme.primary,
                                                modifier = Modifier.size(22.dp)
                                            )
                                        }
                                    }
                                }
                            }
                        }

                        val activeProviders = providers.filter { it.isEnabled && it.models.isNotEmpty() }
                        if (activeProviders.isEmpty()) {
                            item {
                                Column(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .padding(vertical = Spacing.xl),
                                    horizontalAlignment = Alignment.CenterHorizontally
                                ) {
                                    Icon(
                                        imageVector = Icons.Rounded.CloudOff,
                                        contentDescription = null,
                                        tint = MaterialTheme.colorScheme.onSurfaceVariant,
                                        modifier = Modifier.size(48.dp)
                                    )
                                    Spacer(Modifier.height(Spacing.md))
                                    Text(
                                        text = noModelsText,
                                        style = MaterialTheme.typography.bodyMedium,
                                        color = MaterialTheme.colorScheme.onSurface
                                    )
                                    Spacer(Modifier.height(Spacing.xs))
                                    Text(
                                        text = "请先在「服务商」Tab 添加并启用供应商",
                                        style = MaterialTheme.typography.bodySmall,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant
                                    )
                                }
                            }
                        } else {
                            activeProviders.forEach { provider ->
                                // 智能排序：VISION 按识图能力优先，COMPACTION 按上下文长度降序
                                val sortedModels = when (selectionType) {
                                    SelectionType.VISION -> provider.models.sortedByDescending {
                                        modelMetadata[it]?.supportsVision == true
                                    }
                                    SelectionType.COMPACTION -> provider.models.sortedByDescending {
                                        modelMetadata[it]?.let { m -> m.inputTokens ?: m.contextTokens } ?: 0
                                    }
                                }
                                // 搜索同时匹配模型名和供应商名
                                val filteredModels = sortedModels.filter { model ->
                                    searchQuery.isBlank() ||
                                        model.contains(searchQuery, ignoreCase = true) ||
                                        provider.name.contains(searchQuery, ignoreCase = true)
                                }
                                if (filteredModels.isNotEmpty()) {
                                    item(key = "header_${provider.id}") {
                                        val expanded = collapsedProviders[provider.id] != true
                                        Row(
                                            modifier = Modifier
                                                .fillMaxWidth()
                                                .heightIn(min = 44.dp)
                                                .clickable { collapsedProviders[provider.id] = expanded }
                                                .padding(horizontal = Spacing.xs, vertical = Spacing.sm),
                                            verticalAlignment = Alignment.CenterVertically
                                        ) {
                                            Text(
                                                text = "${provider.name} (${filteredModels.size})",
                                                style = MaterialTheme.typography.titleSmall,
                                                color = MaterialTheme.colorScheme.primary,
                                                maxLines = 1,
                                                overflow = TextOverflow.Ellipsis,
                                                modifier = Modifier.weight(1f)
                                            )
                                            Icon(
                                                imageVector = if (expanded) Icons.Outlined.KeyboardArrowDown else Icons.AutoMirrored.Outlined.KeyboardArrowRight,
                                                contentDescription = if (expanded) stringResource(R.string.provider_collapse_brand, provider.name) else stringResource(R.string.provider_expand_brand, provider.name),
                                                tint = MaterialTheme.colorScheme.onSurfaceVariant,
                                                modifier = Modifier.size(20.dp)
                                            )
                                        }
                                    }
                                    if (collapsedProviders[provider.id] != true) {
                                        items(filteredModels, key = { "${provider.id}_$it" }) { model ->
                                            ModelSelectionRow(
                                                model = model,
                                                providerName = provider.name,
                                                selected = provider.id == currentProviderId && model == currentModel,
                                                metadata = modelMetadata[model],
                                                selectionType = selectionType,
                                                onClick = { onSelect(provider.id, model) }
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
    }
}

@Composable
private fun ModelSelectionRow(
    model: String,
    providerName: String,
    selected: Boolean,
    metadata: ModelMetadata?,
    selectionType: SelectionType,
    onClick: () -> Unit
) {
    val showVisionWarning = selectionType == SelectionType.VISION &&
        (metadata == null || metadata.supportsVision == false)

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .then(
                if (selected) Modifier.background(
                    color = MaterialTheme.colorScheme.surfaceVariant,
                    shape = RoundedCornerShape(LocalCornerRadius.current.md)
                ) else Modifier
            )
            .clickable { onClick() }
            .padding(vertical = Spacing.sm, horizontal = Spacing.xs)
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            ModelLogoIcon(modelName = model, size = 20.dp)
            Spacer(Modifier.width(Spacing.md))
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = model,
                    style = MaterialTheme.typography.bodyMedium,
                    color = when {
                        selected -> MaterialTheme.colorScheme.primary
                        showVisionWarning -> MaterialTheme.colorScheme.onSurfaceVariant
                        else -> MaterialTheme.colorScheme.onSurface
                    },
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                Spacer(Modifier.height(2.dp))
                Row {
                    Text(
                        text = providerName,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        maxLines = 1
                    )
                    val desc = metadata?.description
                    if (!desc.isNullOrBlank()) {
                        Text(
                            text = " · ",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                        Text(
                            text = desc,
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                            modifier = Modifier.weight(1f)
                        )
                    }
                }
            }
            if (selected) {
                Spacer(Modifier.width(Spacing.sm))
                Icon(
                    imageVector = Icons.Rounded.Check,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.size(20.dp)
                )
            }
        }
        Spacer(Modifier.height(4.dp))
        ModelMetadataTags(metadata)
        if (showVisionWarning) {
            Spacer(Modifier.height(4.dp))
            Surface(
                color = MaterialTheme.colorScheme.errorContainer,
                shape = RoundedCornerShape(50)
            ) {
                Text(
                    text = "可能不支持识图",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onErrorContainer,
                    modifier = Modifier.padding(horizontal = 8.dp, vertical = 3.dp)
                )
            }
        }
    }
}
