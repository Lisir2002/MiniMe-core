package com.mini.me_core.feature.agent.data.remote.zth

import com.mini.me_core.core.security.ZthSensitiveColumnCrypto
import com.mini.me_core.feature.agent.data.local.entity.L0SoftCompactRestoreLogEntity
import com.mini.me_core.feature.agent.data.local.entity.UserConfirmedSentinelEntity
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Entity ↔ FirestoreDto 双向映射器。
 *
 * Phase 1 约束：
 *  - 只做纯字段一一映射，不引入任何 Firestore SDK 依赖（无 @DocumentId / FirebaseFirestore）。
 *  - s_* 加密列因为 Entity 里本身就是加密后 blob（经 ZthSensitiveColumnCrypto 写入 Room），
 *    映射时「原样复制」，Phase 4 Syncer 再用 shared_sync_key 对 Dto 密文**二次加解密**。
 *    所以本 Mapper 不需要接触 ZthSensitiveColumnCrypto（注入但 Phase 1 先不调，Phase 4 校验加调用）。
 *
 * 对应 C.4.18：两次加密（手机本地 = Keystore DEK + 跨设备云同步 = shared_sync_key）
 */
@Singleton
class ZthEntityMapper @Inject constructor(
    @Suppress("unused") // Phase 4 Syncer 启用二次加密时使用；目前强制注入保证依赖图编译
    private val crypto: ZthSensitiveColumnCrypto
) {

    // ── Sentinel ───────────────────────────────────────────────────────────

    fun toDto(e: UserConfirmedSentinelEntity): ZthSentinelFirestoreDto = ZthSentinelFirestoreDto(
        id = e.id,
        sessionId = e.sessionId,
        linkageVersion = e.linkageVersion,
        chainId = e.chainId,
        chainIndex = e.chainIndex,
        cardTemplateId = e.cardTemplateId,
        triggerSubClass = e.triggerSubClass,
        planPayloadCiphertext = e.s_planPayloadCiphertext,
        userTextCiphertext = e.s_userTextCiphertext,
        cardPayloadCiphertext = e.s_cardPayloadCiphertext,
        userChoice = e.userChoice,
        swipeVerified = e.swipeVerified,
        modifiedPlanCiphertext = e.s_modifiedPlanCiphertext,
        expireAtMs = e.expireAtMs,
        rollbackFlag = e.rollbackFlag,
        createdAtMs = e.createdAtMs
    )

    fun toEntity(d: ZthSentinelFirestoreDto): UserConfirmedSentinelEntity = UserConfirmedSentinelEntity(
        id = d.id,
        sessionId = d.sessionId,
        linkageVersion = d.linkageVersion,
        chainId = d.chainId,
        chainIndex = d.chainIndex,
        cardTemplateId = d.cardTemplateId,
        triggerSubClass = d.triggerSubClass,
        s_planPayloadCiphertext = d.planPayloadCiphertext,
        s_userTextCiphertext = d.userTextCiphertext,
        s_cardPayloadCiphertext = d.cardPayloadCiphertext,
        userChoice = d.userChoice,
        swipeVerified = d.swipeVerified,
        s_modifiedPlanCiphertext = d.modifiedPlanCiphertext,
        expireAtMs = d.expireAtMs,
        rollbackFlag = d.rollbackFlag,
        createdAtMs = d.createdAtMs
    )

    // ── L0 Restore Log ─────────────────────────────────────────────────────

    fun toDto(e: L0SoftCompactRestoreLogEntity): ZthL0RestoreLogFirestoreDto =
        ZthL0RestoreLogFirestoreDto(
            id = e.id,
            sessionId = e.sessionId,
            firstMessageId = e.firstMessageId,
            lastMessageId = e.lastMessageId,
            originalRowCount = e.originalRowCount,
            tokensBefore = e.tokensBefore,
            tokensAfter = e.tokensAfter,
            compactSourceDigestCiphertext = e.s_compactSourceDigestCiphertext,
            expireAtMs = e.expireAtMs,
            restoredFlag = e.restoredFlag,
            createdAtMs = e.createdAtMs
        )

    fun toEntity(d: ZthL0RestoreLogFirestoreDto): L0SoftCompactRestoreLogEntity =
        L0SoftCompactRestoreLogEntity(
            id = d.id,
            sessionId = d.sessionId,
            firstMessageId = d.firstMessageId,
            lastMessageId = d.lastMessageId,
            originalRowCount = d.originalRowCount,
            tokensBefore = d.tokensBefore,
            tokensAfter = d.tokensAfter,
            s_compactSourceDigestCiphertext = d.compactSourceDigestCiphertext,
            expireAtMs = d.expireAtMs,
            restoredFlag = d.restoredFlag,
            createdAtMs = d.createdAtMs
        )

}
