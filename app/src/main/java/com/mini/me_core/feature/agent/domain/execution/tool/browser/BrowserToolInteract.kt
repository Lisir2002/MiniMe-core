package com.mini.me_core.feature.agent.domain.execution.tool.browser

import com.mini.me_core.feature.agent.domain.execution.tool.ToolResult
import com.mini.me_core.feature.browser.domain.BrowserController
import com.mini.me_core.feature.browser.domain.BrowserPageSnapshot
import com.mini.me_core.feature.workspace.domain.WorkspacePathMapper
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive

/**
 * 浏览器交互/写操作工具域（行为保持自 [BrowserAgentTool] 原样搬迁）。
 *
 * 承载 click/type/fill_form/select_option/submit/scroll/hover/drag/press_key/upload_file
 * 及写操作统一入口 [writeEnvelope]。所有写操作仍经由 browserController 的同一把互斥锁执行，
 * 统一 envelope + 增量 delta 返回结构逐字不变。
 */
internal class BrowserToolInteract(
    private val env: BrowserToolEnvelope,
    private val browserController: BrowserController,
    private val pathMapper: WorkspacePathMapper
) {

    /**
     * 写操作统一入口（R2.3 写操作自动验证）：执行动作 → 检测"元素未找到"类失败 →
     * 取 controller 计算好的增量 delta 一并返回，模型无需自行轮询。
     */
    suspend fun writeEnvelope(
        action: String,
        args: Map<String, JsonElement>,
        run: suspend () -> BrowserPageSnapshot
    ): ToolResult {
        val snap = run()
        val notFound = snap.pageText.takeIf { it.startsWith("元素 ") && it.contains("未找到") }
        if (notFound != null) {
            return env.envelope(
                action = action,
                ok = false,
                error = notFound,
                errorCode = "ELEMENT_NOT_FOUND",
                recoverable = true,
                summary = "$action 失败：$notFound",
                note = "元素可能因页面刷新/重渲染失效。建议：1) 重新 view(snapshot_level=standard) 获取最新元素标识；" +
                    "2) 换用 CSS 绝对路径或语义描述符（role=… name=… index=…）替代 data-rcb-id；3) 不要重复同样的定位方式"
            )
        }
        val delta = browserController.lastDelta()
        return env.envelope(
            action = action,
            ok = true,
            changed = delta != null &&
                (delta.added.isNotEmpty() || delta.changed.isNotEmpty() || delta.removed.isNotEmpty() || delta.textNote.isNotBlank()),
            summary = delta?.let { env.writeDeltaSummary(action, it) } ?: "$action 完成",
            snapshot = env.snapshotToJson(snap, env.snapshotLevelOf(args)),
            delta = delta
        )
    }

    suspend fun doClick(args: Map<String, JsonElement>): ToolResult {
        val id = args["element_id"]?.jsonPrimitive?.contentOrNull ?: return ToolResult.Error("click 需要 element_id", "MISSING_ELEMENT_ID")
        return writeEnvelope("click", args) { browserController.click(id) }
    }

    suspend fun doType(args: Map<String, JsonElement>): ToolResult {
        val id = args["element_id"]?.jsonPrimitive?.contentOrNull ?: return ToolResult.Error("type 需要 element_id", "MISSING_ELEMENT_ID")
        val text = args["text"]?.jsonPrimitive?.contentOrNull ?: return ToolResult.Error("type 需要 text", "MISSING_TEXT")
        return writeEnvelope("type", args) { browserController.type(id, text) }
    }

    /** 批量表单填写：fields 为 元素标识 -> 文本 的对象映射，一次性填写多个字段。 */
    suspend fun doFillForm(args: Map<String, JsonElement>): ToolResult {
        val fieldsObj = args["fields"]?.jsonObject
            ?: return ToolResult.Error("fill_form 需要 fields 参数（对象映射：元素标识->文本）", "MISSING_FIELDS")
        val fields: Map<String, String> = fieldsObj.mapValues { (_, v) ->
            v.jsonPrimitive.contentOrNull ?: ""
        }
        if (fields.isEmpty()) {
            return env.envelope("fill_form", ok = false, recoverable = true, summary = "fill_form 失败：fields 为空", error = "fields 不能为空")
        }
        return writeEnvelope("fill_form", args) { browserController.fillForm(fields) }
    }

    suspend fun doSelect(args: Map<String, JsonElement>): ToolResult {
        val id = args["element_id"]?.jsonPrimitive?.contentOrNull ?: return ToolResult.Error("select_option 需要 element_id", "MISSING_ELEMENT_ID")
        val value = args["value"]?.jsonPrimitive?.contentOrNull ?: return ToolResult.Error("select_option 需要 value", "MISSING_VALUE")
        return writeEnvelope("select_option", args) { browserController.selectOption(id, value) }
    }

    suspend fun doSubmit(args: Map<String, JsonElement>): ToolResult {
        val id = args["element_id"]?.jsonPrimitive?.contentOrNull
        return writeEnvelope("submit", args) { browserController.submit(id) }
    }

    suspend fun doScroll(args: Map<String, JsonElement>): ToolResult {
        val direction = args["direction"]?.jsonPrimitive?.contentOrNull ?: "down"
        return writeEnvelope("scroll", args) { browserController.scroll(direction) }
    }

    suspend fun doHover(args: Map<String, JsonElement>): ToolResult {
        val id = args["element_id"]?.jsonPrimitive?.contentOrNull ?: return ToolResult.Error("hover 需要 element_id", "MISSING_ELEMENT_ID")
        return writeEnvelope("hover", args) { browserController.hover(id) }
    }

    suspend fun doDrag(args: Map<String, JsonElement>): ToolResult {
        val id = args["element_id"]?.jsonPrimitive?.contentOrNull ?: return ToolResult.Error("drag 需要 element_id", "MISSING_ELEMENT_ID")
        val targetId = args["target_element_id"]?.jsonPrimitive?.contentOrNull
            ?: return ToolResult.Error("drag 需要 target_element_id", "MISSING_TARGET_ELEMENT_ID")
        return writeEnvelope("drag", args) { browserController.drag(id, targetId) }
    }

    suspend fun doPressKey(args: Map<String, JsonElement>): ToolResult {
        val id = args["element_id"]?.jsonPrimitive?.contentOrNull ?: return ToolResult.Error("press_key 需要 element_id", "MISSING_ELEMENT_ID")
        val key = args["key"]?.jsonPrimitive?.contentOrNull ?: return ToolResult.Error("press_key 需要 key", "MISSING_KEY")
        return writeEnvelope("press_key", args) { browserController.pressKey(id, key) }
    }

    suspend fun doUploadFile(args: Map<String, JsonElement>): ToolResult {
        val id = args["element_id"]?.jsonPrimitive?.contentOrNull ?: return ToolResult.Error("upload_file 需要 element_id", "MISSING_ELEMENT_ID")
        val filePath = args["file_path"]?.jsonPrimitive?.contentOrNull ?: return ToolResult.Error("upload_file 需要 file_path", "MISSING_FILE_PATH")
        val hostFile = runCatching { pathMapper.toHostFile(filePath) }.getOrNull()
            ?: java.io.File(filePath)
        val error = browserController.uploadFile(id, hostFile)
        return if (error == null) {
            val after = browserController.snapshot(env.snapshotLevelOf(args))
            env.envelope(
                action = "upload_file",
                ok = true,
                summary = "已上传文件 ${pathMapper.toContainerPath(hostFile.absolutePath)} 到元素 #$id",
                snapshot = env.snapshotToJson(after, env.snapshotLevelOf(args))
            )
        } else {
            env.envelope("upload_file", ok = false, error = error, recoverable = true, summary = "上传失败：$error")
        }
    }
}
