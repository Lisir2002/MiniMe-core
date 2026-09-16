package com.mini.me_core.di

import com.mini.me_core.core.security.CredentialFieldRewriter
import com.mini.me_core.feature.credentials.data.repository.CredentialRepositoryV2Impl
import com.mini.me_core.feature.credentials.domain.repository.CredentialRepository
import com.mini.me_core.feature.settings.data.repository.AIProviderRepositoryV2Impl
import com.mini.me_core.feature.settings.domain.repository.AIProviderRepository
import com.mini.me_core.feature.t2i.data.repository.T2IRepositoryV2Impl
import com.mini.me_core.feature.t2i.domain.repository.T2IRepository
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import dagger.multibindings.ElementsIntoSet
import javax.inject.Singleton

/**
 * 业务 Repository DI（v2-full-takeover P2 批 1，去双路径后）。
 *
 * 数据层已完全由 V2 SQLDelight 接管，直接注入 V2 实现。
 * 旧 Room 实现类在 P3 剔除。
 */
@Module
@InstallIn(SingletonComponent::class)
object RepositoryModule {

    @Provides
    @Singleton
    fun provideAIProviderRepository(v2Impl: AIProviderRepositoryV2Impl): AIProviderRepository = v2Impl

    @Provides
    @Singleton
    fun provideCredentialRepository(v2Impl: CredentialRepositoryV2Impl): CredentialRepository = v2Impl

    @Provides
    @Singleton
    fun provideT2IRepository(v2Impl: T2IRepositoryV2Impl): T2IRepository = v2Impl

    /**
     * DEK 轮换字段重写器集合（multibinding）。
     *
     * 当前尚无任何加密域接入，提供空集合使 Hilt 可注入 `Set<CredentialFieldRewriter>`。
     * 后续某域接入时，新增 `@Provides @IntoSet fun xxx(): CredentialFieldRewriter` 即可并入；
     * 轮换在集合非空且真正重写字段时才会切换 DEK，否则安全拒绝（见 CredentialEncryptor）。
     */
    @Provides
    @ElementsIntoSet
    fun provideEmptyCredentialFieldRewriters(): Set<CredentialFieldRewriter> = emptySet()
}
