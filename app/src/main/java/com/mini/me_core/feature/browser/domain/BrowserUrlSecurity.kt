package com.mini.me_core.feature.browser.domain

import com.mini.me_core.core.util.FileLogger

/**
 * WebView 加载安全控制：敏感域名黑名单校验与操作审计日志。
 *
 * 黑名单覆盖常见钓鱼 / 恶意软件 / 凭据窃取站点的域名模式。命中时阻断导航并记录日志，
 * 避免用户或自动化脚本被诱导到高风险站点。
 */
internal object BrowserUrlSecurity {

    private const val TAG = "BrowserUrlSecurity"

    /** 敏感域名黑名单：域名子串匹配（小写）。 */
    private val BLOCKED_HOST_KEYWORDS = listOf(
        "login-secure-verify",
        "account-verify-center",
        "secure-signin-",
        "update-account-now",
        "wallet-connect-phish",
        "metamask-phish",
        "airdrop-claim-free",
        "free-nft-claim",
        "verify-wallet-now",
        "seed-phrase-input",
    )

    /** 不允许 WebView 直接导航的危险 Scheme。 */
    private val BLOCKED_SCHEMES = setOf("file", "javascript", "vbscript")

    /**
     * 判断 URL 是否应被阻断。命中黑名单域名或危险 scheme 返回 true。
     */
    fun isBlocked(url: String?): Boolean {
        if (url.isNullOrBlank()) return false
        val lower = url.lowercase()
        // 危险 scheme 阻断
        for (scheme in BLOCKED_SCHEMES) {
            if (lower.startsWith("$scheme:")) {
                FileLogger.w(TAG, "阻断危险 scheme: $url")
                return true
            }
        }
        val host = runCatching {
            android.net.Uri.parse(url).host?.lowercase()
        }.getOrNull() ?: return false
        for (kw in BLOCKED_HOST_KEYWORDS) {
            if (host.contains(kw)) {
                FileLogger.w(TAG, "命中敏感域名黑名单 host=$host keyword=$kw url=$url")
                return true
            }
        }
        return false
    }

    /** 记录一次页面导航。 */
    fun logNavigation(url: String?) {
        FileLogger.i(TAG, "页面导航: $url")
    }

    /** 记录一次文件下载。 */
    fun logDownload(url: String?, mimeType: String?) {
        FileLogger.i(TAG, "文件下载: url=$url mime=$mimeType")
    }

    /** 记录一次 JS 执行。 */
    fun logJsExecution(snippet: String?) {
        val preview = snippet?.trim()?.take(120).orEmpty()
        FileLogger.i(TAG, "JS 执行: $preview")
    }

    /** 记录一次 JS 弹窗（alert/confirm/prompt）。 */
    fun logJsDialog(type: String, message: String?) {
        FileLogger.i(TAG, "JS 弹窗 type=$type msg=${message?.take(120)}")
    }
}
