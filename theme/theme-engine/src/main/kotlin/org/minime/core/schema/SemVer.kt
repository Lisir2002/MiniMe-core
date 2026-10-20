package org.minime.core.schema

import kotlin.math.min

/**
 * SemVer 2.0.0 子集（引擎版本与主题包 engineVersion 兼容区间用）。
 * 优先级规则与规范一致：release > prerelease；prerelease 标识数值型 < 字符型，
 * 逐段比较，段多者大（前缀相同时）。
 *
 * **引擎版本为二段式**（项目规范）：`主.次`，步长 +0.1，**逢九进一**
 * （`1.0 → 1.1 → … → 1.9 → 2.0`，不存在 `1.10`）。二段式在解析时补齐 patch=0
 * 参与比较（`1.0` ≡ `1.0.0`），因此主题包区间可写作 `>=1.0 <2.0`。
 */
data class SemVersion(
    val major: Int,
    val minor: Int,
    val patch: Int,
    val prerelease: List<String> = emptyList(),
    val build: String? = null,
) : Comparable<SemVersion> {

    override fun compareTo(other: SemVersion): Int {
        if (major != other.major) return major - other.major
        if (minor != other.minor) return minor - other.minor
        if (patch != other.patch) return patch - other.patch
        return comparePrerelease(prerelease, other.prerelease)
    }

    private fun comparePrerelease(a: List<String>, b: List<String>): Int {
        if (a.isEmpty() && b.isEmpty()) return 0
        if (a.isEmpty()) return 1   // release 大于同号 prerelease
        if (b.isEmpty()) return -1
        for (i in 0 until min(a.size, b.size)) {
            val ai = a[i]
            val bi = b[i]
            val an = ai.toIntOrNull()
            val bn = bi.toIntOrNull()
            val c = when {
                an != null && bn != null -> an.compareTo(bn)
                an != null -> -1   // 数值标识 < 字符标识
                bn != null -> 1
                else -> ai.compareTo(bi)
            }
            if (c != 0) return c
        }
        return a.size - b.size
    }

    override fun toString(): String = buildString {
        append(major).append('.').append(minor).append('.').append(patch)
        if (prerelease.isNotEmpty()) append('-').append(prerelease.joinToString("."))
        if (build != null) append('+').append(build)
    }
}

object SemVer {
    /** 三段式（标准 SemVer） */
    private val VERSION =
        Regex("^(0|[1-9]\\d*)\\.(0|[1-9]\\d*)\\.(0|[1-9]\\d*)(?:-([0-9A-Za-z.-]+))?(?:\\+([0-9A-Za-z.-]+))?$")

    /** 二段式（引擎版本：`主.次`，patch 补 0） */
    private val VERSION_TWO_PART =
        Regex("^(0|[1-9]\\d*)\\.(0|[1-9]\\d*)(?:-([0-9A-Za-z.-]+))?(?:\\+([0-9A-Za-z.-]+))?$")

    /** 单个比较器（操作符可选，版本部分含 prerelease/build 字符） */
    private val COMPARATOR = Regex("(>=|<=|>|<|=)?\\s*([0-9A-Za-z.+-]+)")

    /**
     * 解析版本号：二段式（`1.0`）与三段式（`1.0.0`）皆可；四段及以上拒绝。
     * 二段式按引擎版本规范补 patch=0。
     */
    fun parse(raw: String): SemVersion {
        val text = raw.trim()
        VERSION.matchEntire(text)?.let { m ->
            return SemVersion(
                major = m.groupValues[1].toInt(),
                minor = m.groupValues[2].toInt(),
                patch = m.groupValues[3].toInt(),
                prerelease = m.groupValues[4].takeIf { it.isNotEmpty() }?.split(".") ?: emptyList(),
                build = m.groupValues[5].takeIf { it.isNotEmpty() },
            )
        }
        VERSION_TWO_PART.matchEntire(text)?.let { m ->
            return SemVersion(
                major = m.groupValues[1].toInt(),
                minor = m.groupValues[2].toInt(),
                patch = 0,
                prerelease = m.groupValues[3].takeIf { it.isNotEmpty() }?.split(".") ?: emptyList(),
                build = m.groupValues[4].takeIf { it.isNotEmpty() },
            )
        }
        throw IllegalArgumentException("非法版本号（须为二段式 1.0 或三段式 1.0.0）: \"$raw\"")
    }

    /**
     * 引擎版本步进（二段式 +0.1，**逢九进一**）：`1.0 → 1.1 … 1.9 → 2.0`。
     * 用于引擎发版时推导下一版本，保证不出现 `1.10` 这类两位次版本号。
     */
    fun nextEngineVersion(current: SemVersion): SemVersion =
        if (current.minor >= 9) SemVersion(major = current.major + 1, minor = 0, patch = 0)
        else SemVersion(major = current.major, minor = current.minor + 1, patch = 0)

    /**
     * [version] 是否满足 [range]（全部比较器满足才通过）。
     * 支持 >= / <= / > / < / = 与裸版本（精确）；比较器之间可用**逗号或空白分隔**
     *（白皮书 §5.1 示例为空白分隔：`">=1.0.0 <2.0.0"`）。
     */
    fun satisfies(version: SemVersion, range: String): Boolean {
        val comparators = COMPARATOR.findAll(range).map { m ->
            val op = m.groupValues[1].ifEmpty { "=" }
            op to m.groupValues[2]
        }.toList()
        require(comparators.isNotEmpty()) { "空版本区间: \"$range\"" }
        // 区间字符串须被比较器完整覆盖（禁止夹带非法字符）
        val consumed = COMPARATOR.findAll(range).map { it.value }.joinToString("")
        val skeleton = range.filterNot { it.isWhitespace() || it == ',' }
        val consumedSkeleton = consumed.filterNot { it.isWhitespace() || it == ',' }
        require(skeleton == consumedSkeleton) { "非法版本区间: \"$range\"" }

        return comparators.all { (op, raw) ->
            val target = parse(raw)
            when (op) {
                ">=" -> version >= target
                "<=" -> version <= target
                ">" -> version > target
                "<" -> version < target
                else -> version == target
            }
        }
    }

    fun satisfies(version: String, range: String): Boolean = satisfies(parse(version), range)
}
