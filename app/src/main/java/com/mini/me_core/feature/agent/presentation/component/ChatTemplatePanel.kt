package com.mini.me_core.feature.agent.presentation.component

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Add
import androidx.compose.material3.AssistChip
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.nestedscroll.NestedScrollConnection
import androidx.compose.ui.input.nestedscroll.NestedScrollSource
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import com.mini.me_core.R
import kotlin.math.roundToInt

/** 一个对话模板。 */
data class ChatTemplate(
    val name: String,
    val content: String,
    val category: String,
    val subCategory: String,
    val builtin: Boolean = true,
)

/** 模板大分类。 */
data class TemplateCategory(
    val key: String,
    val label: String,
    val subCategories: List<TemplateSubCategory>,
)

/** 模板子分类。 */
data class TemplateSubCategory(
    val key: String,
    val label: String,
)

/**
 * MiniMe 对话模板面板。
 *
 * BottomSheet 默认占屏 7/10 高，上滑可全屏；顶部搜索框 + 横向大分类 Tab；
 * 下方左侧垂直子分类导航，右侧模板卡片网格。点击模板把内容填入输入框。
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ChatTemplatePanel(
    onDismiss: () -> Unit,
    onUseTemplate: (String) -> Unit,
) {
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    var query by remember { mutableStateOf("") }
    var selectedCategory by remember { mutableStateOf("ui_component") }
    var selectedSubCategory by remember { mutableStateOf<String?>(null) }
    var isFullScreen by remember { mutableStateOf(false) }
    var customTemplates by remember { mutableStateOf(listOf<ChatTemplate>()) }
    var showEditor by remember { mutableStateOf(false) }

    val categories = remember { templateCategories() }
    val builtin = remember { builtinTemplates() }
    val all = builtin + customTemplates

    val currentCategory = categories.find { it.key == selectedCategory }
    val heightFraction by animateFloatAsState(
        targetValue = if (isFullScreen) 1f else 0.7f,
        label = "sheetHeight",
    )

    // 上滑全屏：内容滚动到顶部后继续上滑触发
    val nestedScrollConnection = remember {
        object : NestedScrollConnection {
            override fun onPreScroll(
                available: androidx.compose.ui.geometry.Offset,
                source: NestedScrollSource,
            ): androidx.compose.ui.geometry.Offset {
                if (available.y < -5f && !isFullScreen && source == NestedScrollSource.Drag) {
                    isFullScreen = true
                    return available
                }
                return androidx.compose.ui.geometry.Offset.Zero
            }
        }
    }

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = sheetState,
    ) {
        Column(
            Modifier
                .fillMaxHeight(heightFraction)
                .nestedScroll(nestedScrollConnection),
        ) {
            // 拖拽指示条 + 全屏提示
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(vertical = 8.dp),
                contentAlignment = Alignment.Center,
            ) {
                Box(
                    modifier = Modifier
                        .width(40.dp)
                        .height(4.dp)
                        .clip(RoundedCornerShape(2.dp))
                        .background(MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.4f)),
                )
            }

            // 搜索框
            OutlinedTextField(
                value = query,
                onValueChange = { query = it },
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp),
                placeholder = { Text(stringResource(R.string.template_search_hint)) },
                singleLine = true,
            )
            Spacer(Modifier.height(8.dp))

            // 横向大分类 Tab
            LazyRow(
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                modifier = Modifier.padding(horizontal = 16.dp),
            ) {
                items(categories) { cat ->
                    AssistChip(
                        onClick = {
                            selectedCategory = cat.key
                            selectedSubCategory = null
                        },
                        label = { Text(cat.label) },
                    )
                }
            }
            Spacer(Modifier.height(8.dp))

            // 左侧垂直子分类导航 + 右侧内容区
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .weight(1f),
            ) {
                // 左侧垂直导航
                currentCategory?.let { cat ->
                    LazyColumn(
                        modifier = Modifier
                            .width(96.dp)
                            .fillMaxHeight()
                            .background(MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.3f)),
                    ) {
                        item {
                            SubCategoryNavItem(
                                label = "全部",
                                selected = selectedSubCategory == null,
                                onClick = { selectedSubCategory = null },
                            )
                        }
                        items(cat.subCategories) { sub ->
                            SubCategoryNavItem(
                                label = sub.label,
                                selected = selectedSubCategory == sub.key,
                                onClick = { selectedSubCategory = sub.key },
                            )
                        }
                    }
                }

                // 右侧内容区
                val filtered = all.filter { t ->
                    t.category == selectedCategory &&
                        (selectedSubCategory == null || t.subCategory == selectedSubCategory) &&
                        (query.isBlank() ||
                            t.name.contains(query, ignoreCase = true) ||
                            t.content.contains(query, ignoreCase = true))
                }
                LazyVerticalGrid(
                    columns = GridCells.Fixed(2),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp),
                    modifier = Modifier
                        .weight(1f)
                        .fillMaxHeight()
                        .padding(horizontal = 8.dp),
                ) {
                    items(filtered) { tpl ->
                        Surface(
                            shape = MaterialTheme.shapes.medium,
                            color = MaterialTheme.colorScheme.surfaceVariant,
                            onClick = {
                                onUseTemplate(tpl.content)
                                onDismiss()
                            },
                        ) {
                            Column(Modifier.padding(12.dp)) {
                                Text(tpl.name, style = MaterialTheme.typography.bodyMedium)
                                Spacer(Modifier.height(4.dp))
                                Text(
                                    tpl.content.take(50),
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                    maxLines = 3,
                                )
                            }
                        }
                    }
                }
            }

            // 底部新建按钮
            TextButton(
                onClick = { showEditor = true },
                modifier = Modifier.fillMaxWidth(),
            ) {
                Icon(Icons.Rounded.Add, contentDescription = null)
                Spacer(Modifier.width(4.dp))
                Text(stringResource(R.string.template_new))
            }
        }
    }

    if (showEditor) {
        TemplateEditorSheet(
            onDismiss = { showEditor = false },
            onSave = { name, content ->
                customTemplates = customTemplates + ChatTemplate(
                    name, content, "custom", "custom", builtin = false,
                )
                showEditor = false
            },
        )
    }
}

/** 子分类导航项。 */
@Composable
private fun SubCategoryNavItem(
    label: String,
    selected: Boolean,
    onClick: () -> Unit,
) {
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .padding(vertical = 12.dp, horizontal = 8.dp),
        contentAlignment = Alignment.Center,
    ) {
        Text(
            label,
            style = MaterialTheme.typography.bodySmall,
            color = if (selected) {
                MaterialTheme.colorScheme.primary
            } else {
                MaterialTheme.colorScheme.onSurfaceVariant
            },
        )
    }
    // 选中指示器
    if (selected) {
        Box(
            modifier = Modifier
                .width(3.dp)
                .height(24.dp)
                .background(MaterialTheme.colorScheme.primary, RoundedCornerShape(2.dp)),
        )
    }
}

/** 新建/编辑模板小表单。 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun TemplateEditorSheet(
    onDismiss: () -> Unit,
    onSave: (name: String, content: String) -> Unit,
) {
    var name by remember { mutableStateOf("") }
    var content by remember { mutableStateOf("") }
    ModalBottomSheet(onDismissRequest = onDismiss) {
        Column(
            Modifier.padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            OutlinedTextField(
                value = name,
                onValueChange = { name = it },
                label = { Text(stringResource(R.string.template_name_hint)) },
                modifier = Modifier.fillMaxWidth(),
                singleLine = true,
            )
            OutlinedTextField(
                value = content,
                onValueChange = { content = it },
                label = { Text(stringResource(R.string.template_content_hint)) },
                modifier = Modifier
                    .fillMaxWidth()
                    .height(120.dp),
            )
            TextButton(
                onClick = { if (name.isNotBlank() && content.isNotBlank()) onSave(name, content) },
                modifier = Modifier.fillMaxWidth(),
            ) { Text(stringResource(R.string.template_save)) }
        }
    }
}

/** 模板大分类定义。 */
private fun templateCategories(): List<TemplateCategory> = listOf(
    TemplateCategory(
        key = "ui_component",
        label = "UI组件",
        subCategories = listOf(
            TemplateSubCategory("button", "按钮"),
            TemplateSubCategory("navbar", "导航栏"),
            TemplateSubCategory("card", "卡片"),
            TemplateSubCategory("form", "表单"),
            TemplateSubCategory("modal", "弹窗"),
            TemplateSubCategory("table", "表格"),
            TemplateSubCategory("tabs", "标签页"),
            TemplateSubCategory("dropdown", "下拉菜单"),
            TemplateSubCategory("progress", "进度条"),
            TemplateSubCategory("tooltip", "提示气泡"),
        ),
    ),
    TemplateCategory(
        key = "layout",
        label = "页面布局",
        subCategories = listOf(
            TemplateSubCategory("dashboard", "仪表盘"),
            TemplateSubCategory("login", "登录注册"),
            TemplateSubCategory("detail", "商品详情"),
            TemplateSubCategory("settings", "设置页面"),
            TemplateSubCategory("profile", "个人中心"),
            TemplateSubCategory("empty", "空状态"),
            TemplateSubCategory("error", "错误页面"),
        ),
    ),
    TemplateCategory(
        key = "interaction",
        label = "交互设计",
        subCategories = listOf(
            TemplateSubCategory("loading", "加载状态"),
            TemplateSubCategory("error_handle", "错误处理"),
            TemplateSubCategory("confirm", "确认对话框"),
            TemplateSubCategory("pull_refresh", "下拉刷新"),
            TemplateSubCategory("infinite_scroll", "无限滚动"),
            TemplateSubCategory("drag_sort", "拖拽排序"),
        ),
    ),
    TemplateCategory(
        key = "visual",
        label = "视觉设计",
        subCategories = listOf(
            TemplateSubCategory("color", "配色方案"),
            TemplateSubCategory("typography", "排版系统"),
            TemplateSubCategory("icon", "图标规范"),
            TemplateSubCategory("shadow", "阴影系统"),
            TemplateSubCategory("radius", "圆角系统"),
            TemplateSubCategory("spacing", "间距系统"),
        ),
    ),
    TemplateCategory(
        key = "responsive",
        label = "响应式",
        subCategories = listOf(
            TemplateSubCategory("breakpoint", "断点规范"),
            TemplateSubCategory("mobile", "移动端适配"),
            TemplateSubCategory("fluid", "流式布局"),
            TemplateSubCategory("image", "图片响应式"),
        ),
    ),
    TemplateCategory(
        key = "accessibility",
        label = "无障碍",
        subCategories = listOf(
            TemplateSubCategory("audit", "无障碍审计"),
            TemplateSubCategory("aria", "ARIA规范"),
            TemplateSubCategory("keyboard", "键盘导航"),
            TemplateSubCategory("contrast", "对比度检查"),
        ),
    ),
    TemplateCategory(
        key = "design_system",
        label = "设计系统",
        subCategories = listOf(
            TemplateSubCategory("build", "设计系统搭建"),
            TemplateSubCategory("api", "组件API设计"),
            TemplateSubCategory("theme", "主题切换"),
            TemplateSubCategory("docs", "组件文档"),
        ),
    ),
    TemplateCategory(
        key = "animation",
        label = "动效设计",
        subCategories = listOf(
            TemplateSubCategory("enter", "入场动画"),
            TemplateSubCategory("micro", "微交互"),
            TemplateSubCategory("loading_anim", "加载动画"),
            TemplateSubCategory("transition", "页面转场"),
            TemplateSubCategory("gesture", "手势动画"),
        ),
    ),
    TemplateCategory(
        key = "mobile",
        label = "移动端UI",
        subCategories = listOf(
            TemplateSubCategory("ios", "iOS规范"),
            TemplateSubCategory("material", "Material Design"),
            TemplateSubCategory("bottom_nav", "底部导航"),
            TemplateSubCategory("gesture_nav", "手势导航"),
        ),
    ),
    TemplateCategory(
        key = "code_gen",
        label = "代码生成",
        subCategories = listOf(
            TemplateSubCategory("react", "React组件"),
            TemplateSubCategory("vue", "Vue组件"),
            TemplateSubCategory("native", "原生组件"),
            TemplateSubCategory("restore", "页面还原"),
        ),
    ),
    TemplateCategory(
        key = "web_style",
        label = "Web样式",
        subCategories = listOf(
            TemplateSubCategory("visual_effect", "视觉特效"),
            TemplateSubCategory("page_section", "页面区块"),
            TemplateSubCategory("nav_footer", "导航页脚"),
            TemplateSubCategory("data_display", "数据展示"),
            TemplateSubCategory("feedback", "反馈状态"),
            TemplateSubCategory("form_input", "表单输入"),
        ),
    ),
    TemplateCategory(
        key = "program_feature",
        label = "编程功能",
        subCategories = listOf(
            TemplateSubCategory("backend_api", "后端API"),
            TemplateSubCategory("frontend_feature", "前端功能"),
            TemplateSubCategory("database", "数据库"),
            TemplateSubCategory("algorithm", "算法实现"),
            TemplateSubCategory("tool_script", "工具脚本"),
            TemplateSubCategory("architecture", "架构设计"),
        ),
    ),
    TemplateCategory(
        key = "daily_life",
        label = "日常生活",
        subCategories = listOf(
            TemplateSubCategory("time_manage", "时间管理"),
            TemplateSubCategory("health_food", "健康饮食"),
            TemplateSubCategory("travel", "出行旅游"),
            TemplateSubCategory("shopping", "购物消费"),
            TemplateSubCategory("learning", "学习成长"),
            TemplateSubCategory("communication", "人际沟通"),
        ),
    ),
    TemplateCategory(
        key = "program_bug",
        label = "编程bug",
        subCategories = listOf(
            TemplateSubCategory("error_analysis", "错误分析"),
            TemplateSubCategory("debug_method", "调试方法"),
            TemplateSubCategory("performance", "性能问题"),
            TemplateSubCategory("security", "安全漏洞"),
            TemplateSubCategory("compatibility", "兼容性"),
            TemplateSubCategory("logic_error", "逻辑错误"),
        ),
    ),
)

