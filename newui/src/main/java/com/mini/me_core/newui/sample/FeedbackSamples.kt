package com.mini.me_core.newui.sample

import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Add
import androidx.compose.material.icons.rounded.Archive
import androidx.compose.material.icons.rounded.Code
import androidx.compose.material.icons.rounded.Delete
import androidx.compose.material.icons.rounded.DeleteOutline
import androidx.compose.material.icons.rounded.Home
import androidx.compose.material.icons.rounded.InsertDriveFile
import androidx.compose.material.icons.rounded.Notifications
import androidx.compose.material.icons.rounded.Palette
import androidx.compose.material.icons.rounded.Search
import androidx.compose.material.icons.rounded.Settings
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import com.mini.me_core.newui.designsystem.primitive.AppIcon
import com.mini.me_core.newui.designsystem.component.AppActionSheet
import com.mini.me_core.newui.designsystem.component.AppActionSheetItem
import com.mini.me_core.newui.designsystem.component.AppAlertDialog
import com.mini.me_core.newui.designsystem.component.AppAlertTone
import com.mini.me_core.newui.designsystem.component.AppAvatar
import com.mini.me_core.newui.designsystem.component.AppBadge
import com.mini.me_core.newui.designsystem.component.AppBadgeDot
import com.mini.me_core.newui.designsystem.component.AppBottomSheetList
import com.mini.me_core.newui.designsystem.component.AppBreadcrumb
import com.mini.me_core.newui.designsystem.component.AppButton
import com.mini.me_core.newui.designsystem.component.AppButtonVariant
import com.mini.me_core.newui.designsystem.component.AppCascadeNode
import com.mini.me_core.newui.designsystem.component.AppCascadingMenu
import com.mini.me_core.newui.designsystem.component.AppConfirmDialog
import com.mini.me_core.newui.designsystem.component.AppComboBox
import com.mini.me_core.newui.designsystem.component.AppCommandGroup
import com.mini.me_core.newui.designsystem.component.AppCommandPalette
import com.mini.me_core.newui.designsystem.component.AppContextMenu
import com.mini.me_core.newui.designsystem.component.AppCountdownDialog
import com.mini.me_core.newui.designsystem.component.AppCrumb
import com.mini.me_core.newui.designsystem.component.AppDock
import com.mini.me_core.newui.designsystem.component.AppDockItem
import com.mini.me_core.newui.designsystem.component.AppDialogTone
import com.mini.me_core.newui.designsystem.component.AppFAB
import com.mini.me_core.newui.designsystem.component.AppFileCard
import com.mini.me_core.newui.designsystem.component.AppFileState
import com.mini.me_core.newui.designsystem.component.AppFormDialog
import com.mini.me_core.newui.designsystem.component.AppGradientBorder
import com.mini.me_core.newui.designsystem.component.AppIconButton
import com.mini.me_core.newui.designsystem.component.AppInlineAlert
import com.mini.me_core.newui.designsystem.component.AppKeyCap
import com.mini.me_core.newui.designsystem.component.AppKeyCombo
import com.mini.me_core.newui.designsystem.component.AppLoadingOverlay
import com.mini.me_core.newui.designsystem.component.AppMarquee
import com.mini.me_core.newui.designsystem.component.AppMenu
import com.mini.me_core.newui.designsystem.component.AppMenuAction
import com.mini.me_core.newui.designsystem.component.AppMenuDivider
import com.mini.me_core.newui.designsystem.component.AppMenuItem
import com.mini.me_core.newui.designsystem.component.AppMenuRow
import com.mini.me_core.newui.designsystem.component.AppMiniBarChart
import com.mini.me_core.newui.designsystem.component.AppMultiSelectDialog
import com.mini.me_core.newui.designsystem.component.AppNavigationItem
import com.mini.me_core.newui.designsystem.component.AppNavigationMenu
import com.mini.me_core.newui.designsystem.component.AppNotificationItem
import com.mini.me_core.newui.designsystem.component.AppPermissionDialog
import com.mini.me_core.newui.designsystem.component.AppProgressBar
import com.mini.me_core.newui.designsystem.component.AppProgressSteps
import com.mini.me_core.newui.designsystem.component.AppPromptDialog
import com.mini.me_core.newui.designsystem.component.AppRingProgress
import com.mini.me_core.newui.designsystem.component.AppRollingNumber
import com.mini.me_core.newui.designsystem.component.AppScrambleText
import com.mini.me_core.newui.designsystem.component.AppScrollProgress
import com.mini.me_core.newui.designsystem.component.AppSelectField
import com.mini.me_core.newui.designsystem.component.AppSelectionDialog
import com.mini.me_core.newui.designsystem.component.AppSelectionItem
import com.mini.me_core.newui.designsystem.component.AppSelectionList
import com.mini.me_core.newui.designsystem.component.AppSelectionMode
import com.mini.me_core.newui.designsystem.component.AppShimmerBox
import com.mini.me_core.newui.designsystem.component.AppSkeletonList
import com.mini.me_core.newui.designsystem.component.AppSparkline
import com.mini.me_core.newui.designsystem.component.AppSpotlightCard
import com.mini.me_core.newui.designsystem.component.AppStatCard
import com.mini.me_core.newui.designsystem.component.AppSuccessDialog
import com.mini.me_core.newui.designsystem.component.AppSwipeAction
import com.mini.me_core.newui.designsystem.component.AppSwipeButton
import com.mini.me_core.newui.designsystem.component.AppTerminalLog
import com.mini.me_core.newui.designsystem.component.AppTimeline
import com.mini.me_core.newui.designsystem.component.AppTimelineItem
import com.mini.me_core.newui.designsystem.component.AppTimelineTone
import com.mini.me_core.newui.designsystem.component.AppToast
import com.mini.me_core.newui.designsystem.component.AppTypewriterText
import com.mini.me_core.newui.designsystem.component.AppUpdateDialog
import com.mini.me_core.newui.designsystem.component.AppConfetti
import com.mini.me_core.newui.designsystem.theme.appPalette
import com.mini.me_core.newui.designsystem.token.generated.AppColor
import com.mini.me_core.newui.designsystem.token.generated.AppRadius
import com.mini.me_core.newui.designsystem.token.generated.AppSpacing
import kotlinx.coroutines.delay
import kotlin.random.Random

