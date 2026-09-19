package com.mini.me_core.core.util

/**
 * 统一日志脱敏管线：纯函数 (String) -> String，可单测。
 *
 * 设计原则：只替换敏感片段本身，保留前后上下文（URL host、JSON 其余字段不动），
 * 便于排障。落盘日志（FileLogger）与对话历史（AILogger.redactLargeMedia 之后）
 * 统一过 [redact]。
 *
 * 规则：
 *  (a) URL 内嵌凭据 https://user:password@host -> https://user:[redacted]@host
 *  (b) query 参数 token=/api_key= /key= 的值
 *  (c) Authorization: Bearer xxx / X-API-Key: xxx 等请求头
 *  (d) sk-/ghp_/gho_/ghs_/xoxb-/AKIA/AIza 前缀后 20+ 字符
 *  (e) PEM 私钥整段 -> [ssh key redacted]
 */
object RedactionPipeline {

    private val URL_CRED = Regex("(https?://[^\\s/:@]+:)[^@\\s/]+(@)")
    private val TOKEN_PARAM = Regex("([?&](?:token|api_key|apikey|key|access_token)=)[^&#\\s]+", RegexOption.IGNORE_CASE)
    private val BEARER = Regex("(?i)(authorization\\s*:\\s*bearer\\s+)\\S+")
    private val API_KEY_HEADER = Regex("(?i)(x-api-key\\s*:\\s*)\\S+")
    private val PREFIX_SECRET = Regex("\\b(sk-|ghp_|gho_|ghs_|xoxb-|AKIA|AIza)[A-Za-z0-9_\\-]{16,}")
    private val PEM = Regex("-----BEGIN [A-Z ]*PRIVATE KEY-----[\\s\\S]*?-----END [A-Z ]*PRIVATE KEY-----")

    /** 自定义敏感词（运行期从 KVStore 注入，重启生效）。 */
    @Volatile
    private var customTerms: List<Regex> = emptyList()

    fun setCustomTerms(terms: List<String>) {
        customTerms = terms.filter { it.isNotBlank() }.map { Regex(Regex.escape(it)) }
    }

    fun redact(input: String): String {
        var out = input
        out = PEM.replace(out, "[ssh key redacted]")
        out = URL_CRED.replace(out, "$1[redacted]$2")
        out = TOKEN_PARAM.replace(out, "$1[redacted]")
        out = BEARER.replace(out, "$1[redacted]")
        out = API_KEY_HEADER.replace(out, "$1[redacted]")
        out = PREFIX_SECRET.replace(out) { m ->
            val pfx = m.value.takeWhile { !it.isLetterOrDigit() || it == '-' || it == '_' }
            pfx + "[redacted]"
        }
        for (r in customTerms) out = r.replace(out, "[custom-redacted]")
        return out
    }
}
