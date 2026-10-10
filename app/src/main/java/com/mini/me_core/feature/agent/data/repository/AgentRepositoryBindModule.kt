package com.mini.me_core.feature.agent.data.repository

import com.mini.me_core.feature.agent.domain.execution.mcp.McpConfigRepository
import com.mini.me_core.feature.agent.domain.execution.permission.PermissionRulesRepository
import com.mini.me_core.feature.agent.domain.knowledge.skill.SkillStateRepository
import dagger.Binds
import dagger.Module
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import javax.inject.Singleton

/**
 * 把 agent 域携带 Android IO/磁盘/DB 实现的 Repository 端口
 * 绑定到 data 层实现。实现均为 @Singleton @Inject，与原作用域一致。
 *
 * 独立 @Module 以避免与 [com.mini.me_core.di.AgentModule]（object，不能 @Binds）相互干扰。
 */
@Module
@InstallIn(SingletonComponent::class)
abstract class AgentRepositoryBindModule {

    @Binds
    @Singleton
    abstract fun bindMcpConfigRepository(impl: McpConfigRepositoryImpl): McpConfigRepository

    @Binds
    @Singleton
    abstract fun bindPermissionRulesRepository(impl: PermissionRulesRepositoryImpl): PermissionRulesRepository

    @Binds
    @Singleton
    abstract fun bindSkillStateRepository(impl: SkillStateRepositoryImpl): SkillStateRepository
}
