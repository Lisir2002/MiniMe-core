package com.mini.me_core.newui.designsystem.component.molecule

import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.relocation.BringIntoViewRequester
import androidx.compose.foundation.relocation.bringIntoViewRequester
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.ArrowDropDown
import androidx.compose.material.icons.rounded.Check
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material.icons.rounded.Search
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextField
import androidx.compose.material3.TextFieldDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.input.key.KeyEvent
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.key
import androidx.compose.ui.input.key.onPreviewKeyEvent
import androidx.compose.ui.input.key.type
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.mini.me_core.newui.designsystem.token.generated.AppRadius
import com.mini.me_core.newui.designsystem.token.generated.AppSizing
import com.mini.me_core.newui.designsystem.token.generated.AppSpacing

/**
 * 可搜索下拉候选项（分子组 · AppSearchableDropdown 的数据单元）。
 *
 * @property label 主标签（必选）；选中态与过滤均以它为准。
 * @property subtitle 可选副标题（灰字次行）。
 * @property icon 可选前置图标。
 * @property trailing 可选尾部信息（如版本号 / 计数，灰字）。
 */
data class AppSearchableOption(
    val label: String,
    val subtitle: String? = null,
    val icon: ImageVector? = null,
    val trailing: String? = null,
)

/**
 * 可搜索下拉选择框（分子组 · AppSearchableDropdown）——设计系统统一 Combobox。
 *
 * 参考 shadcn/ui Combobox（Radix Command + Popover）与 M3 ExposedDropdownMenu：
 * 文本框常驻显示当前值，点击展开候选面板，**输入即过滤**；支持两种数据源：
 *  - **本地**：传 [options]，组件内按「前缀命中优先、包含命中次之、忽略大小写」过滤排序；
 *  - **远程/异步**：传 [remoteQuery]（把 query 映射为结果列表，调用方自行防抖 150–300ms），
 *    并配合 [loading] 注入过滤中的转圈态。
 *
 * 交互细节（对齐 spec §1）：
 *  - 未选显示灰色 placeholder，已选显示选中 label（onSurface）；右侧箭头随展开旋转；
 *    输入有内容时尾部出现清除按钮（只清空 query，不清已选 value）；
 *  - 弹出层锚定字段下方，下方空间不足时 M3 DropdownMenu 自动向上翻转；点外部 / 返回键关闭；
 *  - 键盘导航（外接键盘 / DPAD）：↑/↓ 循环移动高亮、Enter 选中高亮项、Esc 关闭，
 *    高亮项自动滚动到可见区；
 *  - 过滤无结果渲染空态卡片（放大镜 + 「未找到匹配项」+ 「试试其他关键词」）；
 *  - 远程过滤期间渲染小转圈行；当前选中项尾部显示主色对勾；
 *  - 菜单高度 `heightIn(max = 280dp)`，内部 LazyColumn。
 *
 * @param value 当前选中 label；null 表示未选。外部改值会即时回显（修复旧 AppComboBox 不同步问题）。
 * @param onSelect 选中候选项回调（组件同时收起下拉）。
 * @param options 本地数据源。
 * @param remoteQuery 远程/异步数据源：非空时忽略 [options]，组件把当前 query 透传给它取结果。
 * @param loading 远程过滤中（显示转圈行）。
 * @param onQueryChange query 透传（可驱动外部远程检索，也可忽略让组件内部处理）。
 */
