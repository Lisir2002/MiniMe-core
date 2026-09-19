package com.mini.me_core.di

import com.mini.me_core.feature.agent.data.repository.AgentMessageAdapter
import com.mini.me_core.feature.agent.data.repository.GoalAdapter
import com.mini.me_core.feature.agent.data.repository.PlanAdapter
import com.mini.me_core.feature.agent.data.repository.TodoAdapter
import com.mini.me_core.feature.agent.data.repository.JobAdapter
import com.mini.me_core.feature.agent.data.repository.PlaybookAdapter
import com.mini.me_core.feature.agent.data.repository.ScheduleAdapter
import com.mini.me_core.feature.agent.data.repository.CheckpointAdapter
import com.mini.me_core.feature.agent.data.repository.FileEditHunkAdapter
import com.mini.me_core.feature.agent.data.repository.KvAdapter
import com.mini.me_core.feature.agent.data.repository.SkillAdapter
import com.mini.me_core.feature.agent.data.repository.FuseAdapter
import com.mini.me_core.feature.agent.data.repository.TrajectoryAdapter
import com.mini.me_core.feature.agent.data.repository.WakeAdapter
import com.mini.me_core.feature.agent.data.repository.AgentSessionAdapter
import com.mini.me_core.feature.agent.domain.goal.GoalPort
import com.mini.me_core.feature.agent.domain.plan.PlanPort
import com.mini.me_core.feature.agent.domain.tool.todo.TodoPort
import com.mini.me_core.feature.agent.domain.job.JobPort
import com.mini.me_core.feature.agent.domain.playbook.PlaybookPort
import com.mini.me_core.feature.agent.domain.schedule.SchedulePort
import com.mini.me_core.feature.agent.domain.trajectory.TrajectoryPort
import com.mini.me_core.feature.agent.domain.checkpoint.CheckpointPort
import com.mini.me_core.feature.agent.domain.skill.SkillPort
import com.mini.me_core.feature.agent.domain.tool.FileEditHunkPort
import com.mini.me_core.feature.agent.domain.container.ExecutionModePort
import com.mini.me_core.feature.backup.domain.BackupDataSource
import com.mini.me_core.feature.agent.domain.normflow.NormFlowSettingsPort
import com.mini.me_core.feature.agent.domain.zth.ZthTierPort
import com.mini.me_core.feature.agent.domain.mcp.server.KvPort
import com.mini.me_core.feature.agent.domain.zth.FusePort
import com.mini.me_core.feature.agent.domain.wake.WakePort
import com.mini.me_core.feature.agent.domain.session.AgentMessagePort
import com.mini.me_core.feature.agent.domain.session.AgentSessionPort
import dagger.Binds
import dagger.Module
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import javax.inject.Singleton

/** domain 端口 -> SQLDelight 适配器绑定（ARC-02：domain 不感知 datalayer）。 */
@Module
@InstallIn(SingletonComponent::class)
abstract class AgentPortsModule {
    @Binds
    @Singleton
    abstract fun bindAgentSessionPort(impl: AgentSessionAdapter): AgentSessionPort

    @Binds
    @Singleton
    abstract fun bindAgentMessagePort(impl: AgentMessageAdapter): AgentMessagePort

    @Binds
    @Singleton
    abstract fun bindGoalPort(impl: GoalAdapter): GoalPort

    @Binds
    @Singleton
    abstract fun bindPlanPort(impl: PlanAdapter): PlanPort

    @Binds
    @Singleton
    abstract fun bindTodoPort(impl: TodoAdapter): TodoPort

    @Binds
    @Singleton
    abstract fun bindWakePort(impl: WakeAdapter): WakePort

    @Binds
    @Singleton
    abstract fun bindSchedulePort(impl: ScheduleAdapter): SchedulePort

    @Binds
    @Singleton
    abstract fun bindJobPort(impl: JobAdapter): JobPort

    @Binds
    @Singleton
    abstract fun bindPlaybookPort(impl: PlaybookAdapter): PlaybookPort

    @Binds
    @Singleton
    abstract fun bindTrajectoryPort(impl: TrajectoryAdapter): TrajectoryPort

    @Binds
    @Singleton
    abstract fun bindFusePort(impl: FuseAdapter): FusePort

    @Binds
    @Singleton
    abstract fun bindCheckpointPort(impl: CheckpointAdapter): CheckpointPort

    @Binds
    @Singleton
    abstract fun bindSkillPort(impl: SkillAdapter): SkillPort

    @Binds
    @Singleton
    abstract fun bindFileEditHunkPort(impl: FileEditHunkAdapter): FileEditHunkPort

    @Binds
    @Singleton
    abstract fun bindKvPort(impl: KvAdapter): KvPort

    @Binds
    @Singleton
    abstract fun bindExecutionModePort(impl: ExecutionModePortAdapter): ExecutionModePort

    @Binds
    @Singleton
    abstract fun bindBackupDataSource(impl: AgentBackupDataSourceAdapter): BackupDataSource

    @Binds
    @Singleton
    abstract fun bindNormFlowSettingsPort(impl: NormFlowSettingsPortAdapter): NormFlowSettingsPort

    @Binds
    @Singleton
    abstract fun bindZthTierPort(impl: ZthTierPortAdapter): ZthTierPort
}
