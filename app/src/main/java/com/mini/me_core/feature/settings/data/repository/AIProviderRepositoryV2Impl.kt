package com.mini.me_core.feature.settings.data.repository

import com.mini.me_core.core.security.CredentialEncryptor
import com.mini.me_core.core.util.FileLogger
import com.mini.me_core.core.util.EnumSafe
import com.mini.me_core.datalayer.isActive
import com.mini.me_core.datalayer.isEnabled
import com.mini.me_core.datalayer.useFullUrl
import com.mini.me_core.datalayer.useResponseApi
import com.mini.me_core.datalayer.repository.SettingsRepository as V2SettingsRepository
import com.mini.me_core.datalayer.store.KVStore
import com.mini.me_core.feature.settings.domain.model.AIProviderConfig
import com.mini.me_core.feature.settings.domain.model.ProviderType
import com.mini.me_core.feature.settings.domain.repository.AIProviderRepository
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import javax.inject.Inject
import javax.inject.Singleton

/**
 * AIProvider 仓库 V2 实现（v2-full-takeover P2-1 批 1：settings 域切 V2 读源）。
 *
 * 语义与 Room 版完全对齐（RC68 P0-1 active 互斥 / RC71 加密失败中止 / RC68 SCHEMA 38 只写密文列）：
 *  - 读：V2 observeProviders() / observeActiveProvider()（Flow 响应式，P0-1 补齐）；
 *  - 写：V2 saveProvider() 内含「isActive 时先 deactivateAll」事务化（比 Room 版的两步非原子更强）；
 *  - 加密：保留 CredentialEncryptor 列级加解密唯一入口，失败抛异常中止保存。
 */
