package com.mini.me_core.feature.packager.di

import com.mini.me_core.feature.packager.data.repository.BuildStoreImpl
import com.mini.me_core.feature.packager.data.repository.ProjectStoreImpl
import com.mini.me_core.feature.packager.domain.repository.BuildStore
import com.mini.me_core.feature.packager.domain.repository.ProjectStore
import dagger.Binds
import dagger.Module
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import javax.inject.Singleton

/**
 * 应用打包器 Hilt 依赖注入模块。
 *
 * 端口绑定：[ProjectStore] / [BuildStore] 绑定到 data 层实现。实现类自带
 * @Inject @Singleton（通过 @ApplicationContext 拿私有目录），作用域与原 @Provides 一致。
 */
@Module
@InstallIn(SingletonComponent::class)
abstract class PackagerModule {

    @Binds
    @Singleton
    abstract fun bindProjectStore(impl: ProjectStoreImpl): ProjectStore

    @Binds
    @Singleton
    abstract fun bindBuildStore(impl: BuildStoreImpl): BuildStore
}
