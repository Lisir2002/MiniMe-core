package com.mini.me_core.feature.markdown

import android.graphics.Typeface

/**
 * Markdown 高亮片段（中间层数据结构）。
 *
 * 描述文档中一段连续文本的样式，由各高亮模块产出，
 * 最终在 MarkdownAnalyzer 中被翻译为 sora-editor 的 Span。
 *
 * 设计说明：这里使用裸 ARGB 颜色（而非 sora-editor 的颜色 ID），
 * 是因为行内代码的 token 颜色来自「另一门语言」的 TextMate grammar，
 * 无法直接复用主编辑器的颜色 ID 表；通过渲染期颜色解析器
 * （SpanConstColorResolver）把裸 ARGB 直接交给渲染管线。
 *
 * @property start 起始偏移（相对于所属内容的全局起点；行内代码场景下为相对代码内容的偏移）
 * @property end 结束偏移（不含）
 * @property color ARGB 前景色；为 0 表示沿用基础高亮颜色
 * @property scope TextMate scope（如 markup.heading.code 或 string.quoted.single.python）
 * @property isMarker 是否语法符号（如反引号本身），通常做淡化处理
 * @property fontStyle 字体样式位，取 [Typeface.NORMAL]/[Typeface.BOLD]/[Typeface.ITALIC] 及其按位或
 * @property isUnderline 是否下划线
 * @property isStrikethrough 是否删除线
 */
data class MarkdownSpan(
    val start: Int,
    val end: Int,
    val color: Int,
    val scope: String,
    val isMarker: Boolean = false,
    val fontStyle: Int = Typeface.NORMAL,
    val isUnderline: Boolean = false,
    val isStrikethrough: Boolean = false,
)