/**
 * 分子组件族 · 列表菜单 / 弹窗 / 反馈 / 高动效 / 数据可视化 / 时序 / 快捷键 / 文件。
 * 弹窗与全屏浮层（Dialog / Popup 类）自包含在本 composable 内渲染，状态无需跨文件共享；
 * [swipeExpanded] / [onSwipeExpanded] 与对话流示例共享（同批只开一项）。
 */
@Composable
internal fun FeedbackGallerySection(
    swipeExpanded: Int?,
    onSwipeExpanded: (Int?) -> Unit,
) {
    // 列表菜单 / 弹窗族演示状态
    var menuExpanded by remember { mutableStateOf(false) }
    var selectValue by remember { mutableStateOf("Claude") }
    var bottomSheetOpen by remember { mutableStateOf(false) }
    var singlePick by remember { mutableStateOf(0) }
    var multiPick by remember { mutableStateOf(setOf(1, 3)) }
    var showDialog by remember { mutableStateOf(false) }
    var showAlertDialog by remember { mutableStateOf(false) }
    var showSelectionDialog by remember { mutableStateOf(false) }
    var showActionSheet by remember { mutableStateOf(false) }
    var showPromptDialog by remember { mutableStateOf(false) }
    var showUpdateDialog by remember { mutableStateOf(false) }
    var comboValue by remember { mutableStateOf("Auto") }
    var contextVisible by remember { mutableStateOf(false) }
    var contextPos by remember { mutableStateOf(Offset.Zero) }
    var paletteOpen by remember { mutableStateOf(false) }
    var paletteQuery by remember { mutableStateOf("") }
    var showFormDialog by remember { mutableStateOf(false) }
    var showPermissionDialog by remember { mutableStateOf(false) }
    var showSuccessDialog by remember { mutableStateOf(false) }
    var showMultiSelectDialog by remember { mutableStateOf(false) }
    var multiSelData by remember { mutableStateOf(setOf(0, 2)) }
    var cascadeExpanded by remember { mutableStateOf(false) }
    var navIndex by remember { mutableStateOf(0) }
    var showCountdownDialog by remember { mutableStateOf(false) }
    var showLoadingOverlay by remember { mutableStateOf(false) }
    var fabExpanded by remember { mutableStateOf(false) }
    var toastVisible by remember { mutableStateOf(false) }
    // 文件卡运行态：自动循环演示（上传推进 → 完成）
    var fileProgress by remember { mutableStateOf(0f) }
    var fileState by remember { mutableStateOf(AppFileState.Uploading) }
    LaunchedEffect(Unit) {
        while (true) {
            fileState = AppFileState.Uploading
            fileProgress = 0f
            while (fileProgress < 1f) {
                delay(360)
                fileProgress = (fileProgress + 0.10f).coerceAtMost(1f)
            }
            fileState = AppFileState.Downloaded
            delay(1800)
        }
    }
    // 可视化运行态：进度/环形/步骤/图表自动循环演示
    var progressBar by remember { mutableStateOf(0f) }
    var ringProgress by remember { mutableStateOf(0f) }
    var stepIndex by remember { mutableStateOf(0) }
    var sparkData by remember { mutableStateOf(listOf(20f, 34f, 28f, 52f, 48f, 70f, 86f, 66f, 92f)) }
    var barData by remember { mutableStateOf(listOf(40f, 72f, 58f, 90f, 66f, 84f)) }
    LaunchedEffect(Unit) {
        val rnd = Random.Default
        while (true) {
            progressBar = 0f
            ringProgress = 0f
            repeat(10) { i ->
                progressBar = (i + 1) / 10f
                ringProgress = (i + 1) / 10f
                delay(240)
            }
            stepIndex = (stepIndex + 1) % 4
            sparkData = List(9) { 20f + rnd.nextFloat() * 80f }
            barData = List(6) { 30f + rnd.nextFloat() * 70f }
            delay(1500)
        }
    }

    Section("分子组件族 · 列表菜单") {
        // 下拉菜单：锚定到按钮的 DropdownMenu
        Box {
            AppButton(
                text = "更多操作",
                variant = AppButtonVariant.Outlined,
                onClick = { menuExpanded = !menuExpanded },
            )
            AppMenu(expanded = menuExpanded, onDismiss = { menuExpanded = false }) {
                AppMenuItem(label = "重命名", leadingIcon = Icons.Rounded.Settings, onClick = { menuExpanded = false })
                AppMenuItem(label = "加入收藏", leadingIcon = Icons.Rounded.Notifications, onClick = { menuExpanded = false })
                AppMenuDivider()
                AppMenuItem(
                    label = "删除",
                    leadingIcon = Icons.Rounded.DeleteOutline,
                    tint = AppColor.StatusDanger,
                    onClick = { menuExpanded = false },
                )
            }
        }
        // 暴露式下拉选择框：常驻显示已选项
        AppSelectField(
            value = selectValue,
            options = listOf("Claude", "OpenAI", "Gemini"),
            onSelect = { selectValue = it },
            label = "默认模型",
            leadingIcon = Icons.Rounded.Settings,
        )
        // 内联选择列表：单选
        AppSelectionList(
            items = listOf(
                AppSelectionItem("Claude", "Sonnet 4"),
                AppSelectionItem("OpenAI", "GPT-5"),
                AppSelectionItem("Gemini", "2.0 Pro"),
            ),
            mode = AppSelectionMode.Single,
            selected = singlePick,
            onSelect = { singlePick = it },
        )
        // 内联选择列表：多选
        AppSelectionList(
            items = listOf(
                AppSelectionItem("聊天气泡"),
                AppSelectionItem("文件卡片"),
                AppSelectionItem("消息通知"),
                AppSelectionItem("状态指示"),
            ),
            mode = AppSelectionMode.Multiple,
            selected = multiPick,
            onSelect = {},
            onToggle = { i -> multiPick = if (i in multiPick) multiPick - i else multiPick + i },
        )
        AppButton(text = "抽屉选择列表", variant = AppButtonVariant.FilledTonal, onClick = { bottomSheetOpen = true })
        if (bottomSheetOpen) {
            AppBottomSheetList(onDismiss = { bottomSheetOpen = false }, title = "选择目标位置") {
                AppSelectionList(
                    items = listOf(
                        AppSelectionItem("工作区", "workspace"),
                        AppSelectionItem("文档", "docs"),
                        AppSelectionItem("日志", "logs"),
                    ),
                    mode = AppSelectionMode.Single,
                    selected = singlePick,
                    onSelect = { singlePick = it }
                )
            }
        }
        // 可搜索下拉选择框：输入即过滤
        AppComboBox(
            value = comboValue,
            options = listOf("Auto", "Gemini 2.0", "GPT-5", "Claude Sonnet", "Qwen Max", "DeepSeek V3"),
            onSelect = { comboValue = it },
            label = "可搜索下拉选择框",
            leadingIcon = Icons.Rounded.Search,
            onClear = { comboValue = "" },
        )
        // 上下文菜单：长按目标弹出
        Box(
            Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(AppRadius.Md))
                .background(MaterialTheme.colorScheme.surfaceVariant)
                .pointerInput(Unit) {
                    detectTapGestures(onLongPress = { pos -> contextPos = pos; contextVisible = true })
                }
                .padding(AppSpacing.Md),
        ) {
            Text(
                text = "长按此处打开上下文菜单",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        if (contextVisible) {
            AppContextMenu(
                visible = true,
                position = contextPos,
                items = listOf(
                    AppMenuAction("复制路径", Icons.Rounded.Code),
                    AppMenuAction("打开文件", Icons.Rounded.InsertDriveFile),
                    AppMenuAction("删除", Icons.Rounded.Delete, danger = true),
                ),
                onItemClick = { contextVisible = false },
                onDismiss = { contextVisible = false },
            )
        }
        Text(
            text = "命令面板 = 系统快捷键命令中枢（桌面入口）",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        AppIconButton(
            text = "命令面板 Ctrl+K",
            variant = AppButtonVariant.FilledTonal,
            // 装饰图标：旁侧已有文字/语义，跳过无障碍
            leadingIcon = { Icon(Icons.Rounded.Search, contentDescription = null) },
            onClick = { paletteOpen = true },
        )
        // 级联子菜单：文件 → 导出 → 格式 的多级飞墙
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.End,
        ) {
            Box {
                AppIconButton(
                    text = "级联子菜单",
                    variant = AppButtonVariant.FilledTonal,
                    // 装饰图标：旁侧已有文字/语义，跳过无障碍
                    leadingIcon = { Icon(Icons.Rounded.InsertDriveFile, contentDescription = null) },
                    onClick = { cascadeExpanded = !cascadeExpanded },
                )
                AppCascadingMenu(
                    expanded = cascadeExpanded,
                    onDismiss = { cascadeExpanded = false },
                    items = listOf(
                        AppCascadeNode("文件", icon = Icons.Rounded.InsertDriveFile, children = listOf(
                            AppCascadeNode("打开…", icon = Icons.Rounded.Search),
                            AppCascadeNode("导出", icon = Icons.Rounded.Archive, children = listOf(
                                AppCascadeNode("Markdown", icon = Icons.Rounded.Code),
                                AppCascadeNode("JSON", icon = Icons.Rounded.Code),
                                AppCascadeNode("PNG 插图", icon = Icons.Rounded.Palette),
                            )),
                        )),
                        AppCascadeNode("分享", icon = Icons.Rounded.Notifications),
                        AppCascadeNode("删除", icon = Icons.Rounded.Delete, danger = true),
                    ),
                    onItemClick = { cascadeExpanded = false },
                )
            }
        }
        // 导航/侧栏列表：带计数徽标与危险项
        AppNavigationMenu(
            items = listOf(
                AppNavigationItem("工作台", Icons.Rounded.Home),
                AppNavigationItem("会话", Icons.Rounded.Code, badge = 12),
                AppNavigationItem("通知", Icons.Rounded.Notifications, badge = 3),
                AppNavigationItem("退出登录", Icons.Rounded.Settings, danger = true),
            ),
            selectedIndex = navIndex,
            onSelect = { navIndex = it },
        )
    }

    Section("分子组件 · 弹窗") {
        Text("确认 / 提示 / 输入 / 选择 / 更新 · 动作面板", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        Row(horizontalArrangement = Arrangement.spacedBy(AppSpacing.Sm)) {
            AppButton(text = "确认", onClick = { showDialog = true })
            AppButton(text = "提示", variant = AppButtonVariant.FilledTonal, onClick = { showAlertDialog = true })
            AppButton(text = "输入", variant = AppButtonVariant.Text, onClick = { showPromptDialog = true })
        }
        Row(horizontalArrangement = Arrangement.spacedBy(AppSpacing.Sm)) {
            AppButton(text = "选择", variant = AppButtonVariant.Outlined, onClick = { showSelectionDialog = true })
            AppButton(text = "更新", variant = AppButtonVariant.Outlined, onClick = { showUpdateDialog = true })
            AppButton(text = "动作面板", variant = AppButtonVariant.Outlined, onClick = { showActionSheet = true })
        }
        // 次级动作：等宽两列网格，左对齐；右列起始 x 不随左列文字宽度漂移
        Row(horizontalArrangement = Arrangement.spacedBy(AppSpacing.Sm)) {
            Box(Modifier.weight(1f), contentAlignment = Alignment.CenterStart) {
                AppButton(text = "表单", variant = AppButtonVariant.Text, onClick = { showFormDialog = true })
            }
            Box(Modifier.weight(1f), contentAlignment = Alignment.CenterStart) {
                AppButton(text = "权限", variant = AppButtonVariant.Text, onClick = { showPermissionDialog = true })
            }
        }
        Row(horizontalArrangement = Arrangement.spacedBy(AppSpacing.Sm)) {
            Box(Modifier.weight(1f), contentAlignment = Alignment.CenterStart) {
                AppButton(text = "成功", variant = AppButtonVariant.Text, onClick = { showSuccessDialog = true })
            }
            Box(Modifier.weight(1f), contentAlignment = Alignment.CenterStart) {
                AppButton(text = "多选", variant = AppButtonVariant.Text, onClick = { showMultiSelectDialog = true })
            }
        }
        Row(horizontalArrangement = Arrangement.spacedBy(AppSpacing.Sm)) {
            Box(Modifier.weight(1f), contentAlignment = Alignment.CenterStart) {
                AppButton(text = "倒计时防误", variant = AppButtonVariant.Text, onClick = { showCountdownDialog = true })
            }
            Box(Modifier.weight(1f), contentAlignment = Alignment.CenterStart) {
                AppButton(text = "阻塞遮罩", variant = AppButtonVariant.Text, onClick = { showLoadingOverlay = true })
            }
        }
    }

    Section("分子组件族 · 反馈高动效") {
        AppProgressBar(progress = progressBar)
        Row(
            horizontalArrangement = Arrangement.spacedBy(AppSpacing.Lg),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            AppShimmerBox(Modifier.size(56.dp))
            Column(verticalArrangement = Arrangement.spacedBy(AppSpacing.Sm)) {
                AppShimmerBox(Modifier.height(AppSpacing.Md).fillMaxWidth(0.7f))
                AppShimmerBox(Modifier.height(AppSpacing.Md).fillMaxWidth())
                AppShimmerBox(Modifier.height(AppSpacing.Md).fillMaxWidth())
            }
        }
        Row(horizontalArrangement = Arrangement.spacedBy(AppSpacing.Xl)) {
            Box {
                AppIcon(icon = Icons.Rounded.Notifications)
                // 角标锚定图标右上角并向外偏移（半压角），不压住图标中心
                AppBadge(
                    count = 12,
                    modifier = Modifier
                        .align(Alignment.TopEnd)
                        .offset(x = 5.dp, y = (-5).dp),
                )
            }
            AppBadge(count = 999, maxShow = 99)
            AppBadgeDot()
        }
    }

    Section("分子组件族 · 数据 / 导航 / 操作") {
        Row(horizontalArrangement = Arrangement.spacedBy(AppSpacing.Md)) {
            AppAvatar(text = "MiniMe-core", online = true)
            AppAvatar(text = "AI", online = false)
        }
        // 短路径：不触发折叠
        AppBreadcrumb(items = listOf("工作区", "remote", "agents", "prompts"))
        // 长路径（>4 段）：首项 + … + 末 2 项折叠演示
        AppBreadcrumb(
            items = listOf(
                AppCrumb(label = "根目录"),
                AppCrumb(label = "projects"),
                AppCrumb(label = "mini-me"),
                AppCrumb(label = "src"),
                AppCrumb(label = "main"),
                AppCrumb(label = "kotlin"),
                AppCrumb(label = "designsystem"),
                AppCrumb(label = "BreadcrumbDemo.kt"),
            ),
        )
        Row(horizontalArrangement = Arrangement.spacedBy(AppSpacing.Md)) {
            AppStatCard(
                label = "已执行命令",
                value = 1284.0,
                icon = Icons.Rounded.Code,
                trend = "+12.5%",
                modifier = Modifier.weight(1f),
            )
            AppStatCard(
                label = "完成率",
                value = 86.0,
                icon = Icons.Rounded.Home,
                trend = "+3.2%",
                modifier = Modifier.weight(1f),
            )
        }
        Row(horizontalArrangement = Arrangement.spacedBy(AppSpacing.Md)) {
            AppFAB(
                icon = Icons.Rounded.Add,
                text = "新建",
                expanded = fabExpanded,
                onClick = { fabExpanded = !fabExpanded },
            )
            AppButton(
                text = if (toastVisible) "隐藏 Toast" else "显示 Toast",
                onClick = { toastVisible = !toastVisible },
            )
        }
        AppToast(
            message = "操作成功，已保存更改",
            visible = toastVisible,
            icon = Icons.Rounded.Notifications,
        )
    }

    Section("分子组件族 · 数据可视化") {
        Row(horizontalArrangement = Arrangement.spacedBy(AppSpacing.Xl), verticalAlignment = Alignment.CenterVertically) {
            AppRingProgress(progress = ringProgress)
            AppRingProgress(progress = ringProgress, boxSize = 64.dp, strokeWidth = 6.dp)
        }
        AppSparkline(
            data = sparkData,
            modifier = Modifier.fillMaxWidth().height(56.dp),
        )
    }

    Section("分子组件族 · 反馈层") {
        AppSkeletonList(rows = 2)
        AppInlineAlert(tone = AppAlertTone.Success, title = "配置已保存", message = "更改已同步到远端仓库。")
        AppInlineAlert(tone = AppAlertTone.Warning, message = "该命令需要容器运行时，请先启动 PRoot。")
        AppInlineAlert(tone = AppAlertTone.Danger, message = "数据目录不可写，请检查权限。", onDismiss = {})
    }

    Section("分子组件族 · 滑扫操作") {
        val swipeRows = listOf("会话 A · minime-agent", "会话 B · settings refactor", "会话 C · terminal local")
        swipeRows.forEachIndexed { index, title ->
            Column {
                AppSwipeAction(
                    index = index,
                    expandedIndex = swipeExpanded,
                    onExpanded = onSwipeExpanded,
                    actionWidth = 128.dp,
                    actions = {
                        AppSwipeButton(
                            icon = Icons.Rounded.Archive,
                            label = "归档",
                            background = AppColor.StatusInfo,
                            onClick = { /* 预留接入归档逻辑 */ },
                        )
                        AppSwipeButton(
                            icon = Icons.Rounded.Delete,
                            label = "删除",
                            background = AppColor.StatusDanger,
                            onClick = { /* 预留接入删除逻辑 */ },
                        )
                    },
                ) {
                    AppMenuRow(title = title, subtitle = "左滑露出操作 · 点击内容收起 · 同批只开一项", icon = Icons.Rounded.Code)
                }
                if (index != swipeRows.lastIndex) {
                    Spacer(Modifier.height(AppSpacing.Sm))
                }
            }
        }
    }

    Section("分子组件族 · 高动效展示") {
        var progress by remember { mutableStateOf(0f) }
        var rolling by remember { mutableStateOf(0) }
        LaunchedEffect(Unit) {
            while (true) {
                rolling = (24..180).random()
                progress = 0f
                delay(2000)
                progress = 1f
                delay(2400)
            }
        }
        // 进度条补标签：满进度时单独一条全宽蓝线语义不明，标签让其成为"执行进度"控件。
        Row(
            modifier = Modifier.fillMaxWidth().padding(horizontal = AppSpacing.Sm),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text("执行进度", style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
            Spacer(Modifier.width(AppSpacing.Sm))
            AppScrollProgress(fraction = progress, modifier = Modifier.weight(1f))
        }
        Spacer(Modifier.height(AppSpacing.Sm))

        Row(
            modifier = Modifier.fillMaxWidth().padding(AppSpacing.Sm),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            // weight(1f) 占剩余空间：窄屏上压缩文本列，避免与 AppDock 互相叠压。
            Column(Modifier.weight(1f)) {
                Text("令牌速率", style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                AppRollingNumber(value = rolling, style = MaterialTheme.typography.headlineMedium, color = appPalette().primary)
            }
            AppDock(
                selectedIndex = 0,
                items = listOf(
                    AppDockItem(Icons.Rounded.Home, "工作台"),
                    AppDockItem(Icons.Rounded.Code, "代码"),
                    AppDockItem(Icons.Rounded.Settings, "设置"),
                    AppDockItem(Icons.Rounded.Notifications, "通知"),
                ),
            )
        }
        Spacer(Modifier.height(AppSpacing.Xs))

        AppTerminalLog()
        Spacer(Modifier.height(AppSpacing.Md))

        AppMarquee(background = appPalette().surfaceDim) {
            Row(
                horizontalArrangement = Arrangement.spacedBy(AppSpacing.Lg),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text("容器已就绪", style = MaterialTheme.typography.bodySmall, color = appPalette().primary)
                Text("·", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                Text("MCP 服务器监听 0.0.0.0:9898", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                Text("·", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                Text("PRoot + Alpine 3.21 · arm64", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        }
        Spacer(Modifier.height(AppSpacing.Md))

        AppGradientBorder(modifier = Modifier.fillMaxWidth()) {
            AppMenuRow(title = "渐变描边卡", subtitle = "边缘锥形渐变缓慢流淌", icon = Icons.Rounded.Palette)
        }
        Spacer(Modifier.height(AppSpacing.Md))

        AppTypewriterText(text = "正在生成 tool_call → 执行 shell 构建…")
        Spacer(Modifier.height(AppSpacing.Md))

        AppScrambleText(text = "AGENT_RUN_0.0.0.2")
        Spacer(Modifier.height(AppSpacing.Md))

        AppSpotlightCard(modifier = Modifier.fillMaxWidth().height(112.dp)) {
            // 内容垂直居中：卡高 112dp 只有两行文案，顶对齐会留下大片空白且高光在底部孤悬。
            Column(Modifier.align(Alignment.CenterStart).fillMaxWidth()) {
                Text("聚光高亮卡", style = MaterialTheme.typography.titleMedium)
                Spacer(Modifier.height(AppSpacing.Xs))
                Text("舞台高光沿卡片缓慢游弋", style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        }
        Spacer(Modifier.height(AppSpacing.Md))

        Box(Modifier.fillMaxWidth().height(88.dp)) {
            AppConfetti(Modifier.fillMaxSize())
            Column(
                Modifier.align(Alignment.Center).fillMaxWidth(),
                horizontalAlignment = Alignment.CenterHorizontally,
            ) {
                Text("发版成功", style = MaterialTheme.typography.titleMedium, color = AppColor.StatusSuccess)
                Text("v0.0.0.2 · 庆祝彩带", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        }
    }

    Section("分子组件族 · 时序 / 流程") {
        AppTimeline(
            items = listOf(
                AppTimelineItem(title = "创建会话", subtitle = "初始化 AI Agent 上下文", time = "10:02", icon = Icons.Rounded.Add),
                AppTimelineItem(title = "执行构建", subtitle = "assembleDebug 通过", time = "10:05", tone = AppTimelineTone.Success),
                AppTimelineItem(title = "推送提交", subtitle = "feat(agent): 流式工具调用", time = "10:11", icon = Icons.Rounded.Code),
                AppTimelineItem(title = "检测远端变更", subtitle = "合入前需解决冲突", time = "10:23", tone = AppTimelineTone.Danger),
            ),
        )
        AppProgressSteps(steps = listOf("解析", "授权", "执行", "完成"), currentIndex = stepIndex)
    }

    Section("分子组件族 · 快捷键 / 通知") {
        Row(horizontalArrangement = Arrangement.spacedBy(AppSpacing.Md), verticalAlignment = Alignment.CenterVertically) {
            AppKeyCombo(keys = listOf("⌘", "K"))
            AppKeyCombo(keys = listOf("Ctrl", "⇧", "P"))
            AppKeyCap(label = "Esc")
        }
        AppNotificationItem(
            title = "容器启动完成",
            body = "PRoot Alpine 已就绪，可执行终端命令。",
            time = "刚刚",
            icon = Icons.Rounded.Notifications,
            unread = true,
        )
        AppNotificationItem(
            title = "会员权限已更新",
            time = "5 分钟前",
            icon = Icons.Rounded.Settings,
        )
    }

    Section("分子组件族 · 数据可视化 / 文件") {
        Row(horizontalArrangement = Arrangement.spacedBy(AppSpacing.Md)) {
            AppMiniBarChart(
                values = barData,
                highlightIndex = 3,
                modifier = Modifier.weight(1f).height(72.dp),
            )
            AppMiniBarChart(
                values = barData,
                barColor = AppColor.StatusSuccess,
                modifier = Modifier.weight(1f).height(72.dp),
            )
        }
        AppFileCard(fileName = "README.md", fileSize = "4.1 KB", state = AppFileState.Ready)
        AppFileCard(
            fileName = "tokens.json",
            fileSize = "—",
            state = fileState,
            progress = fileProgress,
            icon = Icons.Rounded.Code,
        )
        AppFileCard(fileName = "secrets.local", fileSize = "12 B", state = AppFileState.Error)
    }

    // ===== 弹窗 / 浮层叠加层（Dialog / Popup 类，渲染在滚动内容之外） =====
    if (showDialog) {
        AppConfirmDialog(
            visible = true,
            title = "删除会话？",
            message = "该操作不可撤销，会话及其历史将永久删除。",
            onDismiss = { showDialog = false },
            confirmText = "删除",
            onConfirm = { showDialog = false },
            tone = AppDialogTone.Danger,
        )
    }
    if (showAlertDialog) {
        AppAlertDialog(
            visible = true,
            title = "容器未启动",
            message = "执行 Shell 前请先在终端启动 PRoot 容器。",
            onDismiss = { showAlertDialog = false },
            tone = AppDialogTone.Warning,
        )
    }
    if (showPromptDialog) {
        AppPromptDialog(
            visible = true,
            title = "新建会话",
            onDismiss = { showPromptDialog = false },
            onConfirm = { showPromptDialog = false },
            placeholder = "给会话起个名字…",
            confirmText = "创建",
        )
    }
    if (showSelectionDialog) {
        AppSelectionDialog(
            visible = true,
            title = "移动到分组",
            options = listOf("会话", "收藏", "归档"),
            selectedIndex = singlePick,
            onSelect = { singlePick = it },
            onDismiss = { showSelectionDialog = false },
        )
    }
    if (showUpdateDialog) {
        AppUpdateDialog(
            visible = true,
            title = "发现新版本",
            version = "0.0.0.3",
            notes = listOf("重构滑扫操作系统", "新增列表菜单与弹窗组件族", "修复若干崩溃"),
            onDismiss = { showUpdateDialog = false },
            onUpdate = { showUpdateDialog = false },
            tone = AppDialogTone.Info,
        )
    }
    AppActionSheet(
        visible = showActionSheet,
        title = "对“minime-agent”执行",
        items = listOf(
            AppActionSheetItem(Icons.Rounded.Delete, "删除", danger = true),
            AppActionSheetItem(Icons.Rounded.Archive, "归档"),
        ),
        onItemClick = { showActionSheet = false },
        onDismiss = { showActionSheet = false },
    )
    // 补充弹窗类型渲染
    if (showFormDialog) {
        AppFormDialog(
            visible = true,
            title = "新建反馈",
            onDismiss = { showFormDialog = false },
            onConfirm = { _, _ -> showFormDialog = false },
            subjectLabel = "主题",
            bodyLabel = "详情",
            confirmText = "提交",
            tone = AppDialogTone.Info,
        )
    }
    if (showPermissionDialog) {
        AppPermissionDialog(
            visible = true,
            title = "开启通知权限？",
            message = "开启后我们会在构建完成、会话超时等关键节点提醒你，不会推送无关信息。",
            onDismiss = { showPermissionDialog = false },
            onAllow = { showPermissionDialog = false },
            permissionName = "通知权限",
            allowText = "允许",
            deniedText = "暂不",
        )
    }
    if (showSuccessDialog) {
        AppSuccessDialog(
            visible = true,
            title = "导出成功",
            message = "设计令牌已导出为打包产物。",
            detail = listOf("tokens.json", "AppTokens.kt", "style.css"),
            onDismiss = { showSuccessDialog = false },
            confirmText = "完成",
        )
    }
    if (showMultiSelectDialog) {
        AppMultiSelectDialog(
            visible = true,
            title = "选择批量导出字段",
            options = listOf("会话", "消费", "工具", "文件", "凭据"),
            selected = multiSelData,
            onToggle = { i -> multiSelData = if (i in multiSelData) multiSelData - i else multiSelData + i },
            onDismiss = { showMultiSelectDialog = false },
            onConfirm = { showMultiSelectDialog = false },
        )
    }
    // 命令面板：全屏浮层
    if (paletteOpen) {
        AppCommandPalette(
            visible = true,
            groups = listOf(
                AppCommandGroup("文件", listOf(
                    AppMenuAction("打开文件", Icons.Rounded.InsertDriveFile),
                    AppMenuAction("复制路径", Icons.Rounded.Code),
                    AppMenuAction("新建会话", Icons.Rounded.Add),
                )),
                AppCommandGroup("操作", listOf(
                    AppMenuAction("归档", Icons.Rounded.Archive),
                    AppMenuAction("删除", Icons.Rounded.Delete, danger = true),
                )),
            ),
            query = paletteQuery,
            onQueryChange = { paletteQuery = it },
            onSelect = { paletteOpen = false },
            onDismiss = { paletteOpen = false },
        )
    }
    // 阻塞加载遮罩：演示为 3.2 秒后自动收起
    LaunchedEffect(showLoadingOverlay) {
        if (showLoadingOverlay) {
            delay(3200)
            showLoadingOverlay = false
        }
    }
    // 阻塞遮罩必须包在全屏 Dialog 里，否则在滚动 Column 中只占当前视口、且不拦截穿透点击
    if (showLoadingOverlay) {
        Dialog(
            onDismissRequest = { /* 阻塞遮罩不可点外/返回关闭，3.2s 后自动收起 */ },
            properties = DialogProperties(
                dismissOnBackPress = false,
                dismissOnClickOutside = false,
                usePlatformDefaultWidth = false,
            ),
        ) {
            AppLoadingOverlay(
                visible = true,
                message = "正在同步工作区…",
            )
        }
    }
    // 倒计时防误弹窗：删除操作前强制读秒
    if (showCountdownDialog) {
        AppCountdownDialog(
            visible = true,
            title = "清空回收站",
            message = "回收站内的 12 个会话将被彻底清除，该操作无法撤销。",
            onDismiss = { showCountdownDialog = false },
            onConfirm = { showCountdownDialog = false },
            seconds = 3,
            confirmText = "清空",
            cancelText = "取消",
            tone = AppDialogTone.Danger,
        )
    }
}
