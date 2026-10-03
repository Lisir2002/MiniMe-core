package com.mini.me_core.feature.browser.domain.antidetect

import kotlinx.serialization.json.JsonObject

/**
 * 反爬信号检测器。
 *
 * 分析浏览器操作的返回结果，识别网站反爬系统触发的各种信号。
 * 检测到的信号会传递给 [AntidetectController] 进行状态更新和自适应调整。
 *
 * 信号类型：
 * - captcha_detected：检测到验证码（图形/滑块/点选/文字）
 * - http_403：返回 403 Forbidden
 * - http_429：返回 429 Too Many Requests
 * - http_503：返回 503 Service Unavailable
 * - block_page：检测到拦截页（Cloudflare/Akamai/自定义）
 * - ip_banned：IP 被封禁
 * - page_blank：页面空白（可能被拦截）
 * - slow_response：响应异常缓慢
 * - content_mismatch：返回内容与预期不符
 * - login_required：突然要求登录
 */
object SignalDetector {

    /**
     * 反爬信号。
     */
    data class Signal(
        /** 信号类型 */
        val type: String,
        /** 严重程度：low / medium / high / critical */
        val severity: String,
        /** 发生信号的页面 URL */
        val url: String,
        /** 详细信息（JSON） */
        val details: JsonObject? = null,
        /** 检测时间戳 */
        val timestamp: Long = System.currentTimeMillis(),
    ) {
        /** 信号描述（用于日志和 UI） */
        val description: String
            get() = when (type) {
                "captcha_detected" -> "检测到验证码"
                "http_403" -> "返回 403 Forbidden"
                "http_429" -> "返回 429 请求过多"
                "http_503" -> "返回 503 服务不可用"
                "block_page" -> "检测到拦截页"
                "ip_banned" -> "IP 被封禁"
                "page_blank" -> "页面空白"
                "slow_response" -> "响应异常缓慢"
                "content_mismatch" -> "返回内容与预期不符"
                "login_required" -> "要求登录"
                else -> type
            }
    }

    // ===== 拦截页关键词库 =====

    /** Cloudflare 拦截页关键词 */
    private val CLOUDFLARE_KEYWORDS = listOf(
        "Checking your browser",
        "Please wait while your request is being verified",
        "Attention Required",
        "Cloudflare",
        "cf-chl",
        "challenge-platform",
        "Just a moment",
        "Verify you are human",
    )

    /** Akamai 拦截页关键词 */
    private val AKAMAI_KEYWORDS = listOf(
        "Access Denied",
        "You don't have permission to access",
        "Reference #",
        "akamai",
        "Pardon Our Interruption",
    )

    /** 通用拦截页关键词 */
    private val GENERIC_BLOCK_KEYWORDS = listOf(
        "访问被拒绝",
        "请求过于频繁",
        "操作过于频繁",
        "请稍后再试",
        "系统检测到异常",
        "验证失败",
        "安全验证",
        "人机验证",
        "行为验证",
        "forbidden",
        "access denied",
        "blocked",
        "rate limit",
        "too many requests",
    )

    /** 验证码关键词 */
    private val CAPTCHA_KEYWORDS = listOf(
        "captcha",
        "recaptcha",
        "hcaptcha",
        "geetest",
        "验证码",
        "滑动验证",
        "点选验证",
        "图形验证",
        "请完成验证",
        "请输入验证码",
        "verify you are human",
        "i'm not a robot",
    )

    /** IP 封禁关键词 */
    private val IP_BANNED_KEYWORDS = listOf(
        "ip has been banned",
        "ip address blocked",
        "your ip has been",
        "ip 被封",
        "ip 已被封禁",
        "ip 地址被禁止",
        "该 ip 已被",
    )

    // ===== 检测方法 =====

    /**
     * 检测 HTTP 状态码相关信号。
     *
     * @param statusCode HTTP 状态码
     * @param url 请求 URL
     * @return 检测到的信号（无则返回 null）
     */
    fun detectStatusCode(statusCode: Int, url: String): Signal? = when (statusCode) {
        403 -> Signal("http_403", "high", url)
        429 -> Signal("http_429", "medium", url)
        503 -> Signal("http_503", "medium", url)
        502 -> Signal("http_503", "medium", url) // 502 也可能是反爬
        else -> null
    }

