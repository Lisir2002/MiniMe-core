package com.mini.me_core.feature.settings.presentation.component

import android.content.ContentValues
import android.os.Build
import android.provider.MediaStore
import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.AutoAwesome
import androidx.compose.material.icons.rounded.BarChart
import androidx.compose.material.icons.rounded.Book
import androidx.compose.material.icons.rounded.FactCheck
import androidx.compose.material.icons.rounded.Input
import androidx.compose.material.icons.rounded.Memory
import androidx.compose.material.icons.rounded.Shield
import androidx.compose.material.icons.rounded.Timer
import androidx.compose.material3.Button
import androidx.compose.material3.OutlinedButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.mini.me_core.R
import com.mini.me_core.core.theme.AppSectionGroup
import com.mini.me_core.core.theme.AppSectionHeader
import com.mini.me_core.core.theme.Spacing
import kotlinx.coroutines.launch

/**
 * 「规范流程」二级页（D1-7，对齐 norm-chain-design.md §3.5）：
 * 总开关 [normFlowEnabled]（关闭即 step 前注入 / guard 链整体停用）
 * + 子开关 [stepInjectEnabled]（step 前注入纪律）/ [toolGuardEnabled]（guard 链 + 文件观察）/
 * [reasoningBudgetEnabled]（推理预算，D2-2）/ [usageCardEnabled]（用量卡片，D2-4）/
 * [sopSummaryEnabled]（SOP 清单摘要，D4-3）/ [playbookAutoEnabled]（Playbook 自动触发，D5-pa）/
 * [idleConvergeEnabled]（空转软收敛，D2-1，默认关）。
 * 除空转收敛外默认全开。
 */
