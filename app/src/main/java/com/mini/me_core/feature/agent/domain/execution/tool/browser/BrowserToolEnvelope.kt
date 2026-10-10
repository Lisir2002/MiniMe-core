package com.mini.me_core.feature.agent.domain.execution.tool.browser

import com.mini.me_core.feature.agent.domain.execution.tool.ToolResult
import com.mini.me_core.feature.browser.domain.BrowserElement
import com.mini.me_core.feature.browser.domain.BrowserHeading
import com.mini.me_core.feature.browser.domain.BrowserPageSnapshot
import com.mini.me_core.feature.browser.domain.BrowserSnapshotDelta
import com.mini.me_core.feature.browser.domain.SnapshotLevel
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.jsonPrimitive

/**
 * 浏览器工具统一 envelope 与快照序列化集群（行为保持自 [BrowserAgentTool] 原样搬迁）。
 *
 * 承载所有动作共用的返回结构 [envelope]、增量 delta 序列化、快照分级序列化与
 * 快照级别解析。字段拼装顺序、默认参数、返回 JSON 结构逐字未改，
 * 供 [BrowserAgentTool] 门面与 Nav/Inspect/Interact/Antidetect 各工具域协作者共享回调。
 */
@Suppress("ClassOrdering") // 行为保持搬迁：成员按职责（envelope/快照序列化/私有映射）自然分组
internal class BrowserToolEnvelope {

    private companion object {
        const val MAX_ELEMENTS = 120
        const val MAX_TEXT = 8000
    }

    /**
     * 在已有 ToolResult.Success 的 envelope 中追加 note 提示（用于反爬信号提醒等场景）。
     * 对 Error 类型直接返回原样不修改。
     */
    fun appendNoteToResult(result: ToolResult, note: String): ToolResult {
        if (result !is ToolResult.Success) return result
        val original = result.data as? JsonObject ?: return result
        val existingNote = (original["note"] as? JsonPrimitive)?.content ?: ""
        val merged = if (existingNote.isBlank()) note else "$existingNote\n$note"
        val newFields = original.toMutableMap()
        newFields["note"] = JsonPrimitive(merged)
        return ToolResult.Success(JsonObject(newFields))
    }

    /**
     * 统一动作 envelope（R2.3 干净替换）：所有动作返回 `{ok, action, changed, summary, note|error,
     * recoverable, snapshot?, delta?}`，[extra] 用于追加结构化的动作专属数据（tabs/requests 等）。
     */
    @Suppress("LongParameterList") // 行为保持搬迁/签名保持：envelope 为全动作共用返回结构，参数顺序逐字不变
    fun envelope(
        action: String,
        ok: Boolean = true,
        changed: Boolean = false,
        summary: String = "",
        note: String = "",
        error: String = "",
        errorCode: String = "",
        recoverable: Boolean = false,
        snapshot: JsonObject? = null,
        delta: BrowserSnapshotDelta? = null,
        extra: JsonObject? = null
    ): ToolResult {
        val fields = linkedMapOf<String, JsonElement>(
            "ok" to JsonPrimitive(ok),
            "action" to JsonPrimitive(action),
            "changed" to JsonPrimitive(changed),
            "summary" to JsonPrimitive(summary)
        )
        if (note.isNotBlank()) fields["note"] = JsonPrimitive(note)
        if (error.isNotBlank()) fields["error"] = JsonPrimitive(error)
        if (errorCode.isNotBlank()) fields["error_code"] = JsonPrimitive(errorCode)
        if (recoverable) fields["recoverable"] = JsonPrimitive(true)
        snapshot?.let { fields["snapshot"] = it }
        delta?.let { fields["delta"] = deltaToJson(it) }
        extra?.let { fields.putAll(it) }
        return ToolResult.Success(JsonObject(fields))
    }

    /** 增量 delta 序列化（R2.1）：新增/变化元素以紧凑摘要返回，消失元素只给 id。 */
    fun deltaToJson(delta: BrowserSnapshotDelta): JsonObject = JsonObject(
        mapOf(
            "added" to JsonArray(delta.added.map { elementBriefJson(it) }),
            "removed" to JsonArray(delta.removed.map { JsonPrimitive(it) }),
            "changed" to JsonArray(delta.changed.map { elementBriefJson(it) }),
            "text_note" to JsonPrimitive(delta.textNote)
        )
    )

