// Compose 约定：@Composable 函数用大驼峰命名（与仓库既有页面一致，见 config/detekt/baseline.xml 中
// CapabilityCenterScreen / McpEditorDialog 等既有条目）。此处按文件级豁免 detekt 的
// FunctionNaming，而不是逐条塞进 baseline —— baseline 只会静默膨胀，且看不出豁免理由。
@file:Suppress("FunctionNaming")

package com.mini.me_core.feature.capability.presentation.component


import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListScope
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Description
import androidx.compose.material.icons.rounded.KeyboardArrowDown
import androidx.compose.material.icons.rounded.KeyboardArrowUp
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.mini.me_core.R
import com.mini.me_core.core.theme.Radius
import com.mini.me_core.core.theme.Spacing
import com.mini.me_core.core.theme.components.AppTextField
import com.mini.me_core.core.ui.rememberPersistentLazyListState
import com.mini.me_core.feature.agent.domain.execution.mcp.McpPromptArgument
import com.mini.me_core.feature.agent.domain.execution.mcp.McpPromptDescriptor
import com.mini.me_core.feature.agent.domain.execution.mcp.McpResourceDescriptor
import com.mini.me_core.feature.capability.domain.AssetBodyState
import com.mini.me_core.feature.capability.domain.AssetKind
import com.mini.me_core.feature.capability.domain.AssetListState
import com.mini.me_core.feature.capability.domain.CapabilityGroupCard
import com.mini.me_core.feature.capability.presentation.CapabilityGroupAssetsViewModel

/**
 * G4：分组详情的 prompts 页。
 *
 * 数据走 `prompts/list`。server 未声明该能力或此刻未连接时，这里显示的是**明确原因**
 * 而不是一片空白：空白会被读成「还在加载」，而真正的责任方在远端。
 *
 * 只在首次进入本页时枚举一次（见 [AssetListState.Idle] 分支），再次回来复用上轮结果；
 * 失败时用卡片上的「重试」强制刷新。
 */
@Composable
internal fun GroupPromptsTab(
    group: CapabilityGroupCard,
    viewModel: CapabilityGroupAssetsViewModel = hiltViewModel()
) {
    val state by viewModel.promptsOf(group.id).collectAsStateWithLifecycle()
    LaunchedEffect(group.id) { viewModel.loadPrompts(group.id) }

    AssetListHost(
        state = state,
        scrollKey = "prompts_${group.id}",
        emptyText = stringResource(R.string.capability_assets_empty_prompts),
        unsupportedText = stringResource(R.string.capability_assets_unsupported_prompts),
        onRetry = { viewModel.loadPrompts(group.id, force = true) }
    ) { items ->
        items(items, key = { it.name }) { prompt ->
            PromptItem(serverName = group.id, prompt = prompt, viewModel = viewModel)
        }
    }
}

/** G4：分组详情的 resources 页。语义与 [GroupPromptsTab] 平行，主键换成 URI。 */
@Composable
internal fun GroupResourcesTab(
    group: CapabilityGroupCard,
    viewModel: CapabilityGroupAssetsViewModel = hiltViewModel()
) {
    val state by viewModel.resourcesOf(group.id).collectAsStateWithLifecycle()
    LaunchedEffect(group.id) { viewModel.loadResources(group.id) }

    AssetListHost(
        state = state,
        scrollKey = "resources_${group.id}",
        emptyText = stringResource(R.string.capability_assets_empty_resources),
        unsupportedText = stringResource(R.string.capability_assets_unsupported_resources),
        onRetry = { viewModel.loadResources(group.id, force = true) }
    ) { items ->
        items(items, key = { it.uri }) { resource ->
            ResourceItem(serverName = group.id, resource = resource, viewModel = viewModel)
        }
    }
}

/**
 * 两个资产页共用的列表骨架。
 *
 * 抽出来是因为「五态各自渲染什么」是一条不变量：三个非 [AssetListState.Ready] 态的处理
 * 在两页必须完全一致，分开写迟早会出现「prompts 页给了重试按钮、resources 页没有」。
 *
 * @param scrollKey 滚动位置保存键，**必须按资产类型区分**：两页共用 group.id 会让
 *   切换 Tab 时滚动位置互相串。
 * @param content 仅在 [AssetListState.Ready] 且列表非空时调用的列表内容。
 */
