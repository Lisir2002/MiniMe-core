package com.mini.me_core.feature.agent.domain.guard

import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Test

/**
 * GuardLogRepository 单测：日志环形缓冲 + Block 级累计拦截计数。
 *
 * 验收对照：
 * - isBlock=true 计入 blockCount，Advisory（false）不计；
 * - 新记录入队首；
 * - clear() 同时清空日志与计数。
 */
class GuardLogRepositoryTest {

    private val repo = GuardLogRepository()

    private fun entry(code: String) = GuardLogEntry(
        timestamp = 0L, toolName = "bash", guardId = "g", code = code, message = "m"
    )

    @Test
    fun `block count increments only on block verdicts`() = runBlocking {
        repo.log(entry("ADV_1"), isBlock = false) // advisory 不计
        repo.log(entry("BLK_1"), isBlock = true)
        repo.log(entry("BLK_2"), isBlock = true)
        assertEquals(2, repo.blockCount.first())
    }

    @Test
    fun `entries are prepended to log`() = runBlocking {
        repo.log(entry("FIRST"), isBlock = false)
        repo.log(entry("SECOND"), isBlock = false)
        val logs = repo.logsFlow.first()
        assertEquals("SECOND", logs[0].code)
        assertEquals("FIRST", logs[1].code)
    }

    @Test
    fun `clear resets both log and block count`() = runBlocking {
        repo.log(entry("BLK"), isBlock = true)
        repo.clear()
        assertEquals(0, repo.blockCount.first())
        assertEquals(0, repo.logsFlow.first().size)
    }
}
