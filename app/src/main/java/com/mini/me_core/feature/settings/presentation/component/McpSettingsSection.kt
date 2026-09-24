package com.mini.me_core.feature.settings.presentation.component
import com.mini.me_core.core.theme.tokens.LocalComponentTokens
import com.mini.me_core.core.theme.tokens.LocalCornerRadius

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.spring
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.gestures.detectHorizontalDragGestures
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
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.requiredWidth
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Add
import androidx.compose.material.icons.rounded.KeyboardArrowDown
import androidx.compose.material.icons.rounded.KeyboardArrowUp
import androidx.compose.material.icons.rounded.Delete
import androidx.compose.material.icons.rounded.Dns
import androidx.compose.material.icons.automirrored.rounded.KeyboardArrowRight
import androidx.compose.material.icons.rounded.Terminal
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.mini.me_core.core.theme.Brand
import com.mini.me_core.core.theme.Radius
import com.mini.me_core.core.theme.Spacing
import com.mini.me_core.feature.agent.domain.mcp.McpServerConfig
import com.mini.me_core.feature.agent.domain.mcp.McpServerStatus
import kotlin.math.roundToInt
import kotlinx.coroutines.launch
import androidx.compose.ui.res.stringResource
import com.mini.me_core.R

/**
 * MCP 二级页（可视化）：全新现代化设计的 Server 列表页面。
 */