@Composable
private fun <T> AssetListHost(
    state: AssetListState<T>,
    scrollKey: String,
    emptyText: String,
    unsupportedText: String,
    onRetry: () -> Unit,
    content: LazyListScope.(items: List<T>) -> Unit
) {
    when (state) {
        AssetListState.Idle,
        AssetListState.Loading -> AssetMessageCard(stringResource(R.string.capability_assets_loading))
        AssetListState.Unsupported -> AssetMessageCard(unsupportedText)
        is AssetListState.Failed -> AssetMessageCard(
            text = stringResource(R.string.capability_assets_failed, state.reason),
            actionLabel = stringResource(R.string.capability_assets_retry),
            onAction = onRetry
        )
        is AssetListState.Ready -> {
            if (state.items.isEmpty()) {
                AssetMessageCard(emptyText)
            } else {
                val listState = rememberPersistentLazyListState(scrollKey)
                LazyColumn(
                    state = listState,
                    modifier = Modifier.fillMaxSize(),
                    contentPadding = PaddingValues(
                        start = Spacing.lg,
                        end = Spacing.lg,
                        bottom = Spacing.xxl
                    ),
                    verticalArrangement = Arrangement.spacedBy(Spacing.sm)
                ) {
                    content(state.items)
                }
            }
        }
    }
}

/** 资产页的整幅说明卡：无列表时撑起内容区，避免出现「只有一行字却吊在页面顶部」的观感。 */
@Composable
private fun AssetMessageCard(
    text: String,
    actionLabel: String? = null,
    onAction: (() -> Unit)? = null
) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(horizontal = Spacing.lg, vertical = Spacing.xl)
    ) {
        Text(
            text = text,
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
        if (actionLabel != null && onAction != null) {
            Spacer(Modifier.height(Spacing.sm))
            TextButton(onClick = onAction) {
                Text(text = actionLabel, fontWeight = FontWeight.Medium)
            }
        }
    }
}

/**
 * 单条 prompt。
 *
 * 无参数的 prompt 展开即自动取正文——用户点开就是想看内容，再让他点一次「获取」是多余的
 * 一步；**有参数的必须等填完再取**，否则 server 只会回「缺必填参数」，在 UI 上表现为
 * 莫名其妙的报错。
 */
@Composable
private fun PromptItem(
    serverName: String,
    prompt: McpPromptDescriptor,
    viewModel: CapabilityGroupAssetsViewModel
) {
    var expanded by rememberSaveable(prompt.name) { mutableStateOf(false) }
    val arguments = remember(prompt.name) { mutableStateMapOf<String, String>() }
    val body by viewModel.bodyOf(AssetKind.PROMPT, serverName, prompt.name)
        .collectAsStateWithLifecycle()
    val noArgs = prompt.arguments.isEmpty()

    LaunchedEffect(expanded, noArgs) {
        if (expanded && noArgs) {
            viewModel.fetchBody(AssetKind.PROMPT, serverName, prompt.name, emptyMap())
        }
    }

    AssetCard {
        Column(modifier = Modifier.padding(Spacing.lg)) {
            AssetHeader(
                title = prompt.name,
                subtitle = prompt.description ?: prompt.title,
                expanded = expanded,
                onToggle = { expanded = !expanded }
            ) {
                if (prompt.arguments.isNotEmpty()) {
                    Pill(
                        text = stringResource(
                            R.string.capability_asset_args_count,
                            prompt.arguments.size
                        ),
                        tint = MaterialTheme.colorScheme.tertiary
                    )
                }
            }
            if (expanded) {
                Spacer(Modifier.height(Spacing.sm))
                if (!noArgs) {
                    PromptArguments(
                        arguments = prompt.arguments,
                        values = arguments,
                        onValueChange = { arguments[it.first] = it.second }
                    )
                    Spacer(Modifier.height(Spacing.sm))
                    TextButton(
                        onClick = {
                            viewModel.fetchBody(
                                AssetKind.PROMPT,
                                serverName,
                                prompt.name,
                                arguments.toMap()
                            )
                        }
                    ) {
                        Text(text = stringResource(R.string.capability_asset_fetch_prompt))
                    }
                }
                AssetBodyView(state = body)
            }
        }
    }
}

