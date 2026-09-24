package com.mini.me_core.feature.agent.data.local.entity

/**
 * 模型自定义配置：用户手动覆盖的输入/输出 token 上限。
 *
 * 与 [ModelCapabilityOverrideEntity] 分离：能力覆盖是「开/关」三态，
 * 这里是数值型配置（可空表示不覆盖，留空跟随自动检测）。
 *
 * 主键 (providerType, modelId)，id 字段仅作冗余用于日志/调试。
 */
data class ModelCustomConfigEntity(
    val id: String,
    val providerType: String,
    val modelId: String,
    val customInputTokens: Int? = null,
    val customOutputTokens: Int? = null,
    val updatedAtMs: Long = System.currentTimeMillis()
) {
    companion object {
        fun composeId(providerType: String, modelId: String): String = "$providerType:$modelId"
    }
}
