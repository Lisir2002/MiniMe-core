package com.mini.me_core.feature.agent.domain.zth

/**
 * Zth 档位读端口（纯 Kotlin）。由 settings 层实现，避免 agent/domain 反向依赖 ZthTierRepository。
 */
interface ZthTierPort {
    suspend fun getCurrentTier(): ZthPresetTier
}
