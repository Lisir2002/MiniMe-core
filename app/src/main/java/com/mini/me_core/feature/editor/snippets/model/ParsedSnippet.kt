package com.mini.me_core.feature.editor.snippets.model

/**
 * 一个占位符（TabStop）。
 *
 * [start]/[end] 是该占位符在「用默认值填充后的片段正文」中的字符区间（半开区间），
 * 仅用于预览与校验；编辑器内的真实定位由 sora-editor 的 SnippetController 负责。
 *
 * @property index 占位符编号（1,2,3...）；0 表示最终光标位置
 * @property start 在填充后正文中的起始偏移
 * @property end 在填充后正文中的结束偏移（不含）
 * @property defaultValue 占位符默认文本
 * @property choices 多选占位符的候选列表（`${1|a,b|}`）；非多选为 null
 */
data class TabStop(
    val index: Int,
    val start: Int,
    val end: Int,
    val defaultValue: String,
    val choices: List<String>? = null,
) {
    val isFinal: Boolean get() = index == 0
}

/**
 * 一个内置变量引用，如 `$TM_FILENAME`、`$TM_CURRENT_LINE`、`$TM_SELECTED_TEXT`。
 *
 * @property name 变量名（不含 `$`）
 * @property start 在填充后正文中的起始偏移
 * @property end 在填充后正文中的结束偏移
 */
data class VariableRef(
    val name: String,
    val start: Int,
    val end: Int,
)

/**
 * 解析后的片段：原始占位符文本 + 结构化的占位符/变量位置。
 *
 * @property rawBody 拼接后的原始正文（保留 `${...}` 语法，直接喂给 sora-editor CodeSnippetParser）
 * @property expandedText 用默认值填充后的纯文本（用于预览）
 * @property tabStops 所有占位符（按在正文中出现顺序）
 * @property variables 所有变量引用
 */
data class ParsedSnippet(
    val rawBody: String,
    val expandedText: String,
    val tabStops: List<TabStop>,
    val variables: List<VariableRef>,
) {
    /** 编号为 [index] 的所有占位符（相同编号需同步修改）。 */
    fun tabStopsOf(index: Int): List<TabStop> = tabStops.filter { it.index == index }

    /** 是否含最终光标占位符 `${0}`。 */
    val hasFinalStop: Boolean get() = tabStops.any { it.isFinal }
}
