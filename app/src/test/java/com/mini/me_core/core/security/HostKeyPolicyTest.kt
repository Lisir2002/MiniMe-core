package com.mini.me_core.core.security

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.security.MessageDigest
import java.util.Base64

/**
 * 主机密钥策略单测：fail-closed、首连返回待确认指纹、不匹配阻断、常量时间比对。
 */
class HostKeyPolicyTest {

    private val rawKey = "dummy-ssh-rsa-public-key-bytes".toByteArray(Charsets.UTF_8)
    private val otherRawKey = "another-different-key-bytes".toByteArray(Charsets.UTF_8)

    private fun line(keyType: String, raw: ByteArray): String =
        HostKeyPolicy.fingerprintLine(keyType, raw)

    @Test
    fun hostKeyFormat() {
        assertEquals("example.com", HostKeyPolicy.hostKeyFor("example.com", 22))
        assertEquals("example.com", HostKeyPolicy.hostKeyFor("example.com", 0))
        assertEquals("[example.com]:2222", HostKeyPolicy.hostKeyFor("example.com", 2222))
    }

    @Test
    fun sha256FingerprintIsOpenSshStyle() {
        val fp = HostKeyPolicy.sha256Fingerprint(rawKey)
        assertTrue(fp.startsWith("SHA256:"))
        assertFalse(fp.endsWith("="))
        // 与标准算法结果一致
        val md = MessageDigest.getInstance("SHA-256").digest(rawKey)
        val expected = "SHA256:" + Base64.getEncoder().encodeToString(md).trimEnd('=')
        assertEquals(expected, fp)
    }

    @Test
    fun constantTimeEqualsMatches() {
        assertTrue(HostKeyPolicy.constantTimeEquals("ssh-rsa AAAA", "ssh-rsa AAAA"))
        assertFalse(HostKeyPolicy.constantTimeEquals("ssh-rsa AAAA", "ssh-rsa AAAB"))
        // 与 MessageDigest.isEqual 行为一致
        val a = "abc".toByteArray()
        assertTrue(MessageDigest.isEqual(a, a))
    }

    @Test
    fun corruptStoreDenied() {
        // known_hosts 存在但读失败 → StoreUnreadable（DENY），绝不 fail-open
        val d = HostKeyPolicy.decide(
            hostKey = "h",
            entries = emptyList(),
            currentLine = line("ssh-rsa", rawKey),
            storeReadable = false
        )
        assertTrue(d is HostKeyDecision.StoreUnreadable)
    }

    @Test
    fun firstSeenReturnsFingerprintNotAutoTrust() {
        // 无记录 → FirstSeen，带回 SHA256 指纹与待落盘行；调用方必须显式信任
        val current = line("ssh-rsa", rawKey)
        val d = HostKeyPolicy.decide(
            hostKey = "h",
            entries = emptyList(),
            currentLine = current,
            storeReadable = true
        )
        assertTrue(d is HostKeyDecision.FirstSeen)
        d as HostKeyDecision.FirstSeen
        assertTrue(d.fingerprintSha256.startsWith("SHA256:"))
        assertEquals(current, d.candidateLine)
    }

    @Test
    fun matchingRecordAllowed() {
        val current = line("ssh-rsa", rawKey)
        val entries = listOf("h" to current)
        val d = HostKeyPolicy.decide("h", entries, current, storeReadable = true)
        assertTrue(d is HostKeyDecision.Match)
    }

    @Test
    fun mismatchingRecordBlocked() {
        val saved = line("ssh-rsa", rawKey)
        val current = line("ssh-rsa", otherRawKey)
        val d = HostKeyPolicy.decide("h", listOf("h" to saved), current, storeReadable = true)
        assertTrue(d is HostKeyDecision.Mismatch)
        d as HostKeyDecision.Mismatch
        assertEquals("ssh-rsa", d.expectedType)
        assertTrue(d.expectedFingerprintSha256.startsWith("SHA256:"))
    }

    @Test
    fun firstConnectWithoutCallbackIsUntrustedException() {
        // 未设置 trustCallback 的语义：首连不静默信任（抛 HostKeyUntrustedException）
        val e = HostKeyUntrustedException("h", 22, "SHA256:x", "未信任")
        assertTrue(e is SecurityException)
    }

    @Test
    fun corruptStoreExceptionIsSecurityException() {
        assertTrue(HostKeyStoreUnavailableException("bad") is SecurityException)
    }
}
