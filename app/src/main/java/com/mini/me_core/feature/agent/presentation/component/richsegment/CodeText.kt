package com.mini.me_core.feature.agent.presentation.component.richsegment

/** Tab 展开宽度（空格数）：与主流代码编辑器默认缩进一致，保证等宽对齐可预期。 */
internal const val TAB_WIDTH = 4

/**
 * 把字符串中的 Tab 展开为 [TAB_WIDTH] 个空格。
 *
 * 只作用于渲染路径：等宽字体下 Tab 的推进宽度由渲染器决定（Android 上并非稳定列数），
 * 会让行号列与代码列对不齐；展开后等宽对齐与横向滚动宽度都是确定的。
 * 复制按钮始终复制未展开的原文（用户粘贴进编辑器后期望的仍是 Tab 缩进）。
 *
 * 必须在语法高亮之前调用：高亮 span 按字符索引定位，事后对高亮结果做字符串替换
 * 会让所有 span 整体错位。
 */
internal fun String.expandTabsForRender(): String {
    if (!contains('\t')) return this
    val sb = StringBuilder(length + length / 8 + 16)
    for (ch in this) {
        if (ch == '\t') repeat(TAB_WIDTH) { sb.append(' ') } else sb.append(ch)
    }
    return sb.toString()
}
