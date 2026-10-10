package com.mini.me_core.feature.agent.domain.execution.tool.browser

import com.mini.me_core.feature.agent.domain.execution.tool.ToolResult
import com.mini.me_core.feature.browser.domain.BrowserController
import com.mini.me_core.feature.browser.domain.BrowserPageSnapshot
import com.mini.me_core.feature.browser.domain.SnapshotLevel
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive

/**
 * 浏览器导航工具域（行为保持自 [BrowserAgentTool] 原样搬迁）。
 *
 * 承载 navigate / view(智能查看) / back·forward·reload(doNavigation) / history。
 * 导航前的安全校验、页面就绪等待、快照级别解析均逐字保持，仍经 browserController 同一锁执行。
 */
internal class BrowserToolNav(
    private val env: BrowserToolEnvelope,
    private val browserController: BrowserController
) {

    private val json = Json { ignoreUnknownKeys = true }

    suspend fun doNavigate(args: Map<String, JsonElement>): ToolResult {
        val url = args["url"]?.jsonPrimitive?.contentOrNull ?: return ToolResult.Error("navigate 需要 url 参数", "MISSING_URL")
        browserController.validateUrl(url)?.let {
            return env.envelope("navigate", ok = false, error = it, recoverable = true, summary = "导航被拦截：$it")
        }
        val snap = browserController.navigate(url)
        return env.envelope(
            action = "navigate",
            ok = true,
            changed = true,
            summary = "已打开 ${snap.url.ifBlank { url }}（${snap.title.ifBlank { "(无标题)" }}）",
            snapshot = env.snapshotToJson(snap, env.navLevelOf(args))
        )
    }

    /**
     * 智能页面查看：可选导航到 url，等待页面稳定，识别页面类型并按需返回对应级别快照。
     * article -> FULL（含正文）；search_results/product -> STANDARD；其余 -> STANDARD。
     */
    suspend fun doView(args: Map<String, JsonElement>): ToolResult {
        val url = args["url"]?.jsonPrimitive?.contentOrNull
        if (url != null) {
            browserController.validateUrl(url)?.let {
                return env.envelope("view", ok = false, error = it, recoverable = true, summary = "导航被拦截：$it")
            }
            browserController.navigate(url)
        }
        runCatching { browserController.waitForPageReady(3000) }
        val structured = browserController.extract(null, "text", autoScroll = false, structured = true)
        val pageType = runCatching {
            json.parseToJsonElement(structured).jsonObject["page_type"]?.jsonPrimitive?.contentOrNull ?: "unknown"
        }.getOrNull() ?: "unknown"
        val level = when (pageType) {
            "article" -> SnapshotLevel.FULL
            "search_results", "product" -> SnapshotLevel.STANDARD
            else -> SnapshotLevel.STANDARD
        }
        val snap = browserController.snapshot(level)
        return env.envelope(
            action = "view",
            ok = true,
            changed = url != null,
            summary = "页面类型: $pageType，已获取${level.name.lowercase()}级快照（${snap.elements.size}个元素）",
            snapshot = env.snapshotToJson(snap, level),
            note = structured.take(4000)
        )
    }

    /** 导航类动作统一处理（back/forward/reload）：页面完全变化，默认给 standard 快照。 */
    suspend fun doNavigation(action: String, snap: BrowserPageSnapshot): ToolResult {
        val verb = when (action) {
            "back" -> "已后退"
            "forward" -> "已前进"
            else -> "已刷新"
        }
        return env.envelope(
            action = action,
            ok = true,
            changed = true,
            summary = "${verb}到 ${snap.title.ifBlank { "(无标题)" }}（${snap.url}）",
            snapshot = env.snapshotToJson(snap, env.navLevelOf(emptyMap()))
        )
    }

    /** 动作历史查询（R2.3）：返回最近 30 条操作 + 结果摘要，避免重复操作。 */
    suspend fun doHistory(): ToolResult {
        val history = browserController.actionHistory()
        return env.envelope(
            action = "history",
            ok = true,
            summary = "最近 ${history.size} 条动作",
            note = history.joinToString("\n")
        )
    }

    private companion object {
        const val MAX_TEXT = 8000
    }
}