@Composable
internal fun McpSection(
    servers: List<McpServerConfig>,
    statuses: List<McpServerStatus>,
    reloading: Boolean,
    onReload: () -> Unit,
    onToggle: (String, Boolean) -> Unit,
    onEdit: (McpServerConfig) -> Unit,
    onDelete: (String) -> Unit,
    modifier: Modifier = Modifier,
    onAddServer: (() -> Unit)? = null
) {
    LazyColumn(
        modifier = modifier.fillMaxSize(),
        contentPadding = PaddingValues(Spacing.lg),
        verticalArrangement = Arrangement.spacedBy(Spacing.md)
    ) {
        if (servers.isEmpty()) {
            item {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(vertical = 48.dp),
                    contentAlignment = Alignment.Center
                ) {
                    Column(
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.spacedBy(Spacing.md)
                    ) {
                        Box(
                            modifier = Modifier
                                .size(64.dp)
                                .background(MaterialTheme.colorScheme.surfaceVariant, RoundedCornerShape(LocalCornerRadius.current.xl)),
                            contentAlignment = Alignment.Center
                        ) {
                            Icon(
                                Icons.Rounded.Terminal,
                                contentDescription = null,
                                modifier = Modifier.size(28.dp),
                                tint = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                        Text(
                            text = stringResource(R.string.mcp_empty),
                            style = MaterialTheme.typography.titleMedium,
                            color = MaterialTheme.colorScheme.onSurface
                        )
                        Text(
                            text = stringResource(R.string.mcp_empty_hint),
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                        if (onAddServer != null) {
                            Spacer(Modifier.height(Spacing.sm))
                            Button(
                                onClick = onAddServer,
                                colors = ButtonDefaults.buttonColors(
                                    containerColor = MaterialTheme.colorScheme.primary,
                                    contentColor = MaterialTheme.colorScheme.onPrimary
                                )
                            ) {
                                Icon(Icons.Rounded.Add, contentDescription = null, modifier = Modifier.size(18.dp))
                                Spacer(Modifier.width(6.dp))
                                Text(stringResource(R.string.mcp_add_first_server))
                            }
                        }
                    }
                }
            }
        } else {
            items(servers, key = { it.name }) { server ->
                McpServerRow(
                    server = server,
                    status = statuses.firstOrNull { it.name == server.name },
                    onClick = { onEdit(server) },
                    onDelete = { onDelete(server.name) }
                )
            }
            if (onAddServer != null) {
                item {
                    Button(
                        onClick = onAddServer,
                        modifier = Modifier.fillMaxWidth(),
                        colors = ButtonDefaults.buttonColors(
                            containerColor = MaterialTheme.colorScheme.surface,
                            contentColor = MaterialTheme.colorScheme.primary
                        ),
                        border = androidx.compose.foundation.BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant),
                        shape = RoundedCornerShape(LocalCornerRadius.current.xxl)
                    ) {
                        Icon(Icons.Rounded.Add, contentDescription = null, modifier = Modifier.size(18.dp))
                        Spacer(Modifier.width(6.dp))
                        Text(stringResource(R.string.mcp_add_server_button))
                    }
                }
            }
        }
    }
}

/** 单个 MCP server 行：现代化卡片样式（图标状态标签 + 药丸标签 + 右侧箭头，支持左滑删除和长按菜单）。 */
@OptIn(ExperimentalMaterial3Api::class, ExperimentalFoundationApi::class)
@Composable
internal fun McpServerRow(
    server: McpServerConfig,
    status: McpServerStatus?,
    onClick: () -> Unit,
    onDelete: () -> Unit
) {
    val isConnected = server.enabled && status?.state == McpServerStatus.State.CONNECTED
    val hasError = status?.state == McpServerStatus.State.FAILED && !status.error.isNullOrBlank()
    var expanded by remember { mutableStateOf(false) }
    var showMenu by remember { mutableStateOf(false) }

    val statusText = when {
        !server.enabled -> stringResource(R.string.mcp_disabled)
        status == null -> stringResource(R.string.mcp_not_connected)
        else -> when (status.state) {
            McpServerStatus.State.CONNECTED -> stringResource(R.string.mcp_connected)
            McpServerStatus.State.CONNECTING -> stringResource(R.string.mcp_connecting)
            McpServerStatus.State.FAILED -> stringResource(R.string.mcp_connection_failed)
            McpServerStatus.State.DISABLED -> stringResource(R.string.mcp_disabled)
        }
    }

    val statusColor = when {
        !server.enabled || status == null || status.state == McpServerStatus.State.DISABLED ->
            MaterialTheme.colorScheme.outline
        status.state == McpServerStatus.State.CONNECTED ->
            MaterialTheme.colorScheme.tertiary
        status.state == McpServerStatus.State.CONNECTING ->
            MaterialTheme.colorScheme.primary
        else ->
            MaterialTheme.colorScheme.error
    }

    val statusBgColor = statusColor.copy(alpha = 0.12f)

    val density = LocalDensity.current
    val revealPx = remember(density) { with(density) { -112.dp.toPx() } }
    val offsetX = remember { Animatable(0f) }
    val coroutineScope = rememberCoroutineScope()

    val revealedWidthDp = with(density) { (-offsetX.value).toDp().coerceAtLeast(0.dp) }
    val maxButtonWidth = 104.dp
    val buttonWidth = if (revealedWidthDp > 8.dp) (revealedWidthDp - 8.dp).coerceAtMost(maxButtonWidth) else 0.dp
    val progress = (buttonWidth / maxButtonWidth).coerceIn(0f, 1f)

    Box(
        modifier = Modifier.fillMaxWidth()
    ) {
        // 1. 底层删除按钮（固定在右端，向右滑动滑动时会受到挤压、缩放与透明度渐变，直到消失）
        Row(
            modifier = Modifier
                .matchParentSize()
                .padding(vertical = 2.dp),
            horizontalArrangement = Arrangement.End,
            verticalAlignment = Alignment.CenterVertically
        ) {
            if (buttonWidth > 0.dp) {
                Box(
                    modifier = Modifier
                        .fillMaxHeight()
                        .width(buttonWidth)
                        .graphicsLayer {
                            alpha = (progress * 1.2f).coerceIn(0f, 1f)
                            scaleX = (0.4f + 0.6f * progress).coerceIn(0f, 1f)
                            scaleY = (0.7f + 0.3f * progress).coerceIn(0f, 1f)
                        }
                        .clip(RoundedCornerShape(LocalCornerRadius.current.xxl))
                        .background(Color(0xFFEF4444))
                        .border(1.dp, Color(0xFFF87171), RoundedCornerShape(LocalCornerRadius.current.xxl))
                        .clickable {
                            coroutineScope.launch {
                                offsetX.animateTo(0f)
                                onDelete()
                            }
                        },
                    contentAlignment = Alignment.Center
                ) {
                    Row(
                        modifier = Modifier.requiredWidth(104.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.Center
                    ) {
                        Icon(
                            imageVector = Icons.Rounded.Delete,
                            contentDescription = stringResource(R.string.common_delete),
                            tint = Color.White,
                            modifier = Modifier.size(20.dp)
                        )
                        Spacer(modifier = Modifier.width(Spacing.sm))
                        Text(
                            text = stringResource(R.string.common_delete),
                            style = MaterialTheme.typography.bodyMedium.copy(
                                fontWeight = FontWeight.SemiBold,
                                fontSize = 15.sp
                            ),
                            color = Color.White
                        )
                    }
                }
            }
        }

        // 2. 表层卡片（支持手势回弹与滑动展开）
        Card(
            modifier = Modifier
                .offset { IntOffset(offsetX.value.roundToInt(), 0) }
                .fillMaxWidth()
                .pointerInput(Unit) {
                    detectHorizontalDragGestures(
                        onDragStart = {
                            coroutineScope.launch { offsetX.stop() }
                        },
                        onDragEnd = {
                            coroutineScope.launch {
                                if (offsetX.value < revealPx / 2) {
                                    offsetX.animateTo(
                                        targetValue = revealPx,
                                        animationSpec = spring(
                                            dampingRatio = Spring.DampingRatioLowBouncy,
                                            stiffness = Spring.StiffnessMediumLow
                                        )
                                    )
                                } else {
                                    offsetX.animateTo(
                                        targetValue = 0f,
                                        animationSpec = spring(
                                            dampingRatio = Spring.DampingRatioNoBouncy,
                                            stiffness = Spring.StiffnessMedium
                                        )
                                    )
                                }
                            }
                        },
                        onDragCancel = {
                            coroutineScope.launch {
                                offsetX.animateTo(0f)
                            }
                        },
                        onHorizontalDrag = { change, dragAmount ->
                            change.consume()
                            coroutineScope.launch {
                                val newOffset = (offsetX.value + dragAmount).coerceIn(revealPx * 1.15f, 0f)
                                offsetX.snapTo(newOffset)
                            }
                        }
                    )
                }
                .combinedClickable(
                    onClick = {
                        if (offsetX.value < -10f) {
                            coroutineScope.launch {
                                offsetX.animateTo(0f, spring(stiffness = Spring.StiffnessMedium))
                            }
                        } else if (hasError) {
                            expanded = !expanded
                        } else {
                            onClick()
                        }
                    },
                    onLongClick = { showMenu = true }
                ),
            shape = RoundedCornerShape(LocalCornerRadius.current.xxl),
            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
            border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant)
        ) {
            Column {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(Spacing.lg),
                verticalAlignment = Alignment.CenterVertically
            ) {
                // 左侧容器图标 + 状态圆点
                Box(
                    modifier = Modifier
                        .size(48.dp)
                        .background(
                            color = MaterialTheme.colorScheme.surfaceVariant,
                            shape = RoundedCornerShape(LocalCornerRadius.current.xl)
                        )
                ) {
                    Icon(
                        imageVector = if (server.isStdio) Icons.Rounded.Terminal else Icons.Rounded.Dns,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier
                            .size(22.dp)
                            .align(Alignment.Center)
                    )
                    // 右下角带描边的状态圆点
                    Box(
                        modifier = Modifier
                            .align(Alignment.BottomEnd)
                            .padding(2.dp)
                            .size(10.dp)
                            .background(color = statusColor, shape = RoundedCornerShape(LocalCornerRadius.current.pill))
                            .border(1.5.dp, MaterialTheme.colorScheme.surface, RoundedCornerShape(LocalCornerRadius.current.pill))
                    )
                }

                Spacer(modifier = Modifier.width(Spacing.lg))

                // 中间标题和 Pill 标签
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = server.name,
                        style = MaterialTheme.typography.titleMedium.copy(
                            fontWeight = FontWeight.SemiBold,
                            fontSize = LocalComponentTokens.current.text.bodyLargeFontSize
                        ),
                        color = MaterialTheme.colorScheme.onSurface,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )

                    Spacer(Modifier.height(Spacing.sm))

                    Row(
                        horizontalArrangement = Arrangement.spacedBy(Spacing.sm),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        // 1. 状态 Pill
                        Box(
                            modifier = Modifier
                                .background(statusBgColor, RoundedCornerShape(LocalCornerRadius.current.pill))
                                .padding(horizontal = Spacing.sm, vertical = 2.dp)
                        ) {
                            Text(
                                text = statusText,
                                style = MaterialTheme.typography.bodySmall,
                                color = statusColor
                            )
                        }

                        // 2. 类型 Pill
                        Box(
                            modifier = Modifier
                                .background(
                                    MaterialTheme.colorScheme.surfaceVariant,
                                    RoundedCornerShape(LocalCornerRadius.current.pill)
                                )
                                .padding(horizontal = Spacing.sm, vertical = 2.dp)
                        ) {
                            Text(
                                text = if (server.isStdio) stringResource(R.string.mcp_builtin) else "HTTP",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }

                        // 3. 工具数量/信息 Pill
                        val infoText = when {
                            isConnected -> stringResource(R.string.mcp_tools_count, status?.toolCount ?: 0, status?.totalToolCount ?: 0)
                            server.isStdio -> server.command.orEmpty().ifEmpty { "stdio" }
                            else -> server.url.orEmpty().ifEmpty { "HTTP" }
                        }
                        Box(
                            modifier = Modifier
                                .background(
                                    MaterialTheme.colorScheme.surfaceVariant,
                                    RoundedCornerShape(LocalCornerRadius.current.pill)
                                )
                                .padding(horizontal = Spacing.sm, vertical = 2.dp)
                        ) {
                            Text(
                                text = infoText,
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis
                            )
                        }
                    }
                }

                Spacer(modifier = Modifier.width(Spacing.sm))

                // 右侧箭头：失败时显示展开/收起箭头，否则显示前进箭头
                Icon(
                    imageVector = when {
                        hasError && expanded -> Icons.Rounded.KeyboardArrowUp
                        hasError -> Icons.Rounded.KeyboardArrowDown
                        else -> Icons.AutoMirrored.Rounded.KeyboardArrowRight
                    },
                    contentDescription = stringResource(
                        if (hasError) {
                            if (expanded) R.string.mcp_tap_to_hide_error else R.string.mcp_tap_to_view_error
                        } else {
                            R.string.mcp_details
                        }
                    ),
                    tint = if (hasError) MaterialTheme.colorScheme.error
                    else MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.size(20.dp)
                )
            }

            // 失败错误详情展开区域
            if (hasError && expanded) {
                HorizontalDivider(
                    modifier = Modifier.padding(horizontal = Spacing.lg),
                    color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f)
                )
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = Spacing.lg, vertical = Spacing.md)
                ) {
                    Text(
                        text = stringResource(R.string.mcp_error_details),
                        style = MaterialTheme.typography.bodySmall.copy(fontWeight = FontWeight.SemiBold),
                        color = MaterialTheme.colorScheme.error
                    )
                    Spacer(Modifier.height(4.dp))
                    Text(
                        text = status.error ?: "",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
            }
        }

        // 长按弹出的编辑/删除菜单
        Box {
            DropdownMenu(
                expanded = showMenu,
                onDismissRequest = { showMenu = false }
            ) {
                DropdownMenuItem(
                    text = { Text(stringResource(R.string.common_edit)) },
                    onClick = {
                        showMenu = false
                        onClick()
                    },
                    leadingIcon = {
                        Icon(Icons.Rounded.Dns, contentDescription = null, modifier = Modifier.size(18.dp))
                    }
                )
                DropdownMenuItem(
                    text = { Text(stringResource(R.string.common_delete), color = MaterialTheme.colorScheme.error) },
                    onClick = {
                        showMenu = false
                        onDelete()
                    },
                    leadingIcon = {
                        Icon(Icons.Rounded.Delete, contentDescription = null, modifier = Modifier.size(18.dp), tint = MaterialTheme.colorScheme.error)
                    }
                )
            }
        }
    }
}
