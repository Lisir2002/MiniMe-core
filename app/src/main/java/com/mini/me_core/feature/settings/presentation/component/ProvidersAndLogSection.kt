package com.mini.me_core.feature.settings.presentation.component
import com.mini.me_core.core.theme.tokens.LocalCornerRadius

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.CloudDownload
import androidx.compose.material.icons.rounded.CheckCircle
import androidx.compose.material.icons.rounded.ContentCopy
import androidx.compose.material.icons.rounded.Edit
import androidx.compose.material.icons.rounded.MoreVert
import androidx.compose.material.icons.rounded.RadioButtonUnchecked
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.mini.me_core.core.theme.Spacing
import com.mini.me_core.feature.settings.presentation.LogViewerUiState
import com.mini.me_core.feature.settings.domain.model.AIProviderConfig
import com.mini.me_core.feature.settings.domain.model.ProviderType
import androidx.compose.ui.res.stringResource
import com.mini.me_core.R

private fun extractHost(url: String): String {
    return try {
        java.net.URI(url).host ?: url
    } catch (e: Exception) {
        url.removePrefix("https://").removePrefix("http://").substringBefore("/")
    }
}

/** 提供商二级页：列表 + 空态提示。 */
@Composable
internal fun ProvidersSection(
    providers: List<AIProviderConfig>,
    activeProviderId: String?,
    onEdit: (AIProviderConfig) -> Unit,
    onSetActive: (String) -> Unit,
    onToggleEnabled: (String, Boolean) -> Unit,
    onDuplicate: (String) -> Unit
) {
    if (providers.isEmpty()) {
        EmptyHint(stringResource(R.string.providers_empty))
        return
    }
    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(Spacing.lg),
        verticalArrangement = Arrangement.spacedBy(Spacing.md)
    ) {
        items(providers) { provider ->
            ProviderItem(
                provider = provider,
                isActive = provider.id == activeProviderId,
                onEdit = { onEdit(provider) },
                onSetActive = { onSetActive(provider.id) },
                onToggleEnabled = { enabled -> onToggleEnabled(provider.id, enabled) },
                onDuplicate = { onDuplicate(provider.id) }
            )
        }
        // 导入/导出配置占位
        item {
            Card(
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(LocalCornerRadius.current.lg),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f))
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(Spacing.lg),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Icon(
                        Icons.Outlined.CloudDownload,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.6f),
                        modifier = Modifier.size(24.dp)
                    )
                    Spacer(Modifier.width(Spacing.md))
                    Column {
                        Text(
                            "导入/导出配置",
                            style = MaterialTheme.typography.bodyLarge,
                            color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.6f)
                        )
                        Text(
                            "即将上线",
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.6f)
                        )
                    }
                }
            }
        }
    }
}

/** 居中空态提示。 */
@Composable
internal fun EmptyHint(text: String) {
    Box(
        modifier = Modifier
            .fillMaxSize()
            .padding(Spacing.xl),
        contentAlignment = Alignment.Center
    ) {
        Text(
            text = text,
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
    }
}

@Composable
fun ProviderItem(
    provider: AIProviderConfig,
    isActive: Boolean,
    onEdit: () -> Unit,
    onSetActive: () -> Unit,
    onToggleEnabled: (Boolean) -> Unit,
    onDuplicate: () -> Unit
) {
    var menuExpanded by remember { mutableStateOf(false) }
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .clickable { onEdit() },
        shape = RoundedCornerShape(LocalCornerRadius.current.lg),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surface
        ),
        border = BorderStroke(
            1.dp,
            if (isActive) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.outlineVariant
        )
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = Spacing.md, vertical = 10.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            ProviderLogoIcon(
                provider = provider,
                size = 32.dp,
                modifier = Modifier.padding(end = Spacing.sm)
            )
            Column(modifier = Modifier.weight(1f)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        text = provider.name,
                        style = MaterialTheme.typography.titleSmall,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.onSurface,
                        maxLines = 1,
                    )
                    if (isActive) {
                        Spacer(Modifier.width(Spacing.xs))
                        Surface(
                            shape = RoundedCornerShape(LocalCornerRadius.current.sm),
                            color = MaterialTheme.colorScheme.primaryContainer
                        ) {
                            Text(
                                "使用中",
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.onPrimaryContainer,
                                modifier = Modifier.padding(horizontal = 6.dp, vertical = 1.dp)
                            )
                        }
                    }
                }
                val typeLabel = when (provider.type) {
                    ProviderType.OPENAI -> "OpenAI"
                    ProviderType.ANTHROPIC -> "Anthropic"
                    ProviderType.GEMINI -> "Gemini"
                }
                Text(
                    "${provider.effectiveModel.ifBlank { "未选模型" }} · $typeLabel · ${extractHost(provider.baseUrl)}",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1,
                    overflow = androidx.compose.ui.text.style.TextOverflow.Ellipsis,
                )
            }
            Column(
                horizontalAlignment = Alignment.End,
                verticalArrangement = Arrangement.spacedBy(0.dp)
            ) {
                Switch(
                    checked = provider.isEnabled,
                    onCheckedChange = onToggleEnabled
                )
                Row(
                    horizontalArrangement = Arrangement.spacedBy(0.dp)
                ) {
                    IconButton(
                        onClick = onSetActive,
                        modifier = Modifier.size(32.dp)
                    ) {
                        Icon(
                            if (isActive) Icons.Rounded.CheckCircle else Icons.Rounded.RadioButtonUnchecked,
                            contentDescription = if (isActive) "当前使用中" else "设为当前",
                            tint = if (isActive) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.size(18.dp)
                        )
                    }
                    IconButton(
                        onClick = onEdit,
                        modifier = Modifier.size(32.dp)
                    ) {
                        Icon(
                            Icons.Rounded.Edit,
                            contentDescription = stringResource(R.string.common_edit),
                            tint = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.size(18.dp)
                        )
                    }
                    Box {
                        IconButton(
                            onClick = { menuExpanded = true },
                            modifier = Modifier.size(32.dp)
                        ) {
                            Icon(
                                Icons.Rounded.MoreVert,
                                contentDescription = "更多操作",
                                tint = MaterialTheme.colorScheme.onSurfaceVariant,
                                modifier = Modifier.size(18.dp)
                            )
                        }
                        DropdownMenu(
                            expanded = menuExpanded,
                            onDismissRequest = { menuExpanded = false }
                        ) {
                            DropdownMenuItem(
                                text = { Text("复制") },
                                leadingIcon = { Icon(Icons.Rounded.ContentCopy, contentDescription = null) },
                                onClick = {
                                    menuExpanded = false
                                    onDuplicate()
                                }
                            )
                        }
                    }
                }
            }
        }
    }
}
