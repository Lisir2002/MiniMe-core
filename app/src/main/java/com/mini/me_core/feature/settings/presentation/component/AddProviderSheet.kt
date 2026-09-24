package com.mini.me_core.feature.settings.presentation.component
import com.mini.me_core.core.theme.tokens.LocalCornerRadius

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material.icons.automirrored.rounded.KeyboardArrowLeft
import androidx.compose.material.icons.automirrored.rounded.KeyboardArrowRight
import androidx.compose.material.icons.rounded.Refresh
import androidx.compose.material.icons.rounded.Visibility
import androidx.compose.material.icons.rounded.VisibilityOff
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.mini.me_core.R
import com.mini.me_core.core.theme.Spacing
import com.mini.me_core.feature.settings.domain.model.AIProviderConfig
import com.mini.me_core.feature.settings.domain.model.ProviderType
import com.mini.me_core.feature.settings.presentation.FetchState
import com.mini.me_core.feature.settings.presentation.SettingsViewModel
import java.util.UUID

/**
 * 添加供应商底部弹窗（屏占比 9/10）—— 仅自定义供应商 2 步向导。
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AddProviderSheet(
    viewModel: SettingsViewModel,
    onDismiss: () -> Unit,
    onSave: (AIProviderConfig) -> Unit
) {
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    val configuration = LocalConfiguration.current
    val context = LocalContext.current

    // 自定义供应商状态
    var customStep by remember { mutableIntStateOf(1) }
    var customName by remember { mutableStateOf("") }
    var customType by remember { mutableStateOf(ProviderType.OPENAI) }
    var customApiKey by remember { mutableStateOf("") }
    var customBaseUrl by remember { mutableStateOf("") }
    var customModels by remember { mutableStateOf(listOf<String>()) }
    var customShowApiKey by remember { mutableStateOf(false) }
    var autoActivate by remember { mutableStateOf(false) }

    // 自定义供应商向导「选择模型」步骤的拉取状态
    val customFetchState by viewModel.customFetchState.collectAsStateWithLifecycle()

    // 进入自定义步骤 2（或切换类型/Key/URL）且已填 API Key 时实时拉取模型列表
    LaunchedEffect(customStep, customApiKey, customBaseUrl, customType) {
        if (customStep == 2 && customApiKey.isNotBlank()) {
            viewModel.fetchCustomModels(
                customBaseUrl.ifBlank { defaultProviderBaseUrl(customType) },
                customApiKey,
                customType
            )
        }
    }

    val currentStep = customStep
    val totalSteps = 2

    val canGoBack = customStep > 1

    val canGoNext = when (customStep) {
        1 -> customName.isNotBlank() || customApiKey.isNotBlank() || customBaseUrl.isNotBlank()
        else -> customModels.isNotEmpty()
    }

    fun goBack() {
        if (customStep > 1) customStep--
    }

    fun goNext() {
        if (customStep < 2) {
            customStep++
        } else {
            val provider = AIProviderConfig(
                id = UUID.randomUUID().toString(),
                name = customName.ifEmpty { "自定义供应商" },
                type = customType,
                apiKey = customApiKey,
                baseUrl = customBaseUrl.ifBlank { defaultProviderBaseUrl(customType) },
                defaultModel = customModels.firstOrNull().orEmpty(),
                isActive = autoActivate,
                models = customModels,
                selectedModel = customModels.firstOrNull().orEmpty(),
                isEnabled = true
            )
            onSave(provider)
            android.widget.Toast.makeText(
                context,
                "已添加供应商「${customName.ifEmpty { "自定义供应商" }}」，可在聊天页切换使用",
                android.widget.Toast.LENGTH_LONG
            ).show()
            onDismiss()
        }
    }

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = sheetState,
        containerColor = MaterialTheme.colorScheme.surface,
        dragHandle = null,
        tonalElevation = 0.dp,
        sheetGesturesEnabled = false
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .fillMaxHeight(0.9f)
        ) {
            // ── 标题栏 ──
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = Spacing.lg, vertical = Spacing.sm),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    stringResource(R.string.provider_add),
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.SemiBold,
                    modifier = Modifier.weight(1f)
                )
                IconButton(onClick = onDismiss) {
                    Icon(
                        Icons.Rounded.Close,
                        contentDescription = stringResource(R.string.common_close),
                        modifier = Modifier.size(20.dp)
                    )
                }
            }

            // ── 内容区 ──
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .weight(1f)
                    .padding(horizontal = Spacing.lg)
            ) {
                CustomProviderContent(
                    step = customStep,
                    name = customName,
                    onNameChange = { customName = it },
                    type = customType,
                    onTypeChange = { newType ->
                        if (customBaseUrl == defaultProviderBaseUrl(customType) || customBaseUrl.isBlank()) {
                            customBaseUrl = defaultProviderBaseUrl(newType)
                        }
                        customType = newType
                    },
                    apiKey = customApiKey,
                    onApiKeyChange = { customApiKey = it },
                    baseUrl = customBaseUrl,
                    onBaseUrlChange = { customBaseUrl = it },
                    showApiKey = customShowApiKey,
                    onShowApiKeyChange = { customShowApiKey = it },
                    models = customModels,
                    onModelsChange = { customModels = it },
                    fetchState = customFetchState,
                    onFetchModels = {
                        viewModel.fetchCustomModels(
                            customBaseUrl.ifBlank { defaultProviderBaseUrl(customType) },
                            customApiKey,
                            customType
                        )
                    },
                    viewModel = viewModel
                )
            }

            // ── 添加后设为当前供应商（仅步骤2） ──
            if (customStep == 2) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier.padding(horizontal = Spacing.lg, vertical = Spacing.xs)
                ) {
                    Text("添加后设为当前供应商", modifier = Modifier.weight(1f))
                    androidx.compose.material3.Switch(
                        checked = autoActivate,
                        onCheckedChange = { autoActivate = it }
                    )
                }
            }

            // ── 底部按钮 ──
            HorizontalDivider()
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(Spacing.lg),
                horizontalArrangement = Arrangement.spacedBy(Spacing.md),
                verticalAlignment = Alignment.CenterVertically
            ) {
                TextButton(
                    onClick = { goBack() },
                    enabled = canGoBack,
                    modifier = Modifier.weight(1f)
                ) {
                    Icon(Icons.AutoMirrored.Rounded.KeyboardArrowLeft, contentDescription = null, modifier = Modifier.size(18.dp))
                    Spacer(Modifier.width(Spacing.xs))
                    Text(stringResource(R.string.provider_step_previous))
                }
                TextButton(
                    onClick = { goNext() },
                    enabled = canGoNext,
                    modifier = Modifier.weight(1f)
                ) {
                    Text(
                        if (currentStep == totalSteps) stringResource(R.string.provider_step_finish)
                        else stringResource(R.string.provider_step_next)
                    )
                    Spacer(Modifier.width(Spacing.xs))
                    Icon(Icons.AutoMirrored.Rounded.KeyboardArrowRight, contentDescription = null, modifier = Modifier.size(18.dp))
                }
            }
        }
    }
}

/** 自定义供应商内容区：2 步向导。 */
@Composable
private fun CustomProviderContent(
    step: Int,
    name: String,
    onNameChange: (String) -> Unit,
    type: ProviderType,
    onTypeChange: (ProviderType) -> Unit,
    apiKey: String,
    onApiKeyChange: (String) -> Unit,
    baseUrl: String,
    onBaseUrlChange: (String) -> Unit,
    showApiKey: Boolean,
    onShowApiKeyChange: (Boolean) -> Unit,
    models: List<String>,
    onModelsChange: (List<String>) -> Unit,
    fetchState: FetchState,
    onFetchModels: () -> Unit,
    viewModel: SettingsViewModel
) {
    val baseUrlError = baseUrl.isNotBlank() && !baseUrl.startsWith("http://") && !baseUrl.startsWith("https://")
    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(vertical = Spacing.md),
        verticalArrangement = Arrangement.spacedBy(Spacing.md)
    ) {
        StepIndicator(current = step, total = 2)

        when (step) {
            1 -> {
                Text(
                    stringResource(R.string.provider_step_basic_info),
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.SemiBold
                )
                OutlinedTextField(
                    value = name,
                    onValueChange = onNameChange,
                    label = { Text(stringResource(R.string.common_name)) },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )
                Row(horizontalArrangement = Arrangement.spacedBy(Spacing.sm)) {
                    ProviderType.entries.forEach { t ->
                        FilterChip(
                            selected = type == t,
                            onClick = { onTypeChange(t) },
                            label = { Text(providerTypeLabel(t)) }
                        )
                    }
                }
                OutlinedTextField(
                    value = apiKey,
                    onValueChange = onApiKeyChange,
                    label = { Text("API Key") },
                    singleLine = true,
                    visualTransformation = if (showApiKey) VisualTransformation.None else PasswordVisualTransformation(),
                    trailingIcon = {
                        IconButton(onClick = { onShowApiKeyChange(!showApiKey) }) {
                            Icon(
                                if (showApiKey) Icons.Rounded.Visibility else Icons.Rounded.VisibilityOff,
                                contentDescription = null
                            )
                        }
                    },
                    modifier = Modifier.fillMaxWidth()
                )
                OutlinedTextField(
                    value = baseUrl,
                    onValueChange = onBaseUrlChange,
                    label = { Text("Base URL") },
                    placeholder = { Text(defaultProviderBaseUrl(type)) },
                    singleLine = true,
                    isError = baseUrlError,
                    supportingText = {
                        if (baseUrlError) {
                            Text("Base URL 需以 http:// 或 https:// 开头", color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodySmall)
                        }
                    },
                    modifier = Modifier.fillMaxWidth()
                )
            }
            2 -> {
                Text(
                    stringResource(R.string.provider_step_select_model),
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.SemiBold
                )
                CustomModelFetchList(
                    fetchState = fetchState,
                    models = models,
                    onModelsChange = onModelsChange,
                    apiKey = apiKey,
                    baseUrl = baseUrl,
                    type = type,
                    onFetchModels = onFetchModels,
                    viewModel = viewModel
                )
            }
        }
    }
}

