package org.minime.core.ui.gallery.pages

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshots.SnapshotStateMap
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import org.minime.core.ui.LocalThemeSnapshot
import org.minime.core.ui.MiniMeTokens
import org.minime.core.ui.draw.ContinuousCornerShape
import org.minime.core.ui.m3.toFontFamily
import org.minime.core.ui.m3.toTextStyle
import org.minime.core.ui.runtime.DiffPreviewResult
import org.minime.core.ui.runtime.FingerprintState
import org.minime.core.ui.runtime.ImportResult
import org.minime.core.ui.runtime.ThemeManager
import org.minime.core.ui.runtime.ThemeStatus
import org.minime.core.ui.gallery.accentColor
import org.minime.core.ui.gallery.fillPrimaryColor
import org.minime.core.ui.gallery.surfaceColor
import org.minime.core.ui.gallery.stateErrorColor
import org.minime.core.ui.gallery.stateSuccessColor
import org.minime.core.ui.gallery.stateWarningColor
import org.minime.core.ui.gallery.textPrimaryColor
import org.minime.core.ui.gallery.textSecondaryColor
import org.minime.core.ui.gallery.textTertiaryColor
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/**
 * 分区五：主题工坊（白皮书 §10.7 / charter §4）——R4 替换 WORKSHOP 占位页。
 *
 * 三区布局：**顶栏**（标题 + 覆写计数 + diff 导入导出 + 重置）+ **分类 tab**（色板 / 字阶 /
 * 圆角 / 间距 / 动效 / 玻璃）+ **工作区**（宽屏左实时预览右令牌控件；窄屏上下堆叠）。
 *
 * 数据面（全部外置，页面即单元——API 五原则）：
 * - 当前生效值：[LocalThemeSnapshot]（MiniMeTheme 随 manager.snapshot 重组）；
 * - 覆写表 / 原值面貌 / 状态：`manager.overlays` / `manager.baseSnapshot` / `manager.status`；
 * - 编辑动作：`manager.applyOverrides` / `manager.resetOverrides`（R4.1 运行时，后台编译
 *   + last-wins 节流 + 失败回滚，本页不含任何编译/合并逻辑）；
 * - diff 传输（R4.3）：导出 = `manager.exportDiff()` 文本经 [onSaveDiff] 交宿主落盘（SAF）；
 *   导入 = 宿主 SAF 读出的文本经 [pendingImportJson] 回填 → 本页 `manager.previewDiff`
 *   （零改态）→ 确认对话框 → `manager.importDiff`（复用 R4.1 覆写管线）。
 *
 * 自举纪律（R4-c）：全部控件自绘 + 令牌消费（滑杆 / 开关 / 步进 / hex 输入 / 分段 /
 * 确认对话框），正式组件族随 R6 组件目录回收。
 */
