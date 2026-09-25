package com.mini.me_core.feature.settings.presentation.component
import com.mini.me_core.core.theme.tokens.LocalCornerRadius

import android.content.ClipData
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Add
import androidx.compose.material.icons.rounded.ArrowForward
import androidx.compose.material.icons.rounded.AutoAwesome
import androidx.compose.material.icons.rounded.Build
import androidx.compose.material.icons.rounded.Chat
import androidx.compose.material.icons.rounded.Check
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material.icons.rounded.Code
import androidx.compose.material.icons.rounded.ContentCopy
import androidx.compose.material.icons.rounded.Image
import androidx.compose.material.icons.rounded.Input
import androidx.compose.material.icons.rounded.ListAlt
import androidx.compose.material.icons.rounded.Mic
import androidx.compose.material.icons.rounded.MoreVert
import androidx.compose.material.icons.rounded.Movie
import androidx.compose.material.icons.rounded.Output
import androidx.compose.material.icons.rounded.PlayArrow
import androidx.compose.material.icons.rounded.Delete
import androidx.compose.material.icons.rounded.Refresh
import androidx.compose.material.icons.rounded.Settings
import androidx.compose.material.icons.rounded.Star
import androidx.compose.material.icons.rounded.StarBorder
import androidx.compose.material.icons.rounded.Warning
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Checkbox
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExposedDropdownMenuBox
import androidx.compose.material3.ExposedDropdownMenuDefaults
import androidx.compose.material3.FilterChip
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.MenuAnchorType
import com.mini.me_core.core.theme.components.AppBottomSheet
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Slider
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.ClipEntry
import androidx.compose.ui.platform.LocalClipboard
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Popup
import androidx.compose.ui.window.PopupProperties
import com.mini.me_core.R
import com.mini.me_core.core.theme.Radius
import com.mini.me_core.core.theme.Spacing
import com.mini.me_core.feature.agent.data.local.entity.ModelCapabilityOverrideEntity
import com.mini.me_core.feature.agent.data.local.entity.ModelCustomConfigEntity
import com.mini.me_core.feature.agent.data.local.entity.ModelSamplingConfigEntity
import com.mini.me_core.feature.settings.data.remote.ModelTestResult
import com.mini.me_core.feature.settings.domain.model.ModelMetadata
import com.mini.me_core.feature.settings.domain.model.ProviderType
import com.mini.me_core.feature.settings.domain.model.temperatureRange
import kotlinx.coroutines.launch

