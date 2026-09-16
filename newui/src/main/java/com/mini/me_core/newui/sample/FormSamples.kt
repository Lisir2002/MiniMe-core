package com.mini.me_core.newui.sample

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Person
import androidx.compose.material.icons.rounded.Search
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import com.mini.me_core.newui.designsystem.component.molecule.AppAccordion
import com.mini.me_core.newui.designsystem.component.molecule.AppBooleanFilter
import com.mini.me_core.newui.designsystem.component.molecule.AppChecklistFilter
import com.mini.me_core.newui.designsystem.component.molecule.AppChecklistToolbar
import com.mini.me_core.newui.designsystem.component.molecule.AppCheckRow
import com.mini.me_core.newui.designsystem.component.molecule.AppCountedTextField
import com.mini.me_core.newui.designsystem.component.molecule.AppDateFilter
import com.mini.me_core.newui.designsystem.component.molecule.AppDropdownFilter
import com.mini.me_core.newui.designsystem.component.molecule.AppFilledTextField
import com.mini.me_core.newui.designsystem.component.molecule.AppFilterChips
import com.mini.me_core.newui.designsystem.component.molecule.AppFilterField
import com.mini.me_core.newui.designsystem.component.molecule.AppFilterSheet
import com.mini.me_core.newui.designsystem.component.molecule.AppFilterTokens
import com.mini.me_core.newui.designsystem.component.molecule.AppInputValidity
import com.mini.me_core.newui.designsystem.component.molecule.AppMessageField
import com.mini.me_core.newui.designsystem.component.molecule.AppMultiLineTextField
import com.mini.me_core.newui.designsystem.component.molecule.AppPagination
import com.mini.me_core.newui.designsystem.component.molecule.AppPasswordField
import com.mini.me_core.newui.designsystem.component.molecule.AppRatingBar
import com.mini.me_core.newui.designsystem.component.molecule.AppRatingFilter
import com.mini.me_core.newui.designsystem.component.molecule.AppRangeFilter
import com.mini.me_core.newui.designsystem.component.molecule.AppSearchBar
import com.mini.me_core.newui.designsystem.component.molecule.AppSearchableDropdown
import com.mini.me_core.newui.designsystem.component.molecule.AppSearchableOption
import com.mini.me_core.newui.designsystem.component.molecule.AppSegmentedToggle
import com.mini.me_core.newui.designsystem.component.molecule.AppSlider
import com.mini.me_core.newui.designsystem.component.molecule.AppStepper
import com.mini.me_core.newui.designsystem.component.molecule.AppSwitchRow
import com.mini.me_core.newui.designsystem.component.molecule.AppTabs
import com.mini.me_core.newui.designsystem.component.molecule.AppTagInput
import com.mini.me_core.newui.designsystem.component.molecule.AppTextField
import com.mini.me_core.newui.designsystem.component.molecule.AppValidatedTextField
import com.mini.me_core.newui.designsystem.token.generated.AppSpacing
import kotlinx.coroutines.delay

/**
 * 分子组件族 · 输入 / 筛选 / 输入框 / 表单 & 检索 / 表单增强 / 分级导航 / 标签分页。
 * 全部演示状态自包含，无需跨文件共享。
 */