/** 单条 resource。展开即读正文——resource 无参可取，不存在「先填再读」的情形。 */
@Composable
private fun ResourceItem(
    serverName: String,
    resource: McpResourceDescriptor,
    viewModel: CapabilityGroupAssetsViewModel
) {
    var expanded by rememberSaveable(resource.uri) { mutableStateOf(false) }
    val body by viewModel.bodyOf(AssetKind.RESOURCE, serverName, resource.uri)
        .collectAsStateWithLifecycle()

    LaunchedEffect(expanded) {
        if (expanded) {
            viewModel.fetchBody(AssetKind.RESOURCE, serverName, resource.uri)
        }
    }

    AssetCard {
        Column(modifier = Modifier.padding(Spacing.lg)) {
            AssetHeader(
                title = resource.title ?: resource.name ?: resource.uri,
                subtitle = resource.description,
                expanded = expanded,
                onToggle = { expanded = !expanded }
            ) {
                resource.mimeType?.let { mime ->
                    Pill(
                        text = stringResource(R.string.capability_asset_mime, mime),
                        tint = MaterialTheme.colorScheme.tertiary
                    )
                }
            }
            Spacer(Modifier.height(Spacing.xs))
            Text(
                text = resource.uri,
                style = MaterialTheme.typography.bodySmall,
                fontFamily = FontFamily.Monospace,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            if (expanded) {
                Spacer(Modifier.height(Spacing.sm))
                AssetBodyView(state = body)
            }
        }
    }
}

/** prompt 的参数输入区。必填参数明确标注，省得用户为「到底缺哪个」反复试错。 */
@Composable
private fun PromptArguments(
    arguments: List<McpPromptArgument>,
    values: Map<String, String>,
    onValueChange: (Pair<String, String>) -> Unit
) {
    Column(verticalArrangement = Arrangement.spacedBy(Spacing.sm)) {
        arguments.forEach { arg ->
            val required = arg.required == true
            AppTextField(
                value = values[arg.name].orEmpty(),
                onValueChange = { onValueChange(arg.name to it) },
                label = {
                    Text(
                        text = arg.name + " · " + stringResource(
                            if (required) {
                                R.string.capability_asset_arg_required
                            } else {
                                R.string.capability_asset_arg_optional
                            }
                        ),
                        style = MaterialTheme.typography.bodySmall
                    )
                },
                singleLine = false
            )
        }
    }
}

/**
 * 正文渲染。
 *
 * [AssetBodyState.Failed] 必须把 server 的原文显示出来：`prompts/get` 最常见的失败是
 * 缺必填参数，而那段报错是唯一能指出缺哪个参数的信息——换成「读取失败，请重试」
 * 等于让用户盲猜。
 */
@Composable
private fun AssetBodyView(state: AssetBodyState) {
    val colors = MaterialTheme.colorScheme
    Text(
        text = stringResource(R.string.capability_asset_body_label),
        style = MaterialTheme.typography.labelMedium,
        color = colors.onSurfaceVariant
    )
    Spacer(Modifier.height(Spacing.xs))
    when (state) {
        AssetBodyState.Idle,
        AssetBodyState.Loading -> Text(
            text = stringResource(R.string.capability_assets_loading),
            style = MaterialTheme.typography.bodyMedium,
            color = colors.onSurfaceVariant
        )
        is AssetBodyState.Failed -> Text(
            text = stringResource(R.string.capability_assets_failed, state.reason),
            style = MaterialTheme.typography.bodyMedium,
            color = colors.error
        )
        is AssetBodyState.Ready -> if (state.text.isEmpty()) {
            Text(
                text = stringResource(R.string.capability_asset_body_empty),
                style = MaterialTheme.typography.bodyMedium,
                color = colors.onSurfaceVariant
            )
        } else {
            Text(
                text = state.text,
                style = MaterialTheme.typography.bodyMedium,
                fontFamily = FontFamily.Monospace,
                color = colors.onSurface
            )
        }
    }
}

/** 资产条目的统一外壳（圆角、底色、内边距），与统一能力页的分组卡保持同一观感。 */
@Composable
private fun AssetCard(content: @Composable () -> Unit) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(Radius.lg),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)
    ) { content() }
}

/**
 * 资产条目的标题行：名称 + 副标题 + 可选徽章 + 折叠箭头，整行可点。
 *
 * 徽章用尾随 lambda 而非普通参数，因为它允许为空——没有徽章时不传即可，
 * 调用侧读起来比传一个空 lambda 干净。
 */
@Composable
private fun AssetHeader(
    title: String,
    subtitle: String?,
    expanded: Boolean,
    onToggle: () -> Unit,
    trailing: @Composable () -> Unit
) {
    val colors = MaterialTheme.colorScheme
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onToggle),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Icon(
            imageVector = Icons.Rounded.Description,
            contentDescription = null,
            tint = colors.primary,
            modifier = Modifier.size(20.dp)
        )
        Spacer(Modifier.width(Spacing.sm))
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = title,
                style = MaterialTheme.typography.titleSmall,
                color = colors.onSurface
            )
            Text(
                text = subtitle?.takeIf { it.isNotBlank() }
                    ?: stringResource(R.string.capability_asset_no_description),
                style = MaterialTheme.typography.bodySmall,
                color = colors.onSurfaceVariant
            )
        }
        trailing()
        Spacer(Modifier.width(Spacing.xs))
        Icon(
            imageVector = if (expanded) Icons.Rounded.KeyboardArrowUp else Icons.Rounded.KeyboardArrowDown,
            contentDescription = stringResource(
                if (expanded) R.string.capability_asset_collapse else R.string.capability_asset_expand
            ),
            tint = colors.onSurfaceVariant
        )
    }
}