@Composable
fun WorkshopSection(
    manager: ThemeManager,
    onSaveDiff: (json: String, suggestedName: String) -> Unit = { _, _ -> },
    onPickDiff: () -> Unit = {},
    pendingImportJson: String? = null,
    pendingImportError: String? = null,
    onPendingImportConsumed: () -> Unit = {},
    modifier: Modifier = Modifier,
) {
    val snapshot = LocalThemeSnapshot.current
    val overlays by manager.overlays.collectAsState()
    val status by manager.status.collectAsState()
    var category by remember { mutableStateOf(WorkshopCategory.PALETTE) }
    var selectedPath by remember { mutableStateOf<String?>(null) }

    // fallback 态（无主题包）：覆写无处施加——编辑器与 diff 传输禁用并明示
    val overridable = manager.currentPackId != ThemeManager.FALLBACK_ID
    // 分组折叠态（页面级记忆，跨分类保持）
    val groupExpanded = remember { mutableStateMapOf<String, Boolean>() }
    // 导出文件名时间戳（yyyyMMdd-HHmmss；remember 保格式化器复用）
    val stamp = remember { SimpleDateFormat("yyyyMMdd-HHmmss", Locale.US) }

    // ---------- diff 导入确认对话框（R4.3） ----------
    // 预览纯解析零改态：文件文本或宿主读取错误回填即呈现；applyRejection = 应用被拒就地呈现
    val previewOfFile: DiffPreviewResult? = remember(pendingImportJson) {
        pendingImportJson?.let { manager.previewDiff(it) }
    }
    var applyRejection by remember(pendingImportJson) { mutableStateOf<ImportResult.Rejected?>(null) }
    val dialogPreview: DiffPreviewResult? = when (val rejection = applyRejection) {
        null -> previewOfFile ?: pendingImportError?.let {
            DiffPreviewResult.Failed("IMPORT_READ_FAILED", it)
        }
        else -> DiffPreviewResult.Failed(rejection.code, rejection.message)
    }

    Box(modifier = modifier.fillMaxSize()) {
        Column(modifier = Modifier.fillMaxSize()) {
            WorkshopTopBar(
                overlayCount = overlays.size,
                diffTransferEnabled = overridable,
                onExportDiff = {
                    // 导出即当前 overlays 快照（count>0 由按钮禁用态保证；fallback 由 diffTransferEnabled 排除）
                    val json = manager.exportDiff()
                    onSaveDiff(json, "minime-diff-${stamp.format(Date())}.tokens.json")
                },
                onPickDiff = onPickDiff,
                onResetCategory = { manager.resetOverrides(WorkshopSpec.pathsOf(category)) },
                onResetAll = { manager.resetOverrides() },
            )
            WorkshopCategoryTabs(
                current = category,
                onSelect = {
                    category = it
                    selectedPath = null // 换类即清选择（选中路径可能不属新类）
                },
            )
            (status as? ThemeStatus.Failed)?.let { failed ->
                WorkshopErrorBanner(code = failed.code.name, message = failed.message)
            }

            BoxWithConstraints(modifier = Modifier.weight(1f).fillMaxWidth()) {
                // 640dp = Material medium 断点：工坊工作区自有响应式（宿主 840dp 管侧栏）
                val wide = maxWidth >= 640.dp
                if (wide) {
                    Row(modifier = Modifier.fillMaxSize()) {
                        WorkshopPreview(modifier = Modifier.weight(1f).fillMaxSize())
                        WorkshopConsole(
                            manager = manager,
                            category = category,
                            selectedPath = selectedPath,
                            overridable = overridable,
                            groupExpanded = groupExpanded,
                            onSelect = { selectedPath = it },
                            modifier = Modifier.weight(1f).fillMaxSize(),
                        )
                    }
                } else {
                    Column(modifier = Modifier.fillMaxSize()) {
                        WorkshopPreview(modifier = Modifier.weight(0.42f).fillMaxWidth())
                        WorkshopConsole(
                            manager = manager,
                            category = category,
                            selectedPath = selectedPath,
                            overridable = overridable,
                            groupExpanded = groupExpanded,
                            onSelect = { selectedPath = it },
                            modifier = Modifier.weight(0.58f).fillMaxWidth(),
                        )
                    }
                }
            }
        }

        // 导入确认对话框（浮于工坊之上；scrim 点击 = 取消）
        dialogPreview?.let { preview ->
            WorkshopDiffImportDialog(
                preview = preview,
                currentPackId = manager.currentPackId,
                onApply = {
                    val json = pendingImportJson
                    if (json != null) {
                        when (val result = manager.importDiff(json)) {
                            // Applied = 已进入覆写编译（后台 + 回滚）；编译结果经 status banner 暴露
                            is ImportResult.Applied -> onPendingImportConsumed()
                            // 防御：预览已解析成功而导入被拒（同文本双跑几乎不可能）——就地呈现
                            is ImportResult.Rejected -> applyRejection = result
                        }
                    }
                },
                onDismiss = {
                    applyRejection = null
                    onPendingImportConsumed()
                },
            )
        }
    }
}

// ---------- 顶栏（操作栏） ----------

