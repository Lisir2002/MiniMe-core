package com.mini.me_core.feature.terminal.presentation.component

import androidx.compose.foundation.layout.size
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.AccountTree
import androidx.compose.material.icons.rounded.Code
import androidx.compose.material.icons.rounded.Memory
import androidx.compose.material.icons.rounded.Public
import androidx.compose.material.icons.rounded.Search
import androidx.compose.material.icons.rounded.Terminal
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import com.mini.me_core.feature.terminal.data.bundle.TerminalBundle
import com.mini.me_core.feature.terminal.data.bundle.TerminalBundleId

/**
 * BundleInstallCard / BundleLogDialog 共用的轻量 UI 适配模型（TerminalBundle 的 UI 子集 + 解析 icon）。
 * Compose 函数入参用它而非 TerminalBundle，避免把 TerminalBundle.id 等业务字段带进 UI 层。
 */
data class UiBundle(
    val id: TerminalBundleId,
    val title: String,
    val description: String,
    val sizeEstimateMb: Int,
    val icon: ImageVector,
)

@Composable
fun TerminalBundle.toUi(): UiBundle = UiBundle(
    id = id,
    title = displayName,
    description = description,
    sizeEstimateMb = sizeEstimateMb,
    icon = bundleIconVector(id),
)

/**
 * F6：icon 映射唯一真源（非 @Composable）。
 * 对应 TerminalBundle.iconName 字符串 / 旧 SharedBundleCard.sharedBundleIcon 映射逻辑。
 * @Composable 薄包装见下。
 */
internal fun bundleIconVector(id: TerminalBundleId): ImageVector = when (id) {
    TerminalBundleId.PYTHON -> Icons.Rounded.Memory
    TerminalBundleId.NODE -> Icons.Rounded.Code
    TerminalBundleId.RIPGREP -> Icons.Rounded.Search
    TerminalBundleId.GIT -> Icons.Rounded.AccountTree
    TerminalBundleId.BASH -> Icons.Rounded.Terminal
    TerminalBundleId.NET -> Icons.Rounded.Public
    TerminalBundleId.QEMU_X86_TRANSLATOR -> Icons.Rounded.Memory
}

/** @Composable 薄包装，避免业务代码直接依赖无参 iconVector。 */
@Composable
private fun TerminalBundle.iconVector(): ImageVector = bundleIconVector(id)