/** 步骤指示器（圆点）。 */
@Composable
private fun StepIndicator(current: Int, total: Int) {
    Row(
        horizontalArrangement = Arrangement.spacedBy(Spacing.sm),
        verticalAlignment = Alignment.CenterVertically
    ) {
        repeat(total) { index ->
            val stepNo = index + 1
            val active = stepNo == current
            val done = stepNo < current
            Box(
                modifier = Modifier
                    .size(if (active) 10.dp else 8.dp)
                    .background(
                        color = when {
                            active -> MaterialTheme.colorScheme.primary
                            done -> MaterialTheme.colorScheme.primary.copy(alpha = 0.5f)
                            else -> MaterialTheme.colorScheme.surfaceVariant
                        },
                        shape = CircleShape
                    )
            )
        }
    }
}

/**
 * 自定义供应商模型列表（支持实时拉取 + 手动添加 + 测试）。
 */
@Composable
private fun CustomModelFetchList(
    fetchState: FetchState,
    models: List<String>,
    onModelsChange: (List<String>) -> Unit,
    apiKey: String,
    baseUrl: String,
    type: ProviderType,
    onFetchModels: () -> Unit,
    viewModel: SettingsViewModel
) {
    val testResults by viewModel.testResults.collectAsStateWithLifecycle()
    val testing by viewModel.testing.collectAsStateWithLifecycle()
    val modelMetadata by viewModel.modelMetadata.collectAsStateWithLifecycle()
    var newModelName by remember { mutableStateOf("") }

    val fetchedCandidates = if (fetchState is FetchState.Success) fetchState.models else emptyList()
    val allSelected = fetchedCandidates.isNotEmpty() && fetchedCandidates.all { it in models }

    Column(verticalArrangement = Arrangement.spacedBy(Spacing.sm)) {
        // ── 拉取按钮 + 全选/反选 + 已选数量 ──
        Row(verticalAlignment = Alignment.CenterVertically) {
            TextButton(
                onClick = onFetchModels,
                enabled = apiKey.isNotBlank() && fetchState !is FetchState.Loading
            ) {
                Icon(
                    Icons.Rounded.Refresh,
                    contentDescription = null,
                    modifier = Modifier.size(16.dp)
                )
                Spacer(Modifier.width(Spacing.xs))
                Text(stringResource(R.string.provider_fetch_models))
            }
            if (fetchedCandidates.isNotEmpty()) {
                TextButton(onClick = {
                    if (allSelected) {
                        onModelsChange(models - fetchedCandidates.toSet())
                    } else {
                        onModelsChange((models + fetchedCandidates).distinct())
                    }
                }) {
                    Text(if (allSelected) "取消全选" else "全选")
                }
            }
            Spacer(Modifier.weight(1f))
            Text(
                "已选 ${models.size} 个模型",
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }

        // ── 拉取状态指示 ──
        when (fetchState) {
            is FetchState.Loading -> {
                Surface(
                    color = MaterialTheme.colorScheme.surfaceVariant,
                    shape = RoundedCornerShape(LocalCornerRadius.current.lg),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Row(
                        modifier = Modifier.padding(Spacing.md),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        CircularProgressIndicator(
                            modifier = Modifier.size(18.dp),
                            strokeWidth = 2.dp
                        )
                        Spacer(Modifier.width(Spacing.md))
                        Text(
                            stringResource(R.string.provider_step_fetch_models),
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
            }
            is FetchState.Error -> {
                Surface(
                    color = MaterialTheme.colorScheme.errorContainer,
                    shape = RoundedCornerShape(LocalCornerRadius.current.lg),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Row(
                        modifier = Modifier.padding(Spacing.md),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            stringResource(R.string.provider_fetch_failed, fetchState.message),
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onErrorContainer,
                            modifier = Modifier.weight(1f)
                        )
                        Spacer(Modifier.width(Spacing.sm))
                        TextButton(onClick = onFetchModels) {
                            Icon(
                                Icons.Rounded.Refresh,
                                contentDescription = null,
                                modifier = Modifier.size(16.dp)
                            )
                            Spacer(Modifier.width(Spacing.xs))
                            Text(stringResource(R.string.provider_step_fetch_retry))
                        }
                    }
                }
            }
            is FetchState.Success -> {
                // success count shown above
            }
            is FetchState.Idle -> {
                if (models.isEmpty() && apiKey.isBlank()) {
                    Text(
                        stringResource(R.string.provider_step_custom_models_hint),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
        }

        // ── 手动添加 ──
        Row(
            horizontalArrangement = Arrangement.spacedBy(Spacing.sm),
            verticalAlignment = Alignment.CenterVertically
        ) {
            OutlinedTextField(
                value = newModelName,
                onValueChange = { newModelName = it },
                label = { Text(stringResource(R.string.provider_model_name)) },
                placeholder = { Text(stringResource(R.string.provider_model_name_hint)) },
                singleLine = true,
                modifier = Modifier.weight(1f)
            )
            TextButton(
                onClick = {
                    val name = newModelName.trim()
                    if (name.isNotEmpty() && name !in models) {
                        onModelsChange(models + name)
                    }
                    newModelName = ""
                },
                enabled = newModelName.isNotBlank()
            ) {
                Text(stringResource(R.string.provider_add_model))
            }
        }

        // ── 模型列表：拉取候选 ∪ 已选 ──
        val displayModels = (fetchedCandidates + models).distinct()
        if (displayModels.isEmpty()) {
            if (fetchState !is FetchState.Loading) {
                Text(
                    stringResource(R.string.provider_step_custom_models_empty),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        } else {
            if (fetchState is FetchState.Success && models.isEmpty() && displayModels.isNotEmpty()) {
                Text(
                    stringResource(R.string.provider_step_model_select_hint),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.primary
                )
            }
            displayModels.forEach { model ->
                ProviderModelRow(
                    model = model,
                    metadata = modelMetadata[model],
                    hasOverride = false,
                    testing = model in testing,
                    result = testResults[model],
                    selected = model in models,
                    onToggleSelected = { checked ->
                        onModelsChange(if (checked) models + model else models - model)
                    },
                    onTest = {
                        val provider = AIProviderConfig(
                            id = "custom-test",
                            name = "自定义供应商",
                            type = type,
                            apiKey = apiKey,
                            baseUrl = baseUrl.ifBlank { defaultProviderBaseUrl(type) },
                            defaultModel = model,
                            isActive = false,
                            models = models,
                            selectedModel = model,
                            isEnabled = true
                        )
                        viewModel.testModel(provider, model)
                    },
                    onRemove = null,
                    onOpenCapabilityOverride = null
                )
            }
        }
    }
}
