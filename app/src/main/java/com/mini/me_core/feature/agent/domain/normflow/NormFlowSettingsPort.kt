package com.mini.me_core.feature.agent.domain.normflow

import kotlinx.coroutines.flow.Flow

/**
 * NormFlow 开关读端口（纯 Kotlin）。
 *
 * 由 settings 层提供实现，避免 agent/domain 反向依赖 feature.settings.data.repository。
 */
interface NormFlowSettingsPort {
    /** NormFlow 总开关流。 */
    val normFlowEnabledFlow: Flow<Boolean>

    /** SOP 摘要是否启用（总开关 && sopSummary 子开关）。 */
    suspend fun isSopSummaryActive(): Boolean

    /** 剧本自动触发是否启用（总开关 && playbookAuto 子开关）。 */
    suspend fun isPlaybookAutoActive(): Boolean
}
