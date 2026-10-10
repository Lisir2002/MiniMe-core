package com.mini.me_core.datalayer.repository

import app.cash.sqldelight.coroutines.asFlow
import app.cash.sqldelight.coroutines.mapToList
import app.cash.sqldelight.coroutines.mapToOneOrNull
import com.mini.mecore.datalayer.sqldelight.AgentDb
import com.mini.mecore.datalayer.sqldelight.agent.Model_capability_overrides
import com.mini.mecore.datalayer.sqldelight.agent.Model_custom_configs
import com.mini.mecore.datalayer.sqldelight.agent.Model_sampling_configs
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.withContext

/**
 * 模型级配置存储：能力覆盖（vision/tools/reasoning/...）、自定义 token 上限、采样参数覆盖。
 * 三表均以 (providerType, modelId) 为主键，物理隔离但同属「模型覆盖」域。
 */
internal class AgentModelConfigStore(private val db: AgentDb) {

    private val q get() = db.agentQueries

    // ── 能力覆盖 ─────────────────────────────────────────────────────────

    @Suppress("LongParameterList") // 签名对应表结构，行为保持
    suspend fun upsertCapabilityOverride(
        id: String, providerType: String, modelId: String,
        overrideVision: Long?, overrideTools: Long?, overrideReasoning: Long?,
        overrideVideo: Long?, overrideAudio: Long?, overrideCode: Long?, overrideStructuredOutput: Long?,
        updatedAtMs: Long,
    ) = withContext(Dispatchers.IO) {
        q.insertCapabilityOverride(
            id, providerType, modelId, overrideVision, overrideTools, overrideReasoning,
            overrideVideo, overrideAudio, overrideCode, overrideStructuredOutput, updatedAtMs,
        )
    }

    suspend fun getCapabilityOverride(providerType: String, modelId: String): Model_capability_overrides? =
        withContext(Dispatchers.IO) { q.selectCapabilityOverride(providerType, modelId).executeAsOneOrNull() }

    fun observeCapabilityOverride(providerType: String, modelId: String): Flow<List<Model_capability_overrides>> =
        q.observeCapabilityOverride(providerType, modelId).asFlow().mapToList(Dispatchers.IO)

    suspend fun deleteCapabilityOverride(providerType: String, modelId: String) =
        withContext(Dispatchers.IO) { q.deleteCapabilityOverride(providerType, modelId) }

    suspend fun deleteCapabilityOverridesByProvider(providerType: String) =
        withContext(Dispatchers.IO) { q.deleteCapabilityOverridesByProvider(providerType) }

    // ── 模型自定义配置（输入/输出 token 上限覆盖）──────────────────────

    suspend fun upsertCustomConfig(
        id: String, providerType: String, modelId: String,
        customInputTokens: Long?, customOutputTokens: Long?, updatedAtMs: Long,
    ) = withContext(Dispatchers.IO) {
        q.insertCustomConfig(id, providerType, modelId, customInputTokens, customOutputTokens, updatedAtMs)
    }

    suspend fun getCustomConfig(providerType: String, modelId: String): Model_custom_configs? =
        withContext(Dispatchers.IO) { q.getCustomConfig(providerType, modelId).executeAsOneOrNull() }

    fun observeCustomConfig(providerType: String, modelId: String): Flow<Model_custom_configs?> =
        q.observeCustomConfig(providerType, modelId).asFlow().mapToOneOrNull(Dispatchers.IO)

    suspend fun deleteCustomConfig(providerType: String, modelId: String) =
        withContext(Dispatchers.IO) { q.deleteCustomConfig(providerType, modelId) }

    suspend fun deleteCustomConfigsByProvider(providerType: String) =
        withContext(Dispatchers.IO) { q.deleteCustomConfigsByProvider(providerType) }

    // ── 模型级采样参数覆盖（temperature/topP/maxTokens，与供应商级物理隔离）──────────

    suspend fun upsertSamplingConfig(
        id: String, providerType: String, modelId: String,
        customTemperature: Double?, customTopP: Double?, customMaxTokens: Long?, updatedAtMs: Long,
    ) = withContext(Dispatchers.IO) {
        q.insertSamplingConfig(id, providerType, modelId, customTemperature, customTopP, customMaxTokens, updatedAtMs)
    }

    suspend fun getSamplingConfig(providerType: String, modelId: String): Model_sampling_configs? =
        withContext(Dispatchers.IO) { q.getSamplingConfig(providerType, modelId).executeAsOneOrNull() }

    fun observeSamplingConfig(providerType: String, modelId: String): Flow<Model_sampling_configs?> =
        q.observeSamplingConfig(providerType, modelId).asFlow().mapToOneOrNull(Dispatchers.IO)

    suspend fun deleteSamplingConfig(providerType: String, modelId: String) =
        withContext(Dispatchers.IO) { q.deleteSamplingConfig(providerType, modelId) }

    suspend fun deleteSamplingConfigsByProvider(providerType: String) =
        withContext(Dispatchers.IO) { q.deleteSamplingConfigsByProvider(providerType) }
}
