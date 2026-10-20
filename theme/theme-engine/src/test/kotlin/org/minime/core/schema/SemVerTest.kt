package org.minime.core.schema

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertThrows
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

/** SemVer 2.0.0 子集 + 版本区间（§5.1 manifest.engineVersion） */
class SemVerTest {

    private fun v(raw: String) = SemVer.parse(raw)

    @Test
    fun `parse valid versions`() {
        assertEquals(v("1.2.3"), v("1.2.3"))
        assertEquals(listOf("rc1"), v("1.2.3-rc1").prerelease)
        assertEquals("build", v("1.2.3+build").build)
        assertEquals(v("1.2.3-rc.1"), v("1.2.3-rc.1"))
    }

    @Test
    fun `parse rejects invalid`() {
        assertThrows(IllegalArgumentException::class.java) { v("01.2.3") } // 前导零
        assertThrows(IllegalArgumentException::class.java) { v("1.2.3-") }
        assertThrows(IllegalArgumentException::class.java) { v("v1.2.3") }
        assertThrows(IllegalArgumentException::class.java) { v("1.2.3.4") } // 四段式不是版本号
        assertThrows(IllegalArgumentException::class.java) { v("1") }      // 单段
    }

    @Test
    fun `parse two-part engine version`() {
        // 引擎版本为二段式（项目规范）：补 patch=0 参与比较
        assertEquals(SemVersion(1, 0, 0), v("1.0"))
        assertEquals(v("1.0.0"), v("1.0"))
        assertEquals(v("1.9.0"), v("1.9"))
        assertTrue(v("1.0") < v("1.1"))
        assertTrue(v("1.9") < v("2.0"))
        assertEquals("1.0.0", v("1.0").toString())
    }

    @Test
    fun `engine version steps by 0_1 with carry at nine`() {
        // 逢九进一：1.0 → 1.1 … 1.9 → 2.0（不存在 1.10）
        var current = v("1.0")
        val seen = ArrayList<String>()
        repeat(9) {
            current = SemVer.nextEngineVersion(current)
            seen += "${current.major}.${current.minor}"
        }
        assertEquals(
            listOf("1.1", "1.2", "1.3", "1.4", "1.5", "1.6", "1.7", "1.8", "1.9"),
            seen,
        )
        assertEquals("2.0", "${SemVer.nextEngineVersion(v("1.9")).major}.${SemVer.nextEngineVersion(v("1.9")).minor}")
        assertEquals("2.1", "${SemVer.nextEngineVersion(v("2.0")).major}.${SemVer.nextEngineVersion(v("2.0")).minor}")
    }

    @Test
    fun `current engine version satisfies its compat range`() {
        // 引擎版本落在自身兼容区间内（当前 1.1，区间 >=1.0 <2.0）
        val engine = v(org.minime.core.api.MiniMeEngine.VERSION)
        assertTrue(SemVer.satisfies(engine, org.minime.core.api.MiniMeEngine.COMPAT_RANGE))
        assertTrue(SemVer.satisfies(v("1.0"), ">=1.0 <2.0"))
        assertTrue(SemVer.satisfies(v("1.9"), ">=1.0 <2.0"))
        assertFalse(SemVer.satisfies(v("0.9"), ">=1.0 <2.0"))
        assertFalse(SemVer.satisfies(v("2.0"), ">=1.0 <2.0"))
    }

    @Test
    fun `ordering rules`() {
        assertTrue(v("1.0.0") < v("1.0.1"))
        assertTrue(v("1.0.0") < v("1.1.0"))
        assertTrue(v("1.0.0") < v("2.0.0"))
        // release > prerelease（同号）
        assertTrue(v("1.0.0-rc1") < v("1.0.0"))
        assertTrue(v("1.0.0-rc1") < v("1.0.0-rc2"))
        // 数值标识 < 字符标识
        assertTrue(v("1.0.0-1") < v("1.0.0-alpha"))
        // 段多者大（前缀相同）
        assertTrue(v("1.0.0-alpha") < v("1.0.0-alpha.1"))
    }

    @Test
    fun `range space separated`() {
        // 白皮书 §5.1 示例形态：">=1.0.0 <2.0.0"
        assertTrue(SemVer.satisfies(v("1.0.0"), ">=1.0.0 <2.0.0"))
        assertTrue(SemVer.satisfies(v("1.9.9"), ">=1.0.0 <2.0.0"))
        assertFalse(SemVer.satisfies(v("2.0.0"), ">=1.0.0 <2.0.0"))
        assertFalse(SemVer.satisfies(v("0.9.9"), ">=1.0.0 <2.0.0"))
    }

    @Test
    fun `range comma separated`() {
        assertTrue(SemVer.satisfies(v("1.5.0"), ">=1.0.0, <2.0.0"))
        assertFalse(SemVer.satisfies(v("2.5.0"), ">=1.0.0, <2.0.0"))
    }

    @Test
    fun `range prerelease boundaries`() {
        // prerelease < 同号 release（SemVer 语义）：
        //   1.0.0-rc1 < 1.0.0 → 不入「>=1.0」；2.0.0-rc1 < 2.0.0 → 仍落在「<2.0」内
        assertTrue(SemVer.satisfies(v("1.5.0"), ">=1.0 <2.0"))
        assertFalse(SemVer.satisfies(v("1.0.0-rc1"), ">=1.0 <2.0"))
        assertTrue(SemVer.satisfies(v("2.0.0-rc1"), ">=1.0 <2.0"))
        assertTrue(SemVer.satisfies(v("1.0.0-rc1"), ">=1.0.0-rc1 <2.0.0"))
        assertFalse(SemVer.satisfies(v("1.0.0-rc0"), ">=1.0.0-rc1 <2.0.0")) // rc0 < rc1
    }

    @Test
    fun `range bare version is exact`() {
        assertTrue(SemVer.satisfies(v("1.2.3"), "1.2.3"))
        assertFalse(SemVer.satisfies(v("1.2.4"), "1.2.3"))
    }

    @Test
    fun `range with spaces inside comparator`() {
        assertTrue(SemVer.satisfies(v("1.5.0"), ">= 1.0.0, < 2.0.0"))
    }

    @Test
    fun `range rejects garbage`() {
        assertThrows(IllegalArgumentException::class.java) { SemVer.satisfies(v("1.0.0"), ">=abc") }
        assertThrows(IllegalArgumentException::class.java) { SemVer.satisfies(v("1.0.0"), ">=1.0.0 ~2.0.0") }
        assertThrows(IllegalArgumentException::class.java) { SemVer.satisfies(v("1.0.0"), "") }
    }
}
