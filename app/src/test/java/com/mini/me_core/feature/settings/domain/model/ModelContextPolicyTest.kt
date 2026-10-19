package com.mini.me_core.feature.settings.domain.model

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * CJK-aware token 估算回归。
 *
 * 历史教训：估算曾全量按 chars/4（英文口径），中文会话被低估约 4 倍——压缩触发器
 * 与尾随预算都按错的尺度计算，真实载荷已是模型窗口数倍时应用还以为"未到阈值"，
 * 直接引发「压缩请求自身超限被 API 拒绝 → 静默失败 → 会话永远卡在超限状态」事故。
 */
class ModelContextPolicyTest {

    @Test
    fun `纯英文维持 chars4 口径`() {
        assertEquals(1, ModelContextPolicy.estimateTokens("abcd"))
        assertEquals(3, ModelContextPolicy.estimateTokens("hello world")) // 11 chars → ceil(11/4)
    }

    @Test
    fun `纯中文每字约一 token`() {
        assertEquals(4, ModelContextPolicy.estimateTokens("你好世界"))
        val long = "用户在调试安卓应用的数据库迁移逻辑".repeat(100)
        assertEquals(long.length, ModelContextPolicy.estimateTokens(long))
    }

    @Test
    fun `中英混合按字符类分别折算`() {
        // 2 个汉字（2 token）+ 8 个英文字符（2 token）= 4
        assertEquals(4, ModelContextPolicy.estimateTokens("你好abcdefgh"))
        // 全角标点按 1 token
        assertEquals(2, ModelContextPolicy.estimateTokens("，。"))
    }

    @Test
    fun `中文不再被低估四倍`() {
        val text = "中".repeat(400_000) // 40 万汉字 ≈ 40 万 token
        val estimated = ModelContextPolicy.estimateTokens(text)
        val englishHeuristic = ModelContextPolicy.estimateTokens(text.length)
        assertTrue("中文估算必须显著高于 chars/4 口径", estimated > englishHeuristic * 3)
        assertTrue("40 万汉字必须被识别为远超 128k 窗口", estimated > 128_000)
    }
}
