package com.mini.me_core.core.viewer.native.dto

/** 高亮语义类别（与 native HighlightCategory 对齐）。 */
enum class HighlightCategory(val id: Int) {
    NONE(0), KEYWORD(1), TYPE(2), FUNCTION(3), STRING(4), NUMBER(5), COMMENT(6),
    OPERATOR(7), PROPERTY(8), VARIABLE(9), CONSTANT(10), TAG(11), ATTRIBUTE(12),
    PARAMETER(13), ANNOTATION(14), NAMESPACE(15), PUNCTUATION(16), TEXT(17);

    companion object {
        fun fromId(id: Int): HighlightCategory =
            entries.firstOrNull { it.id == id } ?: NONE
    }
}

data class HighlightSpan(
    val startByte: Int,
    val endByte: Int,
    val category: HighlightCategory,
)

data class FoldRegion(
    val startLine: Int,
    val endLine: Int,
    val kind: Int,
)

data class SymbolNode(
    val name: String,
    val startLine: Int,
    val endLine: Int,
    val kind: Int,
)

data class FileInfoDto(
    val lineCount: Long,
    val size: Long,
    val encoding: Int,
    val lineEnding: Int,
    val largeFileMode: Boolean,
)