@Composable
private fun WorkshopTopBar(
    overlayCount: Int,
    diffTransferEnabled: Boolean,
    onExportDiff: () -> Unit,
    onPickDiff: () -> Unit,
    onResetCategory: () -> Unit,
    onResetAll: () -> Unit,
) {
    val color = MiniMeTokens.LocalColor.current
    val typography = MiniMeTokens.LocalTypography.current
    val spacing = MiniMeTokens.LocalSpacing.current

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = spacing.gutter.value.dp, vertical = spacing.s8.value.dp),
    ) {
        // 第一行：标题 + 覆写计数 + diff 导入导出（设计 §8.1）
        Row(verticalAlignment = Alignment.CenterVertically) {
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = "主题工坊",
                    style = typography.title3.toTextStyle(),
                    color = color.textPrimaryColor,
                )
                Text(
                    text = if (overlayCount == 0) "无覆写 · 当前为包原初面貌" else "$overlayCount 条覆写生效中（内存态，导出才落盘）",
                    style = typography.caption2.toTextStyle(),
                    color = if (overlayCount == 0) color.textSecondaryColor else color.accentColor,
                )
            }
            WorkshopTextButton(
                text = "导出 diff",
                // 无覆写无可导出（count=0 的导出无意义）；fallback 态无基础包
                enabled = diffTransferEnabled && overlayCount > 0,
                onClick = onExportDiff,
            )
            Spacer(Modifier.width(spacing.s8.value.dp))
            WorkshopTextButton(
                text = "导入 diff",
                enabled = diffTransferEnabled,
                onClick = onPickDiff,
            )
        }
        Spacer(Modifier.height(spacing.s8.value.dp))
        // 第二行：重置操作（右对齐）
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.End,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            WorkshopTextButton(
                text = "重置本类",
                enabled = overlayCount > 0,
                onClick = onResetCategory,
            )
            Spacer(Modifier.width(spacing.s8.value.dp))
            WorkshopTextButton(
                text = "全部还原",
                enabled = overlayCount > 0,
                onClick = onResetAll,
            )
        }
    }
}

/** 自绘文字按钮（accent 文字；禁用转 textTertiary——组件族随 R6 回收） */
@Composable
private fun WorkshopTextButton(
    text: String,
    enabled: Boolean,
    onClick: () -> Unit,
    filled: Boolean = false,
) {
    val color = MiniMeTokens.LocalColor.current
    val typography = MiniMeTokens.LocalTypography.current
    val spacing = MiniMeTokens.LocalSpacing.current
    val shape = MiniMeTokens.LocalShape.current

    Box(
        modifier = Modifier
            .clip(ContinuousCornerShape(shape.radiusButton.value.dp))
            .background(
                when {
                    !enabled -> Color.Transparent
                    filled -> color.accentColor
                    else -> color.fillPrimaryColor
                },
            )
            .clickable(enabled = enabled, onClick = onClick)
            .padding(horizontal = spacing.s12.value.dp, vertical = spacing.s8.value.dp),
    ) {
        Text(
            text = text,
            style = typography.callout.toTextStyle(),
            color = when {
                !enabled -> color.textSecondaryColor
                filled -> color.surfaceColor
                else -> color.accentColor
            },
        )
    }
}

// ---------- 分类 tab ----------

@Composable
private fun WorkshopCategoryTabs(current: WorkshopCategory, onSelect: (WorkshopCategory) -> Unit) {
    val color = MiniMeTokens.LocalColor.current
    val typography = MiniMeTokens.LocalTypography.current
    val spacing = MiniMeTokens.LocalSpacing.current

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = spacing.gutter.value.dp),
        horizontalArrangement = Arrangement.spacedBy(spacing.s4.value.dp),
    ) {
        WorkshopCategory.entries.forEach { category ->
            val selected = category == current
            Box(
                modifier = Modifier
                    .weight(1f)
                    .clip(RoundedCornerShape(percent = 50))
                    .background(if (selected) color.accentColor else color.fillPrimaryColor)
                    .clickable { onSelect(category) }
                    .padding(vertical = spacing.s8.value.dp),
                contentAlignment = Alignment.Center,
            ) {
                Text(
                    text = category.title,
                    style = typography.caption1.toTextStyle(),
                    color = if (selected) color.surfaceColor else color.textPrimaryColor,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }
        }
    }
    Spacer(Modifier.height(spacing.s8.value.dp))
}

// ---------- 错误条（§5.2 错误码面） ----------

