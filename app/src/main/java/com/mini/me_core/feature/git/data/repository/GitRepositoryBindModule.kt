package com.mini.me_core.feature.git.data.repository

import com.mini.me_core.feature.git.domain.GitRepository
import dagger.Binds
import dagger.Module
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import javax.inject.Singleton

/**
 * 把 [GitRepository] 端口绑定到 data 层实现（容器命令执行 + 工作区文件直读）。
 */
@Module
@InstallIn(SingletonComponent::class)
abstract class GitRepositoryBindModule {

    @Binds
    @Singleton
    abstract fun bindGitRepository(impl: GitRepositoryImpl): GitRepository
}
