package com.mini.me_core.newui.designsystem.component.molecule

import com.mini.me_core.newui.designsystem.theme.appPalette
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.scaleOut
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.ArrowDropDown
import androidx.compose.material.icons.rounded.Check
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material.icons.rounded.Search
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.TransformOrigin
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Popup
import androidx.compose.ui.window.PopupProperties
import com.mini.me_core.newui.designsystem.token.generated.AppColor
import com.mini.me_core.newui.designsystem.token.generated.AppElevation
import com.mini.me_core.newui.designsystem.token.generated.AppRadius
import com.mini.me_core.newui.designsystem.token.generated.AppSizing
import com.mini.me_core.newui.designsystem.token.generated.AppSpacing
import com.mini.me_core.newui.designsystem.token.generated.AppStroke

/**
 * 可搜索下拉筛选（分子组 · AppDropdownFilter）
 *
 * 参考 iOSDropDown 的交互范式：一个「触发胶囊」内联展示当前已选（可折叠为「label · 已选 N 项」），
 * 点击展开带内置搜索框的下拉列表面板；选中项高亮 + 尾部品牌勾，激活态描边/箭头平滑回转。
 *
 * 语义约定：
 * - [onToggle] 由外部推进选中集合；[singleSelect] 语义（替换/清空）由外部在 handler 中实现，
 *   这样组件保持无状态、可预测。
 * - [onClear] 可选：提供时面板底部才渲染「清空已选」入口。
 */
@Composable
fun AppDropdownFilter(
    options: List<String>,
    selected: Set<Int>,
    onToggle: (Int) -> Unit,
    modifier: Modifier = Modifier,
    placeholder: String = "选择筛选",
    label: String = "筛选",
    singleSelect: Boolean = false,
    searchable: Boolean = true,
    maxPopupHeight: Int = 320,
    onClear: (() -> Unit)? = null,
) {
    var expanded by remember { mutableStateOf(false) }
    var query by remember { mutableStateOf("") }

    val displayText = remember(selected, options, placeholder, label, singleSelect) {
        when {
            selected.isEmpty() -> placeholder
            singleSelect -> options.getOrNull(selected.first()) ?: placeholder
            selected.size == 1 -> options.getOrNull(selected.first()) ?: placeholder
            else -> "$label · 已选 ${selected.size} 项"
        }
    }
    val hasSelection = selected.isNotEmpty()

    val triggerBorder by animateColorAsState(
        targetValue = if (expanded || hasSelection) appPalette().primary else appPalette().separator,
        label = "triggerBorder",
    )
    val rotation by animateFloatAsState(
        targetValue = if (expanded) 180f else 0f,
        animationSpec = spring(
            dampingRatio = Spring.DampingRatioMediumBouncy,
            stiffness = Spring.StiffnessLow,
        ),
        label = "dropdownArrow",
    )

    Box(modifier = modifier) {
        val triggerShape = RoundedCornerShape(AppRadius.Pill)
        Row(
            modifier = Modifier
                .clip(triggerShape)
                .background(appPalette().card)
                .border(AppStroke.Thin, triggerBorder, triggerShape)
                .clickable {
                    expanded = !expanded
                    if (expanded) query = ""
                }
                .padding(start = AppSpacing.Md, end = AppSpacing.Xs, top = 6.dp, bottom = 6.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            if (hasSelection) {
                Box(
                    Modifier
                        .size(AppSizing.IconXs)
                        .clip(RoundedCornerShape(AppRadius.Pill))
                        .background(appPalette().primary.copy(alpha = 0.15f)),
                    contentAlignment = Alignment.Center,
                ) {
                    Text(
                        text = selected.size.toString(),
                        style = MaterialTheme.typography.labelSmall,
                        fontWeight = FontWeight.Bold,
                        color = appPalette().primary,
                    )
                }
            } else {
                // 装饰图标：旁侧已有文字/语义，跳过无障碍
                Icon(
                    imageVector = Icons.Rounded.Search,
                    contentDescription = null,
                    tint = if (expanded) appPalette().primary else appPalette().labelSecondary,
                    modifier = Modifier.size(AppSizing.IconS),
                )
            }
            Spacer(Modifier.width(AppSpacing.Sm))
            Text(
                text = displayText,
                style = MaterialTheme.typography.labelMedium,
                fontWeight = if (hasSelection) FontWeight.SemiBold else FontWeight.Normal,
                color = if (expanded || hasSelection) appPalette().ink else appPalette().labelSecondary,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            Spacer(Modifier.width(AppSpacing.Xs))
            // 装饰图标：旁侧已有文字/语义，跳过无障碍
            Icon(
                imageVector = Icons.Rounded.ArrowDropDown,
                contentDescription = null,
                tint = appPalette().labelSecondary,
                modifier = Modifier
                    .size(AppSizing.IconM)
                    .rotate(rotation),
            )
        }

        AnimatedVisibility(
            visible = expanded,
            enter = fadeIn(spring(stiffness = Spring.StiffnessMedium)) +
                scaleIn(
                    initialScale = 0.96f,
                    transformOrigin = TransformOrigin(0.5f, 0f),
                    animationSpec = spring(
                        dampingRatio = Spring.DampingRatioMediumBouncy,
                        stiffness = Spring.StiffnessMediumLow,
                    ),
                ),
            exit = fadeOut(tween(120)) +
                scaleOut(
                    targetScale = 0.98f,
                    animationSpec = tween(120),
                ),
        ) {
            if (expanded) {
                Popup(
                    onDismissRequest = { expanded = false },
                    properties = PopupProperties(focusable = true),
                ) {
                    val shape = RoundedCornerShape(AppRadius.Lg)
                    val filtered = remember(query, options) {
                        if (query.isBlank()) options
                        else options.filter { it.contains(query.trim(), ignoreCase = true) }
                    }
                    Column(
                        Modifier
                            .fillMaxWidth()
                            .shadow(AppElevation.Z3, shape, clip = true)
                            .background(appPalette().card)
                            .border(AppStroke.Thin, appPalette().separator, shape)
                            .clip(shape)
                            .padding(AppSpacing.Sm),
                    ) {
                        if (searchable) {
                            FilterDropdownSearch(query, onQueryChange = { query = it })
                        }
                        when {
                            filtered.isEmpty() -> {
                                Box(
                                    Modifier
                                        .fillMaxWidth()
                                        .padding(vertical = AppSpacing.Xl),
                                    contentAlignment = Alignment.Center,
                                ) {
                                    Text(
                                        text = "无匹配项",
                                        style = MaterialTheme.typography.bodyMedium,
                                        color = appPalette().labelSecondary,
                                    )
                                }
                            }
                            else -> {
                                LazyColumn(
                                    modifier = Modifier.heightIn(max = maxPopupHeight.dp),
                                    verticalArrangement = Arrangement.spacedBy(AppSpacing.Tiny),
                                ) {
                                    items(filtered, key = { it }) { item ->
                                        val index = options.indexOf(item)
                                        val checked = index in selected
                                        AppDropdownRow(
                                            label = item,
                                            checked = checked,
                                            onClick = { onToggle(index) },
                                        )
                                    }
                                }
                            }
                        }
                        if (onClear != null && hasSelection) {
                            Box(
                                Modifier
                                    .fillMaxWidth()
                                    .padding(top = AppSpacing.Xs, bottom = AppSpacing.Xs)
                                    .clip(RoundedCornerShape(AppRadius.Md))
                                    .clickable(onClick = onClear)
                                    .padding(vertical = AppSpacing.Sm),
                                contentAlignment = Alignment.Center,
                            ) {
                                Text(
                                    text = "清空已选",
                                    style = MaterialTheme.typography.labelMedium,
                                    color = AppColor.StatusDanger,
                                )
                            }
                        }
                    }
                }
            }
        }
    }
}

/** 下拉面板中的单行选项：选中态品牌浅底 + 半字重 + 尾部弹簧勾。 */
@Composable
private fun AppDropdownRow(
    label: String,
    checked: Boolean,
    onClick: () -> Unit,
) {
    val shape = RoundedCornerShape(AppRadius.Md)
    val bg by animateColorAsState(
        targetValue = if (checked) appPalette().primary.copy(alpha = 0.1f) else Color.Transparent,
        label = "rowBg",
    )
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(shape)
            .background(bg)
            .clickable(onClick = onClick)
            .padding(vertical = AppSpacing.Sm, horizontal = AppSpacing.Md),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            text = label,
            style = MaterialTheme.typography.bodyMedium,
            fontWeight = if (checked) FontWeight.SemiBold else FontWeight.Normal,
            color = if (checked) appPalette().ink else appPalette().labelSecondary,
            modifier = Modifier.weight(1f),
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )
        AnimatedVisibility(
            visible = checked,
            enter = scaleIn(
                animationSpec = spring(
                    dampingRatio = Spring.DampingRatioMediumBouncy,
                    stiffness = Spring.StiffnessMedium,
                ),
            ),
            exit = scaleOut(animationSpec = tween(90)),
        ) {
            // 装饰图标：旁侧已有文字/语义，跳过无障碍
            Icon(
                imageVector = Icons.Rounded.Check,
                contentDescription = null,
                tint = appPalette().primary,
                modifier = Modifier.size(AppSizing.IconM),
            )
        }
    }
}

