package com.mini.me_core.feature.agent.domain.container.bridge

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * [BridgeTokenFactory] 一次性令牌单测：两次会话令牌不同、非空、长度足够（256 bit 熵）。
 */
class BridgeTokenFactoryTest {

    @Test
    fun `two sessions produce different tokens`() {
        val t1 = BridgeTokenFactory.generate()
        val t2 = BridgeTokenFactory.generate()
        assertNotEquals(t1, t2)
        assertTrue(t1.isNotBlank())
        assertTrue(t2.isNotBlank())
    }

    @Test
    fun `token length reflects 32 random bytes base64url`() {
        val t = BridgeTokenFactory.generate()
        // 32 字节 base64url 无填充 = 43 字符
        assertEquals(43, t.length)
        assertTrue(t.matches(Regex("[A-Za-z0-9_-]+")))
    }

    @Test
    fun `many tokens are unique`() {
        val tokens = (1..50).map { BridgeTokenFactory.generate() }.toSet()
        assertEquals(50, tokens.size)
    }

    @Test
    fun `token is not a static placeholder`() {
        assertFalse(BridgeTokenFactory.generate().isBlank())
    }
}
