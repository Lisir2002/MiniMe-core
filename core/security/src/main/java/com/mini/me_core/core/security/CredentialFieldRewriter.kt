package com.mini.me_core.core.security

/**
 * DEK 轮换时的「加密字段重写器」扩展点。
 *
 * 背景：[CredentialEncryptor] 采用 MasterKey(Android Keystore) → wrap DEK → AES-256-GCM(data)
 * 双层加密。当轮换生成新 DEK 时，**所有用旧 DEK 加密的存量字段都必须先解密成明文、再用新 DEK
 * 重新加密回写**，否则切到新 DEK 后旧密文将永久无法解密（用户 API Key / Git token / SSH 密码
 * 全部失效）。
 *
 * 各数据域（AIProvider / GitCredential / 远程连接密码 / 代理 secret / KV 密码……）各自实现
 * 本接口，由 Hilt multi-binding 聚合注入 [CredentialEncryptor]。轮换流程：
 *
 *  1. 收集阶段：轮换尚未切换 DEK，此时 [reencrypt] 内部用「旧 DEK 解密 → 新 DEK 加密」。
 *  2. 全部重写器成功后，才把新 DEK 落库并设为生效；任一阶段失败则整体回滚，旧 DEK 继续生效，
 *     存量数据不受影响（绝不把用户留在「换了 DEK 但旧密文解不开」的状态）。
 *
 * 若任一域尚未接入本接口，轮换将**拒绝切换 DEK**并返回失败——宁可少轮换，也不损坏凭据。
 */
interface CredentialFieldRewriter {

    /** 本重写器负责的域名（仅用于审计日志与报告）。 */
    val domain: String

    /**
     * 遍历本域所有「V2:」前缀加密字段：对每条密文调用 [reencrypt] 得到新密文并回写。
     *
     * [reencrypt] 入参为旧密文，返回用新 DEK 重新加密后的密文；入参为空串或非 V2 密文时
     * 应原样返回、跳过。实现必须保证：回写只改加密列，不触碰其他业务字段与状态
     * （如 isActive / isDefault / 时间戳）。
     *
     * @return 成功重写的字段条数。
     */
    suspend fun reencryptAll(reencrypt: suspend (String) -> String): Int
}
