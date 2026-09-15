package com.mini.me_core.newui.designsystem.component.molecule

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.ChevronRight
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
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
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.mini.me_core.newui.designsystem.token.generated.AppSizing
import com.mini.me_core.newui.designsystem.token.generated.AppSpacing
import kotlin.jvm.JvmName

/**
 * 面包屑项：[label] 显示文本；[onClick] 为 null 表示不可点击（末项或禁用态）。
 */
data class AppCrumb(
    val label: String,
    val onClick: (() -> Unit)? = null,
)

/**
 * 面包屑（分子组 · AppBreadcrumb）：路径段以 ChevronRight 分隔，末级高亮加粗。
 * 用于深层结构的返回定位（远程 SSH 路径 / 知识库层级），比 Tab 更轻量。
 *
 * 规则：
 *  - 超过 [overflow]（默认 2，即首项 + 末 2 项）时中间折叠为单个「…」；
 *  - 「…」可点击，弹出下拉列出被折叠的中间项；
 *  - 最后一项不可点击（onClick 为 null 同样不响应）；
 *  - 单项 label 过长：[MAX_CRUMB_WIDTH] + Ellipsis 截断。
 */
@Composable
fun AppBreadcrumb(
    items: List<AppCrumb>,
    modifier: Modifier = Modifier,
    overflow: Int = 2,
) {
    // 折叠：首项 + … + 末 overflow 项
    val visible: List<Any> = remember(items, overflow) {
        if (items.size > overflow + 1) {
            // items[0] + Ellipsis + items.takeLast(overflow)
            val hidden = items.subList(1, items.size - overflow)
            mutableListOf<Any>(items.first(), Ellipsis(hidden)).apply {
                addAll(items.takeLast(overflow))
            }
        } else {
            items
        }
    }

    Row(
        modifier = modifier,
        horizontalArrangement = Arrangement.spacedBy(AppSpacing.Xs),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        visible.forEachIndexed { index, segment ->
            if (index > 0) {
                // 装饰图标：旁侧已有文字/语义，跳过无障碍
                Icon(
                    imageVector = Icons.Rounded.ChevronRight,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.size(AppSizing.IconS),
                )
            }
            when (segment) {
                is Ellipsis -> {
                    EllipsisCrumb(
                        hidden = segment.items,
                    )
                }
                is AppCrumb -> {
                    val isLast = index == visible.lastIndex
                    val clickable = !isLast && segment.onClick != null
                    Text(
                        text = segment.label,
                        style = MaterialTheme.typography.labelMedium,
                        fontWeight = if (isLast) FontWeight.SemiBold else FontWeight.Normal,
                        color = if (isLast) {
                            MaterialTheme.colorScheme.onSurface
                        } else {
                            MaterialTheme.colorScheme.onSurfaceVariant
                        },
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        modifier = Modifier
                            .widthIn(max = MAX_CRUMB_WIDTH)
                            .then(
                                if (clickable) {
                                    Modifier.clickable(onClick = AppHaptics.click { segment.onClick?.invoke() })
                                } else {
                                    Modifier
                                }
                            )
                            .padding(horizontal = AppSpacing.Xs),
                    )
                }
            }
        }
    }
}

/** 向后兼容：旧调用传 List<String>，自动包装为 onClick=null 的不可点击项。 */
@JvmName("appBreadcrumbStrings")
@Composable
fun AppBreadcrumb(
    items: List<String>,
    modifier: Modifier = Modifier,
    overflow: Int = 2,
) {
    AppBreadcrumb(
        items = items.map { AppCrumb(label = it, onClick = null) },
        modifier = modifier,
        overflow = overflow,
    )
}

/** 折叠占位符：「…」可点出下拉列出被折叠的中间项。 */
private data class Ellipsis(val items: List<AppCrumb>)

@Composable
private fun EllipsisCrumb(hidden: List<AppCrumb>) {
    var menuExpanded by remember { mutableStateOf(false) }
    Box {
        Text(
            text = "…",
            style = MaterialTheme.typography.labelMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier
                .clickable(onClick = AppHaptics.click { menuExpanded = true })
                .padding(horizontal = AppSpacing.Xs),
        )
        DropdownMenu(
            expanded = menuExpanded,
            onDismissRequest = { menuExpanded = false },
        ) {
            hidden.forEach { crumb ->
                DropdownMenuItem(
                    text = {
                        Text(
                            text = crumb.label,
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurface,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                        )
                    },
                    onClick = AppHaptics.click {
                        menuExpanded = false
                        crumb.onClick?.invoke()
                    },
                )
            }
        }
    }
}

/** 单项面包屑最大宽度（超过则 Ellipsis 截断）。 */
private val MAX_CRUMB_WIDTH = 120.dp