    /**
     * 检测页面内容相关信号。
     *
     * @param pageText 页面文本内容
     * @param url 页面 URL
     * @param expectedContent 预期内容（可选，用于 content_mismatch 检测）
     * @return 检测到的信号列表
     */
    fun detectPageContent(
        pageText: String,
        url: String,
        expectedContent: String? = null,
    ): List<Signal> {
        val signals = mutableListOf<Signal>()
        val lowerText = pageText.lowercase()

        // 1. 检测验证码
        if (CAPTCHA_KEYWORDS.any { lowerText.contains(it.lowercase()) }) {
            signals.add(Signal("captcha_detected", "high", url))
        }

        // 2. 检测 Cloudflare 拦截
        if (CLOUDFLARE_KEYWORDS.any { lowerText.contains(it.lowercase()) }) {
            signals.add(Signal("block_page", "high", url))
        }

        // 3. 检测 Akamai 拦截
        if (AKAMAI_KEYWORDS.any { lowerText.contains(it.lowercase()) }) {
            signals.add(Signal("block_page", "high", url))
        }

        // 4. 检测通用拦截
        if (GENERIC_BLOCK_KEYWORDS.any { lowerText.contains(it.lowercase()) }) {
            // 如果已经检测到 Cloudflare/Akamai，就不重复添加
            if (signals.none { it.type == "block_page" }) {
                signals.add(Signal("block_page", "medium", url))
            }
        }

        // 5. 检测 IP 封禁
        if (IP_BANNED_KEYWORDS.any { lowerText.contains(it.lowercase()) }) {
            signals.add(Signal("ip_banned", "critical", url))
        }

        // 6. 检测页面空白（文本内容极少）
        if (pageText.trim().length < 50 && !lowerText.contains("<html")) {
            signals.add(Signal("page_blank", "medium", url))
        }

        // 7. 检测内容不匹配
        if (expectedContent != null && expectedContent.isNotEmpty()) {
            if (!lowerText.contains(expectedContent.lowercase())) {
                signals.add(Signal("content_mismatch", "low", url))
            }
        }

        // 8. 检测突然要求登录
        if (lowerText.contains("login") || lowerText.contains("sign in") ||
            lowerText.contains("登录") || lowerText.contains("登入")) {
            // 只有当页面主要内容是登录表单时才标记
            if (pageText.length < 2000 && (lowerText.contains("password") || lowerText.contains("密码"))) {
                signals.add(Signal("login_required", "low", url))
            }
        }

        return signals
    }

    /**
     * 检测响应时间相关信号。
     *
     * @param responseTimeMs 响应时间（毫秒）
     * @param url 请求 URL
     * @param normalTimeMs 正常响应时间基准（毫秒）
     * @return 检测到的信号（无则返回 null）
     */
    fun detectResponseTime(
        responseTimeMs: Long,
        url: String,
        normalTimeMs: Long = 3000,
    ): Signal? {
        // 响应时间超过正常基准的 3 倍
        if (responseTimeMs > normalTimeMs * 3 && responseTimeMs > 5000) {
            return Signal("slow_response", "low", url)
        }
        return null
    }

    /**
     * 综合检测：分析一次浏览器操作的完整结果。
     *
     * @param statusCode HTTP 状态码（可选）
     * @param pageText 页面文本（可选）
     * @param responseTimeMs 响应时间（可选）
     * @param url 页面 URL
     * @param expectedContent 预期内容（可选）
     * @return 检测到的所有信号
     */
    fun detect(
        statusCode: Int? = null,
        pageText: String? = null,
        responseTimeMs: Long? = null,
        url: String = "",
        expectedContent: String? = null,
    ): List<Signal> {
        val signals = mutableListOf<Signal>()

        statusCode?.let { detectStatusCode(it, url)?.let { s -> signals.add(s) } }
        pageText?.let { signals.addAll(detectPageContent(it, url, expectedContent)) }
        responseTimeMs?.let { detectResponseTime(it, url)?.let { s -> signals.add(s) } }

        // 去重（同类型信号只保留最严重的）
        return signals
            .groupBy { it.type }
            .mapValues { (_, list) -> list.maxByOrNull { severityWeight(it.severity) }!! }
            .values
            .sortedByDescending { severityWeight(it.severity) }
    }

    /**
     * 检查页面是否包含验证码元素（通过 DOM 特征）。
     *
     * @param html HTML 内容
     * @return 是否检测到验证码
     */
    fun hasCaptchaElement(html: String): Boolean {
        val lower = html.lowercase()
        return lower.contains("g-recaptcha") ||
            lower.contains("h-captcha") ||
            lower.contains("geetest") ||
            lower.contains("captcha") ||
            lower.contains("tcaptcha") ||
            lower.contains("verify-slide") ||
            lower.contains("滑块") ||
            lower.contains("captcha_image")
    }

    /**
     * 检查是否为拦截页（综合判断）。
     */
    fun isBlockPage(statusCode: Int?, pageText: String?): Boolean {
        if (statusCode in listOf(403, 429, 503)) return true
        pageText?.let {
            val signals = detectPageContent(it, "")
            if (signals.any { it.type in listOf("block_page", "ip_banned") }) return true
        }
        return false
    }

    // ===== 辅助函数 =====

    /** 严重程度权重（用于排序和去重） */
    private fun severityWeight(severity: String): Int = when (severity) {
        "critical" -> 4
        "high" -> 3
        "medium" -> 2
        "low" -> 1
        else -> 0
    }

    /**
     * 获取所有支持的信号类型列表。
     */
    val supportedSignalTypes: List<String> = listOf(
        "captcha_detected",
        "http_403",
        "http_429",
        "http_503",
        "block_page",
        "ip_banned",
        "page_blank",
        "slow_response",
        "content_mismatch",
        "login_required",
    )
}
