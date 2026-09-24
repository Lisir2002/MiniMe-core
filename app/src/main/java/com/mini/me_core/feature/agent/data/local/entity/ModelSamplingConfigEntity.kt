package com.mini.me_core.feature.agent.data.local.entity

/**
 * 模型级采样参数覆盖：temperature / topP / maxTokens。
 *
 * 【物理隔离设计】
 * 与供应商级 [com.mini.me_core.feature.settings.domain.model.AIProviderConfig] 的
 * temperature/topP/maxTokens 字段严格分离——本实体只读写 model_sampling_configs 表，
 * 绝不修改 AIProviderConfig。清除模型覆盖只删本表行，供应商级默认值不受任何影响。
 *
 * 【严格优先级】
 * 字段为 null 表示「未覆盖，完全继承供应商级默认值」；非空即覆盖。
 * 最终生效值 = 模型级覆盖（非null）?: 供应商级默认，不做任何合并/插值计算。
 *
 * 与 [ModelCustomConfigEntity]（上下文长度）、[ModelCapabilityOverrideEntity]（能力开关）
 * 三张表完全独立，清除一张不影响其他。
 *
 * 主键 (providerType, modelId)，id 字段仅作冗余用于日志/调试。
 */
data class ModelSamplingConfigEntity(
    val id: String,
    val providerType: String,
    val modelId: String,
    /** null = 未覆盖（继承供应商级 temperature）；非空 = 已覆盖。 */
    val customTemperature: Float? = null,
    /** null = 未覆盖（继承供应商级 topP）；非空 = 已覆盖。 */
    val customTopP: Float? = null,
    /** null = 未覆盖（继承供应商级 maxTokens）；非空 = 已覆盖。 */
    val customMaxTokens: Int? = null,
    val updatedAtMs: Long = System.currentTimeMillis()
) {
    companion object {
        fun composeId(providerType: String, modelId: String): String = "$providerType:$modelId"
    }
}