@Singleton
class AIProviderRepositoryV2Impl @Inject constructor(
    private val v2: V2SettingsRepository,
    private val encryptor: CredentialEncryptor,
    private val kv: KVStore,
) : AIProviderRepository {

    private companion object {
        const val TAG = "AIProviderRepoV2"
        /** KVStore namespace：存储各 Provider 的 needsProxy 等辅助配置。 */
        const val KV_NAMESPACE = "provider"
        /** KVStore key 前缀：needs_proxy_<providerId> → Boolean。 */
        const val KV_KEY_NEEDS_PROXY_PREFIX = "needs_proxy_"
    }

    /** 读取某 Provider 的 needsProxy 配置；未设置时按类型给默认值（海外模型默认 true）。 */
    private fun readNeedsProxy(providerId: String, type: ProviderType): Boolean {
        kv.getBool(KV_NAMESPACE, KV_KEY_NEEDS_PROXY_PREFIX + providerId)?.let { return it }
        // 未显式设置时：海外官方模型默认需要代理，国内兼容端点默认直连。
        return when (type) {
            ProviderType.OPENAI, ProviderType.ANTHROPIC, ProviderType.GEMINI -> true
        }
    }

    override fun getAllProviders(): Flow<List<AIProviderConfig>> {
        return v2.observeProviders().map { rows ->
            buildList { for (row in rows) add(row.toDomainModel()) }
        }
    }

    override fun getActiveProvider(): Flow<AIProviderConfig?> {
        return v2.observeActiveProvider().map { it?.toDomainModel() }
    }

    override suspend fun getActiveProviderSync(): AIProviderConfig? {
        return v2.getActiveProvider()?.toDomainModel()
    }

    override suspend fun getProviderById(id: String): AIProviderConfig? {
        return v2.getProvider(id)?.toDomainModel()
    }

    override suspend fun saveProvider(provider: AIProviderConfig) {
        FileLogger.i(TAG, "保存提供商 id=${provider.id} name=${provider.name} active=${provider.isActive} enabled=${provider.isEnabled}")
        // RC71：先取已存在的密文，供「用户未改 API Key」时保留，避免空串覆盖。
        val existingEncrypted = runCatching {
            v2.getProvider(provider.id)?.encrypted_api_key ?: ""
        }.getOrDefault("")
        val encrypted = encryptApiKeyOrThrow(provider.apiKey, existingEncrypted)
        // V2 saveProvider 内含 RC68 P0-1 不变量（isActive 时先 deactivateAll），且为单事务。
        v2.saveProvider(
            id = provider.id,
            name = provider.name,
            type = provider.type.name,
            encryptedApiKey = encrypted,
            baseUrl = provider.baseUrl,
            defaultModel = provider.selectedModel.ifBlank { provider.defaultModel },
            isActive = provider.isActive,
            models = provider.models.joinToString("\n"),
            isEnabled = provider.isEnabled,
            useFullUrl = provider.useFullUrl,
            useResponseApi = provider.useResponseApi,
            temperature = provider.temperature.toDouble(),
            topP = provider.topP.toDouble(),
            maxTokens = provider.maxTokens?.toLong(),
            apiPath = provider.apiPath,
            requestTimeout = provider.requestTimeout.toLong(),
            retryCount = provider.retryCount.toLong(),
            fallbackProviderId = provider.fallbackProviderId,
            favoriteModels = provider.favoriteModels.joinToString(","),
            modelOrder = provider.modelOrder.joinToString(","),
        )
        // needsProxy：写入 KVStore（与核心 provider 表解耦，避免 SQLDelight schema migration）。
        kv.putBool(KV_NAMESPACE, KV_KEY_NEEDS_PROXY_PREFIX + provider.id, provider.needsProxy)
    }

    override suspend fun deleteProvider(id: String) {
        FileLogger.i(TAG, "删除提供商 id=$id")
        v2.deleteProvider(id)
    }

    override suspend fun setActiveProvider(id: String) {
        FileLogger.i(TAG, "切换启用提供商 id=$id")
        v2.setActiveProvider(id)
    }

    override suspend fun setSelectedModel(id: String, model: String) {
        FileLogger.i(TAG, "切换模型 provider=$id model=$model")
        v2.setDefaultModel(id, model)
    }

    override suspend fun updateModels(id: String, models: List<String>) {
        FileLogger.d(TAG, "更新模型列表 provider=$id 共 ${models.size} 个")
        v2.setModels(id, models.joinToString("\n"))
    }

    override suspend fun setProviderEnabled(id: String, isEnabled: Boolean) {
        FileLogger.i(TAG, "设置提供商显示启用开关 provider=$id isEnabled=$isEnabled")
        v2.setProviderEnabled(id, isEnabled)
    }

    override suspend fun ensureActiveProvider() {
        if (v2.getActiveProvider() != null) return
        val first = v2.listProviders().firstOrNull() ?: return
        FileLogger.i(TAG, "无激活提供商，自动激活首个: ${first.id} (${first.name})")
        v2.setActiveProvider(first.id)
    }

    /**
     * RC71：apiKey 非空 → 必须加密成功，失败抛异常中止保存（绝不写空串覆盖已有密文）；
     * apiKey 为空但已有密文 → 保留已有密文；都无 → 空串。
     */
    private suspend fun encryptApiKeyOrThrow(apiKey: String, existingEncrypted: String): String = when {
        apiKey.isNotBlank() -> {
            runCatching { encryptor.encrypt(apiKey) }
                .onFailure {
                    FileLogger.e(TAG, "加密 apiKey 失败，中止保存（避免覆盖已有密文）: ${it.message}", it)
                    throw IllegalStateException("API Key 加密失败，保存已中止: ${it.message}", it)
                }
                .getOrThrow()
        }
        existingEncrypted.isNotBlank() -> existingEncrypted
        else -> ""
    }

    /** 只从密文列解密；解密失败 → 空串 + 日志，不崩 UI（RC68 SCHEMA 38）。 */
    private suspend fun decryptApiKey(encryptedApiKey: String): String {
        if (encryptedApiKey.isEmpty()) return ""
        return runCatching { encryptor.decrypt(encryptedApiKey) }
            .onFailure { FileLogger.w(TAG, "解密 apiKey 失败，返回空串: ${it.message}") }
            .getOrDefault("")
    }

    private suspend fun com.mini.mecore.datalayer.sqldelight.settings.Ai_providers.toDomainModel(): AIProviderConfig {
        val modelList = models.split("\n").map { it.trim() }.filter { it.isNotEmpty() }
        val providerType = EnumSafe.valueOf(type, ProviderType.OPENAI, tag = "V2 Ai_providers.type")
        return AIProviderConfig(
            id = id,
            name = name,
            type = providerType,
            apiKey = decryptApiKey(encrypted_api_key),
            baseUrl = base_url,
            defaultModel = default_model,
            isActive = isActive,
            models = modelList,
            // RC68 SCHEMA 38：selectedModel 冗余概念已合并进 defaultModel（UI 上同一语义）。
            selectedModel = default_model,
            isEnabled = isEnabled,
            useFullUrl = useFullUrl,
            useResponseApi = useResponseApi,
            temperature = temperature.toFloat(),
            topP = top_p.toFloat(),
            maxTokens = max_tokens?.toInt(),
            apiPath = api_path,
            requestTimeout = request_timeout.toInt(),
            retryCount = retry_count.toInt(),
            fallbackProviderId = fallback_provider_id,
            favoriteModels = favorite_models.split(",").filter { it.isNotEmpty() },
            modelOrder = model_order.split(",").filter { it.isNotEmpty() },
            // needsProxy：从 KVStore 读取，未设置时按 ProviderType 给默认值。
            needsProxy = readNeedsProxy(id, providerType),
        )
    }
}
