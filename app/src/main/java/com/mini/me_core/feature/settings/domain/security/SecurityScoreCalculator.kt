package com.mini.me_core.feature.settings.domain.security

/**
 * 安全评分计算器（纯函数，可在 JVM 单测覆盖）。
 *
 * 评分权重（满分 100）：
 *  - 数据库全加密            30 分
 *  - 生物识别开启            20 分
 *  - 防截图录屏开启          15 分
 *  - 密钥 90 天内轮换        15 分
 *  - 无紧急解锁记录          20 分
 *
 * 每个未达成项同时产出一条 [SecurityRisk]，供 UI 点击跳转到对应卡片。
 */
object SecurityScoreCalculator {

    /** 密钥轮换新鲜度阈值（毫秒）：90 天。 */
    const val KEY_ROTATION_FRESH_MS: Long = 90L * 24L * 60L * 60L * 1000L

    const val WEIGHT_DB_ENCRYPTED = 30
    const val WEIGHT_BIOMETRIC = 20
    const val WEIGHT_SECURE_SCREEN = 15
    const val WEIGHT_KEY_FRESH = 15
    const val WEIGHT_NO_EMERGENCY = 20

    /** 风险项跳转目标（对应安全页内卡片标识）。 */
    enum class RiskTarget {
        /** 数据库加密卡片。 */
        DB_ENCRYPTION,

        /** 生物识别卡片。 */
        BIOMETRIC,

        /** 防截图录屏卡片。 */
        SECURE_SCREEN,

        /** 凭据密钥轮换卡片。 */
        KEY_ROTATION,

        /** 紧急解锁卡片。 */
        EMERGENCY,
    }

    /** 单项安全风险。 */
    data class SecurityRisk(
        /** 稳定标识，用于测试与跳转映射。 */
        val target: RiskTarget,
        /** 风险标题（文案走 strings.xml，这里仅作 key，UI 层映射为本地化文案）。 */
        val key: String,
    )

    /** 计算输入。所有布尔量由 ViewModel 在 IO 线程查询后传入。 */
    data class Inputs(
        val dbEncrypted: Boolean,
        val biometricEnabled: Boolean,
        val secureScreenEnabled: Boolean,
        /** 距离上次密钥轮换是否在 90 天内。lastRotatedAtMs<=0 视为从未轮换（不新鲜）。 */
        val keyRotatedWithin90Days: Boolean,
        /** 是否没有任何紧急解锁记录。 */
        val noEmergencyUnlockHistory: Boolean,
    ) {
        companion object {
            /** 由「上次轮换时间戳」+「当前时间」推导是否新鲜。 */
            fun isKeyFresh(lastRotatedAtMs: Long, nowMs: Long): Boolean {
                if (lastRotatedAtMs <= 0L) return false
                val delta = nowMs - lastRotatedAtMs
                return delta in 0L..KEY_ROTATION_FRESH_MS
            }
        }
    }

    /** 计算结果：0-100 分 + 风险列表。 */
    data class Result(
        val score: Int,
        val risks: List<SecurityRisk>,
    ) {
        /** 评分等级：高分主题色、低分警告色。 */
        val level: Level
            get() = when {
                score >= 80 -> Level.GOOD
                score >= 50 -> Level.WARNING
                else -> Level.CRITICAL
            }

        enum class Level { GOOD, WARNING, CRITICAL }
    }

    fun calculate(inputs: Inputs): Result {
        var score = 0
        val risks = mutableListOf<SecurityRisk>()

        if (inputs.dbEncrypted) {
            score += WEIGHT_DB_ENCRYPTED
        } else {
            risks += SecurityRisk(RiskTarget.DB_ENCRYPTION, "db_not_encrypted")
        }

        if (inputs.biometricEnabled) {
            score += WEIGHT_BIOMETRIC
        } else {
            risks += SecurityRisk(RiskTarget.BIOMETRIC, "biometric_off")
        }

        if (inputs.secureScreenEnabled) {
            score += WEIGHT_SECURE_SCREEN
        } else {
            risks += SecurityRisk(RiskTarget.SECURE_SCREEN, "secure_screen_off")
        }

        if (inputs.keyRotatedWithin90Days) {
            score += WEIGHT_KEY_FRESH
        } else {
            risks += SecurityRisk(RiskTarget.KEY_ROTATION, "key_stale")
        }

        if (inputs.noEmergencyUnlockHistory) {
            score += WEIGHT_NO_EMERGENCY
        } else {
            risks += SecurityRisk(RiskTarget.EMERGENCY, "emergency_history")
        }

        return Result(score = score.coerceIn(0, 100), risks = risks)
    }
}
