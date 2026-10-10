package com.mini.me_core.feature.agent.domain.execution.tool.browser

import com.mini.me_core.feature.agent.domain.execution.tool.ToolResult
import com.mini.me_core.feature.browser.domain.BrowserController
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.booleanOrNull
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.intOrNull
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive

/**
 * 浏览器 DOM/快照/检查工具域（行为保持自 [BrowserAgentTool] 原样搬迁）。
 *
 * 承载 snapshot / page_text / extract / screenshot / evaluate / wait_for(_change/_network_idle)
 * / get_attribute / handle_dialog。所有读取类操作仍经 browserController 同一锁执行，
 * 快照级别、超时默认值、返回 envelope 结构逐字不变。
 */
internal class BrowserToolInspect(
    private val env: BrowserToolEnvelope,
    private val browserController: BrowserController
) {

    private val json = Json { ignoreUnknownKeys = true }

    suspend fun doSnapshot(args: Map<String, JsonElement>): ToolResult {
        val level = env.snapshotLevelOf(args)
        val autoScroll = args["auto_scroll"]?.jsonPrimitive?.booleanOrNull ?: false
        val snap = browserController.snapshot(level, autoScroll)
        return env.envelope("snapshot", ok = true, summary = env.snapshotSummary(snap), snapshot = env.snapshotToJson(snap, level))
    }

    /** 按需取正文（R2.1）：模型需要页面文本时单独取，不随快照默认返回。 */
    suspend fun doPageText(): ToolResult {
        val text = browserController.pageText().take(MAX_TEXT)
        return env.envelope("page_text", ok = true, summary = "页面正文共 ${text.length} 字（超过 ${MAX_TEXT} 字已截断）", note = text)
    }

    suspend fun doExtract(args: Map<String, JsonElement>): ToolResult {
        val selector = args["selector"]?.jsonPrimitive?.contentOrNull
        val mode = args["mode"]?.jsonPrimitive?.contentOrNull ?: "text"
        val autoScroll = args["auto_scroll"]?.jsonPrimitive?.booleanOrNull ?: false
        val structured = args["structured"]?.jsonPrimitive?.booleanOrNull ?: false
        val result = browserController.extract(selector, mode, autoScroll, structured)
        val note = result.take(MAX_TEXT)
        val summary = if (structured) {
            val pageType = runCatching {
                json.parseToJsonElement(result).jsonObject["page_type"]?.jsonPrimitive?.contentOrNull
            }.getOrNull()
            "结构化提取完成（页面类型: ${pageType ?: "unknown"}）"
        } else {
            "已按 mode=$mode 抽取数据"
        }
        return env.envelope("extract", ok = true, summary = summary, note = note)
    }

    suspend fun doScreenshot(args: Map<String, JsonElement>): ToolResult {
        val elementId = args["element_id"]?.jsonPrimitive?.contentOrNull
        val dataUrl = browserController.screenshot(elementId)
            ?: return env.envelope(
                "screenshot", ok = false, recoverable = true,
                summary = "截图失败", error = "截图失败：浏览器页面尚未渲染（请先在浏览器页打开页面）"
            )
        return env.envelope(
            action = "screenshot",
            ok = true,
            summary = "已截取${if (elementId != null) "元素 #$elementId" else "页面"}截图",
            note = "data:image/png;base64，多模态模型可直接查看",
            extra = JsonObject(mapOf("image_data_url" to JsonPrimitive(dataUrl)))
        )
    }

    suspend fun doEvaluate(args: Map<String, JsonElement>): ToolResult {
        val js = args["js"]?.jsonPrimitive?.contentOrNull ?: return ToolResult.Error("evaluate 需要 js 参数", "MISSING_JS")
        val result = browserController.evaluate(js)
        return env.envelope("evaluate", ok = true, summary = "JS 执行完成", note = result.take(MAX_TEXT))
    }

    suspend fun doWaitFor(args: Map<String, JsonElement>): ToolResult {
        val selector = args["selector"]?.jsonPrimitive?.contentOrNull
        val timeout = runCatching { args["timeout_ms"]?.jsonPrimitive?.contentOrNull?.toLong() }.getOrNull() ?: 10_000L
        val snap = browserController.waitFor(selector, timeout)
        return env.envelope(
            action = "wait_for",
            ok = true,
            changed = true,
            summary = "等待完成：${if (selector.isNullOrBlank()) "页面已就绪" else "元素 $selector 已出现"}",
            snapshot = env.snapshotToJson(snap, env.snapshotLevelOf(args))
        )
    }

    /** 事件驱动等待页面变化（R2.4）：替代轮询 snapshot，页面 DOM/网络变化即返回摘要。 */
    suspend fun doWaitForChange(args: Map<String, JsonElement>): ToolResult {
        val timeout = runCatching { args["timeout_ms"]?.jsonPrimitive?.contentOrNull?.toLong() }.getOrNull() ?: 15_000L
        val result = browserController.waitForChange(timeout)
        return if (result.changed) {
            env.envelope(
                action = "wait_for_change",
                ok = true,
                changed = true,
                summary = "页面发生变化：${result.summary.take(200)}"
            )
        } else {
            env.envelope(
                action = "wait_for_change",
                ok = true,
                changed = false,
                summary = "等待 ${timeout}ms 超时，页面无变化",
                note = "可调用 snapshot 查看当前状态，或继续其他操作"
            )
        }
    }

    /** 等待网络空闲：连续 idleMs 无在途请求即返回，超时则报告。 */
    suspend fun doWaitForNetworkIdle(args: Map<String, JsonElement>): ToolResult {
        val timeoutMs = args["timeout_ms"]?.jsonPrimitive?.intOrNull?.toLong() ?: 10000L
        val idleMs = args["idle_ms"]?.jsonPrimitive?.intOrNull?.toLong() ?: 500L
        val ok = browserController.waitForNetworkIdle(timeoutMs, idleMs)
        return env.envelope(
            action = "wait_for_network_idle",
            ok = ok,
            summary = if (ok) "网络已空闲（连续${idleMs}ms无在途请求）" else "等待网络空闲超时（${timeoutMs}ms）",
            recoverable = true
        )
    }

    suspend fun doGetAttribute(args: Map<String, JsonElement>): ToolResult {
        val id = args["element_id"]?.jsonPrimitive?.contentOrNull ?: return ToolResult.Error("get_attribute 需要 element_id", "MISSING_ELEMENT_ID")
        val attr = args["attribute"]?.jsonPrimitive?.contentOrNull ?: return ToolResult.Error("get_attribute 需要 attribute", "MISSING_ATTRIBUTE")
        val value = browserController.getAttribute(id, attr)
        return env.envelope(
            action = "get_attribute",
            ok = true,
            summary = "属性 $attr 读取完成",
            note = "$id.$attr = ${value ?: "(空)"}"
        )
    }

    suspend fun doHandleDialog(args: Map<String, JsonElement>): ToolResult {
        val accept = runCatching { args["accept"]?.jsonPrimitive?.contentOrNull?.toBoolean() }.getOrNull() ?: true
        val handled = browserController.handleDialog(accept)
        return env.envelope(
            action = "handle_dialog",
            ok = handled,
            summary = if (handled) "对话框已${if (accept) "确认" else "取消"}" else "当前没有待处理的对话框"
        )
    }

    private companion object {
        const val MAX_TEXT = 8000
    }
}
