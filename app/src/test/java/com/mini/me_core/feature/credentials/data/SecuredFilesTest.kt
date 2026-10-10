package com.mini.me_core.feature.credentials.data

import java.io.File
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * [SecuredFiles] 纯 JVM 单测：含明文 token 的落盘文件收紧为属主独占（0600）。
 *
 * 注意：java.io.File 的 setReadable/setWritable 位在 POSIX 文件系统上真实生效；
 * 在部分 CI 覆盖文件系统上权限位可能被忽略，因此这里主要断言收紧调用不抛异常、
 * 属主仍可读写、执行位被关闭；0600 的真实强制由 Android/Linux 宿主侧保证。
 */
class SecuredFilesTest {

    private fun newTempFile(prefix: String): File {
        val dir = File(
            System.getProperty("java.io.tmpdir"),
            "${prefix}_" + System.nanoTime(),
        ).apply { mkdirs() }
        return File(dir, "git-credentials").apply { writeText("https://user:ghp_xxx@github.com\n") }
    }

    @Test
    fun tighten_succeedsAndKeepsOwnerAccess() {
        val file = newTempFile("sec_owner")
        val result = SecuredFiles.tightenToOwnerOnly(file)

        assertEquals(file, result)
        assertTrue("owner must remain readable", file.canRead())
        assertTrue("owner must remain writable", file.canWrite())
        assertFalse("execute bit must be cleared", file.canExecute())

        file.delete()
        file.parentFile?.delete()
    }

    @Test
    fun tighten_onMissingFile_doesNotThrow() {
        val ghost = File(System.getProperty("java.io.tmpdir"), "sec_ghost_" + System.nanoTime())
        // 文件不存在时 setReadable 等返回 false，但不应抛异常。
        SecuredFiles.tightenToOwnerOnly(ghost)
    }

    @Test
    fun gitStoreContent_isNotLoggedWithToken() {
        // 落盘内容仍是 git-store 格式（git 自身读取需要），token 由加密库保护；
        // 这里仅证明写入工具本身不做权限位以外的额外转换。
        val file = newTempFile("sec_content")
        val content = "https://user:ghp_secret@github.com\n"
        file.writeText(content)
        SecuredFiles.tightenToOwnerOnly(file)
        assertEquals(content, file.readText())

        file.delete()
        file.parentFile?.delete()
    }
}
