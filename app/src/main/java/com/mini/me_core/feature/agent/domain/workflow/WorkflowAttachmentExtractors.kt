package com.mini.me_core.feature.agent.domain.workflow

import com.mini.me_core.feature.agent.domain.tool.ToolResult
import com.mini.me_core.feature.agent.presentation.AgentAttachment
import com.mini.me_core.feature.agent.domain.model.AgentImage
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.booleanOrNull
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.json.longOrNull

/**
 * 从 StatefulAgentWorkflow 抽出的纯函数工具结果附件/图片提取器（同包 internal，不改语义）。
 */

internal fun extractInlineImages(result: ToolResult): List<AgentImage> {
    val data = (result as? ToolResult.Success)?.data as? JsonObject ?: return emptyList()
    val image = data["image"] as? JsonObject ?: return emptyList()
    val mimeType = image["mime_type"]?.jsonPrimitive?.contentOrNull ?: return emptyList()
    val base64Data = image["base64_data"]?.jsonPrimitive?.contentOrNull ?: return emptyList()
    val path = image["path"]?.jsonPrimitive?.contentOrNull.orEmpty()
    if (!mimeType.startsWith("image/") || base64Data.isBlank()) return emptyList()
    return listOf(AgentImage(mimeType = mimeType, base64Data = base64Data, path = path))
}

/**
 * 从 sendFile 工具结果的 `files` 数组提取文件卡片元数据（含宿主本地路径，供 UI 打开文件用）。
 * 任一文件缺关键字段则整体返回空（与 sendFile 的原子语义一致）。
 */
internal fun extractAttachments(result: ToolResult): List<AgentAttachment> {
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
internal fun stripAttachments(result: ToolResult): ToolResult {
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

internal fun stripInlineImages(result: ToolResult): ToolResult {
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
