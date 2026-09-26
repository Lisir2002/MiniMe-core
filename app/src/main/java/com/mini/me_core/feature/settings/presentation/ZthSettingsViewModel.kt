package com.mini.me_core.feature.settings.presentation

import android.content.Context
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.mini.me_core.R
import com.mini.me_core.core.util.FileLogger
import com.mini.me_core.feature.agent.domain.zth.ZthPerformanceClass
import com.mini.me_core.feature.agent.domain.zth.ZthPresetTier
import com.mini.me_core.feature.settings.data.repository.ZthTierRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import dagger.hilt.android.qualifiers.ApplicationContext
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
    /** 待确认的降级目标档位（非 null 时 UI 弹确认框）。 */
    val pendingDowngradeTier: ZthPresetTier? = null,
    /** 是否显示恢复默认确认框。 */
    val showResetConfirm: Boolean = false,
)

/**
 * ZTH 独立设置页 ViewModel。
 *
 * 从 [SecuritySettingsViewModel] 抽离：注入 [ZthTierRepository]，
 * 提供档位 / 性能等级 / 滑动确认开关的状态与操作。
 *
 * 加固：
 *  - 写入后立即从仓库读回校验，不一致则提示错误（UI 由仓库 Flow 自动回滚）。
 *  - 档位降级（高→低）需用户二次确认。
 *  - 所有用户可见文案走 strings.xml。
 */
