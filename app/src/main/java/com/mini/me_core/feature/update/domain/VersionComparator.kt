package com.mini.me_core.feature.update.domain

/**
 * 健壮的版本号比较器（纯逻辑、无 Android 依赖，可单测）。
 *
 * 处理：
 *  - `v` / `V` 前缀（v1.2.3 / V1.2.3）
 *  - 多段版本号（0.0.0.21，长度不一，缺省段补 0）
 *  - 预发布标记（1.2.3-beta / 1.2.3-rc.1）：正式版 > 预发布
 *  - 构建元数据（1.2.3+build.5）：忽略，不参与比较
 *  - tag 末尾的空格/描述（v1.2.3 构建说明）：取首个空白前的部分
 *
 * 语义对齐 SemVer 2.0.0 的核心比较规则，对非法/非数字段做宽容降级（按 0 处理）。
 */
object VersionComparator {

    /** 归一化 tag：去 v 前缀、去构建元数据、取空白前主版本段。 */
    fun normalizeTag(tag: String): String {
        var t = tag.trim()
        if (t.startsWith("v", ignoreCase = true)) t = t.substring(1)
        // 去掉 "v1.2.3 release note" 这类尾巴
        t = t.substringBefore(' ')
        t = t.substringBefore('\n')
        // 去掉构建元数据 +build.5
        t = t.substringBefore('+')
        return t.trim()
    }

    /**
     * 比较两个版本号。
     * @return 正数表示 v1 > v2，负数表示 v1 < v2，0 表示相等。
     */
    fun compare(v1: String, v2: String): Int {
        val n1 = normalizeTag(v1)
        val n2 = normalizeTag(v2)
        if (n1.equals(n2, ignoreCase = true)) return 0

        val (base1, pre1) = splitCore(n1)
        val (base2, pre2) = splitCore(n2)

        // 主版本号：按 . 分段，逐段数字比较，缺省补 0。
        val parts1 = base1.split('.').map { leadingInt(it) }
        val parts2 = base2.split('.').map { leadingInt(it) }
        val maxLen = maxOf(parts1.size, parts2.size)
        for (i in 0 until maxLen) {
            val p1 = parts1.getOrElse(i) { 0L }
            val p2 = parts2.getOrElse(i) { 0L }
            if (p1 != p2) return p1.compareTo(p2)
        }

        // 预发布比较：正式版（无 pre）> 预发布版（有 pre）。
        if (pre1.isEmpty() && pre2.isNotEmpty()) return 1
        if (pre1.isNotEmpty() && pre2.isEmpty()) return -1
        if (pre1.isEmpty() && pre2.isEmpty()) return 0
        return comparePreRelease(pre1, pre2)
    }

    /** latest 是否比 current 更新（即存在新版本）。 */
    fun isNewer(latest: String, current: String): Boolean = compare(latest, current) > 0

    /** latest 与 current 是否等同或更旧（即已是最新）。 */
    fun isUpToDate(latest: String, current: String): Boolean = compare(latest, current) <= 0

    /** 拆出 (主版本号, 预发布标识)。 */
    internal fun splitCore(v: String): Pair<String, String> {
        // 去构建元数据已在 normalizeTag 完成；这里再保险一次
        val core = v.substringBefore('+')
        val dashIdx = core.indexOf('-')
        return if (dashIdx < 0) {
            core to ""
        } else {
            core.substring(0, dashIdx) to core.substring(dashIdx + 1)
        }
    }

    /** 取一段开头的连续数字作为 Long；无数字返回 0。 */
    private fun leadingInt(segment: String): Long {
        val digits = segment.takeWhile { it.isDigit() }
        return digits.toLongOrNull() ?: 0L
    }

    /**
     * 预发布段比较（SemVer 11.12）：
     *  - 以 . 分段；数字段按数字比，字母段按字典序；数字段 < 字母段。
     *  - 字段少且其余字段都相等时，字段少者更小（1.0.0-alpha < 1.0.0-alpha.1）。
     */
    private fun comparePreRelease(a: String, b: String): Int {
        val segsA = a.split('.')
        val segsB = b.split('.')
        val maxLen = maxOf(segsA.size, segsB.size)
        for (i in 0 until maxLen) {
            val ra = segsA.getOrNull(i) ?: return -1 // b 字段更多
            val rb = segsB.getOrNull(i) ?: return 1  // a 字段更多
            val na = ra.toLongOrNull()
            val nb = rb.toLongOrNull()
            val cmp = when {
                na != null && nb != null -> na.compareTo(nb)
                na != null -> -1   // 数字 < 字母
                nb != null -> 1
                else -> ra.compareTo(rb, ignoreCase = true)
            }
            if (cmp != 0) return cmp
        }
        return 0
    }
}
