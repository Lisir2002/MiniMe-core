package com.mini.me_core.core.viewer

/** 查看器类型。 */
enum class ViewerKind { CODE, OFFICE, IMAGE, PDF, HEX, TEXT }

/**
 * 文件类型注册表（扩展名 → 查看器类型 / 语法 scope）。
 * 新增类型只需注册一行。
 */
object FileTypeRegistry {
    private data class Entry(val kind: ViewerKind, val language: String?)

    private val byExt = mapOf(
        // code
        "kt" to Entry(ViewerKind.CODE, "kotlin"),
        "java" to Entry(ViewerKind.CODE, "java"),
        "py" to Entry(ViewerKind.CODE, "python"),
        "js" to Entry(ViewerKind.CODE, "javascript"),
        "ts" to Entry(ViewerKind.CODE, "typescript"),
        "tsx" to Entry(ViewerKind.CODE, "tsx"),
        "c" to Entry(ViewerKind.CODE, "c"),
        "h" to Entry(ViewerKind.CODE, "c"),
        "cpp" to Entry(ViewerKind.CODE, "cpp"),
        "cc" to Entry(ViewerKind.CODE, "cpp"),
        "hpp" to Entry(ViewerKind.CODE, "cpp"),
        "go" to Entry(ViewerKind.CODE, "go"),
        "rs" to Entry(ViewerKind.CODE, "rust"),
        "json" to Entry(ViewerKind.CODE, "json"),
        "xml" to Entry(ViewerKind.CODE, "xml"),
        "html" to Entry(ViewerKind.CODE, "html"),
        "htm" to Entry(ViewerKind.CODE, "html"),
        "yaml" to Entry(ViewerKind.CODE, "yaml"),
        "yml" to Entry(ViewerKind.CODE, "yaml"),
        "sh" to Entry(ViewerKind.CODE, "bash"),
        "bash" to Entry(ViewerKind.CODE, "bash"),
        "md" to Entry(ViewerKind.CODE, "markdown"),
        "sql" to Entry(ViewerKind.CODE, null),  // grammar 暂缺
        "txt" to Entry(ViewerKind.TEXT, null),
        "log" to Entry(ViewerKind.TEXT, null),
        // office
        "docx" to Entry(ViewerKind.OFFICE, null),
        "xlsx" to Entry(ViewerKind.OFFICE, null),
        "pptx" to Entry(ViewerKind.OFFICE, null),
        // image
        "png" to Entry(ViewerKind.IMAGE, null),
        "jpg" to Entry(ViewerKind.IMAGE, null),
        "jpeg" to Entry(ViewerKind.IMAGE, null),
        "gif" to Entry(ViewerKind.IMAGE, null),
        "webp" to Entry(ViewerKind.IMAGE, null),
        // pdf
        "pdf" to Entry(ViewerKind.PDF, null),
    )

    fun kindFor(path: String): ViewerKind {
        val ext = path.substringAfterLast('.', "").lowercase()
        return byExt[ext]?.kind ?: ViewerKind.TEXT
    }

    /** 推断 tree-sitter scope；无法识别返回 null（纯文本查看）。 */
    fun languageFor(path: String): String? {
        val ext = path.substringAfterLast('.', "").lowercase()
        return byExt[ext]?.language
    }
}
