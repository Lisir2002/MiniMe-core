package com.mini.me_core.feature.browser.domain

import java.net.URI

/**
 * 浏览器安全策略（行为保持自 [BrowserController] 原样搬迁）。
 *
 * 负责导航目标的规范化与安全校验：协议白名单、危险 scheme 拦截判定、
 * 以及限流响应（429/403）提示。这里的判定字符串与阈值属于安全相关逻辑，
 * 一字未改，仅做机械搬迁。
 */
internal class BrowserSecurityPolicy {

    /** 导航协议白名单：拦截危险scheme。 */
    val allowedSchemes: Set<String> = setOf("http", "https")

    /** 规范化 URL：无协议时自动补 https://。 */
    fun normalizeUrl(url: String): String =
        if (url.matches(Regex("^[a-zA-Z][a-zA-Z0-9+.-]*://.*"))) url else "https://$url"

    /** 校验导航目标：返回 null 表示安全，否则返回拦截原因（供工具侧返回错误）。 */
    fun validateUrl(url: String): String? {
        val normalized = normalizeUrl(url)
        val scheme = runCatching { URI(normalized).scheme }.getOrNull()?.lowercase()
            ?: return "无法解析 URL：$url"
        if (scheme !in allowedSchemes) {
            return "已拦截导航：不允许的协议 '$scheme'（仅允许 http/https）。"
        }
        return null
    }

    /** 检测到限流响应（429/403）时的提示。 */
    fun rateLimitHint(statusCode: Int): String? {
        return when (statusCode) {
            429 -> "检测到 429 Too Many Requests，建议：1) 增加 set_request_interval 间隔 2) 切换 User-Agent 3) 通过代理工具切换节点"
            403 -> "检测到 403 Forbidden，可能被风控拦截，建议：1) 切换 User-Agent 2) 使用 apply_stealth 3) 通过代理工具切换 IP"
            else -> null
        }
    }
}