@Composable
internal fun NormFlowSection(
    normFlowEnabled: Boolean,
    stepInjectEnabled: Boolean,
    toolGuardEnabled: Boolean,
    reasoningBudgetEnabled: Boolean,
    usageCardEnabled: Boolean,
    sopSummaryEnabled: Boolean,
    playbookAutoEnabled: Boolean,
    idleConvergeEnabled: Boolean,
    stepInjectBudget: Int = 800,
    onSetStepInjectBudget: (Int) -> Unit = {},
    onToggleNormFlow: (Boolean) -> Unit,
    onToggleStepInject: (Boolean) -> Unit,
    onToggleToolGuard: (Boolean) -> Unit,
    onToggleReasoningBudget: (Boolean) -> Unit,
    onToggleUsageCard: (Boolean) -> Unit,
    onToggleSopSummary: (Boolean) -> Unit,
    onTogglePlaybookAuto: (Boolean) -> Unit,
    onToggleIdleConverge: (Boolean) -> Unit,
    /** E3：导出配置为 JSON（由 ViewModel 读 KVStore 序列化），返回 null 表示失败。 */
    onExportJson: suspend () -> String? = { null },
    /** E3：导入 JSON 文本，批量写回 KVStore。 */
    onImportJson: (String) -> Unit = {}
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    // E3 导入：打开系统文件选择器，读取选中 JSON 文本后交给 ViewModel 批量写回 KVStore。
    val importLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.OpenDocument()
    ) { uri ->
        uri ?: return@rememberLauncherForActivityResult
        runCatching {
            context.contentResolver.openInputStream(uri)?.use { it.bufferedReader().readText() }
        }.getOrNull()?.takeIf { it.isNotBlank() }?.let { text ->
            onImportJson(text)
            Toast.makeText(context, "已导入配置", Toast.LENGTH_SHORT).show()
        } ?: Toast.makeText(context, "读取文件失败", Toast.LENGTH_SHORT).show()
    }
    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(Spacing.lg),
    ) {
        item {
            AppSectionHeader(stringResource(R.string.settings_norm_flow_group))
            // (E5) 严格/标准/自由三预设，批量写入开关。
            androidx.compose.foundation.layout.Row(
                modifier = Modifier.fillMaxWidth().padding(vertical = Spacing.sm),
                horizontalArrangement = androidx.compose.foundation.layout.Arrangement.spacedBy(Spacing.sm)
            ) {
                androidx.compose.material3.FilterChip(
                    selected = false, onClick = {
                        onToggleStepInject(true); onToggleToolGuard(true); onToggleReasoningBudget(true)
                        onToggleUsageCard(true); onToggleSopSummary(true); onTogglePlaybookAuto(true); onToggleIdleConverge(true)
                    }, label = { androidx.compose.material3.Text("严格") }
                )
                androidx.compose.material3.FilterChip(
                    selected = false, onClick = {
                        onToggleStepInject(true); onToggleToolGuard(true); onToggleReasoningBudget(false)
                        onToggleUsageCard(true); onToggleSopSummary(true); onTogglePlaybookAuto(false); onToggleIdleConverge(true)
                    }, label = { androidx.compose.material3.Text("标准") }
                )
                androidx.compose.material3.FilterChip(
                    selected = false, onClick = {
                        onToggleStepInject(false); onToggleToolGuard(false); onToggleReasoningBudget(false)
                        onToggleUsageCard(false); onToggleSopSummary(false); onTogglePlaybookAuto(false); onToggleIdleConverge(false)
                    }, label = { androidx.compose.material3.Text("自由") }
                )
            }
            // (F6 UI) step 预算 segmented：紧凑400/标准800/宽松1200。
            androidx.compose.foundation.layout.Row(
                modifier = Modifier.fillMaxWidth().padding(vertical = Spacing.sm),
                horizontalArrangement = androidx.compose.foundation.layout.Arrangement.spacedBy(Spacing.sm)
            ) {
                listOf(400 to "紧凑", 800 to "标准", 1200 to "宽松").forEach { (v, label) ->
                    androidx.compose.material3.FilterChip(
                        selected = stepInjectBudget == v,
                        onClick = { onSetStepInjectBudget(v) },
                        label = { androidx.compose.material3.Text(label) }
                    )
                }
            }
            AppSectionGroup {
                GroupSwitchRow(
                    icon = Icons.Rounded.FactCheck,
                    title = stringResource(R.string.settings_norm_flow_master),
                    subtitle = stringResource(R.string.settings_norm_flow_master_desc),
                    checked = normFlowEnabled,
                    onCheckedChange = onToggleNormFlow
                )
                GroupSwitchRow(
                    icon = Icons.Rounded.Input,
                    title = stringResource(R.string.settings_norm_flow_step_inject),
                    subtitle = stringResource(R.string.settings_norm_flow_step_inject_desc),
                    checked = stepInjectEnabled,
                    onCheckedChange = onToggleStepInject
                )
                GroupSwitchRow(
                    icon = Icons.Rounded.Shield,
                    title = stringResource(R.string.settings_norm_flow_tool_guard),
                    subtitle = stringResource(R.string.settings_norm_flow_tool_guard_desc),
                    checked = toolGuardEnabled,
                    onCheckedChange = onToggleToolGuard
                )
                // (E1) 安全底线只读标签：这些始终生效，不受开关影响。
                androidx.compose.material3.Text(
                    text = "始终生效的安全底线：危险命令拦截、七层权限引擎、超时钳制",
                    style = androidx.compose.material3.MaterialTheme.typography.bodySmall,
                    color = androidx.compose.material3.MaterialTheme.colorScheme.onSurfaceVariant
                )
                GroupSwitchRow(
                    icon = Icons.Rounded.Memory,
                    title = stringResource(R.string.settings_norm_flow_reasoning_budget),
                    subtitle = stringResource(R.string.settings_norm_flow_reasoning_budget_desc),
                    checked = reasoningBudgetEnabled,
                    onCheckedChange = onToggleReasoningBudget
                )
                GroupSwitchRow(
                    icon = Icons.Rounded.BarChart,
                    title = stringResource(R.string.settings_norm_flow_usage_card),
                    subtitle = stringResource(R.string.settings_norm_flow_usage_card_desc),
                    checked = usageCardEnabled,
                    onCheckedChange = onToggleUsageCard
                )
                // (F2) SOP 清单摘要开关（默认 ON）。
                GroupSwitchRow(
                    icon = Icons.Rounded.Book,
                    title = "SOP 清单摘要",
                    subtitle = "在对话中展示剧本/规范清单摘要",
                    checked = sopSummaryEnabled,
                    onCheckedChange = onToggleSopSummary
                )
                GroupSwitchRow(
                    icon = Icons.Rounded.AutoAwesome,
                    title = stringResource(R.string.settings_norm_flow_playbook_auto),
                    subtitle = stringResource(R.string.settings_norm_flow_playbook_auto_desc),
                    checked = playbookAutoEnabled,
                    onCheckedChange = onTogglePlaybookAuto
                )
                GroupSwitchRow(
                    icon = Icons.Rounded.Timer,
                    title = stringResource(R.string.settings_norm_flow_idle_converge),
                    subtitle = stringResource(R.string.settings_norm_flow_idle_converge_desc),
                    checked = idleConvergeEnabled,
                    onCheckedChange = onToggleIdleConverge
                )
            }
            // E3 配置导入导出：导出为 JSON 写公共 Download/MiniMe-core/；导入选 JSON 批量写回 KVStore。
            androidx.compose.foundation.layout.Row(
                modifier = Modifier.fillMaxWidth().padding(vertical = Spacing.sm),
                horizontalArrangement = Arrangement.spacedBy(Spacing.sm)
            ) {
                Button(
                    onClick = {
                        scope.launch {
                            val json = onExportJson()
                            if (json != null && writeSettingsToDownloads(context, "settings-export.json", json)) {
                                Toast.makeText(context, "已导出到 Download/MiniMe-core/settings-export.json", Toast.LENGTH_LONG).show()
                            } else {
                                Toast.makeText(context, "导出失败", Toast.LENGTH_SHORT).show()
                            }
                        }
                    },
                    modifier = Modifier.weight(1f)
                ) { androidx.compose.material3.Text("导出配置") }
                OutlinedButton(
                    onClick = { importLauncher.launch(arrayOf("application/json", "text/plain", "*/*")) },
                    modifier = Modifier.weight(1f)
                ) { androidx.compose.material3.Text("导入配置") }
            }
            Spacer(Modifier.height(Spacing.md))
        }
    }
}

/**
 * E3：把导出 JSON 写到公共 Download/MiniMe-core/ 目录（API 29+ MediaStore 免权限；
 * API <29 走 legacy 公共目录）。复用与 FileLogger 导出一致的写法，不引入新存储层。
 */
private fun writeSettingsToDownloads(context: android.content.Context, name: String, content: String): Boolean = runCatching {
    val values = ContentValues().apply {
        put(MediaStore.Downloads.DISPLAY_NAME, name)
        put(MediaStore.Downloads.MIME_TYPE, "application/json")
        put(MediaStore.Downloads.RELATIVE_PATH, "Download/MiniMe-core/")
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            put(MediaStore.MediaColumns.IS_PENDING, 1)
        }
    }
    val resolver = context.contentResolver
    val uri = resolver.insert(MediaStore.Downloads.EXTERNAL_CONTENT_URI, values)
        ?: return@runCatching false
    try {
        resolver.openOutputStream(uri)?.use { it.write(content.toByteArray(Charsets.UTF_8)) }
    } finally {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            values.clear()
            values.put(MediaStore.MediaColumns.IS_PENDING, 0)
            resolver.update(uri, values, null, null)
        }
    }
    true
}.getOrDefault(false)
