package com.mini.me_core.di

import com.mini.me_core.feature.agent.domain.normflow.NormFlowSettingsPort
import com.mini.me_core.feature.settings.data.repository.NormFlowSettingsRepository
import kotlinx.coroutines.flow.Flow
import javax.inject.Inject
import javax.inject.Singleton

/**
 * [NormFlowSettingsPort] 适配：委派 settings 层 [NormFlowSettingsRepository]。
 *
 * 端口只暴露 agent/domain 实际用到的方法，保持方向 agent/domain -> settings 不反向。
 */
@Singleton
class NormFlowSettingsPortAdapter @Inject constructor(
    private val repo: NormFlowSettingsRepository,
) : NormFlowSettingsPort {

    override val normFlowEnabledFlow: Flow<Boolean> get() = repo.normFlowEnabledFlow

    override suspend fun isSopSummaryActive(): Boolean = repo.isSopSummaryActive()

    override suspend fun isPlaybookAutoActive(): Boolean = repo.isPlaybookAutoActive()
}
