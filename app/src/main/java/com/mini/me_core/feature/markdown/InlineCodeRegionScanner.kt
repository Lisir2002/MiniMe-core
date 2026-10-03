package com.mini.me_core.feature.markdown

/**
 * 行内代码反引号区域扫描器（纯逻辑，不依赖 Android）。
 *
 * 从 [MarkdownAnalyzer] 抽取而来，便于在 JVM 单元测试中直接覆盖各种反引号配对场景。
 *
 * 支持单反引号 `code` 与双反引号 `` `code` ``（开闭反引号长度需一致）。
 * 长度不匹配的反引号串视为代码内字面量，继续扫描；未闭合的开反引号被丢弃。
 */
internal object InlineCodeRegionScanner {

    /**
     * 扫描一行文本中的所有行内代码内容区间。
     *
     * @return 区间列表，每项为 [contentStart, contentEnd)（不含反引号本身），按出现顺序排列
     */
    fun scan(line: String): List<Pair<Int, Int>> {
        val regions = ArrayList<Pair<Int, Int>>()
        val n = line.length
        var i = 0
        var openLen = 0
        var contentStart = -1
        while (i < n) {
            if (line[i] == '`') {
                var j = i
                while (j < n && line[j] == '`') j++
                val runLen = j - i
                if (contentStart == -1) {
                    openLen = runLen
                    contentStart = j // 内容从反引号串之后开始
                } else if (runLen == openLen) {
                    regions += Pair(contentStart, i) // 内容区间 [contentStart, i)
                    contentStart = -1
                    openLen = 0
                }
                // 长度不匹配的反引号串视为代码内字面量，继续扫描
                i = j
            } else {
                i++
            }
        }
        return regions
    }
}