@Composable
@OptIn(ExperimentalLayoutApi::class)
internal fun ModelMetadataTags(metadata: ModelMetadata?, hasOverride: Boolean = false) {
    FlowRow(
        horizontalArrangement = Arrangement.spacedBy(6.dp),
        verticalArrangement = Arrangement.spacedBy(6.dp)
    ) {
        // Chat 标签（始终显示）：对话 文字→文字
        CapabilityFlowTag(
            inputIcon = Icons.Rounded.Chat,
            outputIcon = Icons.Rounded.Chat,
            tooltip = "对话：文字→文字",
            backgroundColor = MaterialTheme.colorScheme.surfaceVariant,
            contentColor = MaterialTheme.colorScheme.onSurfaceVariant
        )
        metadata?.let { m ->
            // 识图 图片→文字（蓝色）
            if (m.supportsVision) {
                CapabilityFlowTag(
                    inputIcon = Icons.Rounded.Image,
                    outputIcon = Icons.Rounded.Chat,
                    tooltip = "识图：图片→文字",
                    backgroundColor = MaterialTheme.colorScheme.primaryContainer,
                    contentColor = MaterialTheme.colorScheme.onPrimaryContainer,
                    overridden = hasOverride && m.inferenceReason?.overrideVision != null
                )
            }
            // 视频 视频→文字（蓝色）
            if (m.supportsVideo) {
                CapabilityFlowTag(
                    inputIcon = Icons.Rounded.Movie,
                    outputIcon = Icons.Rounded.Chat,
                    tooltip = "视频：视频→文字",
                    backgroundColor = MaterialTheme.colorScheme.primaryContainer,
                    contentColor = MaterialTheme.colorScheme.onPrimaryContainer
                )
            }
            // 语音 语音→文字（蓝色）
            if (m.supportsAudio) {
                CapabilityFlowTag(
                    inputIcon = Icons.Rounded.Mic,
                    outputIcon = Icons.Rounded.Chat,
                    tooltip = "语音：语音→文字",
                    backgroundColor = MaterialTheme.colorScheme.primaryContainer,
                    contentColor = MaterialTheme.colorScheme.onPrimaryContainer
                )
            }
            // 工具 文字→工具调用（绿色）
            if (m.supportsTools) {
                CapabilityFlowTag(
                    inputIcon = Icons.Rounded.Chat,
                    outputIcon = Icons.Rounded.Build,
                    tooltip = "工具：文字→工具调用",
                    backgroundColor = MaterialTheme.colorScheme.tertiaryContainer,
                    contentColor = MaterialTheme.colorScheme.onTertiaryContainer,
                    overridden = hasOverride && m.inferenceReason?.overrideTools != null
                )
            }
            // 思考 文字→推理链（紫色）
            if (m.supportsReasoning) {
                CapabilityFlowTag(
                    inputIcon = Icons.Rounded.Chat,
                    outputIcon = Icons.Rounded.AutoAwesome,
                    tooltip = "思考：文字→推理链",
                    backgroundColor = MaterialTheme.colorScheme.secondaryContainer,
                    contentColor = MaterialTheme.colorScheme.onSecondaryContainer,
                    overridden = hasOverride && m.inferenceReason?.overrideReasoning != null
                )
            }
            // 代码 文字→代码（灰色）
            if (m.supportsCode) {
                CapabilityFlowTag(
                    inputIcon = Icons.Rounded.Chat,
                    outputIcon = Icons.Rounded.Code,
                    tooltip = "代码：文字→代码",
                    backgroundColor = MaterialTheme.colorScheme.surfaceVariant,
                    contentColor = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
            // 结构化 文字→JSON（青色）
            if (m.supportsStructuredOutput) {
                CapabilityFlowTag(
                    inputIcon = Icons.Rounded.Chat,
                    outputIcon = Icons.Rounded.ListAlt,
                    tooltip = "结构化：文字→JSON",
                    backgroundColor = MaterialTheme.colorScheme.primaryContainer,
                    contentColor = MaterialTheme.colorScheme.onPrimaryContainer
                )
            }
            // 输入窗口：inputTokens 优先，缺失时回退 contextTokens
            val inputValue = m.inputTokens ?: m.contextTokens
            if (inputValue > 0) {
                ContextTokenTag(
                    label = "输入",
                    text = formatTokenLimit(inputValue)
                )
            }
            // 输出窗口：outputTokens 独立显示
            m.outputTokens?.takeIf { it > 0 }?.let { out ->
                ContextTokenTag(
                    label = "输出",
                    text = formatTokenLimit(out)
                )
            }
            if (hasOverride) {
                ModelTag(
                    text = "已覆盖",
                    icon = Icons.Rounded.Settings,
                    backgroundColor = Color.Transparent,
                    contentColor = MaterialTheme.colorScheme.primary,
                    border = BorderStroke(1.dp, MaterialTheme.colorScheme.primary)
                )
            }
        }
    }
}

/**
 * 能力标签：纯图标 pill，形式为「输入图标 → 箭头 → 输出图标」，无文字。
 * 点击后在标签上方弹出文字说明气泡，2 秒后自动消失。
 * overridden=true 时右上角显示小红点，表示该能力被手动覆盖过。
 */
@Composable
private fun CapabilityFlowTag(
    inputIcon: ImageVector,
    outputIcon: ImageVector,
    tooltip: String,
    backgroundColor: Color,
    contentColor: Color,
    overridden: Boolean = false
) {
    var showTip by remember { mutableStateOf(false) }
    LaunchedEffect(showTip) {
        if (showTip) {
            kotlinx.coroutines.delay(2000)
            showTip = false
        }
    }
    Box {
        Surface(
            color = backgroundColor,
            shape = RoundedCornerShape(50),
            modifier = Modifier
                .padding(end = 4.dp)
                .clickable { showTip = true }
        ) {
            Row(
                modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Icon(
                    inputIcon,
                    contentDescription = null,
                    modifier = Modifier.size(14.dp),
                    tint = contentColor
                )
                Spacer(Modifier.width(2.dp))
                Icon(
                    Icons.Rounded.ArrowForward,
                    contentDescription = null,
                    modifier = Modifier.size(10.dp),
                    tint = contentColor.copy(alpha = 0.6f)
                )
                Spacer(Modifier.width(2.dp))
                Icon(
                    outputIcon,
                    contentDescription = null,
                    modifier = Modifier.size(14.dp),
                    tint = contentColor
                )
            }
        }
        if (overridden) {
            Box(
                modifier = Modifier
                    .align(Alignment.TopEnd)
                    .offset(x = 2.dp, y = (-2).dp)
                    .size(8.dp)
                    .clip(RoundedCornerShape(50))
                    .background(MaterialTheme.colorScheme.primary)
            )
        }
        if (showTip) {
            val density = LocalDensity.current
            Popup(
                alignment = Alignment.BottomCenter,
                offset = with(density) { IntOffset(0, (-30).dp.roundToPx()) },
                onDismissRequest = { showTip = false },
                properties = PopupProperties(focusable = false, dismissOnClickOutside = true)
            ) {
                Surface(
                    shape = RoundedCornerShape(6.dp),
                    color = MaterialTheme.colorScheme.inverseSurface
                ) {
                    Text(
                        text = tooltip,
                        modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp),
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.inverseOnSurface
                    )
                }
            }
        }
    }
}

/**
 * 上下文窗口标签：「文字 + 格式化 token 数」pill，灰色。
 */
@Composable
private fun ContextTokenTag(
    label: String,
    text: String
) {
    Surface(
        color = MaterialTheme.colorScheme.surfaceVariant,
        shape = RoundedCornerShape(50),
        modifier = Modifier.padding(end = 4.dp)
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                text = label,
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.7f)
            )
            Spacer(Modifier.width(3.dp))
            Text(
                text = text,
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    }
}

