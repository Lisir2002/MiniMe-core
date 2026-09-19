package com.mini.me_core.di

import com.mini.me_core.core.agentworkflow.ModelMetadataView
import com.mini.me_core.core.agentworkflow.NormFlowSnapshot
import com.mini.me_core.core.agentworkflow.WorkflowSettingsPort
import com.mini.me_core.feature.settings.data.remote.ModelMetadataService
import com.mini.me_core.feature.settings.data.repository.CompatibilityPolicyRepository
import com.mini.me_core.feature.settings.data.repository.CompactionModelSettingsRepository
import com.mini.me_core.feature.settings.data.repository.NormFlowSettingsRepository
import com.mini.me_core.feature.settings.data.repository.VisionModelSettingsRepository
import com.mini.me_core.feature.settings.domain.model.ProviderType
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import kotlinx.coroutines.flow.first
import javax.inject.Singleton

/**
 * :core:agent-workflow 端口的 :app 装配（架构规则 #2/#5）。
 *
 * StatefulAgentWorkflow 原本直接依赖 6 个 settings 具体仓储，现只依赖 [WorkflowSettingsPort]；
 * 这里用适配器把端口接到真实仓储，返回纯数据视图，业务语义不变。
 */
@Module
@InstallIn(SingletonComponent::class)
object WorkflowSettingsModule {

    @Provides
    @Singleton
    fun provideWorkflowSettingsPort(
        normFlow: NormFlowSettingsRepository,
        compat: CompatibilityPolicyRepository,
        vision: VisionModelSettingsRepository,
        compaction: CompactionModelSettingsRepository,
        metadataService: ModelMetadataService,
    ): WorkflowSettingsPort = WorkflowSettingsPortAdapter(normFlow, compat, vision, compaction, metadataService)
}

private class WorkflowSettingsPortAdapter(
    private val normFlow: NormFlowSettingsRepository,
    private val compat: CompatibilityPolicyRepository,
    private val vision: VisionModelSettingsRepository,
    private val compaction: CompactionModelSettingsRepository,
    private val metadataService: ModelMetadataService,
) : WorkflowSettingsPort {

    override suspend fun isIdleConvergeActive() = normFlow.isIdleConvergeActive()
    override suspend fun isStepInjectActive() = normFlow.isStepInjectActive()
    override suspend fun isReasoningBudgetActive() = normFlow.isReasoningBudgetActive()
    override suspend fun isUsageCardActive() = normFlow.isUsageCardActive()
    override suspend fun isToolGuardActive() = normFlow.isToolGuardActive()

    /**
     * F5：一次性读总开关 + 7 个子开关 + step 预算，总开关与各子开关 AND 叠加成快照。
     * 业务语义与逐个调用 isXxxActive() 完全一致（每个 isXxxActive() 内部都是
     * normFlowEnabled && subEnabled）。idleResearchAware 仓库尚未提供，固定 false。
     */
    override suspend fun loadNormFlowSnapshot(): NormFlowSnapshot {
        val master = normFlow.normFlowEnabledFlow.first()
        return NormFlowSnapshot(
            stepInject = master && normFlow.stepInjectEnabledFlow.first(),
            toolGuard = master && normFlow.toolGuardEnabledFlow.first(),
            reasoningBudget = master && normFlow.reasoningBudgetEnabledFlow.first(),
            usageCard = master && normFlow.usageCardEnabledFlow.first(),
            playbookAuto = master && normFlow.playbookAutoEnabledFlow.first(),
            idleConverge = master && normFlow.idleConvergeEnabledFlow.first(),
            idleResearchAware = false,
            sopSummary = master && normFlow.sopSummaryEnabledFlow.first(),
            stepInjectBudget = normFlow.stepInjectBudgetFlow.first(),
        )
    }

    override suspend fun isAutoDowngradeOnSendFailure() = compat.isAutoDowngradeOnSendFailure()

    override suspend fun getViewImageUnknownGuardPolicyRaw(): String =
        compat.getViewImageUnknownGuardPolicy().name

    override suspend fun getVisionProviderId() = vision.getVisionProviderId()
    override suspend fun getVisionModel() = vision.getVisionModel()
    override suspend fun getCompactionProviderId() = compaction.getCompactionProviderId()
    override suspend fun getCompactionModel() = compaction.getCompactionModel()

    override suspend fun resolveModelMetadata(providerTypeRaw: String, model: String): ModelMetadataView? {
        val type = runCatching { ProviderType.valueOf(providerTypeRaw) }.getOrNull() ?: return null
        val md = metadataService.resolve(type, model)
        return ModelMetadataView(supportsVision = md.supportsVision)
    }
}