@Composable
private fun WorkshopErrorBanner(code: String, message: String, title: String = "覆写被拒绝") {
    val color = MiniMeTokens.LocalColor.current
    val typography = MiniMeTokens.LocalTypography.current
    val spacing = MiniMeTokens.LocalSpacing.current
    val shape = MiniMeTokens.LocalShape.current
    val error = color.stateErrorColor

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = spacing.gutter.value.dp, vertical = spacing.s4.value.dp)
            .clip(ContinuousCornerShape(shape.radiusButton.value.dp))
            // banner 惯例透明度（非设计令牌；iOS 横幅风）
            .background(error.copy(alpha = 0.12f))
            .padding(horizontal = spacing.s12.value.dp, vertical = spacing.s8.value.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            text = "$title · $code",
            style = typography.callout.toTextStyle(),
            color = error,
        )
        Spacer(Modifier.width(spacing.s8.value.dp))
        Text(
            text = message,
            style = typography.caption2.toTextStyle(),
            color = color.textSecondaryColor,
            maxLines = 2,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.weight(1f),
        )
    }
}

// ---------- diff 导入确认对话框（R4.3，设计 §8.3；R6 弹窗族就绪前自绘） ----------

@Composable
private fun WorkshopDiffImportDialog(
    preview: DiffPreviewResult,
    currentPackId: String,
    onApply: () -> Unit,
    onDismiss: () -> Unit,
) {
    val color = MiniMeTokens.LocalColor.current
    val typography = MiniMeTokens.LocalTypography.current
    val spacing = MiniMeTokens.LocalSpacing.current
    val shape = MiniMeTokens.LocalShape.current
    val opacity = MiniMeTokens.LocalOpacity.current
    val mono = typography.monospace.toTextStyle().copy(fontFamily = "monospace".toFontFamily())

    Box(modifier = Modifier.fillMaxSize()) {
        // scrim：黑 × opacity.scrim 令牌（同画廊抽屉 idiom），点击消散
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(Color.Black.copy(alpha = opacity.scrim.value))
                .clickable(onClick = onDismiss),
        )
        Column(
            modifier = Modifier
                .align(Alignment.Center)
                .fillMaxWidth(0.92f)
                .widthIn(max = 420.dp)
                .clip(ContinuousCornerShape(shape.radiusDialog.value.dp))
                .background(color.surfaceColor)
                // 吞掉卡片区点击（空操作）：防空白区冒泡到 scrim 误关对话框
                .clickable(onClick = {})
                .padding(horizontal = spacing.s20.value.dp, vertical = spacing.s16.value.dp),
        ) {
            Text(
                text = "导入 diff 主题包",
                style = typography.title3.toTextStyle(),
                color = color.textPrimaryColor,
            )
            Spacer(Modifier.height(spacing.s12.value.dp))

            when (preview) {
                is DiffPreviewResult.Failed -> {
                    WorkshopErrorBanner(
                        code = preview.code,
                        message = preview.message,
                        title = "导入被拒绝",
                    )
                }

                is DiffPreviewResult.Ok -> {
                    // 摘要行
                    Text(
                        text = buildString {
                            append("共 ${preview.total} 条覆写 · 基于 ")
                            append(preview.basePackId ?: "纯 DTCG 文件（无基础包信息）")
                            preview.basePackVersion?.let { append(" $it") }
                        },
                        style = typography.callout.toTextStyle(),
                        color = color.textPrimaryColor,
                    )
                    Spacer(Modifier.height(spacing.s8.value.dp))

                    // 分类 chips（工坊六分类口径，仅非零；超窄屏横向滚动）
                    val counts = categoryCounts(preview.overlays)
                    if (counts.isNotEmpty()) {
                        Row(
                            modifier = Modifier.horizontalScroll(rememberScrollState()),
                            horizontalArrangement = Arrangement.spacedBy(spacing.s4.value.dp),
                        ) {
                            counts.forEach { (category, count) ->
                                WorkshopDiffChip(text = "${category.title} $count")
                            }
                        }
                        Spacer(Modifier.height(spacing.s8.value.dp))
                    }

                    // 指纹 banner（三态；MATCH/MISMATCH 着色，UNKNOWN 普通文本）
                    WorkshopDiffFingerprintBanner(preview = preview, currentPackId = currentPackId)
                    Spacer(Modifier.height(spacing.s8.value.dp))

                    // 必挂路径（词汇表外 / 类型不符）：应用即编译失败，禁「应用」
                    if (preview.vocabularyMisses.isNotEmpty() || preview.typeMisses.isNotEmpty()) {
                        WorkshopDiffBlockedBanner(
                            vocabularyMisses = preview.vocabularyMisses,
                            typeMisses = preview.typeMisses,
                        )
                        Spacer(Modifier.height(spacing.s8.value.dp))
                    }

                    // 样本列表（前 8 条，mono）
                    Text(
                        text = "样本（前 ${preview.sample.size} 条）",
                        style = typography.caption2.toTextStyle(),
                        color = color.textSecondaryColor,
                    )
                    preview.sample.forEach { line ->
                        Text(
                            text = line,
                            style = mono,
                            color = color.textPrimaryColor,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                        )
                    }
                }
            }

            Spacer(Modifier.height(spacing.s16.value.dp))

            // 操作行：取消（文字钮）+ 应用（accent 填充钮；坏包/必挂路径时无应用钮）
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.End,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                WorkshopTextButton(text = "取消", enabled = true, onClick = onDismiss)
                val blocked = preview is DiffPreviewResult.Ok &&
                    (preview.vocabularyMisses.isNotEmpty() || preview.typeMisses.isNotEmpty())
                if (preview is DiffPreviewResult.Ok) {
                    Spacer(Modifier.width(spacing.s8.value.dp))
                    WorkshopTextButton(
                        text = "应用",
                        filled = true,
                        enabled = !blocked,
                        onClick = onApply,
                    )
                }
            }
        }
    }
}