@HiltViewModel
class ZthSettingsViewModel @Inject constructor(
    private val zthTierRepository: ZthTierRepository,
    @ApplicationContext private val context: Context,
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

    // ── 档位 ──────────────────────────────────────────────────────────────────

    /**
     * 入口：用户点选档位。若从高档降到低档（≥2 → ≤1），先弹确认框；否则直接写入。
     */
    fun onTierSelected(tier: ZthPresetTier) {
        val current = uiState.value.tier
        if (tier == current) return
        // 降级：BALANCED/STRICT(≥2) → DISABLED/MINIMAL(≤1) 需确认
        if (current.tier >= 2 && tier.tier < current.tier) {
            _localState.value = _localState.value.copy(pendingDowngradeTier = tier)
            return
        }
        commitTier(tier)
    }

    /** 用户在降级确认框中点「继续」。 */
    fun confirmDowngrade() {
        val target = _localState.value.pendingDowngradeTier ?: return
        _localState.value = _localState.value.copy(pendingDowngradeTier = null)
        commitTier(target)
    }

    /** 用户取消降级。 */
    fun dismissDowngrade() {
        _localState.value = _localState.value.copy(pendingDowngradeTier = null)
    }

    private fun commitTier(tier: ZthPresetTier) {
        viewModelScope.launch {
            try {
                zthTierRepository.setTier(tier)
                // 写后立即读回校验
                val readBack = zthTierRepository.getCurrentTier()
                if (readBack != tier) {
                    FileLogger.w("ZthSettingsVM", "档位写后读回不一致: want=$tier got=$readBack")
                    _localState.value = _localState.value.copy(
                        error = context.getString(R.string.settings_zth_msg_set_fail, "tier readback mismatch"),
                        successMessage = null,
                    )
                    return@launch
                }
                // tier 升到 ≥2 时，若 swipe 被仓库强制开启，Flow 会自动同步；无需此处处理
                val nameRes = when (tier) {
                    ZthPresetTier.DISABLED -> R.string.settings_zth_tier_name_disabled
                    ZthPresetTier.MINIMAL -> R.string.settings_zth_tier_name_minimal
                    ZthPresetTier.BALANCED -> R.string.settings_zth_tier_name_balanced
                    ZthPresetTier.STRICT -> R.string.settings_zth_tier_name_strict
                }
                _localState.value = _localState.value.copy(
                    successMessage = context.getString(R.string.settings_zth_msg_tier_set, context.getString(nameRes)),
                    error = null,
                )
            } catch (e: Exception) {
                FileLogger.w("ZthSettingsVM", "设置 ZTH 档位失败", e)
                _localState.value = _localState.value.copy(
                    error = context.getString(R.string.settings_zth_msg_set_fail, e.message ?: ""),
                    successMessage = null,
                )
            }
        }
    }

    // ── 性能等级 ──────────────────────────────────────────────────────────────

    fun setPerf(cls: ZthPerformanceClass) {
        viewModelScope.launch {
            try {
                zthTierRepository.setPerformanceClass(cls)
                val readBack = zthTierRepository.getCurrentPerformanceClass()
                if (readBack != cls) {
                    FileLogger.w("ZthSettingsVM", "性能等级写后读回不一致: want=$cls got=$readBack")
                    _localState.value = _localState.value.copy(
                        error = context.getString(R.string.settings_zth_msg_set_fail, "perf readback mismatch"),
                        successMessage = null,
                    )
                    return@launch
                }
                val nameRes = when (cls) {
                    ZthPerformanceClass.HIGH_END -> R.string.settings_zth_perf_name_high
                    ZthPerformanceClass.MID_RANGE -> R.string.settings_zth_perf_name_mid
                    ZthPerformanceClass.LOW_END_SKIP_LLM -> R.string.settings_zth_perf_name_low
                }
                _localState.value = _localState.value.copy(
                    successMessage = context.getString(R.string.settings_zth_msg_perf_set, context.getString(nameRes)),
                    error = null,
                )
            } catch (e: Exception) {
                FileLogger.w("ZthSettingsVM", "设置 ZTH 性能等级失败", e)
                _localState.value = _localState.value.copy(
                    error = context.getString(R.string.settings_zth_msg_set_fail, e.message ?: ""),
                    successMessage = null,
                )
            }
        }
    }

    // ── 滑动确认 ───────────────────────────────────────────────────────────────

    fun setSwipe(enabled: Boolean) {
        viewModelScope.launch {
            try {
                val current = zthTierRepository.getCurrentTier()
                zthTierRepository.setSwipeEnabled(enabled, current)
                // 仓库 tier≥2 时拒绝关闭；回读真实值，确保 UI 与仓库一致
                val actual = zthTierRepository.getCurrentSwipeEnabled()
                if (current.tier >= 2 && !enabled && !actual) {
                    // 仓库拒绝关闭但实际值仍为 true（默认），走错误提示
                    _localState.value = _localState.value.copy(
                        swipeEnabled = actual,
                        error = context.getString(R.string.settings_zth_msg_swipe_forced),
                        successMessage = null,
                    )
                } else {
                    _localState.value = _localState.value.copy(
                        swipeEnabled = actual,
                        successMessage = context.getString(
                            if (actual) R.string.settings_zth_msg_swipe_on
                            else R.string.settings_zth_msg_swipe_off
                        ),
                        error = null,
                    )
                }
            } catch (e: Exception) {
                FileLogger.w("ZthSettingsVM", "设置 ZTH 滑动确认失败", e)
                _localState.value = _localState.value.copy(
                    error = context.getString(R.string.settings_zth_msg_set_fail, e.message ?: ""),
                    successMessage = null,
                )
            }
        }
    }

    // ── 恢复默认 ──────────────────────────────────────────────────────────────

    fun requestResetDefault() {
        _localState.value = _localState.value.copy(showResetConfirm = true)
    }

    fun dismissResetConfirm() {
        _localState.value = _localState.value.copy(showResetConfirm = false)
    }

    fun confirmResetDefault() {
        viewModelScope.launch {
            try {
                zthTierRepository.setTier(ZthPresetTier.BALANCED)
                zthTierRepository.setPerformanceClass(ZthPerformanceClass.HIGH_END)
                zthTierRepository.setSwipeEnabled(true, ZthPresetTier.BALANCED)
                // 读回校验
                val tierOk = zthTierRepository.getCurrentTier() == ZthPresetTier.BALANCED
                val perfOk = zthTierRepository.getCurrentPerformanceClass() == ZthPerformanceClass.HIGH_END
                val swipeOk = zthTierRepository.getCurrentSwipeEnabled()
                if (tierOk && perfOk && swipeOk) {
                    _localState.value = _localState.value.copy(
                        showResetConfirm = false,
                        successMessage = context.getString(R.string.settings_zth_msg_reset_done),
                        error = null,
                    )
                } else {
                    _localState.value = _localState.value.copy(
                        showResetConfirm = false,
                        error = context.getString(R.string.settings_zth_msg_set_fail, "reset readback mismatch"),
                        successMessage = null,
                    )
                }
            } catch (e: Exception) {
                FileLogger.w("ZthSettingsVM", "恢复 ZTH 默认设置失败", e)
                _localState.value = _localState.value.copy(
                    showResetConfirm = false,
                    error = context.getString(R.string.settings_zth_msg_set_fail, e.message ?: ""),
                    successMessage = null,
                )
            }
        }
    }

    fun clearMessages() {
        _localState.value = _localState.value.copy(successMessage = null, error = null)
    }
}
