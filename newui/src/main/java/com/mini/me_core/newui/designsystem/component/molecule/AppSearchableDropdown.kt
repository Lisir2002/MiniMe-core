package com.mini.me_core.newui.designsystem.component.molecule

import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
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
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.ArrowDropDown
import androidx.compose.material.icons.rounded.Check
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material.icons.rounded.Search
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.input.key.KeyEvent
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.key
import androidx.compose.ui.input.key.onPreviewKeyEvent
import androidx.compose.ui.input.key.type
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.IntRect
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Popup
import androidx.compose.ui.window.PopupPositionProvider
import androidx.compose.ui.window.PopupProperties
import com.mini.me_core.newui.designsystem.theme.appPalette
import kotlin.math.roundToInt
import com.mini.me_core.newui.designsystem.token.generated.AppElevation
import com.mini.me_core.newui.designsystem.token.generated.AppRadius
import com.mini.me_core.newui.designsystem.token.generated.AppSizing
import com.mini.me_core.newui.designsystem.token.generated.AppSpacing
import com.mini.me_core.newui.designsystem.token.generated.AppStroke

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
 * 设计参考 shadcn/ui Combobox（Radix cmdk + Popover）、Ariakit useComboboxState、M3 ExposedDropdownMenu：
 * 文本输入框常驻，聚焦即过滤；键盘焦点始终留在输入框，↑↓ 只移动高亮索引（不切焦），
 * Enter 选中、Esc 关闭。支持本地全量与远程/异步两种数据源。
 *
 * 架构要点（相对旧版的关键修正）：
 *  - 弹出层**不用 M3 DropdownMenu**（其内容容器是 `width(IntrinsicSize.Max)+verticalScroll`，
 *    内嵌任何 SubcomposeLayout 都会在 intrinsic 测量阶段崩溃），改用独立 `Popup` +
 *    [PopupPositionProvider]，锚定字段真实窗口矩形，下方空间不足自动翻上、右缘不足自动翻左、
 *    夹紧屏幕边距；面板内可以安全地用 [LazyColumn]。
 *  - 颜色一律走 [appPalette]（明暗感知），尺寸 / 圆角 / 描边 / 间距走 AppTokens，不再写死
 *    `MaterialTheme.colorScheme` 浅色值或 `Color(0x...)`。
 *
 * @param value 当前选中 label；null 表示未选。外部改值即时回显。
 * @param onSelect 选中候选项回调（组件同时收起下拉）。
 * @param options 本地数据源。
 * @param remoteQuery 远程/异步数据源：非空时忽略 [options]，组件把当前 query 透传给它取结果
 *   （调用方自行防抖 150–300ms）。
 * @param loading 远程过滤中（显示加载行）。
 * @param onQueryChange query 透传（驱动外部远程检索，也可忽略让组件内部过滤）。
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
    val palette = appPalette()
    val density = LocalDensity.current

    var expanded by remember { mutableStateOf(false) }
    var query by remember { mutableStateOf("") }
    var highlight by remember { mutableIntStateOf(0) }
    val listState = rememberLazyListState()
    val focusRequester = remember { FocusRequester() }
    // 字段宽度（px）：让弹出面板与字段同宽，避免面板忽宽忽窄。
    var fieldWidthPx by remember { mutableIntStateOf(0) }

    val isRemote = remoteQuery != null
    val results: List<AppSearchableOption> = remember(query, options, isRemote, remoteQuery) {
        when {
            isRemote -> remoteQuery!!(query)
            query.isBlank() -> options
            else -> options.filteredByQuery(query)
        }
    }

    // 键盘 ↑↓ 移动高亮后，把高亮项滚入可见区（独立 Popup + LazyColumn，安全可滚）。
    LaunchedEffect(highlight, results.size) {
        if (results.isNotEmpty() && highlight < listState.layoutInfo.totalItemsCount) {
            listState.animateScrollToItem(highlight.coerceIn(0, results.lastIndex))
        }
    }

    /** 点开下拉：灌入当前已选值作为搜索起点，并请求输入焦点（拉起 IME）。 */
    fun open() {
        if (expanded) return
        query = value ?: ""
        onQueryChange?.invoke(query)
        highlight = 0
        expanded = true
        focusRequester.requestFocus()
    }

    fun pick(opt: AppSearchableOption) {
        expanded = false
        onSelect(opt)
    }

    // 键盘导航：焦点留在输入框，↑↓ 循环高亮、Enter 选中、Esc 关闭。
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

    val fieldShape = RoundedCornerShape(AppRadius.Md)
    val arrowRotation by animateFloatAsState(
        targetValue = if (expanded) 180f else 0f,
        animationSpec = spring(dampingRatio = Spring.DampingRatioMediumBouncy, stiffness = Spring.StiffnessLow),
        label = "searchDropdownArrow",
    )
    val fieldText = if (expanded) query else (value ?: "")

    // 标准定位提供者：直接以字段（anchorBounds）窗口矩形计算落点。
    val positionProvider = remember {
        object : PopupPositionProvider {
            override fun calculatePosition(
                anchorBounds: IntRect,
                windowSize: IntSize,
                layoutDirection: LayoutDirection,
                popupContentSize: IntSize,
            ): IntOffset {
                val marginPx = with(density) { AppSpacing.Sm.toPx().roundToInt() }
                // 水平：默认与字段同左缘；右缘溢出则右对齐字段右缘，再夹紧屏幕边距。
                var x = anchorBounds.left
                if (x + popupContentSize.width > windowSize.width - marginPx) {
                    x = anchorBounds.right - popupContentSize.width
                }
                x = x.coerceIn(
                    marginPx,
                    (windowSize.width - popupContentSize.width - marginPx).coerceAtLeast(marginPx),
                )
                // 垂直：默认贴在字段下方；下方空间不足则翻到字段上方。
                var y = anchorBounds.bottom
                if (y + popupContentSize.height > windowSize.height - marginPx) {
                    y = anchorBounds.top - popupContentSize.height
                }
                y = y.coerceIn(
                    marginPx,
                    (windowSize.height - popupContentSize.height - marginPx).coerceAtLeast(marginPx),
                )
                return IntOffset(x, y)
            }
        }
    }

    Column(modifier) {
        if (label != null) {
            Text(
                text = label,
                style = MaterialTheme.typography.labelMedium,
                color = palette.labelSecondary,
                modifier = Modifier.padding(bottom = AppSpacing.Xs),
            )
        }
        Box {
            // —— 字段：填充式输入框，聚焦主色描边 ——
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .onGloballyPositioned { fieldWidthPx = it.size.width }
                    .clip(fieldShape)
                    .background(palette.surfaceDim)
                    .border(
                        width = AppStroke.Thin,
                        color = if (expanded) palette.primary else palette.separator,
                        shape = fieldShape,
                    )
                    .onPreviewKeyEvent(::handleKey)
                    .clickable { open() }
                    .padding(start = AppSpacing.Md, end = AppSpacing.Sm, top = AppSpacing.Sm, bottom = AppSpacing.Sm),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                if (leadingIcon != null) {
                    Icon(
                        imageVector = leadingIcon,
                        contentDescription = null,
                        tint = palette.labelSecondary,
                        modifier = Modifier.size(AppSizing.IconM),
                    )
                    Spacer(Modifier.width(AppSpacing.Sm))
                }
                Box(Modifier.weight(1f)) {
                    if (fieldText.isEmpty()) {
                        Text(
                            text = placeholder,
                            style = MaterialTheme.typography.bodyLarge,
                            color = palette.labelTertiary,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                        )
                    }
                    BasicTextField(
                        value = fieldText,
                        onValueChange = {
                            query = it
                            onQueryChange?.invoke(it)
                        },
                        modifier = Modifier
                            .fillMaxWidth()
                            .focusRequester(focusRequester)
                            .onFocusChanged { if (!it.isFocused && expanded) expanded = false },
                        singleLine = true,
                        textStyle = MaterialTheme.typography.bodyLarge.copy(color = palette.ink),
                        cursorBrush = SolidColor(palette.primary),
                    )
                }
                if (query.isNotEmpty()) {
                    Icon(
                        imageVector = Icons.Rounded.Close,
                        contentDescription = "清空",
                        tint = palette.labelSecondary,
                        modifier = Modifier
                            .size(AppSizing.IconM)
                            .clip(RoundedCornerShape(AppRadius.Pill))
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
                    tint = palette.labelSecondary,
                    modifier = Modifier
                        .size(AppSizing.IconM)
                        .graphicsLayer { rotationZ = arrowRotation },
                )
            }

            // —— 弹出面板：独立 Popup + 固定高度 LazyColumn，安全且可滚 ——
            // focusable=false：弹窗不抢输入焦点，字段的 IME 与光标保持；
            // 外部点击由字段 onFocusChanged 失焦时关闭。
            if (expanded) {
                Popup(
                    popupPositionProvider = positionProvider,
                    onDismissRequest = { expanded = false },
                    properties = PopupProperties(focusable = false),
                ) {
                    val panelShape = RoundedCornerShape(AppRadius.Md)
                    val panelWidth = with(density) { fieldWidthPx.toDp() }.coerceAtLeast(220.dp)
                    Column(
                        modifier = Modifier
                            .shadow(AppElevation.Z3, panelShape, clip = false)
                            .width(panelWidth)
                            .clip(panelShape)
                            .background(palette.card)
                            .padding(vertical = AppSpacing.Xs),
                    ) {
                        when {
                            loading -> SearchDropdownLoadingRow(palette.primary)
                            results.isEmpty() -> SearchDropdownEmptyState(palette.labelSecondary)
                            else -> LazyColumn(
                                state = listState,
                                modifier = Modifier.heightIn(max = 280.dp),
                            ) {
                                itemsIndexed(results) { index, opt ->
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
    val palette = appPalette()
    val rowShape = RoundedCornerShape(AppRadius.Sm)
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = AppSpacing.Xs, vertical = AppSpacing.Tiny)
            .background(
                color = if (highlighted) palette.primaryOverlay12 else androidx.compose.ui.graphics.Color.Transparent,
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
                tint = palette.labelSecondary,
                modifier = Modifier.size(AppSizing.IconM),
            )
            Spacer(Modifier.width(AppSpacing.Sm))
        }
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = option.label,
                style = MaterialTheme.typography.bodyLarge,
                color = palette.ink,
                fontWeight = if (selected) FontWeight.SemiBold else FontWeight.Normal,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            if (!option.subtitle.isNullOrBlank()) {
                Text(
                    text = option.subtitle,
                    style = MaterialTheme.typography.bodySmall,
                    color = palette.labelSecondary,
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
                color = palette.labelTertiary,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        }
        if (selected) {
            Spacer(Modifier.width(AppSpacing.Sm))
            Icon(
                imageVector = Icons.Rounded.Check,
                contentDescription = null,
                tint = palette.primary,
                modifier = Modifier.size(AppSizing.IconS),
            )
        }
    }
}

/** 空态卡片：放大镜 + 未找到匹配项 + 建议文案。 */
@Composable
private fun SearchDropdownEmptyState(tint: androidx.compose.ui.graphics.Color) {
    val palette = appPalette()
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
            tint = tint,
            modifier = Modifier.size(AppSizing.IconL),
        )
        Spacer(Modifier.height(AppSpacing.Sm))
        Text(
            text = "未找到匹配项",
            style = MaterialTheme.typography.bodyMedium,
            color = palette.ink,
        )
        Spacer(Modifier.height(AppSpacing.Xs))
        Text(
            text = "试试其他关键词",
            style = MaterialTheme.typography.bodySmall,
            color = palette.labelSecondary,
        )
    }
}

/** 远程过滤中的小转圈行。 */
@Composable
private fun SearchDropdownLoadingRow(spinnerColor: androidx.compose.ui.graphics.Color) {
    val palette = appPalette()
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
            color = spinnerColor,
        )
        Spacer(Modifier.width(AppSpacing.Sm))
        Text(
            text = "加载中…",
            style = MaterialTheme.typography.bodyMedium,
            color = palette.labelSecondary,
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
