package com.mini.me_core.feature.update.domain

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * [VersionComparator] 纯逻辑单测：覆盖 v 前缀、多段版本号、预发布、构建元数据、非法段降级。
 */
class VersionComparatorTest {

    // ---- normalizeTag ----

    @Test
    fun normalizeTag_stripsVPrefix() {
        assertEquals("1.7.0", VersionComparator.normalizeTag("v1.7.0"))
        assertEquals("1.7.0", VersionComparator.normalizeTag("V1.7.0"))
    }

    @Test
    fun normalizeTag_withoutVPrefix() {
        assertEquals("1.7.0", VersionComparator.normalizeTag("1.7.0"))
    }

    @Test
    fun normalizeTag_trimsWhitespace() {
        assertEquals("1.7.0", VersionComparator.normalizeTag("  v1.7.0  "))
    }

    @Test
    fun normalizeTag_takesSegmentBeforeSpace() {
        assertEquals("1.7.0", VersionComparator.normalizeTag("1.7.0 extra metadata"))
    }

    @Test
    fun normalizeTag_stripsBuildMetadata() {
        assertEquals("1.7.0", VersionComparator.normalizeTag("v1.7.0+g04bc2fa"))
        assertEquals("1.7.0-dev.2", VersionComparator.normalizeTag("1.7.0-dev.2+g04bc2fa"))
    }

    // ---- splitCore ----

    @Test
    fun splitCore_plainRelease() {
        val (base, pre) = VersionComparator.splitCore("1.7.0")
        assertEquals("1.7.0", base)
        assertEquals("", pre)
    }

    @Test
    fun splitCore_preRelease() {
        val (base, pre) = VersionComparator.splitCore("1.7.0-rc1")
        assertEquals("1.7.0", base)
        assertEquals("rc1", pre)
    }

    // ---- compare ----

    @Test
    fun compare_equal() {
        assertEquals(0, VersionComparator.compare("1.7.0", "1.7.0"))
        assertEquals(0, VersionComparator.compare("v1.7.0", "1.7.0"))
        assertEquals(0, VersionComparator.compare("1.7.0-rc1", "1.7.0-rc1"))
    }

    @Test
    fun compare_higherBaseVersion() {
        assertTrue(VersionComparator.compare("1.7.0", "1.6.0") > 0)
        assertTrue(VersionComparator.compare("2.0.0", "1.9.9") > 0)
    }

    @Test
    fun compare_lowerBaseVersion() {
        assertTrue(VersionComparator.compare("1.6.0", "1.7.0") < 0)
        assertTrue(VersionComparator.compare("1.9.9", "2.0.0") < 0)
    }

    @Test
    fun compare_releaseVsPreRelease() {
        assertTrue(VersionComparator.compare("1.7.0", "1.7.0-rc1") > 0)
        assertTrue(VersionComparator.compare("1.7.0-rc1", "1.7.0") < 0)
    }

    @Test
    fun compare_preReleaseOrdering() {
        assertTrue(VersionComparator.compare("1.7.0-rc2", "1.7.0-rc1") > 0)
        assertTrue(VersionComparator.compare("1.7.0-rc1", "1.7.0-rc2") < 0)
    }

    @Test
    fun compare_devVsRc() {
        // rc1 > dev.2（字母段字典序，r > d）
        assertTrue(VersionComparator.compare("1.7.0-rc1", "1.7.0-dev.2+g04bc2fa") > 0)
    }

    @Test
    fun compare_differentSegmentCount() {
        assertEquals(0, VersionComparator.compare("1.7", "1.7.0"))
        assertTrue(VersionComparator.compare("0.0.0.21", "0.0.0.20") > 0)
        assertTrue(VersionComparator.compare("0.0.0.21", "0.0.1.0") < 0)
    }

    @Test
    fun compare_numericPreReleaseIdentifiers() {
        // rc.10 应大于 rc.2（数字段按数值比，不是字典序）
        assertTrue(VersionComparator.compare("1.0.0-rc.10", "1.0.0-rc.2") > 0)
    }

    @Test
    fun compare_shorterPreReleaseIdentifiers() {
        // alpha < alpha.1（SemVer 11.12）
        assertTrue(VersionComparator.compare("1.0.0-alpha", "1.0.0-alpha.1") < 0)
    }

    // ---- isUpToDate / isNewer ----

    @Test
    fun sameVersion_isUpToDate() {
        assertTrue(VersionComparator.isUpToDate("1.7.0", "1.7.0"))
        assertTrue(VersionComparator.isUpToDate("v1.7.0", "1.7.0"))
    }

    @Test
    fun newerLatest_isNotUpToDate() {
        assertFalse(VersionComparator.isUpToDate("1.7.0", "1.7.0-rc1"))
        assertFalse(VersionComparator.isUpToDate("1.7.0", "1.6.0"))
        assertTrue(VersionComparator.isNewer("1.7.0", "1.6.0"))
    }

    @Test
    fun currentIsNewer_isUpToDate() {
        // 当前比 latest 更新（如内测版），不算有更新
        assertTrue(VersionComparator.isUpToDate("1.7.0", "1.8.0-dev"))
        assertFalse(VersionComparator.isNewer("1.7.0", "1.8.0-dev"))
    }

    @Test
    fun latestEqualsCurrentWithBuildHash_isUpToDate() {
        assertTrue(VersionComparator.isUpToDate("1.7.0", "1.7.0+g04bc2fa"))
    }
}
