package com.mini.me_core.feature.agent.domain.execution.workflow

/**
 * 回传给模型的异常文本净化器（纯 Kotlin，无 Android 依赖，可单测）。
 *
 * 工作流把异常 `e.message` 直接拼进 LlmError / 工具错误消息回给模型前，必须先经此净化器：
 *  - 宿主绝对路径（`/home/user/...`、`/data/user/0/...`、`/storage/...`、`/sdcard/...`、`/proc/...`）
 *    替换为 `<host-path>`，避免泄露宿主文件布局；
 *  - SQLCipher / DEK / 主密钥 / 内部类 FQN（`com.mini.me_core.xxx`、`net.sqlcipher.xxx`）等
 *    内部实现细节替换为 `<internal>`，避免把加密层/类名吐给模型被当作可操作指令；
 *  - 保留可操作的安全类别信息（网络超时、HTTP 401/403/429、磁盘满、JSON 解析失败等）。
 *
 * 与 [com.mini.me_core.core.util.LogSanitizer] 互补：LogSanitizer 负责日志里的密钥/凭据打码，
 * 本对象负责「回给模型的异常文本」的路径与内部实现打码。
 */
object AgentErrorSanitizer {

    /** 宿主绝对路径前缀（命中即整体替换为 <host-path>）。 */
    private val HOST_PATH_RE = Regex(
        """(/(?:home|data|storage|sdcard|proc|sys|vendor|system|apex)/[^\s'"`)>]+)"""
    )

    /** 内部类 FQN：com.mini.me_core.* / net.sqlcipher.* / androidx.* 等。 */
    private val INTERNAL_CLASS_RE = Regex(
        """\b(?:com|net|androidx|kotlin|kotlinx|org\.jetbrains)\.[A-Za-z0-9_.$]+"""
    )

    /** 加密层敏感关键词（大小写不敏感，整体替换）。 */
    private val CRYPTO_KEYWORDS_RE = Regex(
        """(?i)\b(?:SQLCipher|DEK|master\s+key|database\s+key|passphrase|keystore|key\s+derivation|PBKDF2)\b"""
    )

    /**
     * 净化异常文本。null/blank 原样返回。
     */
    fun sanitize(raw: String?): String {
        if (raw.isNullOrBlank()) return raw ?: ""
        var out = raw
        out = HOST_PATH_RE.replace(out, "<host-path>")
        out = INTERNAL_CLASS_RE.replace(out, "<internal>")
        out = CRYPTO_KEYWORDS_RE.replace(out, "<internal>")
        return out
    }
}