    fun writeDeltaSummary(action: String, delta: BrowserSnapshotDelta): String = buildString {
        append(action).append("完成")
        val parts = mutableListOf<String>()
        if (delta.added.isNotEmpty()) parts.add("新增 ${delta.added.size}")
        if (delta.changed.isNotEmpty()) parts.add("变化 ${delta.changed.size}")
        if (delta.removed.isNotEmpty()) parts.add("消失 ${delta.removed.size}")
        if (parts.isNotEmpty()) append("：").append(parts.joinToString("，"))
        if (delta.textNote.isNotBlank()) append("；").append(delta.textNote)
    }

    fun snapshotSummary(snap: BrowserPageSnapshot): String = buildString {
        append("页面「").append(snap.title.ifBlank { "(无标题)" }).append("」")
        if (snap.url.isNotBlank()) append(" URL=").append(snap.url)
        val visible = snap.elements.count { it.visible && it.inViewport }
        append(" 控件 ").append(snap.elements.size).append(" 个（视口内 ").append(visible).append("）")
        if (snap.hasLoginForm) append("，检测到登录表单")
        if (snap.pendingRequests > 0) append("，在途请求 ").append(snap.pendingRequests)
    }

    fun snapshotLevelOf(args: Map<String, JsonElement>): SnapshotLevel =
        SnapshotLevel.fromName(args["snapshot_level"]?.jsonPrimitive?.contentOrNull)

    /** 导航/标签切换类动作默认 standard（页面完全变化，给完整元素便于继续操作），可用 snapshot_level 覆盖。 */
    fun navLevelOf(args: Map<String, JsonElement>): SnapshotLevel {
        val explicit = args["snapshot_level"]?.jsonPrimitive?.contentOrNull
        return if (explicit != null) SnapshotLevel.fromName(explicit) else SnapshotLevel.STANDARD
    }

    /**
     * 快照分级序列化（R2.1）：
     *  - summary：标题/URL/登录信号 + 每控件一行的紧凑摘要（含可操作性标注），不含完整元素与 page_text；
     *  - standard：summary + 完整元素 JSON；
     *  - full：standard + page_text。
     */
    @Suppress("CyclomaticComplexMethod", "CognitiveComplexMethod") // 序列化行为保持搬迁：分支为逐元素摘要拼装，拆出私有映射函数后等价
    fun snapshotToJson(snap: BrowserPageSnapshot, level: SnapshotLevel = SnapshotLevel.SUMMARY): JsonObject {
        val elements = snap.elements.take(MAX_ELEMENTS)
        // 每控件一行的紧凑清单 + 可操作性标注（R2.2）
        val sb = StringBuilder()
        for (el in elements) appendElementSummary(sb, el)
        // 标题大纲（缩进展示层级，帮助模型快速理解页面结构）
        val headingSummary = buildHeadingSummary(snap.headings)
        val fields = linkedMapOf<String, JsonElement>(
            "title" to JsonPrimitive(snap.title),
            "url" to JsonPrimitive(snap.url),
            "login_page" to JsonPrimitive(snap.hasLoginForm),
            "login_hint" to JsonPrimitive(snap.loginHint),
            "pending_requests" to JsonPrimitive(snap.pendingRequests),
            "controls_total" to JsonPrimitive(snap.elements.size),
            "controls_shown" to JsonPrimitive(elements.size),
            "controls_summary" to JsonPrimitive(sb.toString()),
            "headings_summary" to JsonPrimitive(headingSummary)
        )
        if (level != SnapshotLevel.SUMMARY) {
            fields["headings"] = JsonArray(snap.headings.map { headingJson(it) })
            fields["elements"] = JsonArray(elements.map { elementJson(it) })
        }
        if (level == SnapshotLevel.FULL) {
            fields["page_text"] = JsonPrimitive(snap.pageText.take(MAX_TEXT))
        }
        return JsonObject(fields)
    }

