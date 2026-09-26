package com.mini.me_core.feature.agent.domain.prompt

import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Test

/**
 * NormFlowStatsRepository 单测：累计注入 token 估算（字符数 / 4）。
 *
 * 验收对照：
 * - 字符数按 /4 累加；
 * - 非正字符数忽略；
 * - clear() 归零。
 */
class NormFlowStatsRepositoryTest {

    private val repo = NormFlowStatsRepository()

    @Test
    fun `recordInjection accumulates chars divided by 4`() = runBlocking {
        repo.recordInjection(400) // 100 tokens
        repo.recordInjection(120) // 30 tokens
        assertEquals(130, repo.injectTokenCount.first())
    }

    @Test
    fun `recordInjection floors integer division`() = runBlocking {
        repo.recordInjection(10) // 10/4 = 2
        assertEquals(2, repo.injectTokenCount.first())
    }

    @Test
    fun `recordInjection ignores non-positive char count`() = runBlocking {
        repo.recordInjection(0)
        repo.recordInjection(-50)
        assertEquals(0, repo.injectTokenCount.first())
    }

    @Test
    fun `clear resets accumulated tokens`() = runBlocking {
        repo.recordInjection(800)
        assertEquals(200, repo.injectTokenCount.first())
        repo.clear()
        assertEquals(0, repo.injectTokenCount.first())
    }
}
