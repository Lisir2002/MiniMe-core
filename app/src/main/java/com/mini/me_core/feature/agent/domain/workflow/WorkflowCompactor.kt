package com.mini.me_core.feature.agent.domain.workflow

import com.mini.me_core.core.util.FileLogger
import com.mini.me_core.feature.agent.domain.model.AgentImage
import com.mini.me_core.feature.agent.domain.model.AgentMessage
import com.mini.me_core.feature.agent.domain.provider.AIProvider
import com.mini.me_core.feature.agent.domain.tool.ToolResult
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.coroutines.CancellationException

/** 从 StatefulAgentWorkflow 抽出的 vision/压缩兜底 provider 解析（同包 extension，不改语义）。 */

internal suspend fun StatefulAgentWorkflow.runVisionFallback(result: ToolResult, sessionId: String?, customPrompt: String? = null): String {
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
        val visionProvider = resolveVisionFallbackProvider(sessionId)
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
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            FileLogger.e("StatefulAgentWorkflow", "识图回退失败", e)
            return "识图失败: ${e.message}"
        }
    }

    /**
     * 当前聊天模型是否「有原生能力支持多模态」。
     *
     * 影响两条关键链路：
     *  (1) `runToolSync(name=="viewImage")` L586 的守卫：
     *      if (!activeModelSupportsVision(...)) → 直接抛「当前聊天模型不支持图片输入」
     *      这条就是用户接入 step-3.7-flash 时截图里看到的错误文案。
     *
     *  (2) `pendingVisionRound` 是否启用独立识图模型 fallback 的判定入口。
     *
     *  判定链优先级（从高到低）：
     *  - ④ 单模型复选框手动覆盖（`ModelCapabilityOverrideDao`，在 resolve 内处理）；
     *  - catalog 明确 supportsVision=true → 支持；
     *  - catalog 明确 supportsVision=false（MODELS_DEV 收录的纯文本模型）→ 不支持；
     *  - ③ 兼容端点默认策略 Repository（STRICT/HEURISTIC/LAX/MANUAL，resolve 内处理）；
     *  - probablyVision 启发式（step- 家族白名单在 ModelMetadataService.default() 命中即为 true）。
     *
     *  关键决策（按用户要求「针对性修复独立出来」）：
     *  - **撤销 的 `source==INFERRED → 一律 true` 全局放宽**；
     *  - 这里只返回最终 `metadata.supportsVision` 布尔值；
     *  - step-3.7-flash 修复仅由 probablyVision step- 家族白名单单独命中，
     *    不影响其他未收录模型（它们恢复 之前的严格语义）。
     */
internal suspend fun StatefulAgentWorkflow.activeModelSupportsVision(sessionId: String?): Boolean {
        val config = resolveProviderConfig(sessionId) ?: return false
        val metadata = modelMetadataService.resolve(config.type, config.effectiveModel)
        return metadata.supportsVision
    }

    /**
     * 发送前是否应该把图片带给模型。
     * 判定链与 [activeModelSupportsVision] 严格一致；
     * 2026-08-10 按用户要求**撤销 的 `source==INFERRED → 一律 true` 全局放宽**，
     * 恢复 之前的严格语义。未收录兼容端点模型仅当 probablyVision 启发式
     * （或③兼容端点策略 / ④单模型覆盖）命中时才会带图发送，避免全局放宽的副作用。
     */
internal suspend fun StatefulAgentWorkflow.shouldSendImages(sessionId: String?): Boolean {
        val config = resolveProviderConfig(sessionId) ?: return false
        val metadata = modelMetadataService.resolve(config.type, config.effectiveModel)
        return metadata.supportsVision
    }

    /**
     * 发送前按模型视觉能力处理消息中的图片：
     * - 支持 vision：原样返回。
     * - 不支持：剥离所有图片（仅影响本次发送，不动持久化数据），历史/输入中的图片不会原样发给
     *   非多模态模型导致请求失败；切回多模态模型后图片上下文仍可正常使用。
     */
internal fun StatefulAgentWorkflow.sanitizeImagesForModel(
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

    /**
     * 识图专用兜底模型是否可用：已配置 providerId 且指向的 provider/model 存在、有 apiKey、且其
     * ModelMetadata.supportsVision 为真。识图轮仅当 [activeModelSupportsVision] 为 false 时才回退到它。
     * 未配置（providerId 空）即视为「跟随聊天模型」，不构成兜底 → 返回 false。
     */
internal suspend fun StatefulAgentWorkflow.visionFallbackReady(): Boolean {
        val providerId = workflowSettings.getVisionProviderId().trim()
        if (providerId.isEmpty()) return false
        val model = workflowSettings.getVisionModel().trim()
        if (model.isEmpty()) return false
        val config = aiProviderRepository.getProviderById(providerId) ?: return false
        if (!config.isEnabled) return false
        if (config.apiKey.isBlank()) return false
        val metadata = modelMetadataService.resolve(config.type, model)
        return metadata.supportsVision
    }

    /**
     * 识图轮专用 provider 解析。仅当当前聊天模型不支持 vision、且专用模型已配置且可用时返回
     * 全新的独立 AIProvider 实例；否则返回 null（表示无需切换、沿用 aiProvider）。
     */
internal suspend fun StatefulAgentWorkflow.resolveVisionFallbackProvider(sessionId: String?): AIProvider? {
        if (activeModelSupportsVision(sessionId)) return null // 当前聊天模型就有原生能力，直接用之
        if (!visionFallbackReady()) return null       // 无可用兜底，仍沿用 aiProvider（守卫已先行拦截并报错）
        val providerId = workflowSettings.getVisionProviderId().trim()
        val model = workflowSettings.getVisionModel().trim()
        val config = aiProviderRepository.getProviderById(providerId)
            ?: error("识图专用模型配置丢失")
        if (config.apiKey.isBlank()) error("识图专用模型「${config.name}」未填写 API Key")
        if (model.isBlank()) error("识图专用模型未指定模型")
        return createStandaloneProvider(config.copy(selectedModel = model), sessionId)
    }

    /**
     * 压缩轮专用 provider 解析。若用户配置了压缩专用模型且 provider 存在、已启用、有 apiKey，
     * 则返回全新的独立 AIProvider 实例；否则返回 null（沿用当前聊天模型）。
     */
internal suspend fun StatefulAgentWorkflow.resolveCompactionFallbackProvider(sessionId: String? = null): AIProvider? {
        val providerId = workflowSettings.getCompactionProviderId().trim()
        if (providerId.isEmpty()) return null
        val model = workflowSettings.getCompactionModel().trim()
        if (model.isEmpty()) return null
        val config = aiProviderRepository.getProviderById(providerId) ?: return null
        if (!config.isEnabled || config.apiKey.isBlank()) return null
        return createStandaloneProvider(config.copy(selectedModel = model), sessionId)
    }