@Composable
fun AppSearchableDropdown(
    value: String?,
    onSelect: (AppSearchableOption) -> Unit,
    modifier: Modifier = Modifier,
    label: String? = null,
    placeholder: String = "输入或选择…",
    leadingIcon: ImageVector? = null,
    options: List<AppSearchableOption> = emptyList(),
    remoteQuery: ((String) -> List<AppSearchableOption>)? = null,
    loading: Boolean = false,
    onQueryChange: ((String) -> Unit)? = null,
) {
    var expanded by remember { mutableStateOf(false) }
    var query by remember { mutableStateOf("") }
    var highlight by remember { mutableIntStateOf(0) }

    val isRemote = remoteQuery != null
    val results: List<AppSearchableOption> = remember(query, options, isRemote, remoteQuery) {
        when {
            isRemote -> remoteQuery!!(query)
            query.isBlank() -> options
            else -> options.filteredByQuery(query)
        }
    }

    // 展开时把已选 label 灌入输入框，并重置高亮到首项；收起后字段回显外部 value（修复不同步）。
    LaunchedEffect(expanded) {
        if (expanded) {
            query = value ?: ""
            highlight = 0
        }
    }

    // 每个候选项一个 BringIntoViewRequester：键盘上下移动高亮时，驱动外层 DropdownMenu
    // 自带的 verticalScroll 把高亮项滚入可见区。不能用 LazyColumn——M3 DropdownMenu 内容容器
    // 本身是 width(IntrinsicSize.Max) + verticalScroll，内嵌 SubcomposeLayout（Lazy*）会在
    // intrinsic 测量阶段直接崩溃。
    val bringRequesters = remember(results.size) {
        List(results.size) { BringIntoViewRequester() }
    }
    LaunchedEffect(highlight, results.size) {
        if (results.isNotEmpty()) {
            bringRequesters.getOrNull(highlight.coerceIn(0, results.lastIndex))
                ?.bringIntoView()
        }
    }

    fun pick(opt: AppSearchableOption) {
        expanded = false
        onSelect(opt)
    }

    // 键盘导航：↑/↓ 循环、Enter 选中、Esc 关闭。
    fun handleKey(ev: KeyEvent): Boolean {
        if (ev.type != KeyEventType.KeyDown) return false
        return when (ev.key) {
            Key.DirectionDown -> {
                if (results.isNotEmpty()) highlight = (highlight + 1) % results.size
                true
            }
            Key.DirectionUp -> {
                if (results.isNotEmpty()) highlight = (highlight - 1 + results.size) % results.size
                true
            }
            Key.Enter -> {
                results.getOrNull(highlight)?.let { pick(it) }
                true
            }
            Key.Escape -> {
                expanded = false
                true
            }
            else -> false
        }
    }

    val shape = RoundedCornerShape(AppRadius.Sm)
    val arrowRotation by animateFloatAsState(
        targetValue = if (expanded) 180f else 0f,
        animationSpec = spring(dampingRatio = Spring.DampingRatioMediumBouncy, stiffness = Spring.StiffnessLow),
        label = "dropdownArrow",
    )
    // 展开态展示输入中的 query；收起态回显外部 value（未选则空串，由 placeholder 占位）。
    val fieldText = if (expanded) query else (value ?: "")

    Box(modifier) {
        Column {
            if (label != null) {
                Text(
                    text = label,
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(bottom = AppSpacing.Xs),
                )
            }
            TextField(
                value = fieldText,
                onValueChange = {
                    query = it
                    onQueryChange?.invoke(it)
                    if (!expanded) expanded = true
                },
                singleLine = true,
                placeholder = {
                    Text(
                        text = if (value.isNullOrBlank()) placeholder else value,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                },
                leadingIcon = leadingIcon?.let {
                    { Icon(it, contentDescription = null, tint = MaterialTheme.colorScheme.onSurfaceVariant) }
                },
                trailingIcon = {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        if (query.isNotEmpty()) {
                            Icon(
                                imageVector = Icons.Rounded.Close,
                                contentDescription = "清空",
                                tint = MaterialTheme.colorScheme.onSurfaceVariant,
                                modifier = Modifier
                                    .size(AppSizing.IconM)
                                    .clickable {
                                        query = ""
                                        onQueryChange?.invoke("")
                                    },
                            )
                            Spacer(Modifier.width(AppSpacing.Sm))
                        }
                        Icon(
                            imageVector = Icons.Rounded.ArrowDropDown,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.graphicsLayer { rotationZ = arrowRotation },
                        )
                    }
                },
                shape = shape,
                colors = TextFieldDefaults.colors(
                    focusedContainerColor = MaterialTheme.colorScheme.surfaceVariant,
                    unfocusedContainerColor = MaterialTheme.colorScheme.surfaceVariant,
                    disabledContainerColor = MaterialTheme.colorScheme.surfaceVariant,
                ),
                modifier = Modifier.fillMaxWidth(),
            )
        }

        DropdownMenu(
            expanded = expanded,
            onDismissRequest = { expanded = false },
            shape = RoundedCornerShape(AppRadius.Md),
            modifier = Modifier.widthIn(min = 220.dp),
        ) {
            // 用普通 Column 渲染候选项：DropdownMenu 内容容器自带 width(IntrinsicSize.Max)
            // 与 verticalScroll，再嵌套 LazyColumn（SubcomposeLayout）会触发 intrinsic 测量崩溃。
            // 高度上限交给外层容器；候选项数量受控（本地全量/远程单页），无需懒加载。
            Column(
                modifier = Modifier
                    .heightIn(max = 280.dp)
                    .onPreviewKeyEvent(::handleKey),
                verticalArrangement = Arrangement.spacedBy(AppSpacing.Tiny),
            ) {
                when {
                    loading -> SearchDropdownLoadingRow()
                    results.isEmpty() -> SearchDropdownEmptyState()
                    else -> results.forEachIndexed { index, opt ->
                        Box(
                            modifier = Modifier
                                .fillMaxWidth()
                                .then(
                                    bringRequesters.getOrNull(index)?.let {
                                        Modifier.bringIntoViewRequester(it)
                                    } ?: Modifier
                                ),
                        ) {
                            SearchDropdownRow(
                                option = opt,
                                highlighted = index == highlight,
                                selected = opt.label == value,
                                onClick = { pick(opt) },
                            )
                        }
                    }
                }
            }
        }
    }
}

