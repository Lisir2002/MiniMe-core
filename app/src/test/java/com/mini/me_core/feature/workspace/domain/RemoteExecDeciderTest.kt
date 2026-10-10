package com.mini.me_core.feature.workspace.domain

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * 远程写/命令结果判定单测：非零退出必须上抛类型化异常（不能仅 warn 当成功），
 * stderr 截断为脱敏片段。
 */
class RemoteExecDeciderTest {

    @Test
    fun zeroExitIsSuccess() {
        // exitCode=0 不抛
        RemoteExecDecider.requireWriteSuccess(0, "", "/remote/f")
        RemoteExecDecider.requireSuccess(0, "", "mkdir")
    }

    @Test
    fun nonZeroWriteThrowsTypedException() {
        val e = runCatching {
            RemoteExecDecider.requireWriteSuccess(1, "Permission denied\n", "/remote/f")
        }.exceptionOrNull()
        assertTrue(e is RemoteWriteFailedException)
        e as RemoteWriteFailedException
        assertEquals(1, e.exitCode)
        assertEquals("/remote/f", e.remotePath)
        assertEquals("Permission denied", e.stderr)
    }

    @Test
    fun nonZeroCommandThrowsTypedException() {
        val e = runCatching {
            RemoteExecDecider.requireSuccess(2, "no such dir", "远程创建目录失败: /x")
        }.exceptionOrNull()
        assertTrue(e is RemoteCommandFailedException)
        e as RemoteCommandFailedException
        assertEquals(2, e.exitCode)
    }

    @Test
    fun stderrIsTruncatedAndTrimmed() {
        val long = "x".repeat(1000)
        val tail = RemoteExecDecider.tail(long)
        assertTrue(tail.length <= RemoteExecDecider.TAIL_LIMIT)
        assertEquals("abc", RemoteExecDecider.tail("  abc\n"))
    }
}