@Composable
internal fun FormGallerySection() {
    var fieldText by remember { mutableStateOf("") }
    var searchText by remember { mutableStateOf("") }
    var segmentedIndex by remember { mutableStateOf(0) }
    var switchOn by remember { mutableStateOf(true) }
    var stepperValue by remember { mutableStateOf(3) }
    var rating by remember { mutableStateOf(3) }
    var sliderValue by remember { mutableStateOf(34f) }
    var checkOn by remember { mutableStateOf(true) }
    var accordionOpen by remember { mutableStateOf(true) }
    var tabIndex by remember { mutableStateOf(0) }
    var filterSet by remember { mutableStateOf(setOf(0, 2)) }
    var page by remember { mutableStateOf(2) }
    var tags by remember { mutableStateOf(listOf("kotlin", "compose", "agent")) }
    // 筛选组件族演示状态
    var filterText by remember { mutableStateOf("") }
    var checklistSel by remember { mutableStateOf(setOf(0, 2)) }
    var rangeVal by remember { mutableStateOf(30f..80f) }
    var boolIdx by remember { mutableStateOf(0) }
    var ratingFilter by remember { mutableStateOf(3) }
    var dateIdx by remember { mutableStateOf(0) }
    var dropdownSel by remember { mutableStateOf(setOf(0)) }
    var dropdownSingle by remember { mutableStateOf(setOf(2)) }
    var activeTokens by remember {
        mutableStateOf(listOf("状态" to "进行中", "类型" to "代码文件"))
    }
    // 输入框族演示状态
    var passText by remember { mutableStateOf("secret123") }
    var countedText by remember { mutableStateOf("Compose 语法") }
    var validText by remember { mutableStateOf("user@example.com") }
    var multiText by remember { mutableStateOf("") }
    var msgText by remember { mutableStateOf("") }
    var dialogInput by remember { mutableStateOf("") }
    var showDialogInput by remember { mutableStateOf(false) }
    // AppSearchableDropdown 演示：本地 / 远程（150ms 假延迟）双数据源
    var localPick by remember { mutableStateOf<String?>(null) }
    var remotePick by remember { mutableStateOf<String?>(null) }
    var remoteQ by remember { mutableStateOf("") }
    var remoteLoading by remember { mutableStateOf(false) }
    var remoteResults by remember { mutableStateOf<List<AppSearchableOption>>(emptyList()) }

    Section("分子组件 · 输入") {
        AppTextField(
            value = fieldText,
            onValueChange = { fieldText = it },
            label = "示例输入",
            placeholder = "请输入内容…",
        )
    }

    Section("分子组件族 · 筛选（按数据类型个性化）") {
        // 综合筛选面板：把多种数据类型的筛选控件组合进抽屉卡片，联动「已选计数」。
        val sheetActive =
            (if (filterText.isNotEmpty()) 1 else 0) +
                (if (boolIdx != 0) 1 else 0) +
                (if (rangeVal != 30f..80f) 1 else 0) +
                (if (ratingFilter > 0) 1 else 0) +
                checklistSel.size
        AppFilterSheet(
            title = "数据筛选",
            activeCount = sheetActive,
            onClearAll = {
                filterText = ""; boolIdx = 0; rangeVal = 30f..80f
                ratingFilter = 0; checklistSel = emptySet()
            },
            onReset = {
                boolIdx = 0; rangeVal = 30f..80f; ratingFilter = 0; checklistSel = emptySet()
            },
            onApply = {},
        ) {
            // 文本型：关键字筛选
            AppFilterField(
                value = filterText,
                onValueChange = { filterText = it },
                placeholder = "按名称 / 标签筛查…",
            )
            // 布尔型：三态（全部 / 进行中 / 已完成）
            AppBooleanFilter(
                labels = listOf("全部状态", "进行中", "已完成"),
                selectedIndex = boolIdx,
                onSelect = { boolIdx = it },
            )
            // 数值型：价格 / 大小范围双滑块
            AppRangeFilter(
                value = rangeVal,
                onValueChange = { rangeVal = it },
                prefix = "¥",
            )
            // 评分型：星级筛选
            AppRatingFilter(
                value = ratingFilter,
                onValueChange = { ratingFilter = it },
            )
        }
        // 枚举型：多选 + 计数 + 全选/清空
        AppChecklistToolbar(
            selectedCount = checklistSel.size,
            total = 4,
            onSelectAll = { checklistSel = setOf(0, 1, 2, 3) },
            onClear = { checklistSel = emptySet() },
        )
        AppChecklistFilter(
            options = listOf("聊天对话", "代码文件", "设计文档", "终端会话"),
            counts = listOf(128, 45, 23, 67),
            selected = checklistSel,
            onToggle = { i ->
                checklistSel = if (i in checklistSel) checklistSel - i else checklistSel + i
            },
        )
        // 日期型：预设范围
        AppDateFilter(
            presets = listOf("不限", "今日", "本周", "本月"),
            selectedIndex = dateIdx,
            onSelect = { dateIdx = it },
            selectedRangeText = if (dateIdx == 0) {
                "不限时间"
            } else {
                listOf("2026/9/5 至今", "2026/8/31 ~9/5", "2026/9/1 ~9/5")[dateIdx - 1]
            },
        )
        Spacer(Modifier.height(AppSpacing.Sm))
        // 可搜索下拉筛选（多选 + 单选两种语义，参考 iOSDropDown 交互）
        AppDropdownFilter(
            options = listOf("全部类型", "代码文件", "设计文档", "终端会话", "聊天对话"),
            selected = dropdownSel,
            onToggle = { i ->
                dropdownSel = if (i in dropdownSel) dropdownSel - i else dropdownSel + i
            },
            label = "类型",
            maxPopupHeight = 240,
            onClear = { dropdownSel = emptySet() },
        )
        Spacer(Modifier.height(AppSpacing.Sm))
        AppDropdownFilter(
            options = listOf("全部状态", "进行中", "已完成", "已归档"),
            selected = dropdownSingle,
            onToggle = { i ->
                dropdownSingle = if (i in dropdownSingle) emptySet() else setOf(i)
            },
            label = "状态",
            singleSelect = true,
            searchable = false,
            maxPopupHeight = 200,
        )
        // 已激活筛选 token 行（参考 iOS 26 search tokens）：可逐个移除 + 批量清除
        AppFilterTokens(
            active = activeTokens,
            onRemove = { i -> activeTokens = activeTokens.filterIndexed { idx, _ -> idx != i } },
            onClearAll = { activeTokens = emptyList() },
        )
    }

    Section("分子组件族 · 输入框") {
        // 填充式文本输入框（带前置图标 + 一键清除）
        AppFilledTextField(
            value = fieldText,
            onValueChange = { fieldText = it },
            label = "填充式文本框",
            placeholder = "例如：项目名称",
            leadingIcon = Icons.Rounded.Person,
        )
        // 密码输入框（可见性切换）
        AppPasswordField(
            value = passText,
            onValueChange = { passText = it },
            label = "密码输入框",
            placeholder = "输入密码",
        )
        // 带字符计数输入框（超限截断）
        AppCountedTextField(
            value = countedText,
            onValueChange = { countedText = it },
            label = "带计数输入框",
            maxLength = 16,
        )
        // 验证态输入框（Normal / Error / Success）
        AppValidatedTextField(
            value = validText,
            onValueChange = { validText = it },
            label = "验证态输入框",
            placeholder = "请输入邮箱",
            validity = when {
                validText.isBlank() -> AppInputValidity.Normal
                "@" in validText && "." in validText -> AppInputValidity.Success
                else -> AppInputValidity.Error
            },
            helper = when {
                validText.isBlank() -> "邮箱 / 手机号等格式校验"
                "@" in validText && "." in validText -> "校验通过"
                else -> "请输入合法邮箱地址"
            },
        )
        // 多行文本域（随内容增高）
        AppMultiLineTextField(
            value = multiText,
            onValueChange = { multiText = it },
            label = "多行文本域",
            placeholder = "支持多行输入，随内容增高…",
        )
        // 消息输入框（Chat Composer）：输入后发送按钮亮起
        AppMessageField(
            value = msgText,
            onValueChange = { msgText = it },
            onSend = { if (msgText.isNotBlank()) msgText = "" },
            placeholder = "消息输入框：输入后发送按钮亮起…",
        )
        // 弹窗输入框：输入框在弹窗 / 表单内的标准用法
        TextButton(onClick = { showDialogInput = true }) { Text("打开弹窗输入") }
        if (showDialogInput) {
            AlertDialog(
                onDismissRequest = { showDialogInput = false },
                title = { Text("弹窗输入框") },
                text = {
                    AppFilledTextField(
                        value = dialogInput,
                        onValueChange = { dialogInput = it },
                        label = "名称",
                        placeholder = "请输入…",
                        leadingIcon = Icons.Rounded.Person,
                    )
                },
                confirmButton = { TextButton(onClick = { showDialogInput = false }) { Text("确定") } },
                dismissButton = { TextButton(onClick = { showDialogInput = false }) { Text("取消") } },
            )
        }
    }

    Section("分子组件族 · 表单 & 检索") {
        AppSegmentedToggle(
            options = listOf("全部", "进行中", "已完成"),
            selectedIndex = segmentedIndex,
            onSelect = { segmentedIndex = it },
        )
        AppSwitchRow(
            title = "自动同步",
            subtitle = "开启后在后台自动同步远端变更",
            checked = switchOn,
            onCheckedChange = { switchOn = it },
        )
        AppStepper(
            value = stepperValue,
            onValueChange = { stepperValue = it },
            min = 0,
            max = 10,
        )
        AppSearchBar(
            value = searchText,
            onValueChange = { searchText = it },
            placeholder = "搜索项目 / 命令 / 会话…",
            onClear = { searchText = "" },
        )
        // 可搜索下拉 · 本地数据源：组件内前缀优先过滤，支持键盘 ↑↓·Enter·Esc
        Text(
            "可搜索下拉 · 本地数据源（前缀优先 · 键盘 ↑↓·Enter·Esc）",
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        AppSearchableDropdown(
            value = localPick,
            onSelect = { localPick = it.label },
            label = "选择城市",
            placeholder = "输入城市名…",
            leadingIcon = Icons.Rounded.Search,
            onClear = { localPick = null },
            options = remember {
                listOf(
                    AppSearchableOption("北京", subtitle = "Beijing · 华北", trailing = "2189万"),
                    AppSearchableOption("上海", subtitle = "Shanghai · 华东", trailing = "2487万"),
                    AppSearchableOption("广州", subtitle = "Guangzhou · 华南"),
                    AppSearchableOption("深圳", subtitle = "Shenzhen · 华南"),
                    AppSearchableOption("杭州", subtitle = "Hangzhou · 华东"),
                    AppSearchableOption("成都", subtitle = "Chengdu · 西南"),
                    AppSearchableOption("南京", subtitle = "Nanjing · 华东"),
                    AppSearchableOption("武汉", subtitle = "Wuhan · 华中"),
                    AppSearchableOption("西安", subtitle = "Xi'an · 西北"),
                    AppSearchableOption("苏州", subtitle = "Suzhou · 华东"),
                    AppSearchableOption("重庆", subtitle = "Chongqing · 西南"),
                    AppSearchableOption("长沙", subtitle = "Changsha · 华中"),
                )
            },
        )
        // 可搜索下拉 · 远程数据源：150ms 假延迟 + loading 转圈 + 无结果空态
        Text(
            "可搜索下拉 · 远程数据源（150ms 假延迟 · 输入无结果显示空态）",
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        val remoteCatalog = remember {
            listOf(
                "Anthropic", "OpenAI", "Google Gemini", "DeepSeek", "Qwen", "Llama",
                "Mistral", "Grok", "Gemma", "Claude", "Yi Large", "Baichuan",
                "Doubao", "Skylark", "Hunyuan",
            ).map { AppSearchableOption(it, subtitle = "远程模型") }
        }
        // 调用方自行防抖：150ms 后产出远程结果，期间 loading=true
        LaunchedEffect(remoteQ) {
            if (remoteQ.isBlank()) {
                remoteResults = emptyList()
                remoteLoading = false
                return@LaunchedEffect
            }
            remoteLoading = true
            delay(150)
            val needle = remoteQ.trim()
            remoteResults = remoteCatalog.filter { it.label.contains(needle, ignoreCase = true) }
            remoteLoading = false
        }
        AppSearchableDropdown(
            value = remotePick,
            onSelect = { remotePick = it.label },
            label = "远端模型检索",
            placeholder = "输入 ≥1 个字触发远程…",
            leadingIcon = Icons.Rounded.Search,
            options = emptyList(),
            remoteQuery = { q ->
                if (q.isBlank() || q == remotePick) remoteCatalog else remoteResults
            },
            loading = remoteLoading,
            onQueryChange = { remoteQ = it },
            onClear = { remotePick = null },
        )
    }

    Section("分子组件族 · 表单增强") {
        AppRatingBar(value = rating, onValueChange = { rating = it })
        AppSlider(value = sliderValue, onValueChange = { sliderValue = it }, valueRange = 0f..100f)
        AppCheckRow(
            title = "始终显示输出面板",
            subtitle = "执行命令后自动展开运行区",
            checked = checkOn,
            onCheckedChange = { checkOn = it },
        )
    }

    Section("分子组件族 · 分级 / 导航 / 检索") {
        AppTabs(
            tabs = listOf("会话", "工具", "进程"),
            selectedIndex = tabIndex,
            onSelect = { tabIndex = it },
        )
        AppFilterChips(
            options = listOf("全部", "未读", "已收藏", "归档"),
            selectedIndices = filterSet,
            onToggle = { i ->
                filterSet = if (i in filterSet) filterSet - i else filterSet + i
            },
        )
        AppAccordion(
            title = if (accordionOpen) "展开的高级选项" else "折叠的高级选项",
            subtitle = "点击展开 / 收起",
            expanded = accordionOpen,
            onToggle = { accordionOpen = !accordionOpen },
        ) {
            AppCheckRow(title = "启用沙箱隔离", checked = checkOn, onCheckedChange = { checkOn = it })
        }
    }

    Section("分子组件族 · 标签 / 分页") {
        AppTagInput(
            tags = tags,
            onAdd = { if (it !in tags) tags = tags + it },
            onRemove = { tags = tags - it },
            placeholder = "输入标签后回车添加…",
        )
        AppPagination(
            page = page,
            pageCount = 9,
            onPageChange = { page = it },
        )
    }
}