/** 分类 chips 单体（药丸形 + fillPrimary 底） */
@Composable
private fun WorkshopDiffChip(text: String) {
    val color = MiniMeTokens.LocalColor.current
    val typography = MiniMeTokens.LocalTypography.current
    val spacing = MiniMeTokens.LocalSpacing.current

    Box(
        modifier = Modifier
            .clip(RoundedCornerShape(percent = 50))
            .background(color.fillPrimaryColor)
            .padding(horizontal = spacing.s8.value.dp, vertical = spacing.s4.value.dp),
    ) {
        Text(
            text = text,
            style = typography.caption2.toTextStyle(),
            color = color.textPrimaryColor,
        )
    }
}

/** 指纹 banner（设计 §8.3）：MATCH=success 12% / MISMATCH=warning 12% / UNKNOWN=普通文本 */
@Composable
private fun WorkshopDiffFingerprintBanner(preview: DiffPreviewResult.Ok, currentPackId: String) {
    val color = MiniMeTokens.LocalColor.current
    val typography = MiniMeTokens.LocalTypography.current
    val spacing = MiniMeTokens.LocalSpacing.current
    val shape = MiniMeTokens.LocalShape.current

    val (bannerColor, message) = when (preview.fingerprint) {
        FingerprintState.MATCH -> color.stateSuccessColor to "基础包匹配（$currentPackId，同包 diff）"
        FingerprintState.MISMATCH -> color.stateWarningColor to
            "基础包不同：${preview.basePackId ?: "?"} vs 当前 $currentPackId——不存在的路径将报错"
        FingerprintState.UNKNOWN -> color.textSecondaryColor to
            "未声明基础包指纹（纯 DTCG 文件）——不存在的路径将报错"
    }
    val tinted = preview.fingerprint != FingerprintState.UNKNOWN

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(ContinuousCornerShape(shape.radiusButton.value.dp))
            .background(if (tinted) bannerColor.copy(alpha = 0.12f) else Color.Transparent)
            .padding(horizontal = spacing.s12.value.dp, vertical = spacing.s8.value.dp),
    ) {
        Text(
            text = message,
            style = typography.caption2.toTextStyle(),
            color = bannerColor,
            maxLines = 2,
            overflow = TextOverflow.Ellipsis,
        )
    }
}