/** 候选项行：高亮底 + 可选 icon/副标题/尾部信息 + 选中主色对勾。 */
@Composable
private fun SearchDropdownRow(
    option: AppSearchableOption,
    highlighted: Boolean,
    selected: Boolean,
    onClick: () -> Unit,
) {
    val rowShape = RoundedCornerShape(AppRadius.Sm)
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = AppSpacing.Xs, vertical = 2.dp)
            .background(
                color = if (highlighted) MaterialTheme.colorScheme.surfaceVariant else Color.Transparent,
                shape = rowShape,
            )
            .clickable(onClick = AppHaptics.click(onClick))
            .padding(horizontal = AppSpacing.Md, vertical = AppSpacing.Sm),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        if (option.icon != null) {
            Icon(
                imageVector = option.icon,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.size(AppSizing.IconM),
            )
            Spacer(Modifier.width(AppSpacing.Sm))
        }
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = option.label,
                style = MaterialTheme.typography.bodyLarge,
                color = MaterialTheme.colorScheme.onSurface,
                fontWeight = if (selected) FontWeight.SemiBold else FontWeight.Normal,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            if (!option.subtitle.isNullOrBlank()) {
                Text(
                    text = option.subtitle,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }
        }
        if (!option.trailing.isNullOrBlank()) {
            Spacer(Modifier.width(AppSpacing.Sm))
            Text(
                text = option.trailing,
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        }
        if (selected) {
            Spacer(Modifier.width(AppSpacing.Sm))
            Icon(
                imageVector = Icons.Rounded.Check,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.primary,
                modifier = Modifier.size(AppSizing.IconS),
            )
        }
    }
}

/** 空态卡片：放大镜 + 未找到匹配项 + 建议文案。 */
@Composable
private fun SearchDropdownEmptyState() {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .heightIn(min = 120.dp)
            .padding(vertical = AppSpacing.Xl),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center,
    ) {
        Icon(
            imageVector = Icons.Rounded.Search,
            contentDescription = null,
            tint = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.size(AppSizing.IconL),
        )
        Spacer(Modifier.height(AppSpacing.Sm))
        Text(
            text = "未找到匹配项",
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurface,
        )
        Spacer(Modifier.height(AppSpacing.Xs))
        Text(
            text = "试试其他关键词",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}

/** 远程过滤中的小转圈行。 */
@Composable
private fun SearchDropdownLoadingRow() {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = AppSpacing.Md, vertical = AppSpacing.Lg),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.Center,
    ) {
        CircularProgressIndicator(
            modifier = Modifier.size(AppSizing.IconS),
            strokeWidth = 2.dp,
            color = MaterialTheme.colorScheme.primary,
        )
        Spacer(Modifier.width(AppSpacing.Sm))
        Text(
            text = "加载中…",
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}

/**
 * 本地过滤排序：忽略大小写；**前缀命中优先**，包含命中次之；
 * 中文按包含匹配即可（lowercase 对汉字无副作用）。
 */
private fun List<AppSearchableOption>.filteredByQuery(q: String): List<AppSearchableOption> {
    val needle = q.trim().lowercase()
    if (needle.isEmpty()) return this
    data class Hit(val opt: AppSearchableOption, val prefix: Boolean)
    val hits = mapNotNull { opt ->
        val label = opt.label.lowercase()
        when {
            label.startsWith(needle) -> Hit(opt, prefix = true)
            label.contains(needle) -> Hit(opt, prefix = false)
            else -> null
        }
    }
    return hits
        .sortedWith(compareByDescending<Hit> { it.prefix }.thenBy { it.opt.label })
        .map { it.opt }
}
