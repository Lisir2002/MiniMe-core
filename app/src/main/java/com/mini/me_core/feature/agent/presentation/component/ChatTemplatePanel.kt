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
)
