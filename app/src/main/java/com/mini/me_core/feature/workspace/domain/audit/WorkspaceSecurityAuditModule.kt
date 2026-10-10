package com.mini.me_core.feature.workspace.domain.audit

import com.mini.me_core.core.security.SecurityAuditSink
import dagger.Binds
import dagger.Module
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import javax.inject.Singleton

/**
 * feature/workspace 侧 Hilt 绑定：把 [SecurityAuditSink] 端口接到
 * [WorkspaceSecurityAuditSink]（转发远程审计仓储）。
 *
 * ⚠️ 与 B 组默认绑定的协调：B 组在 core/security/SecurityAuditModule.kt 中
 * @Binds 了 NoOpSecurityAuditSink。Hilt 不允许同一接口两个 @Binds，
 * 收口阶段必须二选一——本实现为正式转发实现，收口应删除/注释 B 侧 NoOp 绑定。
 */
@Module
@InstallIn(SingletonComponent::class)
abstract class WorkspaceSecurityAuditModule {

    @Binds
    @Singleton
    abstract fun bindWorkspaceSecurityAuditSink(
        impl: WorkspaceSecurityAuditSink,
    ): SecurityAuditSink
}
