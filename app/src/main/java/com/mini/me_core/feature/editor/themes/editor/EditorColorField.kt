package com.mini.me_core.feature.editor.themes.editor

/**
 * 可视化主题编辑器中可调的颜色字段定义。
 *
 * 每个字段映射到主题 JSON 中的一处颜色：
 *  - [colorKey] 非空 → 对应 `colors.<colorKey>`（编辑器基础色）
 *  - 否则对应 `tokenColors` 中 scope 为 [scope] 的规则的 foreground（语法色）
 *
 * 纯数据定义，不依赖 Android，便于单测。
 */
data class EditorColorField(
    val key: String,
    val labelKey: String,
    val group: Group,
    val colorKey: String? = null,
    val scope: String? = null,
) {
    enum class Group { EDITOR, SYNTAX }

    companion object {
        /** 编辑器基础色（6 项）。 */
        val EDITOR_FIELDS = listOf(
            EditorColorField("editor_background", "editor_background", Group.EDITOR, colorKey = "editor.background"),
            EditorColorField("editor_foreground", "editor_foreground", Group.EDITOR, colorKey = "editor.foreground"),
            EditorColorField("line_number", "line_number", Group.EDITOR, colorKey = "editorLineNumber.foreground"),
            EditorColorField("line_highlight", "line_highlight", Group.EDITOR, colorKey = "editor.lineHighlightBackground"),
            EditorColorField("selection", "selection", Group.EDITOR, colorKey = "editor.selectionBackground"),
            EditorColorField("cursor", "cursor", Group.EDITOR, colorKey = "editorCursor.foreground"),
        )

        /** 语法高亮色（9 项）。 */
        val SYNTAX_FIELDS = listOf(
            EditorColorField("keyword", "keyword", Group.SYNTAX, scope = "keyword"),
            EditorColorField("string", "string", Group.SYNTAX, scope = "string"),
            EditorColorField("comment", "comment", Group.SYNTAX, scope = "comment"),
            EditorColorField("number", "number", Group.SYNTAX, scope = "constant.numeric"),
            EditorColorField("function", "function", Group.SYNTAX, scope = "entity.name.function"),
            EditorColorField("type", "type", Group.SYNTAX, scope = "storage.type"),
            EditorColorField("variable", "variable", Group.SYNTAX, scope = "variable"),
            EditorColorField("operator", "operator", Group.SYNTAX, scope = "keyword.operator"),
            EditorColorField("constant", "constant", Group.SYNTAX, scope = "constant.language"),
        )

        val ALL = EDITOR_FIELDS + SYNTAX_FIELDS
    }
}
