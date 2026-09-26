package com.mini.me_core.feature.update.presentation

import androidx.lifecycle.ViewModel
import com.mini.me_core.feature.update.domain.UpdateAvailability
import com.mini.me_core.feature.update.data.GitHubReleaseRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.StateFlow
import javax.inject.Inject

/**
 * 供关于页「检查更新」入口使用的轻量 ViewModel：
 * 仅暴露仓库的更新可用性流（红点 / 有更新徽标），不触发额外网络请求。
 * 静默检查由 Application 启动时驱动。
 */
@HiltViewModel
class UpdateBadgeViewModel @Inject constructor(
    repository: GitHubReleaseRepository,
) : ViewModel() {
    val availability: StateFlow<UpdateAvailability> = repository.availability
}
