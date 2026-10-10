package com.mini.me_core.feature.workspace.domain

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File

/**
 * [PathBoundary] 纯边界判定单测：兄弟前缀 / 尾部分隔符 / 空根 等反例必须拒绝，
 * 合法根内路径必须通过。`..` 与符号链接由 File.canonicalPath 在生产侧解析。
 */
class PathBoundaryTest {

    private val root = "/data/data/com.mini.me_core/files/projects"

    @Test
    fun `exactly root is within`() {
        assertTrue(PathBoundary.isWithin(root, root))
    }

    @Test
    fun `root child via separator is within`() {
        assertTrue(PathBoundary.isWithin("$root/sub/file.txt", root))
    }

    @Test
    fun `sibling prefix like workspace-evil rejected`() {
        assertFalse(PathBoundary.isWithin("$root-evil/x", root))
        assertFalse(PathBoundary.isWithin("${root}evil/x", root))
    }

    @Test
    fun `parent escape rejected`() {
        assertFalse(PathBoundary.isWithin("/data/data/com.mini.me_core/files", root))
    }

    @Test
    fun `absolute path outside root rejected`() {
        assertFalse(PathBoundary.isWithin("/etc/passwd", root))
    }

    @Test
    fun `empty root rejects everything`() {
        assertFalse(PathBoundary.isWithin(root, ""))
        assertFalse(PathBoundary.isWithin("$root/x", ""))
    }

    @Test
    fun `root trailing slash tolerated`() {
        assertTrue(PathBoundary.isWithin("$root/x", "$root${File.separator}"))
    }

    @Test
    fun `canonicalized traversal resolves before check at mapper level`() {
        // 生产链：File(root, "../../etc/passwd").canonicalPath 已脱离 root，
        // 这里验证判定函数对解析后的结果（/etc/passwd vs root）拒绝。
        val resolved = File(root, "../../etc/passwd").canonicalPath
        assertFalse(PathBoundary.isWithin(resolved, root))
    }

    @Test
    fun `canonicalized legal child passes`() {
        val resolved = File(root, "src/Main.kt").canonicalPath
        assertTrue(PathBoundary.isWithin(resolved, root))
    }
}
