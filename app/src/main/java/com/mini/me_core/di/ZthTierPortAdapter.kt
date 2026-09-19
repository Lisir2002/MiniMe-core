package com.mini.me_core.di

import com.mini.me_core.feature.agent.domain.zth.ZthPresetTier
import com.mini.me_core.feature.agent.domain.zth.ZthTierPort
import com.mini.me_core.feature.settings.data.repository.ZthTierRepository
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class ZthTierPortAdapter @Inject constructor(
    private val repo: ZthTierRepository,
) : ZthTierPort {
    override suspend fun getCurrentTier(): ZthPresetTier = repo.getCurrentTier()
}
