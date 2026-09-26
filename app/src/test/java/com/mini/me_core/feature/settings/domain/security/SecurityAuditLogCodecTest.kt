package com.mini.me_core.feature.settings.domain.security

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class SecurityAuditLogCodecTest {

    @Test
    fun emptyDecodesToEmpty() {
        assertTrue(SecurityAuditLogCodec.decode(null).isEmpty())
        assertTrue(SecurityAuditLogCodec.decode("").isEmpty())
    }

    @Test
    fun roundTrip_keepsEntries() {
        val entries = listOf(
            SecurityAuditEntry(SecurityAuditEntry.ACTION_KEY_ROTATE, true, "v3", 1000L),
            SecurityAuditEntry(SecurityAuditEntry.ACTION_BIOMETRIC, false, "failed", 2000L),
        )
        val raw = SecurityAuditLogCodec.encode(entries)
        val decoded = SecurityAuditLogCodec.decode(raw)
        assertEquals(2, decoded.size)
        assertEquals(SecurityAuditEntry.ACTION_KEY_ROTATE, decoded[0].action)
        assertEquals(false, decoded[1].success)
    }

    @Test
    fun append_newestFirst() {
        val old = listOf(
            SecurityAuditEntry("OLD", true, null, 1000L),
        )
        val merged = SecurityAuditLogCodec.append(
            old,
            SecurityAuditEntry("NEW", true, null, 5000L)
        )
        assertEquals(2, merged.size)
        assertEquals("NEW", merged[0].action)
    }

    @Test
    fun append_capsAtMaxEntries() {
        val old = (1..SecurityAuditEntry.MAX_ENTRIES).map {
            SecurityAuditEntry("OLD$it", true, null, it.toLong())
        }
        val merged = SecurityAuditLogCodec.append(
            old,
            SecurityAuditEntry("NEW", true, null, 999_999L)
        )
        assertEquals(SecurityAuditEntry.MAX_ENTRIES, merged.size)
        // 最新的 NEW 排在最前
        assertEquals("NEW", merged[0].action)
    }

    @Test
    fun corruptJsonFallsBackToEmpty() {
        assertTrue(SecurityAuditLogCodec.decode("{not valid json").isEmpty())
    }
}
