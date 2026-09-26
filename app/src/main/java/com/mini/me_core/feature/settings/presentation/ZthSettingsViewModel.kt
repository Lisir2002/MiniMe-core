package com.mini.me_core.feature.settings.presentation

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.mini.me_core.core.util.FileLogger
import com.mini.me_core.feature.agent.domain.zth.ZthPerformanceClass
import com.mini.me_core.feature.agent.domain.zth.ZthPresetTier
import com.mini.me_core.feature.settings.data.repository.ZthTierRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import javax.inject.Inject

/**
 * ZTH（零幻觉容忍）设置页 UI 状态。
 */
data class ZthUiState(
    val tier: ZthPresetTier = ZthPresetTier.BALANCED,
    val perfClass: ZthPerformanceClass = ZthPerformanceClass.HIGH_END,
    val swipeEnabled: Boolean = true,
    val successMessage: String? = null,
    val error: String? = null,
)

/**
 * ZTH 独立设置页 ViewModel。
 *
 * 从 [SecuritySettingsViewModel] 抽离：注入 [ZthTierRepository]，
 * 提供档位 / 性能等级 / 滑动确认开关的状态与操作。
 */
@HiltViewModel
class ZthSettingsViewModel @Inject constructor(
    private val zthTierRepository: ZthTierRepository,
) : ViewModel() {

    private val _localState = MutableStateFlow(ZthUiState())

    val uiState: StateFlow<ZthUiState> =
        combine(
            _localState,
            zthTierRepository.tierFlow,
            zthTierRepository.perfClassFlow,
            zthTierRepository.swipeEnabledFlow,
        ) { local, tier, perf, swipe ->
            local.copy(tier = tier, perfClass = perf, swipeEnabled = swipe)
        }.stateIn(
            scope = viewModelScope,
            started = SharingStarted.Eagerly,
            initialValue = ZthUiState(),
        )

    fun setTier(tier: ZthPresetTier) {
        viewModelScope.launch {
            try {
                zthTierRepository.setTier(tier)
                _localState.value = _localState.value.copy(
                    successMessage = "ZTH 档位已设置为：$tier",
                    error = null,
                )
            } catch (e: Exception) {
                FileLogger.w("ZthSettingsVM", "设置 ZTH 档位失败", e)
                _localState.value = _localState.value.copy(error = "设置档位失败: ${e.message}")
            }
        }
    }

    fun setPerf(cls: ZthPerformanceClass) {
        viewModelScope.launch {
            try {
                zthTierRepository.setPerformanceClass(cls)
                _localState.value = _localState.value.copy(
                    successMessage = "ZTH 性能等级已设置为：${cls.name}",
                    error = null,
                )
            } catch (e: Exception) {
                FileLogger.w("ZthSettingsVM", "设置 ZTH 性能等级失败", e)
                _localState.value = _localState.value.copy(error = "设置性能等级失败: ${e.message}")
            }
        }
    }

    fun setSwipe(enabled: Boolean) {
        viewModelScope.launch {
            try {
                val current = zthTierRepository.getCurrentTier()
                zthTierRepository.setSwipeEnabled(enabled, current)
                // tier>=2 时仓库拒绝关闭，回读真实值
                val actual = zthTierRepository.getCurrentSwipeEnabled()
                _localState.value = if (current.tier >= 2 && !enabled) {
                    _localState.value.copy(
                        swipeEnabled = actual,
                        error = "档位 BALANCED/STRICT：必须启用滑动确认，已阻止关闭。",
                        successMessage = null,
                    )
                } else {
                    _localState.value.copy(
                        swipeEnabled = actual,
                        successMessage = "ZTH 滑动确认：${if (enabled) "开" else "关"}（仅档位 MINIMAL 以下允许关闭）",
                        error = null,
                    )
                }
            } catch (e: Exception) {
                FileLogger.w("ZthSettingsVM", "设置 ZTH 滑动确认失败", e)
                _localState.value = _localState.value.copy(error = "设置滑动确认失败: ${e.message}")
            }
        }
    }

    fun clearMessages() {
        _localState.value = _localState.value.copy(successMessage = null, error = null)
    }
}
