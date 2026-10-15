package com.mini.me_core.feature.agent.domain.core.provider

import retrofit2.HttpException

/**
 * LLM 调用失败的归类（纯 Kotlin，无 Android 依赖，可单测）。
 *
 * 用途：把「Software caused connection abort」这类面向开发者的 OS 异常原文映射为
 * 用户可行动的类别。UI 层按类别展示友好文案（strings.xml），原文仍可展开查看。
 *
 * 与 [AgentErrorSanitizer] 互补：sanitizer 负责脱敏（路径/类名/密钥），
 * 本对象负责分类（网络/鉴权/限流/服务端/首字节超时）。
 *
 * 注意：适配器最外层 catch 会先走 `enrichWithHttpErrorBody()`，它把 [HttpException]
 * 包装成 IllegalStateException（见 HttpErrorEnricher），因此分类必须遍历 cause 链
 * 才能取到 HTTP 状态码——「先类型后文本」的顺序不可调换。
 */
enum class LlmErrorKind {
    /** 连接被中止（ECONNABORTED）：网络切换、对端 RST、watchdog 关流等。 */
    NETWORK_ABORT,

    /** 连接被重置（ECONNRESET）。 */
    NETWORK_RESET,

    /** 域名解析失败。 */
    DNS,

    /** 读写超时（含相邻数据块间隔超限）。 */
    TIMEOUT,

    /** TLS/证书握手失败。 */
    SSL,

    /** 连接被拒绝（端口未监听/防火墙拦截）。 */
    CONNECT_REFUSED,

    /** 鉴权失败（HTTP 401/403）：API Key 无效或无权限。 */
    HTTP_AUTH,

    /** 限流或额度不足（HTTP 429）。 */
    HTTP_429,

    /** 服务端错误（HTTP 5xx）。 */
    HTTP_5XX,

    /** 模型服务在 SSE 流中返回的错误事件。 */
    STREAM_ERROR,

    /** 首字节等待超时：watchdog 主动关流（区别于真实网络中断）。 */
    FIRST_BYTE_TIMEOUT,

    /** 设备离线（调用方预检命中，未实际发请求）。 */
    OFFLINE,

    /** 无法归类：UI 展示原文。 */
    UNKNOWN,
}

object LlmErrorClassifier {

    /** cause 链最大遍历深度：enrich 包装通常只多 1-2 层，4 层足够触达 HttpException。 */
    private const val MAX_CAUSE_DEPTH = 4

    /**
     * 归类异常。null 或无法识别时返回 [LlmErrorKind.UNKNOWN]（调用方展示原文）。
     */
    @Suppress("ReturnCount") // 分类器本质：按异常类型逐类直判，每类一个命中即返回，合并会丢失可读性
    fun classify(t: Throwable?): LlmErrorKind {
        if (t == null) return LlmErrorKind.UNKNOWN
        var cur: Throwable? = t
        var depth = 0
        val seen = HashSet<Throwable>()
        while (cur != null && depth < MAX_CAUSE_DEPTH && seen.add(cur)) {
            when (cur) {
                is FirstByteTimeoutException -> return LlmErrorKind.FIRST_BYTE_TIMEOUT
                is StreamApiException -> return LlmErrorKind.STREAM_ERROR
                is HttpException -> return httpKind(cur.code())
                is java.net.UnknownHostException -> return LlmErrorKind.DNS
                is java.net.ConnectException -> return LlmErrorKind.CONNECT_REFUSED
                is java.net.SocketTimeoutException -> return LlmErrorKind.TIMEOUT
                is javax.net.ssl.SSLException -> return LlmErrorKind.SSL
            }
            cur = cur.cause
            depth++
        }
        return fromMessage(t.message ?: t.toString())
    }

    private fun httpKind(code: Int): LlmErrorKind = when (code) {
        401, 403 -> LlmErrorKind.HTTP_AUTH
        429 -> LlmErrorKind.HTTP_429
        408 -> LlmErrorKind.TIMEOUT
        in 500..599 -> LlmErrorKind.HTTP_5XX
        else -> LlmErrorKind.UNKNOWN
    }

    /** 类型判定未命中时的文本兜底（大小写不敏感）。 */
    private fun fromMessage(raw: String): LlmErrorKind {
        val m = raw.lowercase()
        return when {
            m.contains("software caused connection abort") ||
                m.contains("connection abort") ||
                m.contains("connection was aborted") -> LlmErrorKind.NETWORK_ABORT
            m.contains("connection reset") || m.contains("econnreset") -> LlmErrorKind.NETWORK_RESET
            m.contains("unable to resolve host") || m.contains("unknownhost") -> LlmErrorKind.DNS
            m.contains("connection refused") || m.contains("econnrefused") -> LlmErrorKind.CONNECT_REFUSED
            m.contains("timeout") || m.contains("timed out") || m.contains("etimedout") -> LlmErrorKind.TIMEOUT
            m.contains("ssl") || m.contains("certificate") || m.contains("handshake") -> LlmErrorKind.SSL
            else -> LlmErrorKind.UNKNOWN
        }
    }
}
