package com.mini.me_core.feature.agent.domain.execution.tool.browser

/**
 * 浏览器错误分类（行为保持自 [BrowserAgentTool] 原样搬迁）。
 *
 * 根据异常类型与消息判断具体原因，返回 (用户可读消息, 错误码)，
 * 帮助模型区分"服务器断开"和"元素找不到"，避免盲目重试形成死循环。
 * 判定字符串与错误码一字未改，仅做机械搬迁。
 */
internal class BrowserToolErrorClassifier {

    /**
     * 浏览器错误分类：根据异常类型和消息判断具体原因，
     * 返回 (用户可读消息, 错误码)。帮助模型区分"服务器断开"和"元素找不到"，
     * 避免盲目重试形成死循环。
     */
    @Suppress("UNUSED_PARAMETER") // 行为保持搬迁：action 原即未参与判定，签名与调用保持不变
    fun classifyBrowserError(e: Exception, action: String): Pair<String, String> {
        val msg = e.message ?: ""
        val lowerMsg = msg.lowercase()

        return when {
            // 连接被拒绝：本地服务器或目标服务已停止
            lowerMsg.contains("connection refused") || lowerMsg.contains("econnrefused") ->
                "连接被拒绝：目标服务器可能已停止运行（如本地开发服务已断开）。" +
                    "请确认服务是否仍在运行，或调用 navigate 检查页面状态。" to "CONNECTION_REFUSED"

            // DNS 解析失败
            lowerMsg.contains("unable to resolve host") || lowerMsg.contains("nodename nor servname") ->
                "DNS 解析失败：无法解析目标主机名。请检查 URL 是否正确，或网络连接是否正常。" to "DNS_FAILURE"

            // 连接超时
            lowerMsg.contains("connection timed out") || lowerMsg.contains("etimedout") ->
                "连接超时：目标服务器响应过慢或不可达。建议稍后重试，或检查网络/代理设置。" to "CONNECTION_TIMEOUT"

            // 页面无响应 / WebView 相关错误
            lowerMsg.contains("webview") || lowerMsg.contains("page not responding") ||
                lowerMsg.contains("javascript interface") || lowerMsg.contains("evaluatejavascript") ->
                "页面无响应：WebView 可能已卡死。建议调用 navigate 或 reload 重新加载页面。" to "PAGE_UNRESPONSIVE"

            // 元素找不到（业务层面的错误，通常可恢复）
            lowerMsg.contains("not found") || lowerMsg.contains("no such element") ||
                lowerMsg.contains("unable to locate") ->
                "元素未找到：页面中不存在指定元素。可能是页面尚未加载完成、元素已变化，或定位描述不准确。" +
                    "建议先调用 snapshot 查看当前页面结构，再调整元素定位。" to "ELEMENT_NOT_FOUND"

            // 通用错误
            else ->
                "浏览器操作失败: $msg" to "UNKNOWN"
        }
    }
}
