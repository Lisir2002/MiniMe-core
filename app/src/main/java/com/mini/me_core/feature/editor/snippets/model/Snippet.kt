package com.mini.me_core.feature.editor.snippets.model

/**
 * 代码片段定义。
 *
 * 同时承载内置片段（来自 assets）与用户自定义片段（来自 [com.mini.me_core.feature.editor.snippets.CustomSnippetStore]）。
 *
 * @property id 全局唯一标识；内置片段为 "<language>-<prefix>"，自定义片段为随机 UUID
 * @property name 片段展示名
 * @property prefix 触发词（用户输入后在补全列表中匹配）
 * @property language 所属语言 id（如 "python"、"javascript"），与 scopeName 解耦
 * @property description 片段描述，展示在补全列表副标题
 * @property body 片段正文，每行一个元素；内含 VS Code 风格占位符语法
 * @property builtin 是否内置（内置片段不可删除，仅可禁用）
 * @property enabled 是否启用（禁用后不参与补全）
 */
data class Snippet(
    val id: String,
    val name: String,
    val prefix: String,
    val language: String,
    val description: String,
    val body: List<String>,
    val builtin: Boolean = false,
    val enabled: Boolean = true,
) {
    /** 拼接为单行字符串（供 sora-editor CodeSnippetParser 解析）。 */
    fun rawBody(): String = body.joinToString("\n")

    fun withEnabled(enabled: Boolean): Snippet = copy(enabled = enabled)
}
