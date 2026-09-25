package com.mini.me_core.feature.settings.presentation.component

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Apps
import androidx.compose.material.icons.outlined.AutoAwesome
import androidx.compose.material.icons.outlined.Build
import androidx.compose.material.icons.outlined.Chat
import androidx.compose.material.icons.outlined.Check
import androidx.compose.material.icons.outlined.Close
import androidx.compose.material.icons.outlined.CloudDownload
import androidx.compose.material.icons.outlined.ErrorOutline
import androidx.compose.material.icons.outlined.Image
import androidx.compose.material.icons.outlined.KeyboardArrowDown
import androidx.compose.material.icons.outlined.Tune
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.outlined.KeyboardArrowRight
import androidx.compose.material.icons.outlined.Add
import androidx.compose.material.icons.rounded.Add
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material.icons.rounded.CloudDownload
import androidx.compose.material.icons.rounded.Check
import androidx.compose.material.icons.rounded.ContentPaste
import androidx.compose.material.icons.rounded.Delete
import androidx.compose.material.icons.rounded.Extension
import androidx.compose.material.icons.rounded.Memory
import androidx.compose.material.icons.rounded.Refresh
import androidx.compose.material.icons.rounded.Tune
import androidx.compose.material.icons.rounded.Visibility
import androidx.compose.material.icons.rounded.VisibilityOff
import androidx.compose.material.icons.rounded.Warning
import androidx.compose.material3.AlertDialog
import com.mini.me_core.core.theme.components.AppDialog
import com.mini.me_core.core.theme.components.AppDialogType
import androidx.compose.material3.AssistChip
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.*
import androidx.compose.ui.platform.LocalClipboard
import android.content.ClipData
import androidx.compose.ui.platform.ClipEntry
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.Switch
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import kotlinx.coroutines.delay
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.foundation.background
import kotlinx.coroutines.launch
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.mini.me_core.core.theme.Radius
import com.mini.me_core.core.theme.Spacing
import com.mini.me_core.core.theme.tokens.LocalCornerRadius
import com.mini.me_core.feature.agent.data.local.entity.ModelCapabilityOverrideEntity
import com.mini.me_core.feature.settings.data.remote.ModelTestResult
import com.mini.me_core.feature.settings.data.repository.CompatibilityPolicyRepository
import com.mini.me_core.feature.settings.data.repository.DefaultPolicy
import com.mini.me_core.feature.settings.data.repository.ViewImageUnknownGuardPolicy
import com.mini.me_core.feature.settings.domain.model.AIProviderConfig
import com.mini.me_core.feature.settings.domain.model.ModelMetadata
import com.mini.me_core.feature.settings.domain.model.ProviderType
import com.mini.me_core.feature.settings.domain.model.defaultProviderApiPath
import com.mini.me_core.feature.settings.domain.model.temperatureRange
import com.mini.me_core.feature.settings.presentation.ConnectionTestState
import com.mini.me_core.feature.settings.presentation.FetchState
import com.mini.me_core.feature.settings.presentation.SettingsViewModel
import java.util.UUID
import androidx.compose.ui.res.stringResource
import com.mini.me_core.R


@OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class)
@Composable
fun ProviderEditorScreen(
    viewModel: SettingsViewModel,
    initialProvider: AIProviderConfig?,
    onNavigateBack: () -> Unit,
    onSave: (AIProviderConfig) -> Unit,
    onDelete: (String) -> Unit
) {
    val context = LocalContext.current
    var name by remember { mutableStateOf(initialProvider?.name ?: "") }
    var apiKey by remember { mutableStateOf(initialProvider?.apiKey ?: "") }
    var baseUrl by remember { mutableStateOf(initialProvider?.baseUrl ?: "") }
    var useFullUrl by remember { mutableStateOf(initialProvider?.useFullUrl ?: false) }
    var useResponseApi by remember { mutableStateOf(initialProvider?.useResponseApi ?: false) }
    var isEnabled by remember { mutableStateOf(initialProvider?.isEnabled ?: true) }
    var type by remember { mutableStateOf(initialProvider?.type ?: ProviderType.OPENAI) }
    var showApiKey by remember { mutableStateOf(false) }
    var showDeleteConfirm by remember { mutableStateOf(false) }
    val providerId = remember { initialProvider?.id ?: UUID.randomUUID().toString() }
    val models = remember { mutableStateListOf<String>().apply { addAll(initialProvider?.models ?: emptyList()) } }
    var showAddModelSheet by remember { mutableStateOf(false) }
    var showFetchDialog by remember { mutableStateOf(false) }
    var fetchDialogKey by remember { mutableIntStateOf(0) }
    var selectedTab by remember { mutableIntStateOf(0) }
    // RC63 ④：当前「能力覆盖」面板正在编辑哪一个模型；null=关闭。
    var capabilityOverrideModel by remember { mutableStateOf<String?>(null) }

    // ── 新增高级参数状态（采样 / API Path / 超时重试 / 备用供应商） ──
    var temperature by remember { mutableFloatStateOf(initialProvider?.temperature ?: 1.0f) }
    var topP by remember { mutableFloatStateOf(initialProvider?.topP ?: 1.0f) }
    var maxTokensText by remember { mutableStateOf(initialProvider?.maxTokens?.takeIf { it > 0 }?.toString() ?: "") }
    // API Path 开关：默认关闭（自动补充默认路径），打开后用户手动填写且不能为空。
    // 编辑已有供应商时，若其 apiPath 与该类型默认值不同，则视为已自定义，开关默认打开。
    var useCustomApiPath by remember {
        mutableStateOf(
            initialProvider?.apiPath != null &&
                initialProvider.apiPath != defaultProviderApiPath(initialProvider.type ?: ProviderType.OPENAI)
        )
    }
    var apiPath by remember {
        mutableStateOf(
            if (initialProvider?.apiPath != null &&
                initialProvider.apiPath != defaultProviderApiPath(initialProvider.type ?: ProviderType.OPENAI)
            ) initialProvider.apiPath else ""
        )
    }
    var apiPathError by remember { mutableStateOf(false) }
    var requestTimeoutText by remember { mutableStateOf(initialProvider?.requestTimeout?.toString() ?: "30") }
    var retryCountText by remember { mutableStateOf(initialProvider?.retryCount?.toString() ?: "0") }
    var fallbackProviderId by remember { mutableStateOf(initialProvider?.fallbackProviderId) }
    /** 本地收藏列表（编辑期即时更新；保存时写入 config，已有供应商同时由 ViewModel 持久化）。 */
    var localFavorites by remember { mutableStateOf<List<String>>(initialProvider?.favoriteModels ?: emptyList()) }

    val fetchState by viewModel.fetchState.collectAsStateWithLifecycle()
    val testResults by viewModel.testResults.collectAsStateWithLifecycle()
    val testing by viewModel.testing.collectAsStateWithLifecycle()
    val modelMetadata by viewModel.modelMetadata.collectAsStateWithLifecycle()
    /** RC72：保存失败提示（不闪退，弹 Toast）。 */
    val saveError by viewModel.saveError.collectAsStateWithLifecycle()
    /** RC63 ③ 兼容端点全局策略（下拉 + 两个 Switch）。 */
    val defaultPolicy by viewModel.compatibilityDefaultPolicyFlow.collectAsStateWithLifecycle()
    val autoDowngrade by viewModel.autoDowngradeOnSendFailureFlow.collectAsStateWithLifecycle()
    val viewImageGuard by viewModel.viewImageUnknownGuardPolicyFlow.collectAsStateWithLifecycle()
    /** 供应商级连通性测试状态。 */
    val connectionTestState by viewModel.connectionTestState.collectAsStateWithLifecycle()
    /** 全量供应商列表（备用供应商下拉用）。 */
    val allProviders by viewModel.providers.collectAsStateWithLifecycle()
    val modelSnapshot = models.toList()

    /** RC63 ④ 单模型覆盖：ProviderModelRow 每个模型 hasOverride 的即时快照（Flow -> State）。 */
    val overridesMap: Map<String, ModelCapabilityOverrideEntity> by remember(type, models) {
        // 注意 1：下面每一步都显式标注类型，原因是 CI（Kotlin 2.1.20 + AGP 8.9.3）对
        // "combine(List<Flow<Pair<A,B?>>>)" 这种嵌套泛型 + lambda 的推断会失败，
        // 报错 "Cannot infer type for type parameter T / R / B / K / V"，IDE 的 Kotlin 插件反而可以过。
        // 注意 2：必须使用 combine(flowList) { values: Array<T> -> ... } 这种「Flow 列表 + transform」
        // 三参/二参重载，避免 combine(vararg flows: Flow<T>) { ... } 推断不出来。
        val flowsMap: Map<String, kotlinx.coroutines.flow.Flow<ModelCapabilityOverrideEntity?>> =
            models.associateWith { modelId -> viewModel.observeCapabilityOverride(type, modelId) }
        val modelIds: List<String> = flowsMap.keys.toList()
        val flowList: List<kotlinx.coroutines.flow.Flow<ModelCapabilityOverrideEntity?>> =
            modelIds.map { id -> flowsMap.getValue(id) }
        val combined: kotlinx.coroutines.flow.Flow<Map<String, ModelCapabilityOverrideEntity>> =
            kotlinx.coroutines.flow.combine(
                flows = flowList
            ) { values: Array<ModelCapabilityOverrideEntity?> ->
                val out: MutableMap<String, ModelCapabilityOverrideEntity> = linkedMapOf()
                values.forEachIndexed { index, entity ->
                    if (entity != null) out[modelIds[index]] = entity
                }
                out
            }
        combined
    // 注意：这里不能写 <Map<...>> 单个泛型实参！
    // Kotlin 标准库签名是 fun <T : R, R> Flow<T>.collectAsState(initial: R, ...): State<R>
    // 显式写 2 个实参太啰嗦，利用 remember 返回值 combined 的显式类型 Flow<Map<...>> 自动推断即可。
    // IDE 插件能兼容单实参写法，但 CI Kotlin 2.1.20 严格检查会报错：
    // "2 type arguments expected for fun <T : R, R> Flow<T>.collectAsState(...)"
    }.collectAsState(initial = emptyMap())

    // ── 防截图录屏：读取用户开关，动态控制 FLAG_SECURE ──
    val secureScreenEnabled by viewModel.secureScreenEnabled.collectAsState()

    DisposableEffect(secureScreenEnabled) {
        viewModel.resetFetchState()
        viewModel.clearTestResults()
        viewModel.resetConnectionTest()
        val activity = context as? android.app.Activity
        if (secureScreenEnabled) {
            activity?.window?.addFlags(android.view.WindowManager.LayoutParams.FLAG_SECURE)
        } else {
            activity?.window?.clearFlags(android.view.WindowManager.LayoutParams.FLAG_SECURE)
        }
        onDispose {
            viewModel.resetFetchState()
            viewModel.clearTestResults()
            viewModel.resetConnectionTest()
            activity?.window?.clearFlags(android.view.WindowManager.LayoutParams.FLAG_SECURE)
        }
    }

    // RC72：保存失败时弹 Toast（不闪退），消费后不再重复弹。
    LaunchedEffect(saveError) {
        val err = saveError
        if (err != null) {
            android.widget.Toast.makeText(context, err, android.widget.Toast.LENGTH_LONG).show()
            viewModel.consumeSaveError()
        }
    }

    LaunchedEffect(type, modelSnapshot) {
        viewModel.resolveModelMetadata(type, modelSnapshot)
    }

    fun currentConfig() = AIProviderConfig(
        id = providerId,
        name = name.ifEmpty { context.getString(R.string.provider_new) },
        type = type,
        apiKey = apiKey,
        baseUrl = baseUrl.ifBlank { defaultProviderBaseUrl(type) },
        useFullUrl = useFullUrl,
        isEnabled = isEnabled,
        defaultModel = initialProvider?.defaultModel ?: "",
        isActive = initialProvider?.isActive ?: false,
        models = models.toList(),
        selectedModel = initialProvider?.selectedModel ?: "",
        useResponseApi = useResponseApi,
        temperature = temperature,
        topP = topP,
        maxTokens = maxTokensText.trim().toIntOrNull()?.takeIf { it > 0 },
        apiPath = if (useCustomApiPath) apiPath.trim() else defaultProviderApiPath(type),
        requestTimeout = requestTimeoutText.trim().toIntOrNull()?.coerceIn(5, 120) ?: 30,
        retryCount = retryCountText.trim().toIntOrNull()?.coerceIn(0, 5) ?: 0,
        fallbackProviderId = fallbackProviderId?.takeIf { it.isNotBlank() && it != providerId },
        favoriteModels = localFavorites,
        modelOrder = initialProvider?.modelOrder ?: emptyList(),
    )

    // 新建场景下判断用户是否填写了实质内容：名称、API Key、Base URL 任一非空白，或已添加模型。
    // 全空白时退出不应落库，否则会存入一条名为“新提供商”的空记录。
    fun hasSubstantiveInput(): Boolean =
        initialProvider != null ||
            name.isNotBlank() ||
            apiKey.isNotBlank() ||
            baseUrl.isNotBlank() ||
            models.isNotEmpty()

    fun saveCurrent() {
        if (!hasSubstantiveInput()) return
        // 自定义 API Path 时不能为空
        if (useCustomApiPath && apiPath.isBlank()) {
            apiPathError = true
            android.widget.Toast.makeText(context, "自定义 API Path 不能为空", android.widget.Toast.LENGTH_SHORT).show()
            return
        }
        onSave(currentConfig())
    }

    fun saveAndNavigateBack() {
        saveCurrent()
        onNavigateBack()
    }

    BackHandler {
        saveAndNavigateBack()
    }

    Scaffold(
        containerColor = MaterialTheme.colorScheme.background,
        topBar = {
            TopAppBar(
                title = { Text(if (initialProvider == null) stringResource(R.string.provider_add) else stringResource(R.string.provider_edit)) },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.background,
                    titleContentColor = MaterialTheme.colorScheme.onBackground
                ),
                navigationIcon = {
                    IconButton(onClick = { saveAndNavigateBack() }) {
                        Icon(Icons.AutoMirrored.Rounded.ArrowBack, contentDescription = stringResource(R.string.common_back))
                    }
                },
                actions = {
                    TextButton(onClick = {
                        saveCurrent()
                        android.widget.Toast.makeText(context, "已保存", android.widget.Toast.LENGTH_SHORT).show()
                    }) {
                        Text("保存")
                    }
                    if (initialProvider != null) {
                        IconButton(onClick = { showDeleteConfirm = true }) {
                            Icon(
                                Icons.Rounded.Delete,
                                contentDescription = stringResource(R.string.provider_delete),
                                tint = MaterialTheme.colorScheme.error
                            )
                        }
                    }
                    // B8：移除顶栏「添加模型」按钮——模型 Tab 内已有「拉取模型」+「手动输入」入口，无需重复
                }
            )
        },
        bottomBar = {
            NavigationBar(
                containerColor = MaterialTheme.colorScheme.background,
                tonalElevation = 0.dp
            ) {
                NavigationBarItem(
                    icon = { Icon(Icons.Rounded.Tune, contentDescription = stringResource(R.string.provider_config)) },
                    label = { Text(stringResource(R.string.provider_config)) },
                    selected = selectedTab == 0,
                    onClick = { selectedTab = 0 }
                )
                NavigationBarItem(
                    icon = { Icon(Icons.Rounded.Memory, contentDescription = stringResource(R.string.common_model)) },
                    label = { Text(stringResource(R.string.common_model)) },
                    selected = selectedTab == 1,
                    onClick = { selectedTab = 1 }
                )
            }
        }
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
        ) {
            if (selectedTab == 0) {
                Column(
                    modifier = Modifier
                        .fillMaxSize()
                        .verticalScroll(rememberScrollState())
                        .padding(Spacing.lg),
                    verticalArrangement = Arrangement.spacedBy(Spacing.sm)
                ) {
                    // ── Section 1：基本信息 ──
                    Text(
                        text = "基本信息",
                        style = MaterialTheme.typography.titleSmall,
                        fontWeight = FontWeight.SemiBold,
                        color = MaterialTheme.colorScheme.onSurface,
                        modifier = Modifier.padding(top = Spacing.xs)
                    )
                    HorizontalDivider()
                    OutlinedTextField(
                        value = name,
                        onValueChange = { name = it },
                        label = { Text(stringResource(R.string.common_name)) },
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth()
                    )

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Text(stringResource(R.string.common_enabled))
                        Switch(
                            checked = isEnabled,
                            onCheckedChange = { isEnabled = it }
                        )
                    }

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(Spacing.sm)
                    ) {
                        FilterChip(
                            selected = type == ProviderType.OPENAI,
                            onClick = {
                                val old = type
                                if (baseUrl == defaultProviderBaseUrl(old) || baseUrl.isBlank()) {
                                    baseUrl = defaultProviderBaseUrl(ProviderType.OPENAI)
                                }
                                type = ProviderType.OPENAI
                                // B1：类型切换时若开启了自定义 API Path，静默同步为新类型默认路径，避免请求 404
                                if (useCustomApiPath) apiPath = defaultProviderApiPath(ProviderType.OPENAI)
                            },
                            label = { Text("OpenAI 兼容") }
                        )
                        FilterChip(
                            selected = type == ProviderType.ANTHROPIC,
                            onClick = {
                                val old = type
                                if (baseUrl == defaultProviderBaseUrl(old) || baseUrl.isBlank()) {
                                    baseUrl = defaultProviderBaseUrl(ProviderType.ANTHROPIC)
                                }
                                type = ProviderType.ANTHROPIC
                                if (useCustomApiPath) apiPath = defaultProviderApiPath(ProviderType.ANTHROPIC)
                            },
                            label = { Text("Anthropic 兼容") }
                        )
                        FilterChip(
                            selected = type == ProviderType.GEMINI,
                            onClick = {
                                val old = type
                                if (baseUrl == defaultProviderBaseUrl(old) || baseUrl.isBlank()) {
                                    baseUrl = defaultProviderBaseUrl(ProviderType.GEMINI)
                                }
                                type = ProviderType.GEMINI
                                if (useCustomApiPath) apiPath = defaultProviderApiPath(ProviderType.GEMINI)
                            },
                            label = { Text("Gemini") }
                        )
                    }

                    // ── Section 2：连接配置 ──
                    Text(
                        text = "连接配置",
                        style = MaterialTheme.typography.titleSmall,
                        fontWeight = FontWeight.SemiBold,
                        color = MaterialTheme.colorScheme.onSurface,
                        modifier = Modifier.padding(top = Spacing.sm)
                    )
                    HorizontalDivider()
                    val baseUrlError = baseUrl.isNotBlank() && !baseUrl.startsWith("http://") && !baseUrl.startsWith("https://")
                    OutlinedTextField(
                        value = apiKey,
                        onValueChange = { apiKey = it },
                        label = { Text("API Key") },
                        singleLine = true,
                        visualTransformation = if (showApiKey) VisualTransformation.None else PasswordVisualTransformation(),
                        trailingIcon = {
                            Row {
                                // B5：眼睛（显隐）在前，粘贴在后
                                IconButton(onClick = { showApiKey = !showApiKey }) {
                                    Icon(
                                        if (showApiKey) Icons.Rounded.Visibility else Icons.Rounded.VisibilityOff,
                                        contentDescription = null
                                    )
                                }
                                IconButton(onClick = {
                                    val cm = context.getSystemService(android.content.Context.CLIPBOARD_SERVICE) as? android.content.ClipboardManager
                                    val pasted = cm?.primaryClip?.takeIf { it.itemCount > 0 }?.getItemAt(0)?.text?.toString()
                                    if (!pasted.isNullOrBlank()) apiKey = pasted.trim()
                                }) {
                                    Icon(Icons.Rounded.ContentPaste, contentDescription = "粘贴")
                                }
                            }
                        },
                        modifier = Modifier.fillMaxWidth()
                    )

                    OutlinedTextField(
                        value = baseUrl,
                        onValueChange = { baseUrl = it },
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

                    // ── 测试连接按钮 + 内联结果（放在 Base URL 下方）──
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        OutlinedButton(
                            onClick = { viewModel.testProviderConnection(currentConfig()) },
                            enabled = connectionTestState !is ConnectionTestState.Loading
                        ) {
                            Icon(Icons.Rounded.Refresh, contentDescription = null, modifier = Modifier.size(16.dp))
                            Spacer(Modifier.width(Spacing.xs))
                            Text("测试连接")
                        }
                        Spacer(Modifier.width(Spacing.sm))
                        when (val st = connectionTestState) {
                            is ConnectionTestState.Loading -> CircularProgressIndicator(modifier = Modifier.size(18.dp), strokeWidth = 2.dp)
                            is ConnectionTestState.Success -> Row(verticalAlignment = Alignment.CenterVertically) {
                                Icon(Icons.Rounded.Check, contentDescription = null, tint = MaterialTheme.colorScheme.tertiary, modifier = Modifier.size(16.dp))
                                Spacer(Modifier.width(Spacing.xs))
                                Text(st.message, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.tertiary)
                            }
                            is ConnectionTestState.Error -> Row(verticalAlignment = Alignment.CenterVertically) {
                                Icon(Icons.Rounded.Warning, contentDescription = null, tint = MaterialTheme.colorScheme.error, modifier = Modifier.size(16.dp))
                                Spacer(Modifier.width(Spacing.xs))
                                Text(st.message.semanticConnectionError(), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.error)
                            }
                            ConnectionTestState.Idle -> {}
                        }
                    }

                    // ── API Path 开关模式：默认关闭自动补充，打开后手动填写且不能为空 ──
                    // B7：Full URL 模式下 API Path 由 Base URL 决定，开关禁用
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Column(modifier = Modifier.weight(1f)) {
                            Text("自定义 API Path", style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.Medium)
                            Text(
                                when {
                                    useFullUrl -> "Full URL 模式下 Path 由 Base URL 决定"
                                    useCustomApiPath -> "手动填写请求路径，不能为空"
                                    else -> "自动使用默认路径：${defaultProviderApiPath(type)}"
                                },
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                        Switch(
                            checked = useCustomApiPath,
                            enabled = !useFullUrl,
                            onCheckedChange = {
                                useCustomApiPath = it
                                if (!it) apiPathError = false
                            }
                        )
                    }
                    if (useCustomApiPath && !useFullUrl) {
                        Spacer(Modifier.height(Spacing.xs))
                        OutlinedTextField(
                            value = apiPath,
                            onValueChange = {
                                apiPath = it
                                apiPathError = it.isBlank()
                            },
                            label = { Text("API Path") },
                            placeholder = { Text(defaultProviderApiPath(type)) },
                            singleLine = true,
                            isError = apiPathError,
                            supportingText = {
                                if (apiPathError) {
                                    Text("API Path 不能为空", color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodySmall)
                                }
                            },
                            modifier = Modifier.fillMaxWidth()
                        )
                    }

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Column {
                            Text(stringResource(R.string.provider_full_url))
                            Text(
                                if (useFullUrl) stringResource(R.string.provider_full_url_on_desc) else stringResource(R.string.provider_full_url_off_desc),
                                style = androidx.compose.material3.MaterialTheme.typography.bodySmall,
                                color = androidx.compose.material3.MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                        Switch(
                            checked = useFullUrl,
                            onCheckedChange = {
                                useFullUrl = it
                                // B7：开启 Full URL 时关闭自定义 Path，避免两个开关语义冲突
                                if (it) {
                                    useCustomApiPath = false
                                    apiPathError = false
                                }
                            }
                        )
                    }

                    // ── Section 3：高级参数折叠区（Response API + 能力判定策略 + 采样/超时/备用） ──
                    HorizontalDivider(modifier = Modifier.padding(vertical = Spacing.sm))
                    var advancedExpanded by remember { mutableStateOf(false) }
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable { advancedExpanded = !advancedExpanded },
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            "高级参数",
                            style = MaterialTheme.typography.titleSmall,
                            fontWeight = FontWeight.SemiBold,
                            modifier = Modifier.weight(1f)
                        )
                        Icon(
                            if (advancedExpanded) Icons.Outlined.KeyboardArrowDown else Icons.AutoMirrored.Outlined.KeyboardArrowRight,
                            contentDescription = null
                        )
                    }
                    if (advancedExpanded) {
                        Column(verticalArrangement = Arrangement.spacedBy(Spacing.sm)) {
                            // ── Response API（仅 OpenAI）——从连接配置区移入高级参数 ──
                            if (type == ProviderType.OPENAI) {
                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.SpaceBetween
                                ) {
                                    Text(stringResource(R.string.provider_response_api))
                                    Switch(
                                        checked = useResponseApi,
                                        onCheckedChange = { useResponseApi = it }
                                    )
                                }
                            }

                            // ── 能力判定策略子组（原独立区块移入高级参数） ──
                            Text(
                                text = "能力判定策略",
                                style = MaterialTheme.typography.titleSmall,
                                fontWeight = FontWeight.SemiBold,
                                color = MaterialTheme.colorScheme.onSurface,
                                modifier = Modifier.padding(top = Spacing.xs)
                            )
                            CompatibilityPolicyDropdown(
                                currentPolicy = defaultPolicy,
                                onPolicySelected = { viewModel.setCompatibilityDefaultPolicy(it) }
                            )
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.SpaceBetween
                            ) {
                                Column(modifier = Modifier.weight(1f)) {
                                    Text("发送失败自动降级（识图兜底）", style = MaterialTheme.typography.bodyMedium)
                                    Text(
                                        "若当前聊天模型返回「不支持 image_url」，自动用识图模型生成文字摘要再重试。关闭后遇到此类错误将直接抛给用户（用于排查）。",
                                        style = MaterialTheme.typography.bodySmall,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant
                                    )
                                }
                                Switch(
                                    checked = autoDowngrade,
                                    onCheckedChange = { viewModel.setAutoDowngradeOnSendFailure(it) }
                                )
                            }
                            ViewImageGuardDropdown(
                                current = viewImageGuard,
                                onChange = { viewModel.setViewImageUnknownGuardPolicy(it) }
                            )

                            HorizontalDivider(modifier = Modifier.padding(vertical = Spacing.xs))

                            // ── Temperature ──
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Column(modifier = Modifier.weight(1f)) {
                                    Text("Temperature（温度）")
                                    Text("默认值，模型可单独覆盖", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                                }
                                Text("%.1f".format(temperature), color = MaterialTheme.colorScheme.onSurfaceVariant)
                            }
                            Slider(
                                value = temperature,
                                onValueChange = { temperature = (it * 10).toInt() / 10f },
                                valueRange = type.temperatureRange()
                            )
                            Text(
                                "越高越随机，越低越确定（范围 0-${if (type == ProviderType.ANTHROPIC) "1" else "2"}）",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )

                            // ── Top P ──
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Column(modifier = Modifier.weight(1f)) {
                                    Text("Top P（核采样）")
                                    Text("默认值，模型可单独覆盖", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                                }
                                Text("%.1f".format(topP), color = MaterialTheme.colorScheme.onSurfaceVariant)
                            }
                            Slider(
                                value = topP,
                                onValueChange = { topP = (it * 10).toInt() / 10f },
                                valueRange = 0f..1f
                            )
                            Text(
                                "核采样，与温度二选一",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )

                            // ── Max Tokens ──
                            OutlinedTextField(
                                value = maxTokensText,
                                onValueChange = { maxTokensText = it.filter { c -> c.isDigit() } },
                                label = { Text("Max Tokens（最大输出）") },
                                singleLine = true,
                                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                                supportingText = { Text("默认值，模型可单独覆盖；最大输出 token 数，留空=不限制", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant) },
                                modifier = Modifier.fillMaxWidth()
                            )

                            // ── 请求超时 ──
                            OutlinedTextField(
                                value = requestTimeoutText,
                                onValueChange = { requestTimeoutText = it.filter { c -> c.isDigit() } },
                                label = { Text("请求超时（秒）") },
                                singleLine = true,
                                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                                supportingText = { Text("请求超时时间（秒），范围 5-120", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant) },
                                modifier = Modifier.fillMaxWidth()
                            )

                            // ── 重试次数 ──
                            OutlinedTextField(
                                value = retryCountText,
                                onValueChange = { retryCountText = it.filter { c -> c.isDigit() } },
                                label = { Text("重试次数") },
                                singleLine = true,
                                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                                supportingText = { Text("失败时自动重试次数，范围 0-5", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant) },
                                modifier = Modifier.fillMaxWidth()
                            )

                            // ── 备用供应商下拉 ──
                            FallbackProviderDropdown(
                                selectedId = fallbackProviderId,
                                providers = allProviders.filter { it.id != providerId && it.isEnabled },
                                onSelect = { fallbackProviderId = it }
                            )
                        }
                    }
                }
            } else {
                Column(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(Spacing.lg),
                    verticalArrangement = Arrangement.spacedBy(Spacing.sm)
                ) {
                    // ── 模型管理区 ──
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            stringResource(R.string.provider_models_count, models.size),
                            style = MaterialTheme.typography.titleSmall,
                            modifier = Modifier.weight(1f)
                        )
                        TextButton(
                            onClick = {
                                fetchDialogKey++
                                showFetchDialog = true
                            }
                        ) {
                            Icon(Icons.Rounded.CloudDownload, contentDescription = null, modifier = Modifier.size(18.dp))
                            Spacer(Modifier.width(Spacing.xs))
                            Text(stringResource(R.string.provider_fetch_models))
                        }
                    }

                    // 已添加模型列表（收藏置顶：先按 localFavorites 顺序，其余按原顺序追加）
                    val favoriteSet = localFavorites.toSet()
                    val sortedModels = remember(models.toList(), favoriteSet) {
                        val favOrdered = localFavorites.filter { it in models }
                        val rest = models.filter { it !in favoriteSet }
                        favOrdered + rest
                    }
                    if (models.isEmpty()) {
                        // B4：空状态引导——图标 + 文案 + 两个入口
                        Column(
                            modifier = Modifier
                                .weight(1f)
                                .fillMaxWidth(),
                            horizontalAlignment = Alignment.CenterHorizontally,
                            verticalArrangement = Arrangement.Center
                        ) {
                            Icon(
                                Icons.Rounded.Extension,
                                contentDescription = null,
                                modifier = Modifier.size(48.dp),
                                tint = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                            Spacer(Modifier.height(Spacing.sm))
                            Text(
                                "还没有添加模型",
                                style = MaterialTheme.typography.bodyLarge,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                            Spacer(Modifier.height(Spacing.md))
                            Column(
                                verticalArrangement = Arrangement.spacedBy(Spacing.sm),
                                horizontalAlignment = Alignment.CenterHorizontally
                            ) {
                                Button(onClick = {
                                    fetchDialogKey++
                                    showFetchDialog = true
                                }) {
                                    Icon(Icons.Rounded.CloudDownload, contentDescription = null, modifier = Modifier.size(18.dp))
                                    Spacer(Modifier.width(Spacing.xs))
                                    Text("从服务商拉取")
                                }
                                OutlinedButton(onClick = { showAddModelSheet = true }) {
                                    Icon(Icons.Rounded.Add, contentDescription = null, modifier = Modifier.size(18.dp))
                                    Spacer(Modifier.width(Spacing.xs))
                                    Text("手动输入")
                                }
                            }
                        }
                    } else {
                    Column(
                        modifier = Modifier
                            .weight(1f)
                            .verticalScroll(rememberScrollState()),
                        verticalArrangement = Arrangement.spacedBy(Spacing.sm)
                    ) {
                        sortedModels.forEach { model ->
                            ProviderModelRow(
                                model = model,
                                metadata = modelMetadata[model],
                                hasOverride = overridesMap.containsKey(model),
                                testing = model in testing,
                                result = testResults[model],
                                onTest = { viewModel.testModel(currentConfig(), model) },
                                onRemove = {
                                    models.remove(model)
                                    localFavorites = localFavorites - model
                                    saveCurrent()
                                },
                                onOpenCapabilityOverride = { capabilityOverrideModel = model },
                                isFavorite = model in favoriteSet,
                                onToggleFavorite = {
                                    localFavorites = if (model in localFavorites) localFavorites - model else localFavorites + model
                                    // B2：新建模式下 providerId 是临时 UUID，DB 中尚无记录，直接调 ViewModel 会静默失败/空指针。
                                    // 仅编辑已有供应商时才持久化；新建模式保存时由 Repository 统一写入 favoriteModels。
                                    if (initialProvider != null) {
                                        viewModel.toggleFavoriteModel(providerId, model)
                                    }
                                }
                            )
                        }
                    }
                    }
                }
            }
        }
    }

    if (showAddModelSheet) {
        AddModelSheet(
            existingModels = models,
            onAddModel = { model ->
                if (model !in models) {
                    models.add(model)
                    saveCurrent()
                }
            },
            onDismiss = { showAddModelSheet = false }
        )
    }

    // 模型设置底部面板：点击齿轮按钮时打开（能力覆盖 + 上下文长度自定义）。
    val overrideModel = capabilityOverrideModel
    if (overrideModel != null) {
        ModelSettingsSheet(
            viewModel = viewModel,
            providerType = type,
            modelId = overrideModel,
            metadata = modelMetadata[overrideModel],
            overrideFlow = viewModel.observeCapabilityOverride(type, overrideModel),
            customConfigFlow = viewModel.observeModelCustomConfig(type, overrideModel),
            samplingConfigFlow = viewModel.observeModelSamplingConfig(type, overrideModel),
            providerDefaultTemperature = currentConfig().temperature,
            providerDefaultTopP = currentConfig().topP,
            providerDefaultMaxTokens = currentConfig().maxTokens,
            onDismiss = { capabilityOverrideModel = null }
        )
    }

    // 模型拉取结果弹窗
    if (showFetchDialog) {
        key(fetchDialogKey) {
            FetchModelsDialog(
                fetchState = fetchState,
                modelMetadata = modelMetadata,
                existingModels = models,
                onFetchModels = { viewModel.fetchModels(currentConfig()) },
                onAddModel = { m ->
                    if (m !in models) {
                        models.add(m)
                        saveCurrent()
                    }
                },
                onDismiss = {
                    showFetchDialog = false
                    viewModel.resetFetchState()
                }
            )
        }
    }

    // 删除确认对话框
    if (showDeleteConfirm && initialProvider != null) {
        AppDialog(
            title = "删除供应商",
            message = "删除后 API Key、模型列表等配置将全部丢失，不可恢复。确认删除？",
            type = AppDialogType.Destructive,
            confirmText = "删除",
            onDismiss = { showDeleteConfirm = false },
            onConfirm = { onDelete(initialProvider.id) },
        )
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun AddModelSheet(
    existingModels: List<String>,
    onAddModel: (String) -> Unit,
    onDismiss: () -> Unit
) {
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    var modelName by remember { mutableStateOf("") }
    val trimmedModel = modelName.trim()
    val duplicate = existingModels.any { it == trimmedModel }
    val canAdd = trimmedModel.isNotEmpty() && !duplicate

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = sheetState,
        containerColor = MaterialTheme.colorScheme.surface,
        dragHandle = null
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(Spacing.lg)
                .padding(bottom = Spacing.md),
            verticalArrangement = Arrangement.spacedBy(Spacing.md)
        ) {
            Text(
                text = stringResource(R.string.provider_add_model),
                style = MaterialTheme.typography.titleMedium,
                color = MaterialTheme.colorScheme.onSurface
            )
            OutlinedTextField(
                value = modelName,
                onValueChange = { modelName = it },
                label = { Text(stringResource(R.string.provider_model_name)) },
                placeholder = { Text(stringResource(R.string.provider_model_name_hint)) },
                singleLine = true,
                isError = duplicate,
                modifier = Modifier.fillMaxWidth()
            )
            if (duplicate) {
                Text(
                    text = stringResource(R.string.provider_model_already_added),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.error
                )
            }
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.End,
                verticalAlignment = Alignment.CenterVertically
            ) {
                TextButton(onClick = onDismiss) {
                    Text(stringResource(R.string.common_cancel))
                }
                TextButton(
                    enabled = canAdd,
                    onClick = {
                        onAddModel(trimmedModel)
                        onDismiss()
                    }
                ) {
                    Icon(Icons.Rounded.Add, contentDescription = null, modifier = Modifier.size(18.dp))
                    Spacer(Modifier.width(Spacing.xs))
                    Text(stringResource(R.string.common_add))
                }
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class)
@Composable
private fun FetchModelsDialog(
    fetchState: FetchState,
    modelMetadata: Map<String, ModelMetadata>,
    existingModels: List<String>,
    onFetchModels: () -> Unit,
    onAddModel: (String) -> Unit,
    onDismiss: () -> Unit
) {
    val context = LocalContext.current
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    var searchQuery by remember { mutableStateOf("") }
    val collapsedBrands = remember { mutableStateMapOf<String, Boolean>() }
    
    LaunchedEffect(Unit) {
        // Wait for bottom sheet animation to smooth out before firing network request
        delay(300)
        onFetchModels()
    }
    
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
                    OutlinedTextField(
                        value = searchQuery,
                        onValueChange = { searchQuery = it },
                        placeholder = { Text(stringResource(R.string.provider_filter_models_hint)) },
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth(),
                        shape = RoundedCornerShape(50)
                    )

                    Box(modifier = Modifier.fillMaxWidth().weight(1f)) {
                        when (fetchState) {
                            is FetchState.Loading -> {
                                Row(
                                    modifier = Modifier.fillMaxSize(),
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.Center
                                ) {
                                    CircularProgressIndicator(modifier = Modifier.size(24.dp), strokeWidth = 2.dp)
                                    Spacer(Modifier.width(Spacing.md))
                                    Text(stringResource(R.string.provider_fetching), style = MaterialTheme.typography.bodyMedium)
                                }
                            }
                            is FetchState.Error -> {
                                Text(
                                    stringResource(R.string.provider_fetch_failed, fetchState.message),
                                    style = MaterialTheme.typography.bodyMedium,
                                    color = MaterialTheme.colorScheme.error
                                )
                            }
                            is FetchState.Success -> {
                                val newOnes = fetchState.models.filter { it !in existingModels && it.contains(searchQuery, ignoreCase = true) }
                                if (newOnes.isEmpty()) {
                                    Text(stringResource(R.string.provider_no_matching_models), style = MaterialTheme.typography.bodyMedium)
                                } else {
                                    // 按品牌分组，分类 header 可折叠。"other" 分组永远在最后，其他按显示名称排序。
                                    val grouped = newOnes.groupBy { m -> modelBrandKey(m) }
                                        .toSortedMap(compareBy<String> { it == "other" }.thenBy { brandDisplayName(context, it) })

                                    LazyColumn(
                                        modifier = Modifier.fillMaxSize(),
                                        verticalArrangement = Arrangement.spacedBy(Spacing.xs)
                                    ) {
                                        grouped.forEach { (brandKey, models) ->
                                            item(key = "header_$brandKey") {
                                                val expanded = collapsedBrands[brandKey] != true
                                                val brandName = brandDisplayName(context, brandKey)
                                                Row(
                                                    modifier = Modifier
                                                        .fillMaxWidth()
                                                        .heightIn(min = 44.dp)
                                                        .clickable { collapsedBrands[brandKey] = expanded }
                                                        .padding(horizontal = Spacing.xs, vertical = Spacing.sm),
                                                    verticalAlignment = Alignment.CenterVertically
                                                ) {
                                                    Text(
                                                        "$brandName (${models.size})",
                                                        style = MaterialTheme.typography.titleSmall,
                                                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                                                        maxLines = 1,
                                                        overflow = TextOverflow.Ellipsis,
                                                        modifier = Modifier.weight(1f)
                                                    )
                                                    Icon(
                                                        imageVector = if (expanded) Icons.Outlined.KeyboardArrowDown else Icons.AutoMirrored.Outlined.KeyboardArrowRight,
                                                        contentDescription = if (expanded) stringResource(R.string.provider_collapse_brand, brandName) else stringResource(R.string.provider_expand_brand, brandName),
                                                        tint = MaterialTheme.colorScheme.onSurfaceVariant,
                                                        modifier = Modifier.size(20.dp)
                                                    )
                                                }
                                            }
                                            if (collapsedBrands[brandKey] != true) {
                                                items(models, key = { "${brandKey}_$it" }) { m ->
                                                    FetchModelRow(
                                                        model = m,
                                                        metadata = modelMetadata[m],
                                                        onAdd = { onAddModel(m) }
                                                    )
                                                }
                                            }
                                        }
                                    }
                                }
                            }
                            else -> {
                                Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                                    Text(stringResource(R.string.provider_please_wait), style = MaterialTheme.typography.bodyMedium)
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}

internal fun defaultProviderBaseUrl(type: ProviderType): String = when (type) {
    ProviderType.ANTHROPIC -> "https://api.anthropic.com/"
    ProviderType.GEMINI -> "https://generativelanguage.googleapis.com/"
    else -> "https://api.openai.com/"
}

/**
 * B6：测试连接错误信息语义化。ViewModel 透传的是原始异常 message（可能是 "HTTP 401"、
 * "SocketTimeoutException" 等），这里在 UI 层做一层简单映射，让普通用户看得懂。
 * 已有明确信息（如非 HTTP 码）则原样返回。
 */
internal fun String.semanticConnectionError(): String {
    val lower = this.lowercase()
    return when {
        contains("401") -> "API Key 无效或已过期"
        contains("403") -> "API Key 无权限或被拒绝"
        contains("404") -> "Base URL 或 API Path 错误"
        contains("timeout") || lower.contains("sockettimeout") || lower.contains("connect") -> "网络不可达，请检查地址"
        contains("Unable to resolve host") || contains("UnknownHost") -> "无法解析域名，请检查 Base URL"
        else -> this
    }
}

// ————————————————————————————————————————————————————————————
// RC63 ③：兼容端点策略 & viewImage 守卫 两个下拉选择器
// ————————————————————————————————————————————————————————————

private fun policyDisplayName(p: DefaultPolicy): String = when (p) {
    DefaultPolicy.STRICT -> "严格模式（推荐·默认）—— 和官方收录模型走同一规则，避免 RC62e 那种「全部模型都支持多模态」的副作用"
    DefaultPolicy.HEURISTIC -> "启发式模式（=RC62d）—— 按 probablyVision / probablyTools / probablyReasoning 的名字匹配自动判定"
    DefaultPolicy.LAX -> "宽松模式—— 三能力默认全开，按名字启发式微调"
    DefaultPolicy.MANUAL -> "完全手动—— 三能力默认全关，必须逐个手动勾选"
}

private fun policyShortName(p: DefaultPolicy): String = when (p) {
    DefaultPolicy.STRICT -> "严格模式"
    DefaultPolicy.HEURISTIC -> "启发式模式"
    DefaultPolicy.LAX -> "宽松模式"
    DefaultPolicy.MANUAL -> "完全手动"
}

private fun viewImageGuardDisplayName(p: ViewImageUnknownGuardPolicy): String = when (p) {
    ViewImageUnknownGuardPolicy.FALLBACK_VISION_MODEL -> "自动用识图模型兜底（推荐）—— 聊天模型不能识图时，自动调用专用识图模型生成摘要再重试"
    ViewImageUnknownGuardPolicy.FAIL_FAST -> "直接报错—— 聊天模型不能识图时立即提示用户配置，不自动降级"
}

private fun viewImageGuardShortName(p: ViewImageUnknownGuardPolicy): String = when (p) {
    ViewImageUnknownGuardPolicy.FALLBACK_VISION_MODEL -> "自动兜底（推荐）"
    ViewImageUnknownGuardPolicy.FAIL_FAST -> "直接报错"
}

internal fun providerTypeLabel(t: ProviderType): String = when (t) {
    ProviderType.OPENAI -> "OpenAI 兼容"
    ProviderType.ANTHROPIC -> "Anthropic 兼容"
    ProviderType.GEMINI -> "Gemini"
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun CompatibilityPolicyDropdown(
    currentPolicy: DefaultPolicy,
    onPolicySelected: (DefaultPolicy) -> Unit
) {
    val allPolicies = DefaultPolicy.values()
    var expanded by remember { mutableStateOf(false) }
    ExposedDropdownMenuBox(
        expanded = expanded,
        onExpandedChange = { expanded = it }
    ) {
        OutlinedTextField(
            value = policyDisplayName(currentPolicy),
            onValueChange = { },
            readOnly = true,
            label = { Text(stringResource(R.string.ui______d0034676)) },
            trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded = expanded) },
            modifier = Modifier
                .fillMaxWidth()
                .menuAnchor(MenuAnchorType.PrimaryNotEditable)
        )
        ExposedDropdownMenu(
            expanded = expanded,
            onDismissRequest = { expanded = false }
        ) {
            allPolicies.forEach { p ->
                DropdownMenuItem(
                    text = {
                        Column {
                            Text(policyShortName(p), fontWeight = FontWeight.SemiBold)
                            Text(
                                policyDisplayName(p).substringAfter("—— ").trim(),
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    },
                    onClick = {
                        onPolicySelected(p)
                        expanded = false
                    }
                )
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun ViewImageGuardDropdown(
    current: ViewImageUnknownGuardPolicy,
    onChange: (ViewImageUnknownGuardPolicy) -> Unit
) {
    val all = ViewImageUnknownGuardPolicy.values()
    var expanded by remember { mutableStateOf(false) }
    ExposedDropdownMenuBox(
        expanded = expanded,
        onExpandedChange = { expanded = it }
    ) {
        OutlinedTextField(
            value = viewImageGuardDisplayName(current),
            onValueChange = { },
            readOnly = true,
            label = { Text(stringResource(R.string.ui_viewimage_1d14f381)) },
            trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded = expanded) },
            modifier = Modifier
                .fillMaxWidth()
                .menuAnchor(MenuAnchorType.PrimaryNotEditable)
        )
        ExposedDropdownMenu(
            expanded = expanded,
            onDismissRequest = { expanded = false }
        ) {
            all.forEach { g ->
                DropdownMenuItem(
                    text = {
                        Column {
                            Text(viewImageGuardShortName(g), fontWeight = FontWeight.SemiBold)
                            Text(
                                viewImageGuardDisplayName(g).substringAfter("—— ").trim(),
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    },
                    onClick = {
                        onChange(g)
                        expanded = false
                    }
                )
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun FallbackProviderDropdown(
    selectedId: String?,
    providers: List<AIProviderConfig>,
    onSelect: (String?) -> Unit
) {
    var expanded by remember { mutableStateOf(false) }
    val selectedName = providers.firstOrNull { it.id == selectedId }?.name ?: "无"
    ExposedDropdownMenuBox(
        expanded = expanded,
        onExpandedChange = { expanded = it }
    ) {
        OutlinedTextField(
            value = selectedName,
            onValueChange = { },
            readOnly = true,
            label = { Text("备用供应商") },
            trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded = expanded) },
            supportingText = {
                Text(
                    "当前供应商请求失败时自动切换到备用供应商",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            },
            modifier = Modifier
                .fillMaxWidth()
                .menuAnchor(MenuAnchorType.PrimaryNotEditable)
        )
        ExposedDropdownMenu(
            expanded = expanded,
            onDismissRequest = { expanded = false }
        ) {
            DropdownMenuItem(
                text = { Text("无") },
                onClick = {
                    onSelect(null)
                    expanded = false
                }
            )
            providers.forEach { p ->
                DropdownMenuItem(
                    text = { Text(p.name) },
                    onClick = {
                        onSelect(p.id)
                        expanded = false
                    }
                )
            }
        }
    }
}