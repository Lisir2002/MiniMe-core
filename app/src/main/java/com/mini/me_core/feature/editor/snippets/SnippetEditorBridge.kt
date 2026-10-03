package com.mini.me_core.feature.editor.snippets

import io.github.rosemoe.sora.widget.CodeEditor
import io.github.rosemoe.sora.widget.snippet.variable.FileBasedSnippetVariableResolver

/**
 * 编辑器侧的片段桥接。
 *
 * 负责：
 *  - 为当前编辑器设置文件变量解析器（支持当前文件名 / 文件路径 / 目录变量）
 *  - 暴露移动端占位符跳转（下一个 / 上一个），委托给 sora-editor 的 SnippetController
 *  - 查询当前是否处于片段展开会话中（用于显示/隐藏跳转工具栏）
 *
 * 编辑器内的占位符高亮、相同编号同步修改、最终光标均由 sora-editor SnippetController 处理。
 */
object SnippetEditorBridge {

    /**
     * 绑定当前打开文件路径：让文件名类变量能解析到真实文件名。
     * 应在文件加载后调用。
     */
    fun bindFile(editor: CodeEditor, filePath: String?) {
        if (filePath.isNullOrEmpty()) return
        val controller = editor.snippetController ?: return
        controller.fileVariableResolver = object : FileBasedSnippetVariableResolver() {
            override fun resolve(name: String): String {
                val file = java.io.File(filePath)
                return when (name) {
                    "TM_FILENAME" -> file.name
                    "TM_FILEPATH" -> file.absolutePath
                    "TM_DIRECTORY" -> file.parent ?: ""
                    else -> ""
                }
            }
        }
    }

    /** 当前是否处于片段展开会话中。 */
    fun isSnippetActive(editor: CodeEditor): Boolean =
        runCatching { editor.snippetController?.isInSnippet() ?: false }.getOrDefault(false)

    /** 跳转到下一个占位符。 */
    fun nextPlaceholder(editor: CodeEditor) {
        runCatching { editor.snippetController?.shiftToNextTabStop() }
    }

    /** 跳转到上一个占位符。 */
    fun prevPlaceholder(editor: CodeEditor) {
        runCatching { editor.snippetController?.shiftToPreviousTabStop() }
    }
}
