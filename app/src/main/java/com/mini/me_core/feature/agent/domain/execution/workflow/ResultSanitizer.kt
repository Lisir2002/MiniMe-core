package com.mini.me_core.feature.agent.domain.execution.workflow

import com.mini.me_core.core.util.FileLogger
import com.mini.me_core.feature.agent.domain.core.model.AgentImage
import com.mini.me_core.feature.agent.domain.core.model.AgentMessage
import com.mini.me_core.feature.agent.domain.execution.tool.ToolResult
import com.mini.me_core.feature.agent.presentation.AgentAttachment
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.booleanOrNull
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.json.longOrNull

/**
 * 结果脱敏/整形（P1-09 从 StatefulAgentWorkflow 整体搬迁的叶子协作者）。
 *
 * 职责：从工具结果提取/剥离内联图片与文件卡片附件、发送前按模型视觉能力净化消息图片、
 * 非多模态模型下用识图专用模型把图片翻成文本。纯整形叶子——不改业务判定与阈值，
 * 所有 JSON 字段名、剥离/保留字段、文案逐字保持（行为保持搬迁）。
 *
 * @Suppress ClassOrdering：协作者按「整形方法分组」组织，成员顺序调整不影响行为（行为保持）。
 */
@Suppress("ClassOrdering")
internal class ResultSanitizer(
    private val modelCallRouter: ModelCallRouter
) {

    private companion object {
        // 与搬迁前主类日志 tag 保持一致，避免日志归类漂移。
        const val TAG = "StatefulAgentWorkflow"
    }

    /**
     * 发送前按模型视觉能力处理消息中的图片：
     * - 支持 vision：原样返回。
     * - 不支持：剥离所有图片（仅影响本次发送，不动持久化数据），历史/输入中的图片不会原样发给
     *   非多模态模型导致请求失败；切回多模态模型后图片上下文仍可正常使用。
     */
    fun sanitizeImagesForModel(
        messages: List<AgentMessage>,
        supportsVision: Boolean
    ): List<AgentMessage> {
        if (supportsVision) return messages
        return messages.map { msg ->
            when (msg) {
                is AgentMessage.UserMessage ->
                    if (msg.images.isEmpty()) msg
                    else msg.copy(images = emptyList(), content = msg.content.ifBlank { "（图片已省略：当前模型不支持图片输入）" })
                is AgentMessage.ToolResultMessage ->
                    if (msg.images.isEmpty()) msg else msg.copy(images = emptyList())
                is AgentMessage.AssistantMessage -> msg
            }
        }
    }

    fun extractInlineImages(result: ToolResult): List<AgentImage> {
        val data = (result as? ToolResult.Success)?.data as? JsonObject ?: return emptyList()
        val image = data["image"] as? JsonObject ?: return emptyList()
        // 等价合并原「mime_type 空 / base64_data 空 / 非 image 前缀 / base64 空白」多道早退：
        // 任一不满足即不是可附加的内联图片，返回空（与原逐分支返回空List等价）。
        val mimeType = image["mime_type"]?.jsonPrimitive?.contentOrNull
        val base64Data = image["base64_data"]?.jsonPrimitive?.contentOrNull
        if (mimeType == null || !mimeType.startsWith("image/") || base64Data.isNullOrBlank()) {
            return emptyList()
        }
        val path = image["path"]?.jsonPrimitive?.contentOrNull.orEmpty()
        return listOf(AgentImage(mimeType = mimeType, base64Data = base64Data, path = path))
    }

    /**
     * 从 sendFile 工具结果的 `files` 数组提取文件卡片元数据（含宿主本地路径，供 UI 打开文件用）。
     * 任一文件缺关键字段则整体返回空（与 sendFile 的原子语义一致）。
     */
    fun extractAttachments(result: ToolResult): List<AgentAttachment> {
        val data = (result as? ToolResult.Success)?.data as? JsonObject ?: return emptyList()
        val files = data["files"] as? JsonArray ?: return emptyList()
        val attachments = files.mapNotNull { elem ->
            val obj = elem as? JsonObject ?: return@mapNotNull null
            val path = obj["path"]?.jsonPrimitive?.contentOrNull ?: return@mapNotNull null
            val localPath = obj["local_path"]?.jsonPrimitive?.contentOrNull ?: return@mapNotNull null
            val name = obj["name"]?.jsonPrimitive?.contentOrNull ?: path.substringAfterLast('/')
            val mimeType = obj["mime_type"]?.jsonPrimitive?.contentOrNull ?: "application/octet-stream"
            AgentAttachment(
                fileName = name,
                containerPath = path,
                localPath = localPath,
                mimeType = mimeType,
                sizeBytes = obj["size_bytes"]?.jsonPrimitive?.longOrNull ?: 0L,
                isImage = obj["is_image"]?.jsonPrimitive?.booleanOrNull ?: mimeType.startsWith("image/")
            )
        }
        return if (attachments.size == files.size) attachments else emptyList()
    }

    /** 从回传给模型的 sendFile 结果中剥离宿主本地路径（模型只应看到容器路径）。 */
    fun stripAttachments(result: ToolResult): ToolResult {
        val success = result as? ToolResult.Success ?: return result
        val data = success.data as? JsonObject ?: return result
        val strippedFiles = (data["files"] as? JsonArray)?.map { elem ->
            val obj = elem as? JsonObject ?: return@map elem
            JsonObject(obj.toMutableMap().apply { remove("local_path") })
        } ?: return result
        val strippedData = data.toMutableMap().apply {
            this["files"] = JsonArray(strippedFiles)
            this["files_attached"] = JsonPrimitive(true)
        }
        return ToolResult.Success(JsonObject(strippedData))
    }

    fun stripInlineImages(result: ToolResult): ToolResult {
        val success = result as? ToolResult.Success ?: return result
        val data = success.data as? JsonObject ?: return result
        val image = data["image"] as? JsonObject ?: return result
        val strippedImage = image.toMutableMap().apply {
            remove("base64_data")
            this["base64_omitted"] = JsonPrimitive(true)
            this["note"] = JsonPrimitive("图片数据已作为视觉输入附加，未写入文本工具结果。")
        }
        val strippedData = data.toMutableMap().apply {
            this["image"] = JsonObject(strippedImage)
            this["image_attached"] = JsonPrimitive(true)
        }
        return ToolResult.Success(JsonObject(strippedData))
    }

    /**
     * 非多模态模型的 viewImage 回退：从工具结果中提取 base64 图片，
     * 同步发给识图专用模型理解内容，返回文本结果。
     *
     * @Suppress ReturnCount：多段失败文案需逐字返回（行为保持，文案/分支顺序不变）。
     * @Suppress TooGenericExceptionCaught：识图兜底需隔离任意异常并把 e.message 透传为文案，
     *   cause 已由 FileLogger.e(TAG, "识图回退失败", e) 记录，不丢失。
     */
    @Suppress("ReturnCount", "TooGenericExceptionCaught")
    suspend fun runVisionFallback(result: ToolResult, sessionId: String?, customPrompt: String? = null): String {
        val data = (result as? ToolResult.Success)?.data as? JsonObject
            ?: return "无法解析图片数据"
        val image = data["image"] as? JsonObject
            ?: return "无法提取图片数据"
        val mimeType = image["mime_type"]?.jsonPrimitive?.contentOrNull
            ?: return "无法识别图片格式"
        val base64Data = image["base64_data"]?.jsonPrimitive?.contentOrNull
            ?: return "无法读取图片数据"
        val path = image["path"]?.jsonPrimitive?.contentOrNull.orEmpty()

        val agentImage = AgentImage(mimeType = mimeType, base64Data = base64Data, path = path)
        val visionProvider = modelCallRouter.resolveVisionFallbackProvider(sessionId)
            ?: return "「多模态识图（Vision）」兜底模型不可用，请先在设置中启用。"

        val promptText = if (!customPrompt.isNullOrBlank()) {
            "请针对用户/模型的如下关注重点，详细分析并描述这张图片：\n$customPrompt"
        } else {
            "请详细描述这张图片的内容，包括其中出现的文字、元素、布局、颜色等关键信息。"
        }

        try {
            val messages = listOf(
                AgentMessage.UserMessage(
                    content = promptText,
                    images = listOf(agentImage)
                )
            )
            val response = visionProvider.complete("", messages, emptyList())
            return response.content.ifBlank { "（识图模型未返回内容）" }
        } catch (e: kotlinx.coroutines.CancellationException) {
            throw e
        } catch (e: Exception) {
            FileLogger.e(TAG, "识图回退失败", e)
            return "识图失败: ${e.message}"
        }
    }
}
