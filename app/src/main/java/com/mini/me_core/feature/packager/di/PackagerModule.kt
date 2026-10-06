package com.mini.me_core.feature.packager.di

import android.content.Context
import com.mini.me_core.feature.packager.domain.repository.BuildStore
import com.mini.me_core.feature.packager.domain.repository.ProjectStore
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent
import javax.inject.Singleton

/**
 * 应用打包器 Hilt 依赖注入模块
 */
@Module
@InstallIn(SingletonComponent::class)
object PackagerModule {

    @Provides
    @Singleton
    fun provideProjectStore(@ApplicationContext context: Context): ProjectStore =
        ProjectStore(context)

    @Provides
    @Singleton
    fun provideBuildStore(
        @ApplicationContext context: Context,
        projectStore: ProjectStore
    ): BuildStore = BuildStore(context, projectStore)
}
