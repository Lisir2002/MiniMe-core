package com.mini.me_core.feature.agent.presentation.component

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Add
import androidx.compose.material.icons.rounded.PlayArrow
import androidx.compose.material3.AssistChip
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
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.mini.me_core.R

/** 一个对话模板。 */
data class ChatTemplate(
    val name: String,
    val content: String,
    val category: String,
    val builtin: Boolean = true,
)

/**
 * MiniMe 对话模板面板（F2.7）。
 *
 * BottomSheet 占屏 60% 高：顶部分类 Tab + 搜索框；网格展示内置/自定义模板；
 * 底部「+ 新建模板」。点击模板把内容填入输入框（不自动发送）。
 * 变量 {选中文字}/{剪贴板内容}/{当前时间} 由宿主在填入时替换。
 */
@OptIn(androidx.compose.material3.ExperimentalMaterial3Api::class)
@Composable
fun ChatTemplatePanel(
    onDismiss: () -> Unit,
    onUseTemplate: (String) -> Unit,
) {
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    var selectedCategory by remember { mutableStateOf("all") }
    var query by remember { mutableStateOf("") }
    // 自定义模板（会话内持久；后续可接 Room/KVStore）
    var customTemplates by remember { mutableStateOf(listOf<ChatTemplate>()) }
    var showEditor by remember { mutableStateOf(false) }

    val builtin = remember { builtinTemplates() }
    val all = builtin + customTemplates

    ModalBottomSheet(onDismissRequest = onDismiss, sheetState = sheetState) {
        Column(Modifier.padding(horizontal = 16.dp)) {
            // 搜索框
            OutlinedTextField(
                value = query,
                onValueChange = { query = it },
                modifier = Modifier.fillMaxWidth(),
                placeholder = { Text(stringResource(R.string.template_search_hint)) },
                singleLine = true,
            )
            Spacer(Modifier.height(8.dp))
            // 分类 Tab
            val cats = listOf(
                "all" to stringResource(R.string.template_open),
                "programming" to stringResource(R.string.template_cat_programming),
                "writing" to stringResource(R.string.template_cat_writing),
                "analysis" to stringResource(R.string.template_cat_analysis),
                "creative" to stringResource(R.string.template_cat_creative),
                "custom" to stringResource(R.string.template_cat_custom),
            )
            LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                items(cats.size) { i ->
                    val (key, label) = cats[i]
                    AssistChip(
                        onClick = { selectedCategory = key },
                        label = { Text(label) },
                    )
                }
            }
            Spacer(Modifier.height(8.dp))
            // 模板网格（60% 屏高，可滚动）
            val filtered = all.filter { t ->
                (selectedCategory == "all" || t.category == selectedCategory) &&
                    (query.isBlank() || t.name.contains(query, ignoreCase = true) || t.content.contains(query, ignoreCase = true))
            }
            LazyVerticalGrid(
                columns = GridCells.Fixed(2),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp),
                modifier = Modifier.height(320.dp),
            ) {
                items(filtered) { tpl ->
                    Surface(
                        shape = MaterialTheme.shapes.medium,
                        color = MaterialTheme.colorScheme.surfaceVariant,
                        onClick = {
                            onUseTemplate(tpl.content)
                            onDismiss()
                        }
                    ) {
                        Column(Modifier.padding(12.dp)) {
                            Text(tpl.name, style = MaterialTheme.typography.bodyMedium)
                            Spacer(Modifier.height(4.dp))
                            Text(
                                tpl.content.take(40),
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                maxLines = 2,
                            )
                        }
                    }
                }
            }
            // 底部新建按钮
            TextButton(onClick = { showEditor = true }, modifier = Modifier.fillMaxWidth()) {
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
                customTemplates = customTemplates + ChatTemplate(name, content, "custom", builtin = false)
                showEditor = false
            }
        )
    }
}

/** 新建/编辑模板小表单。 */
@OptIn(androidx.compose.material3.ExperimentalMaterial3Api::class)
@Composable
private fun TemplateEditorSheet(
    onDismiss: () -> Unit,
    onSave: (name: String, content: String) -> Unit,
) {
    var name by remember { mutableStateOf("") }
    var content by remember { mutableStateOf("") }
    ModalBottomSheet(onDismissRequest = onDismiss) {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
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
                modifier = Modifier.fillMaxWidth().height(120.dp),
            )
            TextButton(
                onClick = { if (name.isNotBlank() && content.isNotBlank()) onSave(name, content) },
                modifier = Modifier.fillMaxWidth()
            ) { Text(stringResource(R.string.template_save)) }
        }
    }
}

private fun builtinTemplates(): List<ChatTemplate> = listOf(
    ChatTemplate("解释这段代码", "请解释以下代码的功能与逻辑：\n\n{选中文字}", "programming"),
    ChatTemplate("优化性能", "请优化以下代码的性能，说明优化点：\n\n{选中文字}", "programming"),
    ChatTemplate("添加单元测试", "为以下代码添加单元测试：\n\n{选中文字}", "programming"),
    ChatTemplate("Code Review", "请对以下代码做 Code Review，指出问题与改进建议：\n\n{选中文字}", "programming"),
    ChatTemplate("润色文字", "请润色以下文字，使其更通顺专业：\n\n{选中文字}", "writing"),
    ChatTemplate("翻译为英文", "请将以下内容翻译为英文：\n\n{选中文字}", "writing"),
    ChatTemplate("总结要点", "请总结以下内容的要点：\n\n{选中文字}", "writing"),
    ChatTemplate("扩写", "请扩写以下内容，丰富细节：\n\n{选中文字}", "writing"),
    ChatTemplate("SWOT 分析", "请对以下主题做 SWOT 分析：{选中文字}", "analysis"),
    ChatTemplate("列出优缺点", "请列出以下方案的优缺点：{选中文字}", "analysis"),
    ChatTemplate("对比方案", "请对比方案 A 与方案 B 的优劣：{选中文字}", "analysis"),
    ChatTemplate("头脑风暴", "请围绕以下主题头脑风暴，给出多个创意：{选中文字}", "creative"),
    ChatTemplate("写一首诗", "请以「{选中文字}」为主题写一首诗。", "creative"),
    ChatTemplate("设计一个产品", "请设计一个解决「{选中文字}」问题的产品，给出核心功能与定位。", "creative"),
)
