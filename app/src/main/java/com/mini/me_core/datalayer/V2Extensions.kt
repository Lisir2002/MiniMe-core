package com.mini.me_core.datalayer

import com.mini.mecore.datalayer.sqldelight.agent.Agent_message
import com.mini.mecore.datalayer.sqldelight.agent.Agent_schedules
import com.mini.mecore.datalayer.sqldelight.agent.Agent_session
import com.mini.mecore.datalayer.sqldelight.agent.SelectAllSessionsWithCount
import com.mini.mecore.datalayer.sqldelight.agent.Model_capability_overrides
import com.mini.mecore.datalayer.sqldelight.agent.Skill_conversation_state
import com.mini.mecore.datalayer.sqldelight.agent.Skill_state
import com.mini.me_core.core.util.EnumSafe
import com.mini.me_core.feature.agent.domain.core.model.AgentMode
import com.mini.me_core.feature.agent.domain.core.model.ChatSession
import com.mini.me_core.feature.agent.domain.core.model.ReasoningEffort
import com.mini.me_core.feature.agent.presentation.AgentAttachment
import com.mini.me_core.feature.agent.presentation.AgentUIMessage
import com.mini.me_core.feature.agent.presentation.BACKGROUND_NOTIFICATION_PREFIX
import com.mini.me_core.feature.agent.presentation.MessageRole
import kotlinx.serialization.json.Json
import com.mini.mecore.datalayer.sqldelight.credentials.Git_credentials
import com.mini.mecore.datalayer.sqldelight.settings.Ai_providers
import com.mini.mecore.datalayer.sqldelight.t2i.T2i_provider_models
import com.mini.mecore.datalayer.sqldelight.t2i.T2i_providers
import com.mini.mecore.datalayer.sqldelight.t2i.T2i_task
import com.mini.mecore.datalayer.sqldelight.workspace.Remote_audit_logs
import com.mini.mecore.datalayer.sqldelight.workspace.Remote_mounts

/**
 * V2 SQLDelight 类型的 Boolean 扩展属性。
 *
 * V2 schema 中所有布尔语义列均为 INTEGER (0/1)，业务层用 Boolean。
 * 此处统一封装 `== 1L` 判断，避免业务代码到处写裸比较。
 *
 * P3 迁移阶段 1 外围模块所需；后续阶段（agent 域）需要时再补充。
 */

// ── credentials ──────────────────────────────────────────────

val Git_credentials.isDefault: Boolean get() = is_default == 1L

// ── settings ─────────────────────────────────────────────────

val Ai_providers.isActive: Boolean get() = is_active == 1L
val Ai_providers.isEnabled: Boolean get() = is_enabled == 1L
val Ai_providers.useFullUrl: Boolean get() = use_full_url == 1L
val Ai_providers.useResponseApi: Boolean get() = use_response_api == 1L

// ── workspace ─────────────────────────────────────────────────

val Remote_mounts.isActive: Boolean get() = is_active == 1L
val Remote_mounts.autoConnect: Boolean get() = auto_connect == 1L
val Remote_audit_logs.isSuccess: Boolean get() = success == 1L

// ── t2i ───────────────────────────────────────────────────────

val T2i_providers.isActive: Boolean get() = is_active == 1L
val T2i_providers.isEnabled: Boolean get() = is_enabled == 1L

val T2i_provider_models.supportsHd: Boolean get() = supports_hd == 1L
val T2i_provider_models.supportsInpaint: Boolean get() = supports_inpaint == 1L

val T2i_task.isHd: Boolean get() = hd == 1L

// ── agent ─────────────────────────────────────────────────────

val Agent_schedules.isEnabled: Boolean get() = enabled == 1L

// model_capability_overrides: nullable Boolean? 覆盖字段（Long? 0/1/null）
val Model_capability_overrides.overrideVisionBool: Boolean? get() = override_vision?.let { it != 0L }
val Model_capability_overrides.overrideToolsBool: Boolean? get() = override_tools?.let { it != 0L }
val Model_capability_overrides.overrideReasoningBool: Boolean? get() = override_reasoning?.let { it != 0L }
val Model_capability_overrides.overrideVideoBool: Boolean? get() = override_video?.let { it != 0L }
val Model_capability_overrides.overrideAudioBool: Boolean? get() = override_audio?.let { it != 0L }
val Model_capability_overrides.overrideCodeBool: Boolean? get() = override_code?.let { it != 0L }
val Model_capability_overrides.overrideStructuredOutputBool: Boolean? get() = override_structured_output?.let { it != 0L }

// skill_state / skill_conversation_state
val Skill_state.isEnabled: Boolean get() = enabled == 1L
val Skill_conversation_state.isEnabled: Boolean get() = enabled == 1L

// agent_session → ChatSession 领域模型
fun Agent_session.toChatSession(): ChatSession = ChatSession(
    id = id,
    title = title ?: "",
    createdAt = created_at,
    updatedAt = updated_at,
    workspacePath = workspace_path,
    mode = EnumSafe.valueOf(mode, AgentMode.BUILD, tag = "agent_session.mode"),
    reasoningEffort = EnumSafe.valueOf(reasoning_effort, ReasoningEffort.MEDIUM, tag = "agent_session.reasoning_effort"),
    providerId = provider_id,
    model = model,
    totalInputTokens = total_input_tokens.toInt(),
    totalOutputTokens = total_output_tokens.toInt(),
    lastInputTokens = last_input_tokens.toInt(),
)

// SelectAllSessionsWithCount → ChatSession 领域模型
fun SelectAllSessionsWithCount.toChatSession(): ChatSession = ChatSession(
    id = id,
    title = title ?: "",
    createdAt = created_at,
    updatedAt = updated_at,
    workspacePath = workspace_path,
    mode = EnumSafe.valueOf(mode, AgentMode.BUILD, tag = "agent_session.mode"),
)

// agent_message Boolean 扩展（Long 0/1 → Boolean）
val Agent_message.isErrorBool: Boolean get() = is_error != 0L
val Agent_message.isCompactedBool: Boolean get() = is_compacted != 0L
val Agent_message.isContextSummaryBool: Boolean get() = is_context_summary != 0L
val Agent_message.isCompactionMarkerBool: Boolean get() = is_compaction_marker != 0L

private val uiJson = Json { ignoreUnknownKeys = true }

fun Agent_message.toUIMessage(): AgentUIMessage {
    val roleEnum: MessageRole = runCatching {
        EnumSafe.valueOf(role, MessageRole.USER, tag = "agent_message.role")
    }.getOrElse { MessageRole.USER }
    val attachments = attachments_json?.let { v ->
        runCatching { uiJson.decodeFromString<List<AgentAttachment>>(v) }.getOrDefault(emptyList())
    } ?: emptyList()
    return AgentUIMessage(
        id = id,
        role = roleEnum,
        content = content,
        timestamp = created_at,
        taskId = task_id,
        toolName = tool_name,
        toolArgs = tool_args,
        isError = is_error != 0L,
        reasoning = reasoning,
        attachments = attachments,
        isCompactionMarker = is_compaction_marker != 0L,
        isBackgroundNotification = roleEnum == MessageRole.USER &&
            content.startsWith(BACKGROUND_NOTIFICATION_PREFIX),
        inputTokens = input_tokens.toInt(),
        outputTokens = output_tokens.toInt()
    )
}