/** 下拉面板内置搜索框：圆角填充式 + 前置放大镜 + 一键清除。 */
@Composable
private fun FilterDropdownSearch(
    query: String,
    onQueryChange: (String) -> Unit,
    modifier: Modifier = Modifier,
) {
    val shape = RoundedCornerShape(AppRadius.Md)
    Row(
        modifier = modifier
            .fillMaxWidth()
            .clip(shape)
            .background(appPalette().surfaceDim)
            .padding(horizontal = AppSpacing.Md, vertical = AppSpacing.Tiny),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        // 装饰图标：旁侧已有文字/语义，跳过无障碍
        Icon(
            imageVector = Icons.Rounded.Search,
            contentDescription = null,
            tint = appPalette().labelSecondary,
            modifier = Modifier.size(AppSizing.IconS),
        )
        Box(
            Modifier
                .weight(1f)
                .padding(horizontal = AppSpacing.Sm),
        ) {
            if (query.isEmpty()) {
                Text(
                    text = "搜索筛选",
                    style = MaterialTheme.typography.bodyMedium,
                    color = appPalette().labelTertiary,
                )
            }
            BasicTextField(
                value = query,
                onValueChange = onQueryChange,
                modifier = Modifier.fillMaxWidth(),
                singleLine = true,
                textStyle = MaterialTheme.typography.bodyMedium.copy(color = appPalette().ink),
                cursorBrush = SolidColor(appPalette().primary),
            )
        }
        if (query.isNotEmpty()) {
            Icon(
                imageVector = Icons.Rounded.Close,
                contentDescription = "清除",
                tint = appPalette().labelSecondary,
                modifier = Modifier
                    .size(AppSizing.IconButton)
                    .clip(RoundedCornerShape(AppRadius.Pill))
                    .clickable { onQueryChange("") }
                    .padding(AppSpacing.Sm),
            )
        }
    }
}