package com.mini.me_core.feature.settings.domain.security

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class SecurityScoreCalculatorTest {

    @Test
    fun allSecure_scoreIs100() {
        val result = SecurityScoreCalculator.calculate(
            SecurityScoreCalculator.Inputs(
                dbEncrypted = true,
                biometricEnabled = true,
                secureScreenEnabled = true,
                keyRotatedWithin90Days = true,
                noEmergencyUnlockHistory = true,
            )
        )
        assertEquals(100, result.score)
        assertTrue(result.risks.isEmpty())
        assertEquals(SecurityScoreCalculator.Result.Level.GOOD, result.level)
    }

    @Test
    fun noneSecure_scoreIs0() {
        val result = SecurityScoreCalculator.calculate(
            SecurityScoreCalculator.Inputs(
                dbEncrypted = false,
                biometricEnabled = false,
                secureScreenEnabled = false,
                keyRotatedWithin90Days = false,
                noEmergencyUnlockHistory = false,
            )
        )
        assertEquals(0, result.score)
        assertEquals(5, result.risks.size)
        assertEquals(SecurityScoreCalculator.Result.Level.CRITICAL, result.level)
    }

    @Test
    fun weights_matchSpec() {
        // 只开数据库加密 = 30
        val onlyDb = SecurityScoreCalculator.calculate(
            SecurityScoreCalculator.Inputs(
                dbEncrypted = true,
                biometricEnabled = false,
                secureScreenEnabled = false,
                keyRotatedWithin90Days = false,
                noEmergencyUnlockHistory = false,
            )
        )
        assertEquals(30, onlyDb.score)
        // 只开生物识别 = 20
        val onlyBio = SecurityScoreCalculator.calculate(
            SecurityScoreCalculator.Inputs(
                dbEncrypted = false,
                biometricEnabled = true,
                secureScreenEnabled = false,
                keyRotatedWithin90Days = false,
                noEmergencyUnlockHistory = false,
            )
        )
        assertEquals(20, onlyBio.score)
        // 只开防截图 = 15
        val onlyScreen = SecurityScoreCalculator.calculate(
            SecurityScoreCalculator.Inputs(
                dbEncrypted = false,
                biometricEnabled = false,
                secureScreenEnabled = true,
                keyRotatedWithin90Days = false,
                noEmergencyUnlockHistory = false,
            )
        )
        assertEquals(15, onlyScreen.score)
        // 只新鲜密钥 = 15
        val onlyFresh = SecurityScoreCalculator.calculate(
            SecurityScoreCalculator.Inputs(
                dbEncrypted = false,
                biometricEnabled = false,
                secureScreenEnabled = false,
                keyRotatedWithin90Days = true,
                noEmergencyUnlockHistory = false,
            )
        )
        assertEquals(15, onlyFresh.score)
        // 无紧急记录 = 20
        val onlyNoEmer = SecurityScoreCalculator.calculate(
            SecurityScoreCalculator.Inputs(
                dbEncrypted = false,
                biometricEnabled = false,
                secureScreenEnabled = false,
                keyRotatedWithin90Days = false,
                noEmergencyUnlockHistory = true,
            )
        )
        assertEquals(20, onlyNoEmer.score)
    }

    @Test
    fun riskTargets_matchMissingItems() {
        val result = SecurityScoreCalculator.calculate(
            SecurityScoreCalculator.Inputs(
                dbEncrypted = false,
                biometricEnabled = true,
                secureScreenEnabled = false,
                keyRotatedWithin90Days = true,
                noEmergencyUnlockHistory = true,
            )
        )
        val targets = result.risks.map { it.target }.toSet()
        assertTrue(targets.contains(SecurityScoreCalculator.RiskTarget.DB_ENCRYPTION))
        assertTrue(targets.contains(SecurityScoreCalculator.RiskTarget.SECURE_SCREEN))
        assertEquals(2, result.risks.size)
    }

    @Test
    fun keyFreshBoundary_exactly90DaysIsFresh() {
        val now = 100_000_000_000L
        val delta = SecurityScoreCalculator.KEY_ROTATION_FRESH_MS
        assertTrue(SecurityScoreCalculator.Inputs.isKeyFresh(now - delta, now))
    }

    @Test
    fun keyFresh_justOver90DaysIsStale() {
        val now = 100_000_000_000L
        val delta = SecurityScoreCalculator.KEY_ROTATION_FRESH_MS + 1
        assertFalse(SecurityScoreCalculator.Inputs.isKeyFresh(now - delta, now))
    }

    @Test
    fun keyFresh_neverRotatedIsStale() {
        assertFalse(SecurityScoreCalculator.Inputs.isKeyFresh(0L, 10_000L))
    }

    @Test
    fun levelThresholds() {
        val good = SecurityScoreCalculator.calculate(
            SecurityScoreCalculator.Inputs(true, true, true, true, true)
        )
        assertEquals(SecurityScoreCalculator.Result.Level.GOOD, good.level)

        val warning = SecurityScoreCalculator.calculate(
            // 30 + 20 = 50
            SecurityScoreCalculator.Inputs(true, true, false, false, false)
        )
        assertEquals(50, warning.score)
        assertEquals(SecurityScoreCalculator.Result.Level.WARNING, warning.level)

        val critical = SecurityScoreCalculator.calculate(
            SecurityScoreCalculator.Inputs(true, false, false, false, false)
        )
        assertEquals(30, critical.score)
        assertEquals(SecurityScoreCalculator.Result.Level.CRITICAL, critical.level)
    }
}