@Composable
private fun ModelTag(
    text: String? = null,
    icon: androidx.compose.ui.graphics.vector.ImageVector? = null,
    backgroundColor: androidx.compose.ui.graphics.Color = MaterialTheme.colorScheme.surfaceVariant,
    contentColor: androidx.compose.ui.graphics.Color = MaterialTheme.colorScheme.onSurfaceVariant,
    border: BorderStroke? = null
) {
    Surface(
        color = backgroundColor,
        shape = RoundedCornerShape(50),
        border = border,
        modifier = Modifier.padding(end = 4.dp)
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 8.dp, vertical = 3.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            if (icon != null) {
                Icon(
                    icon,
                    contentDescription = null,
                    modifier = Modifier.size(12.dp),
                    tint = contentColor
                )
            }
            if (icon != null && text != null) Spacer(Modifier.width(4.dp))
            if (text != null) {
                Text(
                    text = text,
                    style = MaterialTheme.typography.labelMedium,
                    color = contentColor
                )
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun ProviderModelRow(
    model: String,
    metadata: ModelMetadata?,
    hasOverride: Boolean,
    testing: Boolean,
    result: ModelTestResult?,
    onTest: () -> Unit,
    onRemove: (() -> Unit)? = null,
    onOpenCapabilityOverride: (() -> Unit)? = null,
    selected: Boolean? = null,
    onToggleSelected: (Boolean) -> Unit = {},
    isFavorite: Boolean = false,
    onToggleFavorite: (() -> Unit)? = null
) {
    var showErrorDetail by remember { mutableStateOf(false) }

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = Spacing.xs, vertical = Spacing.sm)
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            // 选择复选框：仅内置供应商向导（已拉取候选列表）使用，勾选=入库；
            // 编辑页传 null 不显示（编辑页的模型本身已入库，语义不同）。
            selected?.let { checked ->
                Checkbox(checked = checked, onCheckedChange = onToggleSelected)
                Spacer(Modifier.width(Spacing.xs))
            }

            // 模型名 + 描述
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    model,
                    style = MaterialTheme.typography.titleSmall,
                    color = MaterialTheme.colorScheme.onSurface,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                if (metadata?.description?.isNotBlank() == true) {
                    Text(
                        text = metadata.description,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                }
            }

            // 收藏按钮（编辑页使用；传 null 隐藏）
            onToggleFavorite?.let { onFav ->
                IconButton(
                    onClick = onFav,
                    modifier = Modifier.size(32.dp)
                ) {
                    Icon(
                        if (isFavorite) Icons.Rounded.Star else Icons.Rounded.StarBorder,
                        contentDescription = if (isFavorite) "取消收藏" else "收藏",
                        tint = if (isFavorite) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.size(18.dp)
                    )
                }
            }
        }

        Spacer(Modifier.height(4.dp))

        // 能力标签：单行横向滚动，超出可滑动
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .horizontalScroll(rememberScrollState()),
            verticalAlignment = Alignment.CenterVertically
        ) {
            ModelMetadataTags(metadata = metadata, hasOverride = hasOverride)
        }

        Spacer(Modifier.height(8.dp))

        // 功能按钮行：参数设置 / 测试模型(+测速结果) / 删除模型
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(Spacing.sm)
        ) {
            // 参数设置按钮
            onOpenCapabilityOverride?.let { onOpen ->
                OutlinedButton(
                    onClick = onOpen,
                    contentPadding = PaddingValues(horizontal = Spacing.sm, vertical = 4.dp),
                    modifier = Modifier.height(32.dp)
                ) {
                    Icon(
                        Icons.Rounded.Settings,
                        contentDescription = null,
                        modifier = Modifier.size(14.dp),
                        tint = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Spacer(Modifier.width(4.dp))
                    Text(
                        "参数设置",
                        style = MaterialTheme.typography.labelMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }

            // 测试模型按钮 + 测速结果
            OutlinedButton(
                onClick = { if (!testing) onTest() },
                enabled = !testing,
                contentPadding = PaddingValues(horizontal = Spacing.sm, vertical = 4.dp),
                modifier = Modifier.height(32.dp)
            ) {
                if (testing) {
                    CircularProgressIndicator(
                        modifier = Modifier.size(14.dp),
                        strokeWidth = 2.dp
                    )
                } else {
                    Icon(
                        Icons.Rounded.PlayArrow,
                        contentDescription = null,
                        modifier = Modifier.size(14.dp),
                        tint = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
                Spacer(Modifier.width(4.dp))
                Text(
                    "测试",
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }

            // 测速结果：显示在测试按钮旁边
            result?.let { r ->
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier.then(
                        if (!r.success) Modifier.clickable { showErrorDetail = true } else Modifier
                    )
                ) {
                    Icon(
                        if (r.success) Icons.Rounded.Check else Icons.Rounded.Warning,
                        contentDescription = null,
                        tint = if (r.success) MaterialTheme.colorScheme.tertiary else MaterialTheme.colorScheme.error,
                        modifier = Modifier.size(14.dp)
                    )
                    Spacer(Modifier.width(4.dp))
                    val displayMsg = if (r.success) {
                        r.message
                    } else {
                        val codeMatch = Regex("""(?i)(HTTP\s*\d{3}|code[:\s]+[a-zA-Z0-9_]+)""").find(r.message)
                        if (codeMatch != null) codeMatch.value
                        else r.message.lines().firstOrNull()?.let { if (it.length > 20) it.take(20) + "..." else it } ?: "Error"
                    }
                    Text(
                        text = displayMsg,
                        style = MaterialTheme.typography.bodySmall,
                        color = if (r.success) MaterialTheme.colorScheme.tertiary else MaterialTheme.colorScheme.error,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                }
            }

            // 删除模型按钮（红色，推到行尾）
            onRemove?.let { onDel ->
                Spacer(Modifier.weight(1f))
                TextButton(
                    onClick = onDel,
                    contentPadding = PaddingValues(horizontal = Spacing.sm, vertical = 4.dp),
                    modifier = Modifier.height(32.dp)
                ) {
                    Icon(
                        Icons.Rounded.Delete,
                        contentDescription = null,
                        modifier = Modifier.size(14.dp),
                        tint = MaterialTheme.colorScheme.error
                    )
                    Spacer(Modifier.width(4.dp))
                    Text(
                        "删除",
                        style = MaterialTheme.typography.labelMedium,
                        color = MaterialTheme.colorScheme.error
                    )
                }
            }
        }
    }

    if (showErrorDetail && result != null && !result.success) {
        val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
        val clipboard = LocalClipboard.current
        var copied by remember { mutableStateOf(false) }
        val scope = rememberCoroutineScope()

        LaunchedEffect(copied) {
            if (copied) {
                kotlinx.coroutines.delay(1500)
                copied = false
            }
        }

        AppBottomSheet(
            onDismiss = { showErrorDetail = false },
            sheetState = sheetState,
            containerColor = MaterialTheme.colorScheme.surface
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(Spacing.lg)
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Text(
                        text = "Error Details",
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.error
                    )
                    IconButton(onClick = {
                        scope.launch {
                            clipboard.setClipEntry(ClipEntry(ClipData.newPlainText("error", result.message)))
                            copied = true
                        }
                    }) {
                        Icon(
                            if (copied) Icons.Rounded.Check else Icons.Rounded.ContentCopy,
                            contentDescription = "Copy Error",
                            tint = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
                Spacer(Modifier.height(Spacing.sm))
                Text(
                    text = result.message,
                    style = MaterialTheme.typography.bodySmall.copy(fontFamily = FontFamily.Monospace),
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier
                        .fillMaxWidth()
                        .background(MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f), RoundedCornerShape(LocalCornerRadius.current.md))
                        .padding(Spacing.sm)
                )
                Spacer(Modifier.height(Spacing.xl))
            }
        }
    }
}

private fun formatTokenLimit(tokens: Int): String =
    when {
        tokens >= 1_000_000 && tokens % 1_000_000 == 0 -> "${tokens / 1_000_000}M"
        tokens >= 1_000_000 -> "${tokens / 1_000_000.0}M".trimDecimal()
        tokens >= 1_000 && tokens % 1_000 == 0 -> "${tokens / 1_000}K"
        tokens >= 1_000 -> "${tokens / 1_000.0}K".trimDecimal()
        else -> tokens.toString()
    }

private fun String.trimDecimal(): String =
    replace(Regex("(\\.\\d)\\d+"), "$1").removeSuffix(".0")

@OptIn(ExperimentalLayoutApi::class)
@Composable
internal fun FetchModelRow(
    model: String,
    metadata: ModelMetadata?,
    onAdd: () -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable { onAdd() }
            .padding(vertical = Spacing.sm, horizontal = Spacing.xs),
        verticalAlignment = Alignment.CenterVertically
    ) {
        ModelLogoIcon(modelName = model, size = 20.dp)
        Spacer(Modifier.width(Spacing.md))
        Column(modifier = Modifier.weight(1f)) {
            Text(model, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurface)
            if (metadata?.description?.isNotBlank() == true) {
                Text(
                    text = metadata.description,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                Spacer(Modifier.height(2.dp))
            }
            Spacer(Modifier.height(4.dp))
            ModelMetadataTags(metadata = metadata, hasOverride = false)
        }
        IconButton(onClick = onAdd, modifier = Modifier.size(32.dp)) {
            Icon(Icons.Rounded.Add, contentDescription = stringResource(R.string.common_add), tint = MaterialTheme.colorScheme.onSurfaceVariant)
        }
    }
}

// ————————————————————————————————————————————————————————————
// RC63 ④：单模型「三能力复选框手动覆盖」底部面板
// ————————————————————————————————————————————————————————————

/**
 * 三级复选框（Indeterminate）：每一条的状态都可能是：
 *  - null（未覆盖：跟随系统自动推荐）
 *  - true（手动覆盖为开）
 *  - false（手动覆盖为关）
 *
 *  控件实现：文字「点击 null→true→false→null」循环，同时左边 FilterChip 三态切换
 *  （选中=开/不选=关/中间=不覆盖）。小白可直观看到「被覆盖的是哪一个」。
 */
@Composable
private fun TriStateCapabilityRow(
    label: String,
    englishTag: String,
    description: String,
    autoValue: Boolean,    // 系统自动判定的期望值（显示在副标题「自动推荐」里）
    overrideValue: Boolean?,  // null=未覆盖；true/false=覆盖
    onChange: (Boolean?) -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable {
                val next = when (overrideValue) {
                    null -> true
                    true -> false
                    false -> null
                }
                onChange(next)
            }
            .padding(horizontal = Spacing.sm, vertical = Spacing.sm),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(Spacing.sm)
    ) {
        FilterChip(
            selected = overrideValue == true,
            onClick = {
                val next = when (overrideValue) {
                    null -> true
                    true -> false
                    false -> null
                }
                onChange(next)
            },
            label = {
                Text(
                    text = when (overrideValue) {
                        true -> stringResource(R.string.ui_______9188121c)
                        false -> stringResource(R.string.ui_______73f7db5e)
                        null -> stringResource(R.string.ui________be725ae0)
                    }
                )
            },
            leadingIcon = if (overrideValue != null) {
                { Icon(Icons.Rounded.Settings, contentDescription = null, modifier = Modifier.size(14.dp)) }
            } else null
        )
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = "$label（$englishTag）",
                style = MaterialTheme.typography.titleSmall,
                color = MaterialTheme.colorScheme.onSurface
            )
            Text(
                text = buildString {
                    append(description)
                    append(stringResource(R.string.ui______d8195afb))
                    append(if (autoValue) stringResource(R.string.ui____5f15d40d) else stringResource(R.string.ui____127dc326))
                    append(
                        when (overrideValue) {
                            true -> stringResource(R.string.ui________3eeb0c16)
                            false -> stringResource(R.string.ui________b9cce01f)
                            null -> ""
                        }
                    )
                },
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun ModelSettingsSheet(
    viewModel: com.mini.me_core.feature.settings.presentation.SettingsViewModel,
    providerType: ProviderType,
    modelId: String,
    metadata: ModelMetadata?,
    overrideFlow: kotlinx.coroutines.flow.Flow<ModelCapabilityOverrideEntity?>,
    customConfigFlow: kotlinx.coroutines.flow.Flow<ModelCustomConfigEntity?>,
    samplingConfigFlow: kotlinx.coroutines.flow.Flow<ModelSamplingConfigEntity?>,
    providerDefaultTemperature: Float,
    providerDefaultTopP: Float,
    providerDefaultMaxTokens: Int?,
    onDismiss: () -> Unit
) {
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    val scope = rememberCoroutineScope()
    val override by overrideFlow.collectAsStateWithLifecycleCompat(initial = null)
    val customConfig by customConfigFlow.collectAsStateWithLifecycleCompat(initial = null)
    val samplingConfig by samplingConfigFlow.collectAsStateWithLifecycleCompat(initial = null)

    // 本地三态（UI 编辑的草稿）：初始值从 overrideFlow 读，避免打开面板时丢失已有的覆盖。
    var draftVision by remember(override) { mutableStateOf(override?.overrideVision) }
    var draftTools by remember(override) { mutableStateOf(override?.overrideTools) }
    var draftReasoning by remember(override) { mutableStateOf(override?.overrideReasoning) }
    var draftVideo by remember(override) { mutableStateOf(override?.overrideVideo) }
    var draftAudio by remember(override) { mutableStateOf(override?.overrideAudio) }
    var draftCode by remember(override) { mutableStateOf(override?.overrideCode) }
    var draftStructuredOutput by remember(override) { mutableStateOf(override?.overrideStructuredOutput) }

    // 上下文长度草稿：文本框内容，空串表示不覆盖（留空用自动检测值）。
    var draftInputTokens by remember(customConfig) {
        mutableStateOf(customConfig?.customInputTokens?.toString() ?: "")
    }
    var draftOutputTokens by remember(customConfig) {
        mutableStateOf(customConfig?.customOutputTokens?.toString() ?: "")
    }

    // 采样参数草稿：null 表示未覆盖（继承供应商级默认），非 null 表示已覆盖。
    var draftTemperature by remember(samplingConfig) { mutableStateOf(samplingConfig?.customTemperature) }
    var draftTopP by remember(samplingConfig) { mutableStateOf(samplingConfig?.customTopP) }
    var draftMaxTokens by remember(samplingConfig) { mutableStateOf(samplingConfig?.customMaxTokens?.toString()) }

    // 自动检测值（来自 metadata），用于 placeholder 和换算显示。
    val autoInputTokens = metadata?.inputTokens ?: metadata?.contextTokens
    val autoOutputTokens = metadata?.outputTokens

    fun closeSheet() {
        scope.launch { sheetState.hide() }.invokeOnCompletion { onDismiss() }
    }

    AppBottomSheet(
        onDismiss = onDismiss,
        sheetState = sheetState,
        containerColor = MaterialTheme.colorScheme.surface,
        dragHandle = null
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .verticalScroll(rememberScrollState())
                .padding(Spacing.lg)
                .padding(bottom = Spacing.md),
            verticalArrangement = Arrangement.spacedBy(Spacing.md)
        ) {
            // —— 标题栏 ——
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(Spacing.sm)
            ) {
                Text(
                    text = "模型设置",
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.onSurface,
                    modifier = Modifier.weight(1f)
                )
            }

            // 模型名 + 来源标注
            Text(
                text = buildString {
                    append("模型：$modelId")
                    append(if (metadata?.source == ModelMetadata.Source.MODELS_DEV) "（官方收录）" else "（自动检测）")
                },
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )

            HorizontalDivider()

            // —— 能力覆盖分区 ——
            Text(
                text = "能力覆盖",
                style = MaterialTheme.typography.titleSmall,
                fontWeight = FontWeight.SemiBold,
                color = MaterialTheme.colorScheme.onSurface
            )

            TriStateCapabilityRow(
                label = "多模态识图",
                englishTag = "Vision",
                description = "模型是否能接收图片/截图并理解内容",
                autoValue = metadata?.supportsVision == true,
                overrideValue = draftVision,
                onChange = { draftVision = it }
            )
            TriStateCapabilityRow(
                label = "工具调用",
                englishTag = "Tools",
                description = "模型是否能调用外部工具（查文件/跑命令/查知识库）",
                autoValue = metadata?.supportsTools == true,
                overrideValue = draftTools,
                onChange = { draftTools = it }
            )
            TriStateCapabilityRow(
                label = "深度思考",
                englishTag = "Reasoning",
                description = "模型是否支持 extended thinking / reasoning effort",
                autoValue = metadata?.supportsReasoning == true,
                overrideValue = draftReasoning,
                onChange = { draftReasoning = it }
            )
            TriStateCapabilityRow(
                label = "视频理解",
                englishTag = "Video",
                description = "模型是否能接收视频输入并理解内容",
                autoValue = metadata?.supportsVideo == true,
                overrideValue = draftVideo,
                onChange = { draftVideo = it }
            )
            TriStateCapabilityRow(
                label = "语音识别",
                englishTag = "Audio",
                description = "模型是否能接收语音/音频输入并转写",
                autoValue = metadata?.supportsAudio == true,
                overrideValue = draftAudio,
                onChange = { draftAudio = it }
            )
            TriStateCapabilityRow(
                label = "代码生成",
                englishTag = "Code",
                description = "模型是否擅长代码生成与理解",
                autoValue = metadata?.supportsCode == true,
                overrideValue = draftCode,
                onChange = { draftCode = it }
            )
            TriStateCapabilityRow(
                label = "结构化输出",
                englishTag = "Structured Output",
                description = "模型是否支持 JSON/结构化数据输出",
                autoValue = metadata?.supportsStructuredOutput == true,
                overrideValue = draftStructuredOutput,
                onChange = { draftStructuredOutput = it }
            )

            // 恢复自动检测（能力覆盖）
            TextButton(
                onClick = {
                    viewModel.clearCapabilityOverride(providerType, modelId)
                    draftVision = null
                    draftTools = null
                    draftReasoning = null
                    draftVideo = null
                    draftAudio = null
                    draftCode = null
                    draftStructuredOutput = null
                }
            ) {
                Icon(Icons.Rounded.Refresh, contentDescription = null, modifier = Modifier.size(14.dp))
                Spacer(Modifier.width(Spacing.xs))
                Text("恢复自动检测")
            }

            HorizontalDivider()

            // —— 上下文长度分区 ——
            Text(
                text = "上下文长度",
                style = MaterialTheme.typography.titleSmall,
                fontWeight = FontWeight.SemiBold,
                color = MaterialTheme.colorScheme.onSurface
            )

            // 输入上限
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(Spacing.sm)
            ) {
                OutlinedTextField(
                    value = draftInputTokens,
                    onValueChange = { newValue ->
                        // 只允许输入正整数
                        draftInputTokens = newValue.filter { it.isDigit() }
                    },
                    label = { Text("输入上限") },
                    placeholder = { Text("自动：${autoInputTokens ?: 0}") },
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                    singleLine = true,
                    modifier = Modifier.weight(1f)
                )
                Text(
                    text = "≈ ${formatTokens(draftInputTokens.toIntOrNull() ?: autoInputTokens)}",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }

            // 输出上限
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(Spacing.sm)
            ) {
                OutlinedTextField(
                    value = draftOutputTokens,
                    onValueChange = { newValue ->
                        draftOutputTokens = newValue.filter { it.isDigit() }
                    },
                    label = { Text("输出上限") },
                    placeholder = { Text("自动：${autoOutputTokens ?: 0}") },
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                    singleLine = true,
                    modifier = Modifier.weight(1f)
                )
                Text(
                    text = "≈ ${formatTokens(draftOutputTokens.toIntOrNull() ?: autoOutputTokens)}",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }

            Text(
                text = "留空则使用自动检测值，自定义值将覆盖自动检测结果。",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )

            // 恢复默认（上下文长度）
            TextButton(
                onClick = {
                    viewModel.clearModelCustomConfig(providerType, modelId)
                    draftInputTokens = ""
                    draftOutputTokens = ""
                }
            ) {
                Icon(Icons.Rounded.Refresh, contentDescription = null, modifier = Modifier.size(14.dp))
                Spacer(Modifier.width(Spacing.xs))
                Text("恢复默认")
            }

            HorizontalDivider()

            // —— 采样参数分区 ——
            Text(
                text = "采样参数",
                style = MaterialTheme.typography.titleSmall,
                fontWeight = FontWeight.SemiBold,
                color = MaterialTheme.colorScheme.onSurface
            )

            // Temperature 行
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text("Temperature（温度）", modifier = Modifier.weight(1f))
                Text(
                    text = if (draftTemperature != null) "%.1f".format(draftTemperature) else "继承：%.1f".format(providerDefaultTemperature),
                    style = MaterialTheme.typography.bodySmall,
                    color = if (draftTemperature != null) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
            Slider(
                value = draftTemperature ?: providerDefaultTemperature,
                onValueChange = { draftTemperature = (it * 10).toInt() / 10f },
                valueRange = providerType.temperatureRange()
            )
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    text = if (draftTemperature != null) "已覆盖" else "继承供应商默认",
                    style = MaterialTheme.typography.bodySmall,
                    color = if (draftTemperature != null) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.weight(1f)
                )
                if (draftTemperature != null) {
                    TextButton(onClick = { draftTemperature = null }) {
                        Text("清除", style = MaterialTheme.typography.bodySmall)
                    }
                }
            }

            // Top P 行
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text("Top P（核采样）", modifier = Modifier.weight(1f))
                Text(
                    text = if (draftTopP != null) "%.1f".format(draftTopP) else "继承：%.1f".format(providerDefaultTopP),
                    style = MaterialTheme.typography.bodySmall,
                    color = if (draftTopP != null) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
            Slider(
                value = draftTopP ?: providerDefaultTopP,
                onValueChange = { draftTopP = (it * 10).toInt() / 10f },
                valueRange = 0f..1f
            )
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    text = if (draftTopP != null) "已覆盖" else "继承供应商默认",
                    style = MaterialTheme.typography.bodySmall,
                    color = if (draftTopP != null) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.weight(1f)
                )
                if (draftTopP != null) {
                    TextButton(onClick = { draftTopP = null }) {
                        Text("清除", style = MaterialTheme.typography.bodySmall)
                    }
                }
            }

            // Max Tokens 行
            val modelOutputLimit = metadata?.outputTokens
            val draftMaxTokensInt = draftMaxTokens?.toIntOrNull()
            val maxTokensExceedsLimit = draftMaxTokensInt != null && modelOutputLimit != null && draftMaxTokensInt > modelOutputLimit
            Row(verticalAlignment = Alignment.CenterVertically) {
                OutlinedTextField(
                    value = draftMaxTokens ?: "",
                    onValueChange = { newValue ->
                        draftMaxTokens = newValue.filter { it.isDigit() }.ifEmpty { null }
                    },
                    label = { Text("Max Tokens（最大输出）") },
                    placeholder = {
                        Text(
                            "继承：${providerDefaultMaxTokens?.toString() ?: "不限制"}"
                        )
                    },
                    isError = maxTokensExceedsLimit,
                    supportingText = {
                        if (maxTokensExceedsLimit) {
                            Text("超过模型输出上限（$modelOutputLimit）", color = MaterialTheme.colorScheme.error)
                        } else {
                            Text("留空=继承供应商默认", style = MaterialTheme.typography.bodySmall)
                        }
                    },
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                    singleLine = true,
                    modifier = Modifier.weight(1f)
                )
                Text(
                    text = if (draftMaxTokens != null) "已覆盖" else "继承",
                    style = MaterialTheme.typography.bodySmall,
                    color = if (draftMaxTokens != null) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant
                )
            }

            // 恢复采样默认
            TextButton(
                onClick = {
                    viewModel.clearModelSamplingConfig(providerType, modelId)
                    draftTemperature = null
                    draftTopP = null
                    draftMaxTokens = null
                }
            ) {
                Icon(Icons.Rounded.Refresh, contentDescription = null, modifier = Modifier.size(14.dp))
                Spacer(Modifier.width(Spacing.xs))
                Text("恢复采样默认")
            }

            Spacer(Modifier.height(Spacing.sm))

            // —— 底部按钮 ——
            Row(
                horizontalArrangement = Arrangement.End,
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier.fillMaxWidth()
            ) {
                TextButton(onClick = onDismiss) {
                    Text(stringResource(R.string.common_cancel))
                }
                Spacer(Modifier.width(Spacing.sm))
                TextButton(
                    onClick = {
                        // 同时保存能力覆盖（8 参数）和自定义配置
                        viewModel.saveCapabilityOverride(
                            type = providerType,
                            modelId = modelId,
                            vision = draftVision,
                            tools = draftTools,
                            reasoning = draftReasoning,
                            video = draftVideo,
                            audio = draftAudio,
                            code = draftCode,
                            structuredOutput = draftStructuredOutput
                        )
                        viewModel.saveModelCustomConfig(
                            type = providerType,
                            modelId = modelId,
                            inputTokens = draftInputTokens.toIntOrNull(),
                            outputTokens = draftOutputTokens.toIntOrNull()
                        )
                        viewModel.saveModelSamplingConfig(
                            type = providerType,
                            modelId = modelId,
                            temperature = draftTemperature,
                            topP = draftTopP,
                            maxTokens = draftMaxTokens?.toIntOrNull()
                        )
                        closeSheet()
                    }
                ) {
                    Icon(Icons.Rounded.Check, contentDescription = null, modifier = Modifier.size(14.dp))
                    Spacer(Modifier.width(Spacing.xs))
                    Text("保存")
                }
            }
        }
    }
}

/** 将 token 数格式化为人类可读的 K/M 缩写。 */
private fun formatTokens(tokens: Int?): String = when {
    tokens == null -> ""
    tokens >= 1_000_000 -> String.format("%.1fM", tokens / 1_000_000.0)
    tokens >= 1_000 -> String.format("%.0fK", tokens / 1_000.0)
    else -> tokens.toString()
}

/** 兼容 collectAsStateWithLifecycle 在非 androidx.lifecycle:lifecycle-runtime-compose 场景下的兜底实现（直接用 viewModel 的 flow + initial 初值）。 */
@Composable
private fun <T> kotlinx.coroutines.flow.Flow<T>.collectAsStateWithLifecycleCompat(initial: T): androidx.compose.runtime.State<T> {
    return collectAsStateWithLifecycle(initialValue = initial)
}
