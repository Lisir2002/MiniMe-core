package com.mini.me_core.feature.update.data

/**
 * 官方安装包签名证书 SHA-256 白名单（小写 hex）。
 *
 * 自动更新安装前必须：待装 APK 的签名证书 SHA-256 命中本集合才允许调起系统安装器。
 * 白名单为空时一律拒绝安装（fail-closed），防止镜像站被篡改 / 中间人投毒后静默安装
 * 任意 APK。
 *
 * ⚠️ 维护者待办：发布第一个正式签名构建前，必须用
 *   apksigner verify --print-certs app-release.apk | grep SHA-256
 * （或 keytool -printcert -file <cert>）提取真实证书指纹并填入集合，随后把
 * [WHITELIST_FILLED] 置为 true。在真实指纹填入前，自动更新通道不会放行任何安装。
 *
 * 若存在多个签名密钥（发布 / 备用密钥、分渠道重签），全部以小写 hex 形式加入集合。
 * 比较走常量时间 [java.security.MessageDigest.isEqual]，避免时序侧信道。
 */
object UpdateSignatureWhitelist {

    /**
     * 白名单是否已由维护者填入真实指纹。false 时即使集合非空也拒绝安装（占位常量
     * 不应被误当成正式指纹）。
     *
     * TODO(maintainer): 填入真实签名证书 SHA-256 后改为 true。
     */
    const val WHITELIST_FILLED: Boolean = false

    /**
     * 官方签名证书 SHA-256 白名单（小写 hex，64 字符）。
     *
     * 占位为空集合。示例格式（请勿直接使用本示例值）：
     *   "ab12cd34ef56...e0"
     *
     * 提取命令：
     *   apksigner verify --print-certs --verbose app-release.apk
     * 取 "SHA-256 digest:" 一行冒号后内容，去掉空格、转小写。
     */
    val OFFICIAL_SIGNATURE_SHA256: Set<String> = emptySet()

    /** 判定某证书指纹（任意大小写 hex）是否命中白名单。 */
    fun matches(sha256Hex: String?): Boolean {
        if (!WHITELIST_FILLED) return false
        val normalized = sha256Hex?.trim()?.lowercase()?.replace(":", "")?.replace(" ", "")
            .orEmpty()
        if (normalized.isEmpty()) return false
        return OFFICIAL_SIGNATURE_SHA256.any { allowed ->
            java.security.MessageDigest.isEqual(
                allowed.toByteArray(),
                normalized.toByteArray()
            )
        }
    }
}