/** 内置前端UI设计模板。 */
private fun builtinTemplates(): List<ChatTemplate> = listOf(
    // ===== UI组件 =====
    ChatTemplate("按钮组件", "用【框架】+【样式方案】实现一个按钮组件，支持四种状态：默认、悬停、按下、加载中。加载状态禁用按钮并显示旋转动画，焦点状态对键盘用户清晰可见。\n\n{选中文字}", "ui_component", "button"),
    ChatTemplate("导航栏", "实现一个响应式悬浮导航栏，初始透明背景，滚动时变为毛玻璃效果（半透明+模糊+阴影），移动端折叠为汉堡菜单，带展开动画。\n\n{选中文字}", "ui_component", "navbar"),
    ChatTemplate("卡片组件", "实现一个信息卡片组件，包含：封面图、标题、描述、标签、操作按钮。支持悬停上浮效果、加载骨架屏、空状态。\n\n{选中文字}", "ui_component", "card"),
    ChatTemplate("表单组件", "实现一个表单组件，包含：输入框验证（实时+失焦）、错误提示、密码可见切换、下拉选择、日期选择、提交按钮加载态。\n\n{选中文字}", "ui_component", "form"),
    ChatTemplate("弹窗组件", "实现一个模态弹窗，包含：进入/退出动画、遮罩层点击关闭、ESC键关闭、焦点陷阱、滚动锁定、可定制的头部/内容/底部。\n\n{选中文字}", "ui_component", "modal"),
    ChatTemplate("表格组件", "实现一个数据表格，支持：排序、筛选、分页、行选择、列宽拖拽、固定表头、空状态、加载状态、响应式横向滚动。\n\n{选中文字}", "ui_component", "table"),
    ChatTemplate("标签页", "实现一个标签页组件，支持：横向滚动、下划线指示器动画、懒加载、禁用状态、可关闭标签、键盘左右切换。\n\n{选中文字}", "ui_component", "tabs"),
    ChatTemplate("下拉菜单", "实现一个下拉菜单，支持：多级嵌套、分组、图标、快捷键提示、悬停延迟、点击外部关闭、键盘导航、ARIA属性。\n\n{选中文字}", "ui_component", "dropdown"),
    ChatTemplate("进度条", "实现一个进度条组件，支持：线性/环形两种样式、动画过渡、不确定状态、百分比文字、自定义颜色。\n\n{选中文字}", "ui_component", "progress"),
    ChatTemplate("提示气泡", "实现一个 Tooltip 组件，支持：12个方向定位、智能翻转（防止溢出视口）、淡入淡出动画、悬停/点击两种触发、延迟显示。\n\n{选中文字}", "ui_component", "tooltip"),

    // ===== 页面布局 =====
    ChatTemplate("仪表盘布局", "设计一个数据仪表盘页面，包含：顶部导航栏、侧边菜单、数据卡片网格、图表区域、最近活动列表。响应式：移动端侧边栏折叠为抽屉。\n\n{选中文字}", "layout", "dashboard"),
    ChatTemplate("登录注册页", "设计一个登录注册页面，左侧品牌展示区（渐变背景+插画+标语），右侧表单区。支持：表单验证、密码强度指示器、第三方登录、忘记密码流程。\n\n{选中文字}", "layout", "login"),
    ChatTemplate("商品详情页", "设计一个电商商品详情页，包含：图片轮播、商品信息（标题/价格/库存）、规格选择、数量加减、加入购物车/立即购买按钮、商品描述Tab、评价列表。\n\n{选中文字}", "layout", "detail"),
    ChatTemplate("设置页面", "设计一个设置页面，采用分组列表布局，每组有标题。包含：开关切换、下拉选择、滑块、颜色选择、文件上传、危险操作区（红色确认）。\n\n{选中文字}", "layout", "settings"),
    ChatTemplate("个人中心", "设计一个个人中心页面，顶部用户信息卡片（头像/昵称/等级/积分），功能入口网格，订单/收藏/历史快捷入口，设置入口。\n\n{选中文字}", "layout", "profile"),
    ChatTemplate("空状态页面", "设计一组空状态页面：无数据、无网络、搜索无结果、加载失败。每个包含：插画、标题、描述文字、主操作按钮、次操作链接。\n\n{选中文字}", "layout", "empty"),
    ChatTemplate("错误页面", "设计 404/500 错误页面，包含：错误码大字、友好提示、可能原因、返回首页/重试按钮、搜索框。风格与品牌一致，不显得冷冰冰。\n\n{选中文字}", "layout", "error"),

    // ===== 交互设计 =====
    ChatTemplate("加载状态", "为以下场景设计加载状态：页面初次加载（骨架屏）、数据刷新（下拉刷新）、按钮提交（按钮内旋转）、图片加载（模糊占位→清晰）、列表加载更多（底部加载指示器）。\n\n{选中文字}", "interaction", "loading"),
    ChatTemplate("错误处理", "设计表单提交失败的交互：错误信息在字段下方红色显示，顶部显示汇总错误条，支持点击滚动到第一个错误字段，已填数据保留不丢失。\n\n{选中文字}", "interaction", "error_handle"),
    ChatTemplate("确认对话框", "设计危险操作的二次确认对话框：红色警告图标、操作名称、后果说明、取消按钮（默认聚焦）、确认按钮（红色，需等待3秒才可点击）。\n\n{选中文字}", "interaction", "confirm"),
    ChatTemplate("下拉刷新", "实现一个下拉刷新组件：下拉时显示箭头+提示文字，达到阈值后变为加载动画，刷新完成显示成功提示，支持自定义颜色和文字。\n\n{选中文字}", "interaction", "pull_refresh"),
    ChatTemplate("无限滚动", "实现列表无限滚动：滚动到底部自动加载下一页，加载中显示骨架屏，加载失败显示重试按钮，全部加载完显示没有更多了，防抖处理。\n\n{选中文字}", "interaction", "infinite_scroll"),
    ChatTemplate("拖拽排序", "实现一个可拖拽排序的列表：拖拽时项上浮+阴影，放置位置有指示线，支持触摸和鼠标，动画平滑，排序后有震动反馈（移动端）。\n\n{选中文字}", "interaction", "drag_sort"),

    // ===== 视觉设计 =====
    ChatTemplate("配色方案", "为【产品类型】设计一套配色方案，包含：主色、辅助色、强调色、中性色（文字/背景/边框，各5档）、系统色（成功/警告/错误/信息）。给出HEX值、使用场景、对比度验证。\n\n{选中文字}", "visual", "color"),
    ChatTemplate("排版系统", "设计一套字体排版系统，包含：标题（H1-H6，字号/字重/行高）、正文（大/中/小）、辅助文字、按钮文字、代码字体。给出具体数值和使用场景。\n\n{选中文字}", "visual", "typography"),
    ChatTemplate("图标规范", "设计一套图标规范：尺寸（16/20/24/32px）、线条粗细（1.5px/2px）、圆角、视觉重量统一、描边vs填充使用规则、状态变化（默认/悬停/激活/禁用）。\n\n{选中文字}", "visual", "icon"),
    ChatTemplate("阴影系统", "设计一套阴影层级：sm（轻微悬浮）、md（卡片）、lg（弹窗）、xl（模态框）。给出具体的box-shadow值，包含深色模式适配。\n\n{选中文字}", "visual", "shadow"),
    ChatTemplate("圆角系统", "设计一套圆角规范：xs（4px，标签）、sm（8px，按钮/输入框）、md（12px，卡片）、lg（16px，弹窗）、xl（24px，大卡片）、full（圆形）。\n\n{选中文字}", "visual", "radius"),
    ChatTemplate("间距系统", "设计一套8px基准的间距系统：0/4/8/12/16/20/24/32/40/48/64/80/96/128px。说明每个尺寸的典型使用场景。\n\n{选中文字}", "visual", "spacing"),

    // ===== 响应式 =====
    ChatTemplate("断点规范", "定义一套响应式断点：sm（640px，手机横屏）、md（768px，平板）、lg（1024px，小桌面）、xl（1280px，桌面）、2xl（1536px，大桌面）。说明每个断点的布局变化策略。\n\n{选中文字}", "responsive", "breakpoint"),
    ChatTemplate("移动端适配", "将这个桌面端页面适配为移动端：导航栏折叠为汉堡菜单，多列布局改为单列，表格改为卡片列表，侧边栏改为底部抽屉，按钮尺寸增大到44px触控目标。\n\n{选中文字}", "responsive", "mobile"),
    ChatTemplate("流式布局", "实现一个流式布局页面：使用CSS Grid的auto-fit+minmax，卡片数量随视口宽度自动调整，图片等比缩放，文字大小用clamp()流式变化。\n\n{选中文字}", "responsive", "fluid"),
    ChatTemplate("图片响应式", "实现响应式图片方案：使用srcset提供多倍图，picture元素根据视口切换不同裁剪比例，loading=lazy懒加载，模糊占位渐显效果。\n\n{选中文字}", "responsive", "image"),

    // ===== 无障碍 =====
    ChatTemplate("无障碍审计", "审查以下组件的WCAG 2.2 AA合规性：检查ARIA标签、颜色对比度（至少4.5:1）、键盘导航、焦点可见性、语义化HTML、屏幕阅读器兼容性、表单标签关联。列出问题并给出修复代码。\n\n{选中文字}", "accessibility", "audit"),
    ChatTemplate("ARIA规范", "为以下交互组件添加正确的ARIA属性：标签页（role=tablist/tab/tabpanel）、折叠面板（aria-expanded）、模态框（role=dialog+aria-modal）、菜单（role=menu/menuitem）、进度条（role=progressbar）。\n\n{选中文字}", "accessibility", "aria"),
    ChatTemplate("键盘导航", "确保以下组件支持完整键盘导航：Tab键顺序合理、Enter/Space激活、Esc关闭弹窗、方向键在菜单/标签页间移动、焦点陷阱（模态框内）、焦点状态清晰可见、跳过导航链接。\n\n{选中文字}", "accessibility", "keyboard"),
    ChatTemplate("对比度检查", "检查以下配色组合的对比度：文字与背景（正文至少4.5:1，大文字至少3:1）、UI组件与背景（至少3:1）、图形与背景（至少3:1）。不达标给出调整建议。\n\n{选中文字}", "accessibility", "contrast"),

    // ===== 设计系统 =====
    ChatTemplate("设计系统搭建", "为【产品类型】搭建一套完整设计系统，包含：设计令牌（颜色/字体/间距/圆角/阴影/动效）、基础组件（按钮/输入框/卡片/标签/图标）、复合组件（导航/表单/表格/弹窗）、页面模板、使用规范文档。\n\n{选中文字}", "design_system", "build"),
    ChatTemplate("组件API设计", "为【组件名】设计一套完整的组件API：Props列表（名称/类型/默认值/说明）、事件列表、插槽/children、方法、CSS变量（可定制点）、TypeScript类型定义。附使用示例。\n\n{选中文字}", "design_system", "api"),
    ChatTemplate("主题切换", "实现一个完整的主题切换系统：支持浅色/深色/跟随系统，CSS变量驱动，切换有平滑过渡动画，用户偏好持久化存储，首次访问根据系统设置自动选择，所有组件适配两种主题。\n\n{选中文字}", "design_system", "theme"),
    ChatTemplate("组件文档", "为以下组件编写完整文档：组件介绍、何时使用、Props/事件/方法说明、基础用法示例、多种状态展示、自定义样式示例、无障碍说明、设计规范（尺寸/间距/颜色）。\n\n{选中文字}", "design_system", "docs"),

    // ===== 动效设计 =====
    ChatTemplate("入场动画", "设计一组页面/元素入场动画：淡入（opacity 0→1，300ms ease-out）、上滑（translateY 20px→0，400ms ease-out）、缩放（scale 0.9→1，300ms）、左滑/右滑。支持交错延迟（stagger）。\n\n{选中文字}", "animation", "enter"),
    ChatTemplate("微交互", "为以下元素设计微交互动画：按钮按下（scale 0.97）、收藏/点赞（心形弹跳+粒子）、开关切换（滑块滑动+颜色过渡）、标签选择（下划线动画）、下拉展开（高度auto动画）。\n\n{选中文字}", "animation", "micro"),
    ChatTemplate("加载动画", "设计一组加载动画：旋转圆环（经典）、脉冲点（三个点依次缩放）、进度条（indeterminate来回滑动）、骨架屏（微光扫过）、品牌Logo动画（粒子聚合）。\n\n{选中文字}", "animation", "loading_anim"),
    ChatTemplate("页面转场", "设计页面切换转场动画：淡入淡出、左滑进入/右滑退出、向上覆盖、共享元素过渡（图片从列表到详情）。确保动画时长不超过300ms，不影响可访问性（尊重prefers-reduced-motion）。\n\n{选中文字}", "animation", "transition"),
    ChatTemplate("手势动画", "实现移动端手势交互动画：左滑删除（露出删除按钮+回弹）、下拉刷新（橡皮筋效果）、双指缩放（图片缩放+边界回弹）、长按菜单（震动+弹出）。\n\n{选中文字}", "animation", "gesture"),

    // ===== 移动端UI =====
    ChatTemplate("iOS规范", "按照iOS Human Interface Guidelines设计页面：使用SF Pro字体、安全区域适配（刘海/灵动岛）、大标题导航栏、毛玻璃效果、圆角20px、手势返回、Haptic Feedback。\n\n{选中文字}", "mobile", "ios"),
    ChatTemplate("Material Design", "按照Material Design 3设计页面：使用Roboto字体、Material You动态取色、FAB按钮、底部导航栏、波纹效果（ripple）、高程阴影、形状（大/中/小圆角）、snackbar提示。\n\n{选中文字}", "mobile", "material"),
    ChatTemplate("底部导航", "实现一个移动端底部导航栏：3-5个图标+文字，选中状态高亮+动画，中间可放置凸起的主操作按钮，安全区域适配，切换页面有过渡动画，badge红点提示。\n\n{选中文字}", "mobile", "bottom_nav"),
    ChatTemplate("手势导航", "实现全面屏手势导航：左边缘右滑返回、底部上滑回桌面、底部上滑停顿多任务、与页面内横向滚动冲突的处理（边缘20px判定为返回手势）。\n\n{选中文字}", "mobile", "gesture_nav"),

    // ===== 代码生成 =====
    ChatTemplate("React组件", "用React + TypeScript + Tailwind CSS实现【组件名】，要求：函数组件+hooks、完整TypeScript类型、props默认值、forwardRef支持、所有交互状态、无障碍ARIA、单元测试、使用示例。\n\n{选中文字}", "code_gen", "react"),
    ChatTemplate("Vue组件", "用Vue 3 + TypeScript + script setup实现【组件名】，要求：defineProps/defineEmits类型、v-model支持、插槽、生命周期、过渡动画、CSS变量主题、使用示例。\n\n{选中文字}", "code_gen", "vue"),
    ChatTemplate("原生组件", "用原生HTML + CSS + JavaScript实现【组件名】，要求：无框架依赖、ES6+语法、CSS变量可定制、事件委托、性能优化（防抖/节流）、兼容现代浏览器。\n\n{选中文字}", "code_gen", "native"),
    ChatTemplate("页面还原", "根据以下设计图/描述，用【技术栈】1:1还原页面。要求：语义化HTML结构、CSS布局方法、所有视觉状态（默认/悬停/焦点/按下/禁用）、精确尺寸间距、动画过渡、响应式行为。\n\n{选中文字}", "code_gen", "restore"),

    // ===== Web样式 - 视觉特效 =====
    ChatTemplate("玻璃拟态卡片", "用HTML+CSS实现玻璃拟态（Glassmorphism）卡片：半透明背景rgba(255,255,255,0.1)、backdrop-filter:blur(20px)毛玻璃、1px半透明白色边框、柔和投影、悬停时轻微上浮+边框亮度提升。同时给出浅色和深色两种主题的参数。\n\n{选中文字}", "web_style", "visual_effect"),
    ChatTemplate("新拟态按钮", "用HTML+CSS实现新拟态（Neumorphism）按钮：背景与页面同色、双向阴影（左上亮阴影+右下暗阴影）、圆角16px、按下状态阴影反转（内凹效果）、过渡动画200ms。给出浅色和深色两套参数。\n\n{选中文字}", "web_style", "visual_effect"),
    ChatTemplate("渐变文字", "用HTML+CSS实现渐变文字效果：linear-gradient渐变背景、background-clip:text、-webkit-text-fill-color:transparent、背景尺寸200%实现流动动画、悬停时渐变方向变化。支持多色渐变和角度自定义。\n\n{选中文字}", "web_style", "visual_effect"),
    ChatTemplate("悬停特效按钮", "用HTML+CSS实现5种按钮悬停特效：①填充滑入（背景从左滑入）②光泽扫过（高光从左扫到右）③上浮阴影（translateY+box-shadow）④缩放弹跳（scale+回弹缓动）⑤发光脉冲（box-shadow发光扩散）。每种给出完整代码。\n\n{选中文字}", "web_style", "visual_effect"),
    ChatTemplate("暗黑模式适配", "用CSS变量实现完整的暗黑模式适配：定义:root和[data-theme=dark]两套颜色变量、文字对比度至少4.5:1、图片用filter:brightness(0.8)降低亮度、阴影改用背景色提亮而非黑色阴影、切换时有300ms过渡动画、尊重prefers-color-scheme系统偏好。\n\n{选中文字}", "web_style", "visual_effect"),

    // ===== Web样式 - 页面区块 =====
    ChatTemplate("Hero区域", "用HTML+CSS实现产品落地页Hero区域：全屏视口高度、垂直居中、大标题（渐变文字）+副标题+主按钮+次按钮、左侧文字右侧产品截图占位、背景用渐变+网格图案+浮动装饰圆球、入场动画（文字依次淡入上滑）、响应式移动端单列。\n\n{选中文字}", "web_style", "page_section"),
    ChatTemplate("定价卡片", "用HTML+CSS实现三档定价卡片：基础版/专业版/企业版、中间专业版高亮（放大+边框高亮+最受欢迎徽章）、每卡包含价格（大字号+周期小字）、功能列表（对勾/叉号）、CTA按钮、悬停上浮效果、年度/月度切换标签。\n\n{选中文字}", "web_style", "page_section"),
    ChatTemplate("评价卡片", "用HTML+CSS实现用户评价卡片：头像（圆形）+姓名+职位+五星评分+引用内容+引号装饰图标、卡片悬停上浮、自动轮播（3秒切换+淡入淡出）、左右箭头导航、底部指示点、响应式移动端单卡、桌面端三卡并排。\n\n{选中文字}", "web_style", "page_section"),
    ChatTemplate("特性网格", "用HTML+CSS实现产品特性展示网格：三列布局、每格包含图标（圆形渐变背景）+标题+描述、悬停时图标缩放+卡片上浮、图标背景色与主题色一致、偶数格背景交替、移动端单列、平板双列、加入场交错动画。\n\n{选中文字}", "web_style", "page_section"),
    ChatTemplate("联系表单区块", "用HTML+CSS实现两栏联系区块：左侧联系信息（地址/电话/邮箱/社交图标，带图标）、右侧表单（姓名/邮箱/主题/消息文本域/提交按钮）、表单验证样式（错误红色边框+提示文字、成功绿色）、输入框聚焦时边框高亮+标签上浮。\n\n{选中文字}", "web_style", "page_section"),

    // ===== Web样式 - 导航页脚 =====
    ChatTemplate("滚动渐变导航栏", "用HTML+CSS+JS实现滚动导航栏：初始透明背景、滚动超过50px后变为毛玻璃背景（backdrop-blur+半透明）+阴影+高度收缩、Logo和导航链接、悬停下划线动画、移动端汉堡菜单（点击展开全屏菜单）、当前页面高亮、平滑滚动到锚点。\n\n{选中文字}", "web_style", "nav_footer"),
    ChatTemplate("多列页脚", "用HTML+CSS实现多列页脚：4列布局（产品/资源/公司/法律）、每列有标题+链接列表、悬停链接变色+左移、顶部品牌区（Logo+简介+社交图标）、底部版权栏（版权文字+隐私政策链接+回到顶部按钮）、Newsletter订阅框、响应式移动端折叠为手风琴。\n\n{选中文字}", "web_style", "nav_footer"),
    ChatTemplate("侧边栏导航", "用HTML+CSS实现可折叠侧边栏：固定左侧、宽度240px可收缩到64px（只显示图标）、菜单项（图标+文字）、选中项高亮（左侧指示条+背景色）、悬停背景色、分组标题、底部用户信息卡片、收缩时悬停显示tooltip、主内容区margin自适应。\n\n{选中文字}", "web_style", "nav_footer"),
    ChatTemplate("面包屑导航", "用HTML+CSS实现面包屑导航：层级链接用斜杠/箭头分隔、当前页加粗不可点击、悬停链接下划线、首页用图标、过长时中间层级省略为...（点击展开）、结构化数据SEO（schema.org/BreadcrumbList）、响应式移动端只显示上一级。\n\n{选中文字}", "web_style", "nav_footer"),
    ChatTemplate("分页组件", "用HTML+CSS实现分页组件：页码按钮（圆角）、上一页/下一页箭头、当前页高亮（背景色+白色文字）、禁用状态（灰色+不可点击）、省略号...、首尾页快捷跳转、页码数量多时智能省略（始终显示首尾+当前页附近）、悬停效果、响应式移动端简化。\n\n{选中文字}", "web_style", "nav_footer"),

    // ===== Web样式 - 数据展示 =====
    ChatTemplate("垂直时间线", "用HTML+CSS实现垂直时间线：中间垂直轴线、左右交替排列的卡片、每个节点有圆点（带图标）+日期+标题+内容、卡片悬停上浮、轴线渐变颜色、当前节点高亮（脉冲动画圆点）、移动端全部左对齐（轴线在左侧）、入场动画（卡片从两侧滑入）。\n\n{选中文字}", "web_style", "data_display"),
    ChatTemplate("步骤条组件", "用HTML+CSS实现步骤条：水平排列的步骤节点（圆形数字）、连接线、三种状态（已完成=绿色对勾+连接线绿色、进行中=主题色+脉冲动画+连接线半满、未开始=灰色）、步骤标题+描述、响应式移动端改为垂直步骤条、支持点击跳转。\n\n{选中文字}", "web_style", "data_display"),
    ChatTemplate("环形进度条", "用HTML+CSS+SVG实现环形进度条：SVG circle描边动画（stroke-dasharray+stroke-dashoffset）、渐变色描边、中心显示百分比文字（大字号）+标签、动画从0到目标值（1.5秒缓动）、支持多种尺寸、支持多环嵌套、悬停时轻微放大、颜色可配置。\n\n{选中文字}", "web_style", "data_display"),
    ChatTemplate("数字计数器动画", "用HTML+CSS+JS实现数字滚动计数器：页面滚动到可视区域时触发、数字从0滚动到目标值（缓动函数easeOutQuart）、支持千分位逗号分隔、支持前缀/后缀符号、支持小数位数、持续时间1.5-2秒、多个计数器同时触发、IntersectionObserver实现懒触发。\n\n{选中文字}", "web_style", "data_display"),
    ChatTemplate("仪表盘统计卡片", "用HTML+CSS实现数据仪表盘统计卡片：四列网格、每卡包含图标（彩色渐变圆形背景）+数值（大字号）+标签+趋势（上升绿色箭头/下降红色箭头+百分比）+迷你折线图（sparkline）、悬停上浮+阴影加深、数值动画滚动、不同指标用不同主题色、响应式移动端单列。\n\n{选中文字}", "web_style", "data_display"),

    // ===== Web样式 - 反馈状态 =====
    ChatTemplate("Toast通知", "用HTML+CSS+JS实现Toast通知组件：右上角堆叠、四种类型（成功=绿色对勾/错误=红色叉号/警告=黄色感叹号/信息=蓝色i）、图标+标题+描述+关闭按钮、底部进度条（倒计时自动消失）、入场动画（从右滑入+淡入）、出场动画（滑出+淡出）、悬停暂停倒计时、最多同时显示3条。\n\n{选中文字}", "web_style", "feedback"),
    ChatTemplate("骨架屏加载", "用HTML+CSS实现骨架屏加载动画：灰色占位块（圆角）+微光扫过动画（linear-gradient从左到右移动）、多种布局模板（文章列表/卡片网格/详情页/表格）、与真实内容布局1:1对应、动画循环1.5秒、加载完成后骨架淡出+内容淡入、prefers-reduced-motion时禁用动画。\n\n{选中文字}", "web_style", "feedback"),
    ChatTemplate("404错误页面", "用HTML+CSS实现404错误页面：居中布局、超大404数字（渐变文字+动画）、插画/图标、友好提示文案（不要只说404）、可能原因列表、搜索框、返回首页按钮、最近文章推荐、动画（数字浮动+背景粒子）、响应式、与网站整体风格一致。\n\n{选中文字}", "web_style", "feedback"),
    ChatTemplate("空状态页面", "用HTML+CSS实现一组空状态页面：无数据/无网络/搜索无结果/加载失败/首次使用引导。每个包含：插画（线性风格）+标题+描述文字+主操作按钮+次操作链接、插画动画（轻微浮动）、按钮悬停效果、响应式居中、颜色与主题一致、文案友好不冷冰冰。\n\n{选中文字}", "web_style", "feedback"),
    ChatTemplate("手风琴折叠面板", "用HTML+CSS+JS实现手风琴组件：多个折叠项、每项有标题栏（标题+图标+右侧箭头）+内容区、点击标题展开/收起（高度从0到auto平滑动画）、展开时箭头旋转180度、同时只展开一项或可多项配置、内容区padding、标题悬停背景色、无障碍属性（aria-expanded/aria-controls）、键盘支持。\n\n{选中文字}", "web_style", "feedback"),

    // ===== Web样式 - 表单输入 =====
    ChatTemplate("浮动标签输入框", "用HTML+CSS实现浮动标签输入框：标签初始在输入框内（灰色）、聚焦或有内容时标签上浮到边框上方（缩小字号+变主题色）、边框聚焦时高亮、错误状态（红色边框+红色标签+下方错误提示）、成功状态（绿色边框+对勾图标）、过渡动画200ms、支持文本域/选择框。\n\n{选中文字}", "web_style", "form_input"),
    ChatTemplate("搜索框组件", "用HTML+CSS+JS实现搜索框：左侧搜索图标、输入框、右侧清除按钮（有内容时显示，点击清空）、聚焦时边框高亮+轻微放大、输入时显示下拉建议列表（匹配文字高亮+最近搜索+热门搜索）、键盘上下键选择+回车确认、加载中显示旋转图标、响应式移动端全屏搜索。\n\n{选中文字}", "web_style", "form_input"),
    ChatTemplate("文件上传拖拽区", "用HTML+CSS+JS实现文件上传组件：拖拽区域（虚线边框+上传图标+提示文字）、拖拽文件进入时边框高亮+背景色变化+图标动画、点击选择文件、文件列表（文件名+大小+进度条+删除按钮）、上传中进度条动画、上传成功绿色对勾、上传失败红色重试、支持多文件、限制文件类型和大小提示。\n\n{选中文字}", "web_style", "form_input"),
    ChatTemplate("登录注册分栏页", "用HTML+CSS实现登录注册分栏页面：左侧品牌区（渐变背景+Logo+大标题+卖点列表+装饰图形）、右侧表单区（居中卡片、Tab切换登录/注册、表单输入框、记住我+忘记密码、提交按钮、第三方登录分隔线+图标按钮、底部注册/登录链接）、表单验证、响应式移动端隐藏左侧。\n\n{选中文字}", "web_style", "form_input"),
    ChatTemplate("开关切换组件", "用HTML+CSS实现开关（Toggle）组件：轨道（圆角胶囊）+滑块（圆形）、点击切换、开状态=主题色背景+滑块在右、关状态=灰色背景+滑块在左、过渡动画200ms ease-in-out、滑块悬停时轻微放大、禁用状态（半透明+不可点击）、带文字标签、支持不同尺寸、无障碍role=switch+aria-checked。\n\n{选中文字}", "web_style", "form_input"),

    // ===== 编程功能 - 后端API =====
    ChatTemplate("RESTful CRUD接口", "用【框架/语言】实现一套完整的RESTful CRUD API：资源为【实体名】，包含GET列表（分页+筛选+排序）、GET详情、POST创建、PUT更新、DELETE删除。要求：统一响应格式（code/message/data）、参数校验、错误处理（400/404/500）、权限中间件、日志记录、API文档注释。\n\n{选中文字}", "program_feature", "backend_api"),
    ChatTemplate("用户认证系统", "用【框架/语言】实现用户认证系统：注册（邮箱/手机号+密码+验证码）、登录（JWT token签发+刷新token）、登出（token黑名单）、密码重置（邮件链接）、修改密码、第三方登录（OAuth2.0）。要求：密码bcrypt加密、token过期处理、限流防暴力破解、会话管理、安全HTTP头。\n\n{选中文字}", "program_feature", "backend_api"),
    ChatTemplate("文件上传接口", "用【框架/语言】实现文件上传接口：支持单文件/多文件上传、分片上传（大文件）、断点续传、文件类型校验、大小限制、秒传（MD5去重）、上传进度回调、存储到本地/OSS/COS、生成访问URL、图片自动生成缩略图、文件病毒扫描（可选）。\n\n{选中文字}", "program_feature", "backend_api"),
    ChatTemplate("支付集成", "用【框架/语言】集成【支付宝/微信支付/Stripe】：创建订单、发起支付、支付回调（异步通知验签）、查询订单、退款、退款回调、对账。要求：订单号生成规则、金额处理（分单位避免浮点）、回调幂等性、签名验证、超时关闭订单、支付状态机、日志完整记录。\n\n{选中文字}", "program_feature", "backend_api"),
    ChatTemplate("WebSocket实时通信", "用【框架/语言】实现WebSocket服务：连接建立（鉴权）、心跳保活、消息收发（JSON格式+类型分发）、房间/群组管理、在线状态、消息持久化、未读计数、断线重连、消息ACK确认、离线消息推送、限流防刷、连接数监控。\n\n{选中文字}", "program_feature", "backend_api"),

    // ===== 编程功能 - 前端功能 =====
    ChatTemplate("登录注册流程", "用【前端框架】实现完整登录注册流程：登录页（账号密码+记住我+忘记密码）、注册页（多步骤：基本信息→邮箱验证→完善资料）、表单验证（实时+失焦）、密码强度指示器、验证码（图形/短信）、登录成功跳转、权限路由守卫、token存储（httpOnly cookie/localStorage）、401自动刷新token。\n\n{选中文字}", "program_feature", "frontend_feature"),
    ChatTemplate("数据表格CRUD", "用【前端框架】实现数据管理表格：列表展示（分页+排序+筛选+搜索）、新增（弹窗表单）、编辑（弹窗表单回填）、删除（二次确认）、批量操作（批量删除/导出）、行内编辑、列设置（显示/隐藏/拖拽排序）、数据导出（Excel/CSV）、加载状态、空状态、错误重试。\n\n{选中文字}", "program_feature", "frontend_feature"),
    ChatTemplate("图片裁剪上传", "用【前端框架】实现图片裁剪上传功能：选择图片（点击/拖拽）、预览、裁剪（自由裁剪/固定比例/圆形裁剪）、缩放旋转、滤镜调整（亮度/对比度/饱和度）、压缩（质量/尺寸）、上传（进度条）、上传成功预览、支持多图、格式转换（webp/jpg/png）、EXIF方向修正。\n\n{选中文字}", "program_feature", "frontend_feature"),
    ChatTemplate("无限滚动列表", "用【前端框架】实现无限滚动列表：滚动到底部自动加载下一页、加载中显示骨架屏/加载指示器、加载失败显示重试按钮、全部加载完显示没有更多了、防抖处理、虚拟滚动（大数据量性能优化）、回到顶部按钮、下拉刷新、列表项动画（入场/出场）、缓存已加载数据。\n\n{选中文字}", "program_feature", "frontend_feature"),
    ChatTemplate("表单多步骤向导", "用【前端框架】实现多步骤表单向导：步骤条（已完成/进行中/未开始）、上一步/下一步按钮、每步独立验证、进度保存（刷新不丢失）、最后一步确认预览、提交（加载状态+成功/失败反馈）、支持跳过可选步骤、步骤间数据传递、响应式移动端适配、键盘快捷键支持。\n\n{选中文字}", "program_feature", "frontend_feature"),

    // ===== 编程功能 - 数据库 =====
    ChatTemplate("表结构设计", "为【业务场景】设计数据库表结构：列出所有实体及字段（字段名/类型/长度/是否为空/默认值/注释）、主键/外键/索引设计、表关系（一对一/一对多/多对多）、枚举值定义、时间字段（created_at/updated_at/deleted_at软删除）、字符集和排序规则、给出完整的CREATE TABLE SQL语句。\n\n{选中文字}", "program_feature", "database"),
    ChatTemplate("SQL查询优化", "优化以下慢SQL查询：【SQL语句】。数据库：【MySQL/PostgreSQL】，表数据量：【约X万行】，EXPLAIN执行计划：【粘贴结果】。请：1.分析执行计划找出瓶颈 2.给出优化后的SQL 3.推荐添加的索引（含索引类型和字段顺序）4.说明优化原理 5.给出预期性能提升。\n\n{选中文字}", "program_feature", "database"),
    ChatTemplate("索引优化建议", "针对以下查询需求和表结构，给出索引优化建议：查询需求【描述】，表结构【CREATE TABLE语句】，当前索引【列出】，数据量【约X行】，慢查询日志【粘贴】。请：1.分析现有索引使用率 2.推荐新增/修改/删除的索引 3.给出索引命名规范 4.说明联合索引字段顺序原则 5.提醒索引维护成本（写入影响/存储空间）。\n\n{选中文字}", "program_feature", "database"),
    ChatTemplate("数据迁移脚本", "编写数据库迁移脚本：从【旧表结构】迁移到【新表结构】，包含：1.创建新表 2.数据迁移（字段映射/类型转换/数据清洗/默认值填充）3.数据校验（迁移前后行数/关键字段对比）4.回滚脚本 5.分批次迁移（大表避免锁表）6.灰度迁移方案 7.迁移时间预估 8.注意事项（外键/触发器/存储过程影响）。\n\n{选中文字}", "program_feature", "database"),
    ChatTemplate("分库分表方案", "为【表名】设计分库分表方案：当前数据量【X亿行】，日增长【X万行】，查询场景【描述】。请：1.选择分片键（说明理由）2.分片算法（范围/哈希/一致性哈希）3.分库分表数量规划 4.路由规则设计 5.跨分片查询方案 6.分布式ID生成方案 7.扩容方案（平滑迁移）8.运维工具（数据同步/校验/迁移）9.给出中间件选型建议（ShardingSphere/MyCat等）。\n\n{选中文字}", "program_feature", "database"),

    // ===== 编程功能 - 算法实现 =====
    ChatTemplate("排序算法实现", "用【语言】实现【快速排序/归并排序/堆排序/计数排序】算法：要求1.完整代码实现 2.详细注释说明每一步 3.时间复杂度和空间复杂度分析 4.最好/最坏/平均情况说明 5.稳定性分析 6.适用场景 7.与其他排序算法的对比 8.优化版本（如三数取中/尾递归优化）9.测试用例（含边界情况）。\n\n{选中文字}", "program_feature", "algorithm"),
    ChatTemplate("搜索算法实现", "用【语言】实现【二分查找/广度优先搜索BFS/深度优先搜索DFS/A*寻路】算法：要求1.完整代码实现 2.详细注释 3.时间/空间复杂度分析 4.适用场景 5.变体实现（如二分查找的左边界/右边界/旋转数组查找）6.图搜索的邻接表/邻接矩阵两种存储 7.路径还原 8.访问标记与去重 9.测试用例。\n\n{选中文字}", "program_feature", "algorithm"),
    ChatTemplate("动态规划解题", "用动态规划解决【问题描述】：要求1.问题分析（最优子结构/重叠子问题）2.状态定义（dp数组含义）3.状态转移方程推导 4.初始条件和边界 5.遍历顺序 6.完整代码实现 7.空间优化（滚动数组）8.时间/空间复杂度 9.对比暴力递归/记忆化搜索的性能差异 10.相关变种问题。\n\n{选中文字}", "program_feature", "algorithm"),
    ChatTemplate("字符串处理算法", "用【语言】实现【KMP字符串匹配/Manacher最长回文/Trie字典树/AC自动机】算法：要求1.算法原理讲解 2.完整代码实现 3.详细注释 4.时间/空间复杂度 5.预处理过程说明 6.匹配/查询过程演示 7.适用场景 8.与朴素算法的对比 9.实际应用举例 10.测试用例（含特殊字符/空串/全匹配等边界）。\n\n{选中文字}", "program_feature", "algorithm"),
    ChatTemplate("图算法实现", "用【语言】实现【Dijkstra最短路径/Prim最小生成树/拓扑排序/强连通分量Tarjan】算法：要求1.图的存储（邻接表/邻接矩阵）2.完整代码实现 3.详细注释 4.时间/空间复杂度 5.算法原理讲解 6.适用场景（有向/无向/带权/无权）7.路径还原 8.与其他同类算法对比 9.测试用例（含孤立点/环/负权边等特殊情况）。\n\n{选中文字}", "program_feature", "algorithm"),

    // ===== 编程功能 - 工具脚本 =====
    ChatTemplate("CSV数据处理脚本", "用【Python/Shell】编写CSV数据处理脚本：功能包括1.读取CSV（支持大文件流式读取）2.数据清洗（去重/空值处理/格式统一/异常值过滤）3.数据转换（字段映射/类型转换/编码转换）4.数据统计（分组聚合/计数/求和/平均值）5.数据筛选（按条件过滤）6.排序 7.输出（CSV/JSON/Excel/数据库）8.命令行参数 9.进度显示 10.错误日志。\n\n{选中文字}", "program_feature", "tool_script"),
    ChatTemplate("批量文件重命名", "用【Python/Shell】编写批量文件重命名脚本：支持1.按序号重命名（前缀+数字+后缀，可配置起始值和位数）2.按日期重命名（创建日期/修改日期/当前日期）3.查找替换（文件名中的字符串替换，支持正则）4.大小写转换（全大写/全小写/首字母大写）5.添加前后缀 6.删除指定字符 7.编号补零 8.预览模式（只显示不执行）9.撤销功能 10.日志记录。\n\n{选中文字}", "program_feature", "tool_script"),
    ChatTemplate("日志分析脚本", "用【Python/Shell/AWK】编写日志分析脚本：功能1.按级别过滤（ERROR/WARN/INFO/DEBUG）2.按时间范围筛选 3.按关键词搜索 4.统计分析（错误Top10/接口耗时排行/状态码分布）5.正则提取（IP/URL/耗时/用户ID）6.异常堆栈聚合 7.实时监控（tail -f模式+告警阈值）8.生成报告（文本/HTML/图表）9.大文件分块处理 10.支持多种日志格式（Nginx/Tomcat/自定义）。\n\n{选中文字}", "program_feature", "tool_script"),
    ChatTemplate("定时任务脚本", "用【语言】编写定时任务脚本：功能1.任务调度（cron表达式/固定间隔/特定时间点）2.任务执行（支持Shell命令/Python函数/HTTP请求）3.并发控制（避免重复执行/最大并发数）4.超时控制（任务超时自动终止）5.失败重试（次数/间隔/退避策略）6.任务依赖（A完成后执行B）7.日志记录（执行时间/耗时/输出/错误）8.告警通知（失败时邮件/钉钉/企业微信）9.任务状态持久化 10.管理接口（查看/暂停/恢复/手动触发）。\n\n{选中文字}", "program_feature", "tool_script"),
    ChatTemplate("数据备份脚本", "用【Shell/Python】编写数据备份脚本：功能1.数据库备份（MySQL/PostgreSQL/MongoDB，全量+增量）2.文件备份（指定目录，增量/差异/全量）3.压缩（gzip/tar/zip，可配置压缩率）4.加密（AES/GPG）5.备份命名（时间戳+主机名+类型）6.保留策略（按天/周/月轮转，删除过期备份）7.异地存储（本地+FTP/S3/OSS同步）8.备份校验（MD5/SHA校验+恢复测试）9.执行日志+告警 10.恢复脚本配套。\n\n{选中文字}", "program_feature", "tool_script"),

    // ===== 编程功能 - 架构设计 =====
    ChatTemplate("微服务拆分方案", "为【系统名称】设计微服务拆分方案：当前单体架构【描述】，业务模块【列出】。请：1.服务边界划分（按领域驱动设计DDD限界上下文）2.每个服务的职责和数据归属 3.服务间通信方式（同步REST/gRPC/异步消息队列）4.数据一致性方案（Saga/事件溯源/最终一致性）5.服务拆分优先级和迁移路线（绞杀者模式）6.API网关设计 7.服务注册发现 8.配置中心 9.链路追踪 10.组织架构调整建议。\n\n{选中文字}", "program_feature", "architecture"),
    ChatTemplate("缓存策略设计", "为【业务场景】设计缓存策略：数据特征【读多写少/读写均衡/热点数据】，数据量【X】，QPS【X】，延迟要求【Xms】。请：1.缓存选型（Redis/Memcached/Caffeine/本地缓存）2.缓存粒度（对象缓存/列表缓存/页面缓存）3.缓存key设计（命名规范/过期时间）4.缓存更新策略（Cache Aside/Write Through/Write Behind/Refresh Ahead）5.缓存穿透/击穿/雪崩解决方案 6.缓存一致性（延迟双删/订阅binlog）7.热点key处理 8.大key拆分 9.缓存监控指标 10.给出代码示例。\n\n{选中文字}", "program_feature", "architecture"),
    ChatTemplate("消息队列设计", "为【业务场景】设计消息队列方案：场景【异步解耦/流量削峰/数据同步/事件驱动】，吞吐量【X条/秒】，延迟要求【Xms】，消息顺序要求【是/否】，消息可靠性要求【至少一次/恰好一次】。请：1.中间件选型（Kafka/RabbitMQ/RocketMQ/Pulsar）2.Topic/Queue设计（命名/分区数/副本数）3.消息格式（JSON/Protobuf/Avro，含消息ID/时间戳/版本）4.生产者（确认机制/重试/幂等）5.消费者（订阅模式/批量消费/并发度/手动ACK）6.死信队列处理 7.消息积压处理 8.顺序消息方案 9.事务消息方案 10.监控告警指标。\n\n{选中文字}", "program_feature", "architecture"),
    ChatTemplate("限流熔断设计", "为【系统/接口】设计限流熔断方案：QPS峰值【X】，依赖服务【列出】，故障影响【描述】。请：1.限流算法选型（令牌桶/漏桶/滑动窗口/固定窗口）2.限流粒度（全局限流/用户限流/接口限流/IP限流）3.限流实现（网关层/应用层/分布式限流Redis Lua）4.熔断设计（状态机：关闭/打开/半开，触发条件：错误率/慢调用比例/异常数）5.降级策略（返回默认值/缓存/兜底页面/异步处理）6.熔断框架选型（Sentinel/Hystrix/Resilience4j）7.热点参数限流 8.系统自适应限流（CPU/负载/RT）9.限流熔断后的用户体验 10.监控大盘指标。\n\n{选中文字}", "program_feature", "architecture"),
    ChatTemplate("分布式锁设计", "为【业务场景】设计分布式锁方案：场景【库存扣减/订单创建/定时任务防重/幂等控制】，并发量【X】，锁持有时间【X】，锁粒度【X】。请：1.实现方案选型（Redis/ZooKeeper/数据库/etcd）2.Redis锁实现（SET NX PX+唯一标识+Lua原子释放）3.锁续期（看门狗机制）4.可重入锁设计 5.公平锁/非公平锁 6.锁超时与死锁处理 7.红锁RedLock（多节点）8.锁粒度优化（分段锁/细粒度锁）9.锁竞争优化（乐观锁/无锁/自旋）10.给出完整代码实现和注意事项。\n\n{选中文字}", "program_feature", "architecture"),

    // ===== 日常生活 - 时间管理 =====
    ChatTemplate("每日计划安排", "帮我安排今天的计划：我今天需要做的事情有【列出待办事项】，可用时间是【时间段】，我的最高优先级是【1-2件事】。请：1.按重要紧急四象限分类 2.制定时间块日程（含休息和缓冲时间）3.标出必须完成的3件事 4.建议什么时候处理需要专注的工作 5.提醒可能被低估时间的任务 6.给出今日结束时的检查清单。\n\n{选中文字}", "daily_life", "time_manage"),
    ChatTemplate("任务优先级排序", "帮我给以下任务排优先级：【列出所有任务及大致耗时】。请用艾森豪威尔矩阵（重要/紧急）分类，然后：1.给出今天必须完成的Top3 2.给出本周应该完成的任务 3.可以委托或推迟的任务 4.建议的执行顺序和理由 5.每个任务的预估耗时 6.提醒哪些任务如果不做会有什么后果。\n\n{选中文字}", "daily_life", "time_manage"),
    ChatTemplate("周回顾总结", "帮我做本周回顾：本周完成了【列出成就】，没完成的有【列出】，遇到的困难是【描述】。请：1.总结本周做得好的3件事 2.分析未完成任务的原因 3.找出时间浪费的地方 4.总结学到的经验教训 5.给出下周改进的3个具体行动 6.帮我调整下周的目标使其更合理 7.提醒需要跟进的事项。\n\n{选中文字}", "daily_life", "time_manage"),
    ChatTemplate("会议纪要整理", "帮我整理以下会议记录：【粘贴会议笔记/录音转写】。请整理成：1.会议主题和时间 2.参会人员 3.关键讨论要点（按议题分组）4.做出的决定（明确列出）5.行动项（任务/负责人/截止时间，用表格）6.待跟进的问题 7.下次会议时间 8.需要同步给未参会人员的要点。要求简洁明了，可直接发送。\n\n{选中文字}", "daily_life", "time_manage"),
    ChatTemplate("番茄钟工作计划", "帮我用番茄工作法安排【任务名称】：任务内容是【描述】，预计需要【X】小时，截止时间是【日期】。请：1.将任务拆分成可在25分钟内完成的小步骤 2.安排番茄钟序列（25分钟工作+5分钟休息，每4个后长休息15-30分钟）3.标出每个番茄钟的具体目标 4.建议什么时候休息和喝水 5.提醒如何处理干扰和打断 6.给出完成后的奖励建议。\n\n{选中文字}", "daily_life", "time_manage"),

    // ===== 日常生活 - 健康饮食 =====
    ChatTemplate("一周减脂餐计划", "帮我制定一周减脂餐计划：我的情况是【性别/年龄/身高/体重/目标体重】，饮食习惯是【偏好/忌口/过敏】，烹饪条件是【厨房设备/烹饪时间】，预算是【每周X元】。请：1.制定7天三餐+加餐计划 2.每餐标注热量和三大营养素（蛋白质/碳水/脂肪）3.给出具体食材用量 4.简单易做的做法步骤 5.每周采购清单（按超市区域分类）6.可以提前准备的meal prep建议 7.外食时的选择建议 8.注意营养均衡不要极端节食。\n\n{选中文字}", "daily_life", "health_food"),
    ChatTemplate("健身计划制定", "帮我制定健身计划：我的目标是【增肌/减脂/塑形/提升体能】，当前水平是【新手/中级/高级】，可用时间是【每周X次，每次X分钟】，场地设备是【健身房/居家（有什么器械）】，身体状况是【有无伤病/限制】。请：1.制定每周训练计划（分化训练：胸/背/腿/肩/臂/核心）2.每个动作的组数次数重量建议 3.组间休息时间 4.热身和拉伸安排 5.渐进超负荷方案 6.饮食配合建议 7.休息恢复建议 8.4周后如何调整计划。\n\n{选中文字}", "daily_life", "health_food"),
    ChatTemplate("营养搭配建议", "帮我分析和改善我的饮食：我平时的饮食是【描述一天吃什么】，我的目标是【减脂/增肌/改善健康/控制血糖】，身体状况是【有无疾病/指标异常】。请：1.分析当前饮食的优点和问题 2.热量和营养素摄入估算 3.具体的改善建议（替换什么/增加什么/减少什么）4.推荐的食物清单 5.需要避免或限制的食物 6.一日三餐的示范搭配 7.加餐建议 8.提醒需要咨询医生或营养师的情况。\n\n{选中文字}", "daily_life", "health_food"),
    ChatTemplate("睡眠改善方案", "帮我改善睡眠质量：我目前的睡眠情况是【入睡时间/起床时间/入睡时长/夜醒次数/睡眠感受】，生活习惯是【工作时间/运动情况/咖啡因摄入/睡前习惯】，睡眠环境是【卧室光线/噪音/温度/床品】。请：1.分析可能影响睡眠的因素 2.制定睡前1小时例行程序 3.睡眠环境优化建议 4.白天习惯调整建议（运动/光照/咖啡因/午睡）5.入睡困难时的应对方法 6.夜醒后如何快速再入睡 7.建立稳定生物钟的方法 8.何时需要就医的提醒。\n\n{选中文字}", "daily_life", "health_food"),
    ChatTemplate("食材采购清单", "帮我生成一周食材采购清单：我计划做的菜是【列出菜品】，用餐人数是【X人】，饮食限制是【忌口/过敏】。请：1.按超市区域分类（蔬菜/水果/肉类/水产/蛋奶/豆制品/主食/调料/零食饮品）2.每种食材的具体用量 3.可替代的食材建议 4.挑选食材的小技巧 5.储存方法建议 6.哪些可以买冷冻/罐头替代 7.预算估算 8.提醒检查家里已有的调料避免重复购买。\n\n{选中文字}", "daily_life", "health_food"),

    // ===== 日常生活 - 出行旅游 =====
    ChatTemplate("旅行行程规划", "帮我规划一次旅行：目的地是【城市/国家】，出行时间是【日期，共X天】，人数是【X大X小】，预算是【X元】，兴趣偏好是【自然风光/历史文化/美食/购物/亲子/冒险】，出行方式是【飞机/高铁/自驾】。请：1.按天安排详细行程（上午/下午/晚上）2.每个景点的游玩时长和门票 3.交通方式和耗时 4.推荐餐厅（含人均和招牌菜）5.住宿区域和酒店推荐 6.每日预算明细 7.注意事项和行前准备 8.备选方案（天气不好时）。\n\n{选中文字}", "daily_life", "travel"),
    ChatTemplate("行李打包清单", "帮我生成旅行打包清单：目的地是【地方】，季节天气是【温度/降水】，旅行天数是【X天】，旅行类型是【商务/休闲/户外/亲子】，特殊需求是【带药/带电脑/带婴儿】。请：1.按类别分类（证件/衣物/洗漱/电子/药品/其他）2.每件物品的数量建议 3.哪些可以到当地买 4.收纳打包技巧 5.随身行李和托运行李的分配 6.安检注意事项 7.容易遗漏的物品提醒 8.返程时的打包建议（留空间买纪念品）。\n\n{选中文字}", "daily_life", "travel"),
    ChatTemplate("景点攻略整理", "帮我整理【目的地】的景点攻略：我有【X天】时间，偏好是【描述】，体力情况是【好/一般/差】。请：1.必去景点Top10（含推荐理由/游玩时长/门票/开放时间/最佳游览时段）2.景点之间的距离和交通方式 3.推荐的游览路线（避免走回头路）4.每个景点的拍照机位和避坑指南 5.需要提前预约的景点 6.免费/低价景点推荐 7.适合休息和吃饭的地方 8.人流量大的景点的错峰建议。\n\n{选中文字}", "daily_life", "travel"),
    ChatTemplate("交通方案对比", "帮我对比从【出发地】到【目的地】的交通方案：出行时间是【日期】，人数是【X人】，行李是【X件】，预算是【X元】，时间要求是【越快越好/越便宜越好/舒适优先】。请对比：1.飞机（航班时间/价格/行李额/到机场交通/总耗时）2.高铁/火车（车次/时间/价格/座位等级/到车站交通）3.自驾（里程/油费/过路费/停车费/疲劳度）4.长途汽车 5.每种方案的优缺点 6.推荐方案及理由 7.购票建议和注意事项 8.如果遇到延误的备选方案。\n\n{选中文字}", "daily_life", "travel"),
    ChatTemplate("旅行预算规划", "帮我做旅行预算规划：目的地是【地方】，天数是【X天】，人数是【X人】，总预算是【X元】，旅行风格是【穷游/经济/舒适/豪华】。请：1.各项费用明细（交通/住宿/餐饮/门票/购物/其他）2.每项的预算占比 3.省钱建议（机票/酒店/餐饮/门票各方面）4.容易超支的项目提醒 5.应急备用金建议 6.记账建议 7.汇率和手续费提醒（如出国）8.预算超支时的调整方案。\n\n{选中文字}", "daily_life", "travel"),

    // ===== 日常生活 - 购物消费 =====
    ChatTemplate("购买决策分析", "帮我分析是否应该购买【商品/服务】：商品信息是【名称/价格/功能】，我的需求是【描述】，预算是【X元】，已有类似物品是【有/无，描述】。请用以下维度分析：1.需求真实性（是真需要还是冲动消费）2.使用频率预估 3.性价比分析（对比同类产品）4.持有成本（维护/耗材/空间）5.机会成本（这笔钱的其他用途）6.可以延迟购买吗（等促销/等需要时）7.可以租/借/买二手吗 8.给出明确建议（买/不买/再等等）和理由。\n\n{选中文字}", "daily_life", "shopping"),
    ChatTemplate("产品性价比对比", "帮我对比以下几款产品：【产品A/产品B/产品C，含价格和主要参数】，我的使用场景是【描述】，最看重的是【1-3个维度】，预算是【X元】。请：1.制作对比表格（核心参数/价格/优缺点/适用人群）2.按我的需求加权评分 3.每款产品的最佳适用场景 4.每款产品的硬伤和短板 5.推荐购买哪款及理由 6.购买渠道建议（官网/京东/淘宝/拼多多）7.最佳购买时机（等什么促销）8.需要注意的配件和后续费用。\n\n{选中文字}", "daily_life", "shopping"),
    ChatTemplate("砍价话术技巧", "帮我准备砍价话术：我要买的是【商品/服务】，标价是【X元】，我的心理价位是【X元】，购买场景是【实体店/闲鱼/装修/服务报价】，对方情况是【商家/个人/可议价程度】。请：1.开场话术（不要先出价的技巧）2.了解对方底线的提问方式 3.砍价理由（找瑕疵/比别家/批量/老客户/现金/当场定）4.对方拒绝时的应对话术 5.逐步让步的策略 6.临门一脚的成交话术 7.如果谈不拢如何体面离开 8.注意事项（不要表现太想要/不要伤和气）。\n\n{选中文字}", "daily_life", "shopping"),
    ChatTemplate("退货维权指南", "帮我处理退货维权问题：我购买的商品/服务是【描述】，问题是【质量问题/货不对板/虚假宣传/服务未履行】，购买渠道是【淘宝/京东/拼多多/实体店/线上服务】，购买时间是【日期】，金额是【X元】，我已尝试【协商/客服/平台介入】，对方回应是【描述】。请：1.分析我的权益和法律依据（消费者权益保护法/三包规定/平台规则）2.下一步的具体操作步骤 3.需要收集和保存的证据清单 4.投诉渠道（平台客服/12315/黑猫投诉/行业监管/法院）5.沟通话术模板 6.可以主张的赔偿（退货/退款/赔偿/三倍赔偿）7.时间线和预期 8.注意事项（不要过了维权时效/保留所有证据）。\n\n{选中文字}", "daily_life", "shopping"),
    ChatTemplate("消费习惯分析", "帮我分析我的消费习惯：我最近的消费记录是【列出主要支出或粘贴账单】，我的月收入是【X元】，储蓄目标是【X元/月】。请：1.按类别统计消费占比（餐饮/交通/购物/娱乐/居住/其他）2.找出非理性消费和冲动消费 3.分析消费模式（固定支出/可变支出/一次性大额）4.可以削减的开支建议 5.优化后的预算分配方案（50/30/20法则或自定义）6.储蓄和投资建议 7.避免冲动消费的技巧 8.设定下个月的消费目标和检查点。\n\n{选中文字}", "daily_life", "shopping"),

    // ===== 日常生活 - 学习成长 =====
    ChatTemplate("学习计划制定", "帮我制定学习计划：我想学的是【技能/知识领域】，当前水平是【零基础/入门/中级】，目标是【描述想达到的水平】，可用时间是【每天X小时/每周X小时】，截止时间是【日期】，学习偏好是【视频/书籍/实践/项目】。请：1.学习路径规划（从入门到精通的阶段划分）2.每周学习计划（具体到每天学什么）3.推荐的学习资源（书籍/课程/网站/项目）4.每个阶段的检验标准 5.实践项目建议 6.常见难点和应对方法 7.如何保持动力和避免放弃 8.学习效果复盘方法。\n\n{选中文字}", "daily_life", "learning"),
    ChatTemplate("知识点梳理", "帮我梳理【领域/主题】的知识体系：我已经了解的是【描述已有知识】，我想系统掌握的是【描述目标】。请：1.画出知识体系框架（核心概念→分支→细节）2.按重要程度排序 3.每个知识点的一句话解释 4.知识点之间的关联和依赖关系 5.学习顺序建议（先学什么后学什么）6.容易混淆的概念对比 7.推荐的深入学习资源 8.检验是否掌握的自测问题。\n\n{选中文字}", "daily_life", "learning"),
    ChatTemplate("面试准备方案", "帮我准备【岗位名称】面试：我的背景是【学历/工作年限/项目经验】，目标公司类型是【大厂/创业/外企/国企】，面试时间是【日期】。请：1.该岗位的核心能力要求 2.高频面试题（技术题/行为题/项目题，含回答思路）3.自我介绍模板（1分钟/3分钟版本）4.项目经历梳理方法（STAR法则）5.需要复习的技术知识点清单 6.反问面试官的问题清单 7.面试着装和礼仪建议 8.面试后的跟进和复盘方法。\n\n{选中文字}", "daily_life", "learning"),
    ChatTemplate("读书笔记模板", "帮我整理【书名】的读书笔记：这本书的主题是【描述】，我的阅读目的是【学习什么/解决什么问题】。请按照以下结构整理：1.一句话总结全书核心观点 2.核心概念和金句（按章节）3.对我有启发的观点和原因 4.可以立即应用的行动项 5.我不同意或有疑问的观点 6.与我已有知识的关联 7.推荐给谁读 8.后续需要深入学习的相关书籍或主题。\n\n{选中文字}", "daily_life", "learning"),
    ChatTemplate("技能提升路径", "帮我规划【技能名称】的提升路径：我当前水平是【描述，可附作品/代码】，目标水平是【描述】，可用时间是【X小时/周】，我目前的瓶颈是【描述】。请：1.能力水平评估（初级/中级/高级的标准）2.从当前到目标的差距分析 3.分阶段提升计划（每阶段的目标和时长）4.每个阶段的具体练习项目 5.需要学习的核心知识点 6.获取反馈的方法（社区/导师/作品发布）7.优秀作品/代码参考 8.如何判断可以进入下一阶段 9.常见的学习误区。\n\n{选中文字}", "daily_life", "learning"),

    // ===== 日常生活 - 人际沟通 =====
    ChatTemplate("邮件撰写", "帮我写一封邮件：邮件类型是【工作汇报/请假/申请/道歉/感谢/催促/拒绝/询价】，收件人是【上级/同事/客户/合作方】，邮件目的是【描述】，需要包含的要点是【列出】，期望对方的行动是【描述】，语气要求是【正式/友好/坚决/委婉】。请：1.邮件主题（简洁明确）2.称呼 3.开头（背景/目的）4.正文（要点清晰，分段或列表）5.结尾（行动号召/感谢）6.署名 7.注意事项（不要遗漏什么/避免什么语气）8.如果对方不回复的跟进邮件模板。\n\n{选中文字}", "daily_life", "communication"),
    ChatTemplate("道歉话术", "帮我写道歉的话：我做错的事情是【描述】，对方是【朋友/同事/家人/客户/上级】，事情的影响是【描述】，我已经做了【弥补措施/还没做】，我的目标是【获得原谅/修复关系/承担责任】。请：1.道歉的核心原则（不找借口/承担责任/表达感受/提出弥补）2.具体的道歉话术（口语版和书面版）3.如果对方不接受怎么办 4.如何避免再次发生 5.后续如何修复信任 6.不同关系的道歉方式差异 7.道歉时的肢体语言和语气 8.什么情况下需要当面道歉而不是文字。\n\n{选中文字}", "daily_life", "communication"),
    ChatTemplate("拒绝话术", "帮我准备拒绝的话术：我要拒绝的是【请求/邀请/要求】，对方是【朋友/同事/上级/客户/家人】，拒绝的原因是【描述】，我担心的是【伤感情/影响关系/被误解】，我的底线是【描述】。请：1.拒绝的核心原则（直接但温和/给出理由但不过度解释/提供替代方案）2.具体的拒绝话术（不同场景版本）3.如果对方坚持或施压怎么办 4.如何保持关系不受影响 5.不同关系的拒绝方式差异 6.拒绝后的后续跟进 7.如何克服拒绝时的愧疚感 8.什么情况下可以妥协什么情况下必须坚持。\n\n{选中文字}", "daily_life", "communication"),
    ChatTemplate("谈判技巧准备", "帮我准备谈判：谈判主题是【薪资/价格/合作条件/资源分配】，对方是【老板/客户/合作方/家人】，我的目标是【理想结果/底线】，对方的立场和需求是【描述/推测】，我的筹码是【描述】，对方的筹码是【描述】。请：1.谈判前的准备（信息收集/目标设定/BATNA最佳替代方案）2.开场策略 3.如何提出条件和让步 4.应对对方策略的方法（哭穷/拖延/红脸白脸/最后通牒）5.沟通话术模板 6.如何打破僵局 7.达成协议后的确认和落实 8.如果谈判破裂怎么办 9.注意事项（不要暴露底线/控制情绪/不要人身攻击）。\n\n{选中文字}", "daily_life", "communication"),
    ChatTemplate("冲突调解方案", "帮我处理人际冲突：冲突双方是【我和谁/谁和谁】，冲突起因是【描述】，目前的状态是【冷战/争吵/僵持】，双方的立场和需求是【描述】，我的角色是【当事人/调解者】，我的目标是【解决问题/修复关系/达成共识】。请：1.冲突的核心问题分析（表面问题vs深层需求）2.双方的情绪和立场分析 3.沟通的正确步骤（先倾听再表达/用我信息/对事不对人）4.具体的沟通话术 5.如果对方拒绝沟通怎么办 6.寻求第三方帮助的时机和方法 7.修复关系的后续行动 8.如何避免类似冲突再次发生 9.什么情况下需要保持距离或结束关系。\n\n{选中文字}", "daily_life", "communication"),

    // ===== 编程bug - 错误分析 =====
    ChatTemplate("堆栈追踪解读", "帮我分析这个错误：错误信息是【粘贴完整错误和堆栈追踪】，代码位置是【相关代码片段】，使用的语言/框架是【描述】，复现步骤是【描述】。请：1.逐行解读堆栈追踪，定位真正的出错位置 2.解释错误类型和含义 3.分析最可能的根本原因（按概率排序）4.给出修复方案 5.需要检查的相关代码 6.如何验证修复是否正确 7.如何添加防御性代码避免再次发生 8.类似错误的预防建议。\n\n{选中文字}", "program_bug", "error_analysis"),
    ChatTemplate("空指针异常定位", "帮我定位这个空指针/NoneType错误：错误信息是【粘贴】，相关代码是【粘贴出错函数及上下文】，语言是【描述】，这个对象应该在哪里被赋值是【描述】。请：1.分析哪个对象为null以及为什么 2.列出所有可能导致null的代码路径 3.用什么方法可以快速定位（日志/断点/断言）4.修复方案（判空/默认值/异常处理/确保赋值）5.哪种修复方式最适合这个场景 6.如何添加单元测试覆盖null场景 7.代码中还有哪些类似的潜在空指针风险 8.静态检查工具配置建议。\n\n{选中文字}", "program_bug", "error_analysis"),
    ChatTemplate("类型错误分析", "帮我分析这个类型错误：错误信息是【TypeError/类型不匹配/ClassCastException等，粘贴完整】，相关代码是【粘贴】，期望类型是【描述】，实际类型是【描述】，数据来源是【API返回/用户输入/数据库/计算结果】。请：1.分析类型不匹配的根本原因 2.数据在哪个环节发生了类型变化 3.修复方案（类型转换/类型守卫/接口定义/数据校验）4.如何在入口处做类型校验防止污染下游 5.TypeScript/Python/Java等语言的类型安全最佳实践 6.如何添加运行时类型检查 7.单元测试如何覆盖类型边界 8.类似问题的系统性排查方法。\n\n{选中文字}", "program_bug", "error_analysis"),
    ChatTemplate("内存泄漏排查", "帮我排查内存泄漏问题：现象是【内存持续增长/OOM/页面卡顿】，使用的技术栈是【语言/框架】，泄漏发生在【页面/功能/长时间运行后】，已用工具检测到【堆快照/内存分析结果，粘贴】。请：1.内存泄漏的常见原因分类（未清理的事件监听/未取消的定时器/未关闭的连接/循环引用/全局变量/缓存未清理）2.根据现象判断最可能的原因 3.系统的排查步骤和工具使用方法 4.如何定位具体泄漏的代码 5.修复方案和代码示例 6.如何添加自动化检测（单元测试/CI检查）7.监控和告警建议 8.代码审查时的内存泄漏检查清单。\n\n{选中文字}", "program_bug", "error_analysis"),
    ChatTemplate("并发竞态分析", "帮我分析这个并发问题：现象是【数据不一致/偶发崩溃/结果不确定/死锁】，相关代码是【粘贴多线程/异步/协程代码】，使用的并发机制是【线程/协程/异步回调/Promise/goroutine】，复现概率是【偶发/必现】。请：1.分析竞态条件的根本原因（共享变量未同步/执行顺序不确定/原子性被破坏/可见性问题）2.是否存在死锁（四个必要条件分析）3.修复方案（加锁/原子操作/不可变数据/消息传递/消除共享状态）4.哪种同步机制最适合（互斥锁/读写锁/CAS/信号量/Channel）5.如何避免死锁（锁顺序/超时/减少锁粒度）6.如何编写并发测试（压力测试/竞态检测工具）7.性能影响评估 8.并发编程最佳实践清单。\n\n{选中文字}", "program_bug", "error_analysis"),

    // ===== 编程bug - 调试方法 =====
    ChatTemplate("小黄鸭调试法", "我来给你解释我的代码和我认为它在做什么，你扮演小黄鸭：代码是【粘贴】，我认为它的逻辑是【描述我的理解】，实际行为是【描述bug现象】。请：1.认真听我解释，不要急着给答案 2.针对我解释中的假设提出澄清问题 3.指出我理解中可能有偏差的地方 4.引导我自己发现问题（通过提问而不是直接给答案）5.如果我还是没发现，再给出提示 6.最后总结问题的根本原因 7.帮我整理调试思路，以后遇到类似问题如何自己排查。\n\n{选中文字}", "program_bug", "debug_method"),
    ChatTemplate("最小复现用例", "帮我把这个bug简化为最小复现用例：完整代码是【粘贴或描述项目结构】，bug现象是【描述】，复现步骤是【描述】，涉及的模块是【列出】。请：1.分析哪些代码是bug必需的，哪些可以移除 2.逐步剥离无关代码，给出30行以内的最小复现示例 3.解释每一步为什么可以安全移除 4.在简化过程中，如果bug消失了说明什么 5.最小复现用例的价值和使用场景 6.如何用最小复现用例提issue或求助 7.如何基于最小复现用例编写回归测试 8.如果无法简化，说明可能是什么类型的问题（环境/时序/依赖）。\n\n{选中文字}", "program_bug", "debug_method"),
    ChatTemplate("测试先行调试", "这个函数有bug：症状是【描述】，代码是【粘贴】。先不要修复，请：1.写一个应该通过但目前会失败的单元测试，精确复现这个bug 2.解释这个测试为什么能复现bug 3.运行测试确认它确实失败 4.然后提出最小化的修复方案 5.修复后运行测试确认通过 6.再写2-3个边界情况的测试确保修复完整 7.检查是否有其他类似的代码路径也有同样的bug 8.总结这种测试先行调试方法的好处和适用场景。\n\n{选中文字}", "program_bug", "debug_method"),
    ChatTemplate("假设验证调试", "我有一个bug：在【组件/函数/模块】中，现象是【描述】。我的假设是【我的猜测，比如清理函数在新effect注册后才执行】。相关代码是【粘贴】。请：1.判断我的假设是否合理 2.设计验证假设的方法（加日志/断点/修改变量/隔离测试）3.如果假设正确，给出修复方案 4.如果假设错误，列出下一个最可能的假设 5.系统地列出所有可能的原因（按概率排序）6.如何高效地逐个验证（二分法/控制变量）7.避免常见的调试误区（只改不验证/同时改多处/忽略日志）8.整理一个调试检查清单。\n\n{选中文字}", "program_bug", "debug_method"),
    ChatTemplate("二分法定位bug", "帮我用二分法定位这个bug：现象是【描述】，项目有【X个模块/文件/提交】，bug是最近引入的/一直存在，已知【在某个版本正常/某个模块正常】。请：1.确定二分的范围（代码模块/git提交/配置项/输入数据）2.设计二分策略（每次排除一半）3.具体的验证步骤（如何快速判断每一半是否有bug）4.git bisect的使用方法和命令 5.如果是模块间交互问题如何二分 6.如果是配置或环境问题如何二分 7.定位到具体代码行后的下一步 8.如何避免引入新的bug（每次只改一处）9.二分法的局限性和不适用场景。\n\n{选中文字}", "program_bug", "debug_method"),

    // ===== 编程bug - 性能问题 =====
    ChatTemplate("慢SQL优化", "帮我优化这个慢SQL：SQL语句是【粘贴】，数据库是【MySQL/PostgreSQL，版本】，表结构是【CREATE TABLE语句】，数据量是【约X万行】，EXPLAIN执行计划是【粘贴】，当前执行时间是【X秒】，期望时间是【Xms】。请：1.分析执行计划找出瓶颈（全表扫描/文件排序/临时表/回表/关联顺序）2.给出优化后的SQL 3.推荐添加的索引（含字段顺序和类型）4.如果需要改表结构给出建议 5.说明优化原理 6.如何验证优化效果 7.还有哪些相关SQL可能也有类似问题 8.慢查询监控和告警建议。\n\n{选中文字}", "program_bug", "performance"),
    ChatTemplate("前端性能瓶颈", "帮我排查前端性能问题：现象是【页面加载慢/滚动卡顿/交互延迟/内存占用高】，页面是【描述】，技术栈是【框架/版本】，性能分析数据是【Lighthouse报告/Performance面板/网络瀑布图，粘贴关键数据】。请：1.分析性能瓶颈在哪（网络/渲染/JS执行/内存/图片）2.按影响程度排序优化项 3.给出具体的优化方案（代码分割/懒加载/虚拟列表/防抖节流/缓存/减少重排重绘）4.每个优化的预期收益和实施成本 5.优化的优先级排序 6.如何测量和验证优化效果 7.性能预算设定 8.持续性能监控方案（CI中性能检查/真实用户监控RUM）。\n\n{选中文字}", "program_bug", "performance"),
    ChatTemplate("CPU占用过高", "帮我排查CPU占用过高问题：现象是【服务CPU持续X%/某个请求CPU高/偶发CPU飙升】，技术栈是【语言/框架】，已有的监控数据是【top/pprof/火焰图，粘贴】，相关代码是【可疑代码片段】。请：1.分析CPU高的可能原因（死循环/频繁GC/计算密集/锁竞争/正则回溯/序列化）2.如何用工具定位热点函数（pprof/JFR/async-profiler/火焰图）3.根据现象判断最可能的原因 4.给出优化方案（算法优化/缓存/批量处理/异步化/限流）5.如何验证优化效果 6.如何添加CPU使用率监控和告警 7.压测方法验证 8.代码审查时的性能检查清单 9.什么时候需要水平扩展而不是优化代码。\n\n{选中文字}", "program_bug", "performance"),
    ChatTemplate("启动速度优化", "帮我优化应用启动速度：当前启动时间是【X秒】，技术栈是【Android/iOS/Web/后端服务】，启动流程是【描述各阶段】，各阶段耗时是【如果有测量数据】，期望启动时间是【X秒】。请：1.分析启动流程的瓶颈阶段 2.给出懒加载/延迟初始化方案（哪些可以延后）3.预加载和预热策略 4.减少启动时的同步操作（IO/网络/计算移到后台）5.优化启动时的依赖注入和初始化顺序 6.资源优化（图片/字体/JS/CSS压缩和按需加载）7.缓存策略（本地缓存/预热缓存）8.如何测量启动各阶段耗时 9.优化效果验证方法 10.启动速度监控和防劣化（CI中检查启动时间）。\n\n{选中文字}", "program_bug", "performance"),
    ChatTemplate("接口响应慢优化", "帮我优化这个慢接口：接口是【URL/方法名】，当前响应时间是【Xms/P99是Xms】，技术栈是【框架】，接口逻辑是【描述：查什么表/调什么服务/做什么计算】，已有的监控数据是【慢查询/下游耗时/CPU/内存】。请：1.分析接口耗时分布（网络/计算/数据库/下游服务/序列化）2.找出最大的耗时项 3.给出优化方案（SQL优化/加缓存/并行调用/批量查询/减少字段/分页/异步化）4.每个优化的预期收益 5.优化的实施顺序 6.如何验证优化效果（压测/监控）7.接口性能基线和告警设置 8.如何防止后续劣化（性能测试纳入CI）9.什么时候需要考虑架构层面的优化（读写分离/分库分表/微服务拆分）。\n\n{选中文字}", "program_bug", "performance"),

    // ===== 编程bug - 安全漏洞 =====
    ChatTemplate("SQL注入防护", "帮我审计和修复SQL注入风险：相关代码是【粘贴数据库操作代码】，使用的ORM/数据库库是【描述】，用户输入的参数是【列出】。请：1.分析是否存在SQL注入风险（字符串拼接/动态SQL/未参数化/like/in/order by处理）2.如果存在，给出攻击示例（输入什么会导致注入）3.修复方案（参数化查询/预编译语句/ORM正确用法/存储过程/白名单校验）4.对于无法参数化的部分（表名/字段名/排序）如何安全处理 5.LIKE查询中的特殊字符转义 6.批量IN查询的安全处理 7.数据库权限最小化配置 8.如何添加自动化检测（静态扫描/依赖检查/渗透测试）9.安全编码规范 checklist。\n\n{选中文字}", "program_bug", "security"),
    ChatTemplate("XSS跨站脚本防护", "帮我审计和修复XSS风险：相关代码是【粘贴前端渲染代码】，技术栈是【框架】，用户输入展示的地方是【列出】。请：1.分析是否存在XSS风险（innerHTML/v-html/eval/拼接HTML/未转义输出/dom操作）2.区分反射型/存储型/DOM型XSS 3.给出攻击示例 4.修复方案（文本输出/框架自动转义/DOMPurify消毒/CSP内容安全策略/输入校验）5.富文本场景的安全处理（白名单标签和属性）6.URL中的javascript:协议防护 7.如何设置CSP响应头 8.HttpOnly cookie设置 9.自动化检测方法（ESLint规则/安全扫描/渗透测试）10.安全编码培训要点。\n\n{选中文字}", "program_bug", "security"),
    ChatTemplate("权限越权审计", "帮我审计权限越权漏洞：系统的权限模型是【描述：RBAC/ABAC/角色列表】，相关接口是【列出需要权限控制的接口】，当前权限校验代码是【粘贴】。请：1.分析可能存在的越权风险（水平越权/垂直越权/未授权访问/IDOR不安全直接对象引用）2.给出攻击示例（如何越权访问他人数据/执行高权限操作）3.修复方案（服务端鉴权/对象级权限校验/参数校验/拒绝默认/最小权限原则）4.如何系统地设计权限校验中间件/拦截器 5.权限测试用例设计（未登录/低权限/他人资源/边界情况）6.如何在CI中自动化权限测试 7.敏感操作的二次确认和审计日志 8.权限变更的影响评估方法 9.安全编码规范。\n\n{选中文字}", "program_bug", "security"),
    ChatTemplate("敏感信息泄露排查", "帮我排查敏感信息泄露风险：项目代码是【描述或粘贴可疑部分】，已发现的泄露是【描述/暂无】，涉及的敏感信息是【密钥/密码/Token/个人信息/业务数据】。请：1.排查常见的泄露渠道（日志打印/错误信息/前端代码/注释/URL参数/缓存/备份文件/Git历史/配置文件）2.给出检查清单和grep命令 3.如果发现泄露，应急处理步骤（轮换密钥/清理历史/通知/修复）4.预防方案（环境变量/密钥管理服务/配置中心/日志脱敏/错误信息统一处理/前端不存敏感数据）5.代码提交前的密钥扫描工具（git hooks/CI检查/gitleaks/trufflehog）6.依赖组件的漏洞扫描 7.敏感数据加密存储和传输 8.安全审计流程 9.团队安全意识培训要点。\n\n{选中文字}", "program_bug", "security"),
    ChatTemplate("CSRF攻击防护", "帮我审计和修复CSRF风险：相关表单/接口是【列出状态变更操作】，技术栈是【框架】，当前的防护措施是【描述/暂无】。请：1.分析是否存在CSRF风险（状态变更请求/无token验证/简单请求/跨站表单提交）2.给出攻击示例（恶意页面如何诱导用户提交请求）3.修复方案（CSRF Token/SameSite Cookie属性/自定义请求头/验证Origin/Referer/双重提交Cookie）4.每种方案的优缺点和适用场景 5.如何在框架中配置（Django/Spring/Express等）6.AJAX请求的CSRF处理 7.文件上传表单的特殊处理 8.登出接口的CSRF防护 9.测试方法（用Burp/浏览器插件验证）10.安全编码规范和checklist。\n\n{选中文字}", "program_bug", "security"),

    // ===== 编程bug - 兼容性 =====
    ChatTemplate("浏览器兼容问题", "帮我解决浏览器兼容性问题：问题是【描述在哪个浏览器有问题/什么现象】，涉及的CSS/JS特性是【描述】，需要兼容的浏览器版本是【列出】，当前代码是【粘贴】。请：1.分析兼容性问题的根本原因（特性支持度/前缀差异/渲染引擎差异/JS API差异）2.查caniuse确认各浏览器支持情况 3.给出兼容方案（polyfill/前缀回退/渐进增强/优雅降级/替代实现）4.CSS方面的处理（autoprefixer/feature query @supports/变量回退）5.JS方面的处理（babel转译/core-js/polyfill.io/特性检测）6.如果无法兼容，给出优雅降级方案 7.浏览器兼容性测试方法（BrowserStack/虚拟机/真实设备）8.项目中browserlist配置和构建工具集成 9.如何避免后续引入不兼容代码（ESLint规则/CI检查）。\n\n{选中文字}", "program_bug", "compatibility"),
    ChatTemplate("移动端适配问题", "帮我解决移动端适配问题：问题是【描述在什么设备/屏幕尺寸下有问题】，现象是【布局错乱/文字过小/点击区域小/横向滚动/输入框被键盘遮挡】，当前适配方案是【viewport/rem/vw/媒体查询/flex】，相关代码是【粘贴】。请：1.分析适配问题的根本原因（固定像素/缺少viewport/安全区域/触控目标/软键盘/不同DPR）2.给出修复方案（相对单位/媒体查询/安全区域env()/触控目标44px/100vh问题处理/软键盘适配）3.响应式断点设计建议 4.移动端常见坑和解决方案（iOS橡皮筋/点击延迟/输入框自动放大/刘海屏/底部横条）5.如何用Chrome DevTools的设备模式调试 6.真机测试方法 7.适配方案选型（rem/vw/flex/grid/媒体查询）8.移动端性能优化建议 9.无障碍适配（字体放大/对比度/屏幕阅读器）。\n\n{选中文字}", "program_bug", "compatibility"),
    ChatTemplate("版本升级问题", "帮我处理版本升级问题：我要从【旧版本】升级到【新版本】（框架/库/语言/依赖），当前遇到的问题是【描述错误/不兼容/行为变化】，相关代码是【粘贴报错位置】，升级文档是【描述/暂无】。请：1.分析错误的根本原因（API变更/废弃移除/行为变化/依赖冲突/配置变更）2.查升级指南和CHANGELOG确认breaking changes 3.给出具体的修改方案（API替换/配置更新/代码重构/依赖调整）4.如果有多个不兼容点，按优先级排序修改顺序 5.升级前的准备工作（读升级文档/备份/分支/测试覆盖）6.升级后的验证清单（功能测试/性能测试/回归测试）7.如何分阶段升级（先升小版本/灰度/feature flag）8.如果升级成本太高，是否有替代方案 9.如何防止依赖版本漂移（lock文件/版本范围策略/dependabot）。\n\n{选中文字}", "program_bug", "compatibility"),
    ChatTemplate("依赖冲突解决", "帮我解决依赖冲突问题：错误信息是【粘贴依赖冲突报错】，项目包管理器是【npm/yarn/pnpm/maven/gradle/go mod】，相关依赖是【列出冲突的包和版本】，我的项目是【描述】。请：1.分析冲突的根本原因（传递依赖版本不一致/peer dependency/版本范围不兼容/循环依赖）2.如何查看依赖树（npm ls/mvn dependency:tree/gradle dependencies）3.定位是哪个依赖引入了冲突版本 4.解决方法（升级/降级/锁定版本/overrides/resolutions/exclusions/替换依赖）5.每种方法的风险和注意事项 6.如何验证解决后没有引入新问题 7.如何避免未来的依赖冲突（版本范围策略/定期升级/依赖审计/lock文件）8.自动化工具（dependabot/renovate/snyk）9.依赖健康度检查（废弃包/未维护/安全漏洞）10.monorepo中的依赖管理。\n\n{选中文字}", "program_bug", "compatibility"),
    ChatTemplate("环境差异问题", "帮我解决环境差异问题：现象是【在我本地正常但线上/测试环境有问题/反过来】，差异环境是【描述：操作系统/Node版本/数据库版本/容器/网络】，问题表现是【描述】，已确认的差异是【列出】。请：1.系统排查环境差异的维度（操作系统/运行时版本/依赖版本/配置/环境变量/文件系统/网络/时区/编码/权限/资源限制）2.如何快速定位是哪个差异导致的（控制变量/二分法/容器化复现）3.给出「在我机器上能跑」问题的系统排查步骤 4.如何用Docker保证环境一致性 5.配置管理最佳实践（环境变量/配置中心/不同环境配置分离）6.如何记录和分享可复现的环境 7.线上环境的调试方法（日志/远程调试/抓包/APM）8.如何在CI中覆盖多环境测试 9.环境相关的常见坑（路径分隔符/大小写/换行符/时区/编码/权限）10.文档化环境要求（README/容器文件/CI配置）。\n\n{选中文字}", "program_bug", "compatibility"),

    // ===== 编程bug - 逻辑错误 =====
    ChatTemplate("边界条件处理", "帮我审计这段代码的边界条件：代码是【粘贴】，功能是【描述】，输入范围是【描述】。请：1.列出所有可能的边界情况（空值/零/负数/极大值/极小值/空集合/单元素/重复值/特殊字符/超长字符串/并发/超时/网络异常）2.分析当前代码在每种边界情况下的行为 3.找出会导致崩溃/错误结果/无限循环/内存溢出的边界 4.给出修复方案（参数校验/默认值/异常处理/循环条件/索引检查）5.如何系统地思考边界条件（等价类划分/边界值分析/错误推测）6.编写边界条件的单元测试用例 7.如何在代码审查中检查边界条件 8.防御性编程的原则和实践 9.契约式设计（前置条件/后置条件/不变式）10.常见的边界条件bug案例和教训。\n\n{选中文字}", "program_bug", "logic_error"),
    ChatTemplate("状态机错误", "帮我分析这个状态机的问题：状态有【列出所有状态】，事件有【列出所有事件】，当前状态转换逻辑是【粘贴代码或描述】，问题现象是【描述：状态错乱/无法转换/卡死/非法状态】。请：1.画出完整的状态转换图 2.分析是否存在状态遗漏（每个状态对每个事件的处理）3.找出非法状态转换（在错误状态下响应了错误事件）4.是否存在状态爆炸或状态冗余 5.修复方案（显式状态机/状态模式/表驱动/拒绝非法转换）6.如何设计健壮的状态机（初始状态/终态/异常状态/状态进入退出动作/守卫条件）7.状态机的测试方法（覆盖所有转换/状态不变式/随机测试）8.如何添加状态转换日志便于调试 9.复杂状态机的可视化工具 10.什么时候应该用状态机什么时候不该用 11.状态机和工作流引擎的选型。\n\n{选中文字}", "program_bug", "logic_error"),
    ChatTemplate("数据不一致排查", "帮我排查数据不一致问题：现象是【A表和B表数据对不上/缓存和数据库不一致/主从延迟/分布式事务问题】，涉及的数据是【描述】，相关操作是【描述：什么操作会导致不一致】，复现方式是【描述/偶发】。请：1.分析数据不一致的类型和原因（并发写入/事务未提交/缓存未更新/主从延迟/分布式事务失败/补偿失败/时间差/删除不同步）2.如何定位是哪个环节导致的不一致（日志/binlog/审计/数据对账）3.修复方案（事务/锁/乐观锁/最终一致性/补偿机制/缓存更新策略/双写一致性）4.数据修复方法（对账脚本/回滚/重放/补偿）5.如何预防（数据库约束/唯一索引/外键/事务隔离级别/幂等操作）6.数据一致性监控和告警（定时对账/差异检测）7.分布式系统的一致性模型选择（强一致/最终一致/因果一致）8.Saga模式和TCC模式的适用场景 9.如何设计幂等操作 10.数据修复后的验证方法。\n\n{选中文字}", "program_bug", "logic_error"),
    ChatTemplate("条件判断错误", "帮我分析这个条件判断的逻辑错误：代码是【粘贴if/else/switch/三元表达式】，预期行为是【描述】，实际行为是【描述bug】，测试用例是【列出输入和预期输出】。请：1.分析逻辑错误的类型（运算符优先级/==vs===/and/or短路/取反错误/边界包含/德摩根定律误用/空值判断/类型转换/浮点数比较）2.用真值表分析条件表达式 3.给出修复后的正确条件 4.如何简化复杂的条件表达式（拆分变量/提前返回/布尔代数化简/卫语句）5.如何编写覆盖所有分支的测试（条件覆盖/分支覆盖/路径覆盖/MC/DC）6.代码审查时如何检查条件逻辑 7.常见的条件判断bug模式 8.如何用静态分析工具检测（ESLint/SonarQube/编译器警告）9.防御性编程中的条件校验 10.什么时候应该用多态/策略模式替代复杂的条件判断。\n\n{选中文字}", "program_bug", "logic_error"),
    ChatTemplate("循环逻辑错误", "帮我分析这个循环的逻辑错误：代码是【粘贴for/while/forEach/递归】，预期行为是【描述】，实际行为是【死循环/漏元素/重复处理/索引越界/提前退出/结果错误】，输入数据是【描述】。请：1.分析循环错误的类型（初始化错误/条件错误/更新错误/off-by-one/边界包含/break/continue位置/嵌套循环/递归终止条件/迭代器失效）2.用小数据集手动追踪循环执行过程 3.给出修复方案 4.如何避免循环错误（用高级函数map/filter/reduce替代手写循环/清晰的循环变量命名/循环不变式）5.如何调试循环（打印每次迭代/条件断点/观察循环变量）6.编写循环的测试用例（空集合/单元素/多元素/边界值）7.性能问题（O(n²)/重复计算/提前终止）8.递归循环的栈溢出风险和尾递归优化 9.异步循环的执行顺序问题（forEach+await/for...of+await/Promise.all）10.常见的循环bug模式和教训。\n\n{选中文字}", "program_bug", "logic_error"),
)
