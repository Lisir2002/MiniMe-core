package com.mini.me_core.feature.t2i.data.repository

import com.mini.me_core.core.util.FileLogger
import com.mini.me_core.datalayer.repository.T2iRepository as V2T2iRepository
import com.mini.mecore.datalayer.sqldelight.t2i.T2i_provider_models
import com.mini.mecore.datalayer.sqldelight.t2i.T2i_providers
import com.mini.mecore.datalayer.sqldelight.t2i.T2i_task
import com.mini.me_core.feature.t2i.domain.repository.T2IRepository
import javax.inject.Inject
import javax.inject.Singleton

/**
 * T2I 门面 V2 实现（v2-full-takeover P2-2：t2i 域切 V2 读源）。
 *
 * 读写都走 V2 SQLDelight 库；映射层负责 snake_case ↔ Room Entity 字段转换。
 * 写路径语义对齐 Room：provider 激活互斥（deactivateAll + activate 单事务）、
 * task 全列 REPLACE。权限引擎的 3 个聚合（日/会话额度、成功图计数）走 V2 同构查询。
 */
@Singleton
class T2IRepositoryV2Impl @Inject constructor(
    private val v2: V2T2iRepository,
) : T2IRepository {

    private companion object {
        const val TAG = "T2IRepoV2"
    }

    // ── Provider ──

    override suspend fun getActiveProvider(): T2i_providers? =
        v2.getActiveT2iProvider()

    override suspend fun getEnabledProviders(): List<T2i_providers> =
        v2.listT2iProviders().filter { it.is_enabled == 1L }

    override suspend fun getProviderById(id: String): T2i_providers? =
        v2.getT2iProvider(id)

    override suspend fun upsertProvider(provider: T2i_providers) {
        val now = System.currentTimeMillis()
        // 激活互斥（deactivateAll + upsert）已在 T2iRepository.upsertT2iProvider 单事务内原子完成。
        v2.upsertT2iProvider(
            id = provider.id,
            name = provider.name,
            type = provider.type,
            baseUrl = provider.base_url,
            encryptedApiKey = provider.encrypted_api_key,
            endpointMode = provider.endpoint_mode,
            isActive = provider.is_active,
            priority = provider.priority,
            isEnabled = provider.is_enabled,
            extraHeadersJson = provider.extra_headers_json,
            createdAtMs = if (provider.created_at_ms == 0L) now else provider.created_at_ms,
            updatedAtMs = now,
        )
    }

    override suspend fun deleteProvider(id: String) {
        v2.deleteT2iProvider(id)
    }
    override suspend fun deactivateAllProviders() {
        v2.deactivateAllT2iProviders()
    }
    override suspend fun activateProvider(id: String) {
        v2.setActiveT2iProvider(id)
    }
    override suspend fun updateProviderEndpointMode(id: String, mode: String) {
        FileLogger.d(TAG, "写回 endpointMode provider=$id mode=$mode")
        v2.setT2iProviderEndpointMode(id, mode, System.currentTimeMillis())
    }

    override suspend fun updateProviderEncryptedApiKey(id: String, newEncrypted: String) {
        v2.updateT2iProviderEncryptedApiKey(id, newEncrypted, System.currentTimeMillis())
    }

    // ── Provider Model ──

    override suspend fun getModelsForProvider(providerId: String): List<T2i_provider_models> =
        v2.listT2iModels(providerId)

    override suspend fun getModel(providerId: String, modelId: String): T2i_provider_models? =
        v2.listT2iModels(providerId).firstOrNull { it.model_id == modelId }

    override suspend fun upsertModel(model: T2i_provider_models) {
        v2.upsertT2iProviderModel(
            id = model.id,
            providerId = model.provider_id,
            modelId = model.model_id,
            displayName = model.display_name,
            supportsHd = model.supports_hd,
            supportsInpaint = model.supports_inpaint,
            defaultWidth = model.default_width,
            defaultHeight = model.default_height,
            maxSteps = model.max_steps,
            defaultSteps = model.default_steps,
            costPerImageTokens = model.cost_per_image_tokens,
            createdAtMs = model.created_at_ms,
            updatedAtMs = System.currentTimeMillis(),
        )
    }

    // ── Task ──

    override suspend fun insertTask(task: T2i_task) {
        v2.insertTask(
            id = task.id, sessionId = task.session_id, messageId = task.message_id,
            prompt = task.prompt, negativePrompt = task.negative_prompt,
            width = task.width, height = task.height, steps = task.steps,
            seed = task.seed, hd = task.hd,
            providerId = task.provider_id, modelId = task.model_id,
            providerRef = task.provider_ref, endpointModeRef = task.endpoint_mode_ref,
            status = task.status, imagePath = task.image_path, thumbnailPath = task.thumbnail_path,
            remoteTaskId = task.remote_task_id, progressPercent = task.progress_percent,
            retryCount = task.retry_count, maxRetries = task.max_retries,
            errorCode = task.error_code, errorMessage = task.error_message,
            permissionDecision = task.permission_decision, quotaDeductedTokens = task.quota_deducted_tokens,
            createdAtMs = task.created_at_ms, updatedAtMs = task.updated_at_ms, completedAtMs = task.completed_at_ms,
        )
    }

    override suspend fun getTaskById(id: String): T2i_task? = v2.getTask(id)
    override suspend fun getTaskByMessageId(messageId: String): T2i_task? = v2.getTaskByMessageId(messageId)
    override suspend fun listDanglingTasks(cutoffMs: Long): List<T2i_task> = v2.listDanglingTasks(cutoffMs)
    override suspend fun markTaskSuccess(id: String, imagePath: String, thumbnailPath: String, completedAtMs: Long) {
        v2.markTaskSuccess(id, imagePath, thumbnailPath, completedAtMs)
    }
    override suspend fun markTaskFailedOrRetry(
        id: String, finalStatus: String, errorCode: String, errorMessage: String, retryCount: Int, updatedAtMs: Long,
    ) {
        v2.markTaskFailedOrRetry(id, finalStatus, errorCode, errorMessage, retryCount.toLong(), updatedAtMs)
    }
    override suspend fun setTaskPermissionDecision(id: String, decision: String, deducted: Int, updatedAtMs: Long) {
        v2.setTaskPermissionDecision(id, decision, deducted.toLong(), updatedAtMs)
    }
    override suspend fun deleteTask(id: String) {
        v2.deleteTask(id)
    }
    override suspend fun deleteTasksBySession(sessionId: String) {
        v2.deleteTasksBySession(sessionId)
    }

    // ── 权限引擎聚合 ──

    override suspend fun sumDeductedTokensSince(dayStartMs: Long): Long = v2.sumDeductedTokensSince(dayStartMs)
    override suspend fun sumDeductedTokensForSession(sessionId: String): Long = v2.sumDeductedTokensForSession(sessionId)
    override suspend fun countSuccessfulImagesSince(dayStartMs: Long): Long = v2.countSuccessfulImagesSince(dayStartMs)
}