    /**
     * 逐元素紧凑摘要（R2.2：编号+控件类型+名称+状态+可操作性标注），追加到 [sb]。
     * 由 snapshotToJson 的循环体机械抽出，输出逐字等价。
     */
    private fun appendElementSummary(sb: StringBuilder, el: BrowserElement) {
        sb.append('[').append(el.id).append("] ").append(el.kind)
        val name = el.label.ifBlank { el.text }.ifBlank { el.value }
        if (name.isNotBlank()) sb.append(" \"").append(name).append('"')
        when {
            el.kind.endsWith(":checkbox") || el.kind.endsWith(":radio") ->
                sb.append(if (el.checked) " [已勾选]" else " [未勾选]")
            el.kind == "select" && el.value.isNotBlank() ->
                sb.append(" [当前=").append(el.value).append(']')
        }
        if (el.kind == "select" && el.options.isNotEmpty()) {
            sb.append(" 选项=").append(el.options.joinToString("/") { o -> o.text.ifBlank { o.value } })
        }
        if (el.required) sb.append(" [必填]")
        if (el.disabled) sb.append(" [禁用]")
        // 可操作性标注（R2.2）：不可见/视口外/被遮挡
        if (!el.visible) sb.append(" [不可见]")
        else if (el.needsScroll) sb.append(" [视口外]")
        if (el.overlapped) sb.append(" [被遮挡]")
        if (el.kind == "link" && el.href.isNotBlank()) sb.append(" -> ").append(el.href)
        if (el.placeholder.isNotBlank() && el.label.isBlank()) sb.append(" (placeholder=").append(el.placeholder).append(')')
        sb.append('\n')
    }

    /** 标题大纲（缩进展示层级），输出逐字等价。 */
    private fun buildHeadingSummary(headings: List<BrowserHeading>): String {
        val headingSb = StringBuilder()
        for (h in headings) {
            headingSb.append("  ".repeat((h.level - 1).coerceAtLeast(0)))
                .append('H').append(h.level).append(' ').append(h.text).append('\n')
        }
        return headingSb.toString()
    }

    private fun headingJson(h: BrowserHeading): JsonObject =
        JsonObject(mapOf("level" to JsonPrimitive(h.level), "text" to JsonPrimitive(h.text)))

    /** 完整元素 JSON（standard/full 级）：含三级定位字段与可操作性标注（R2.2）。 */
    private fun elementJson(el: BrowserElement): JsonObject = JsonObject(
        mapOf(
            "id" to JsonPrimitive(el.id),
            "kind" to JsonPrimitive(el.kind),
            "tag" to JsonPrimitive(el.tag),
            "type" to JsonPrimitive(el.type),
            "label" to JsonPrimitive(el.label),
            "text" to JsonPrimitive(if (el.sensitive) "" else el.text),
            "value" to JsonPrimitive(if (el.sensitive) "" else el.value),
            "href" to JsonPrimitive(el.href),
            "placeholder" to JsonPrimitive(el.placeholder),
            "checked" to JsonPrimitive(el.checked),
            "disabled" to JsonPrimitive(el.disabled),
            "required" to JsonPrimitive(el.required),
            "readonly" to JsonPrimitive(el.readonly),
            "options" to JsonArray(
                el.options.map { o ->
                    JsonObject(mapOf("value" to JsonPrimitive(o.value), "text" to JsonPrimitive(o.text)))
                }
            ),
            "sensitive" to JsonPrimitive(el.sensitive),
            // R2.2 三级定位 + 可操作性标注
            "locator" to JsonPrimitive(el.locator),
            "semantic" to JsonPrimitive(el.semantic),
            "in_viewport" to JsonPrimitive(el.inViewport),
            "visible" to JsonPrimitive(el.visible),
            "needs_scroll" to JsonPrimitive(el.needsScroll),
            "overlapped" to JsonPrimitive(el.overlapped)
        )
    )

    /** 增量/变化元素紧凑摘要（delta 用）。 */
    private fun elementBriefJson(el: BrowserElement): JsonObject = JsonObject(
        mapOf(
            "id" to JsonPrimitive(el.id),
            "kind" to JsonPrimitive(el.kind),
            "label" to JsonPrimitive(el.label),
            "text" to JsonPrimitive(if (el.sensitive) "" else el.text),
            "in_viewport" to JsonPrimitive(el.inViewport),
            "visible" to JsonPrimitive(el.visible),
            "needs_scroll" to JsonPrimitive(el.needsScroll),
            "overlapped" to JsonPrimitive(el.overlapped)
        )
    )
}