/** 必挂路径 banner（stateError；明细取前 4 条） */
@Composable
private fun WorkshopDiffBlockedBanner(vocabularyMisses: List<String>, typeMisses: List<String>) {
    val color = MiniMeTokens.LocalColor.current
    val typography = MiniMeTokens.LocalTypography.current
    val spacing = MiniMeTokens.LocalSpacing.current
    val shape = MiniMeTokens.LocalShape.current
    val error = color.stateErrorColor
    val mono = typography.monospace.toTextStyle().copy(fontFamily = "monospace".toFontFamily())
    val detail = (vocabularyMisses + typeMisses)

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .clip(ContinuousCornerShape(shape.radiusButton.value.dp))
            .background(error.copy(alpha = 0.12f))
            .padding(horizontal = spacing.s12.value.dp, vertical = spacing.s8.value.dp),
    ) {
        Text(
            text = buildString {
                append("无法应用：")
                if (vocabularyMisses.isNotEmpty()) append("${vocabularyMisses.size} 条路径不在标准词汇表")
                if (typeMisses.isNotEmpty()) {
                    append(if (vocabularyMisses.isNotEmpty()) "；" else "")
                    append("${typeMisses.size} 条值类型不符")
                }
            },
            style = typography.callout.toTextStyle(),
            color = error,
        )
        detail.take(BLOCKED_DETAIL_LIMIT).forEach { path ->
            Text(text = "· $path", style = mono, color = color.textSecondaryColor, maxLines = 1)
        }
        if (detail.size > BLOCKED_DETAIL_LIMIT) {
            Text(text = "· ……", style = mono, color = color.textSecondaryColor, maxLines = 1)
        }
    }
}

/** 必挂路径明细上限（对话框空间有限） */
private const val BLOCKED_DETAIL_LIMIT = 4

// ---------- 右控制台（编辑器 + 令牌树） ----------

@Composable
private fun WorkshopConsole(
    manager: ThemeManager,
    category: WorkshopCategory,
    selectedPath: String?,
    overridable: Boolean,
    groupExpanded: SnapshotStateMap<String, Boolean>,
    onSelect: (String) -> Unit,
    modifier: Modifier = Modifier,
) {
    val overlays by manager.overlays.collectAsState()
    val spacing = MiniMeTokens.LocalSpacing.current

    Column(modifier = modifier) {
        Box(
            modifier = Modifier
                .weight(0.6f)
                .fillMaxWidth()
                .verticalScroll(rememberScrollState())
                .padding(horizontal = spacing.s12.value.dp, vertical = spacing.s4.value.dp),
        ) {
            if (selectedPath != null) {
                WorkshopEditorCard(
                    path = selectedPath,
                    manager = manager,
                    overridable = overridable,
                )
            } else {
                WorkshopEditorPlaceholder(overridable = overridable)
            }
        }
        WorkshopTree(
            category = category,
            selectedPath = selectedPath,
            overlays = overlays,
            groupExpanded = groupExpanded,
            onSelect = onSelect,
            modifier = Modifier.weight(0.4f).fillMaxWidth(),
        )
    }
}

/** 未选中态提示（或 fallback 态禁用说明） */
@Composable
private fun WorkshopEditorPlaceholder(overridable: Boolean) {
    val color = MiniMeTokens.LocalColor.current
    val typography = MiniMeTokens.LocalTypography.current
    val spacing = MiniMeTokens.LocalSpacing.current
    val shape = MiniMeTokens.LocalShape.current

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .clip(ContinuousCornerShape(shape.radiusCard.value.dp))
            .background(color.surfaceColor)
            .padding(horizontal = spacing.s16.value.dp, vertical = spacing.s24.value.dp),
    ) {
        Text(
            text = if (overridable) "从下方令牌树选择一条令牌" else "当前为 fallback 基座（无主题包），覆写不可用",
            style = typography.callout.toTextStyle(),
            color = color.textSecondaryColor,
        )
        if (overridable) {
            Spacer(Modifier.height(spacing.s4.value.dp))
            Text(
                text = "编辑即时生效于整个画廊（全局实时）；内存态，导出 diff 主题包才落盘（R4.3）。",
                style = typography.caption2.toTextStyle(),
                color = color.textSecondaryColor,
            )
        }
    }
}

/**
 * 分类计数（工坊六分类口径；仅返回非零项——对话框 chips 用）。
 *
 * 口径真源 = [WorkshopSpec.pathsOf]（与工坊编辑范围同一清单，机器可测）；
 * 六分类之外的路径（size/interaction/elevation/opacity）不进 chips，
 * 由摘要行「共 N 条」与样本列表覆盖。
 */
private fun categoryCounts(overlays: Map<String, org.minime.core.schema.TokenValue>): List<Pair<WorkshopCategory, Int>> =
    WorkshopCategory.entries.mapNotNull { category ->
        val inCategory = WorkshopSpec.pathsOf(category).toSet()
        val count = overlays.keys.count { it in inCategory }
        if (count > 0) category to count else null
    }
