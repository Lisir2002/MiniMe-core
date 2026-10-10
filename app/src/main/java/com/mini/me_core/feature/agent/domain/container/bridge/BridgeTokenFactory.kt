package com.mini.me_core.feature.agent.domain.container.bridge

import java.security.SecureRandom

/**
 * 桥接一次性令牌生成（纯逻辑，可单测）：每次会话生成 32 字节（256 bit）
 * SecureRandom 随机量，base64url 无填充编码。两次调用必然不同、非空、熵充足。
 */
object BridgeTokenFactory {

    const val TOKEN_BYTES = 32

    fun generate(random: SecureRandom = SecureRandom()): String {
        val bytes = ByteArray(TOKEN_BYTES).also { random.nextBytes(it) }
        return java.util.Base64.getUrlEncoder().withoutPadding().encodeToString(bytes)
    }
}
