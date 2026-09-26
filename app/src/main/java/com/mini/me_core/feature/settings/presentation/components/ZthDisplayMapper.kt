package com.mini.me_core.feature.settings.presentation.components

import androidx.compose.runtime.Composable
import androidx.compose.ui.res.stringResource
import com.mini.me_core.R
import com.mini.me_core.feature.agent.domain.zth.ZthPerformanceClass
import com.mini.me_core.feature.agent.domain.zth.ZthPresetTier

/**
 * ZTH 枚举 → 中文/英文显示名与参数映射。
 *
 * UI 层专用：把领域枚举（DISABLED / MINIMAL / ...）映射为用户可读的显示名
 * 与该档位的完整参数（熔断阈值 / 幻觉阈值 / 是否强制滑动 / 是否允许取消 / LLM 终检）。
 * 领域模型 [ZthPresetTier] / [ZthPerformanceClass] 不持有 Android 资源，保持纯 Kotlin。
 */
object ZthDisplayMapper {

    /** 档位完整参数快照（用于状态摘要卡片与对比表）。 */
    data class TierParams(
        val displayNameRes: Int,
        val descriptionRes: Int,
        /** 熔断失败次数阈值；null = 永不熔断。 */
        val circuitBreakFails: Int?,
        /** 幻觉置信度阈值；null = 不判定。 */
        val hallucinationThreshold: Float?,
        /** 是否强制滑动确认。 */
        val swipeForced: Boolean,
        /** 是否允许用户取消确认卡。 */
        val cancelAllowed: Boolean,
        /** 是否启用 LLM 终检 / LLM 计划审批。 */
        val llmFinalCheck: Boolean,
        /** 是否自动熔断。 */
        val autoCircuitBreak: Boolean,
    )

    /** 性能等级参数快照。 */
    data class PerfParams(
        val displayNameRes: Int,
        val descriptionRes: Int,
    )

    /** 档位 → 参数表。数值与 ZthCircuitBreakerManager.FAIL_TRIP_BY_TIER / tierHallucinationThreshold 对齐。 */
    fun tierParams(tier: ZthPresetTier): TierParams = when (tier) {
        ZthPresetTier.DISABLED -> TierParams(
            displayNameRes = R.string.settings_zth_tier_name_disabled,
            descriptionRes = R.string.settings_zth_tier_disabled,
            circuitBreakFails = null,
            hallucinationThreshold = null,
            swipeForced = false,
            cancelAllowed = true,
            llmFinalCheck = false,
            autoCircuitBreak = false,
        )
        ZthPresetTier.MINIMAL -> TierParams(
            displayNameRes = R.string.settings_zth_tier_name_minimal,
            descriptionRes = R.string.settings_zth_tier_minimal,
            circuitBreakFails = 5,
            hallucinationThreshold = 0.9f,
            swipeForced = false,
            cancelAllowed = true,
            llmFinalCheck = false,
            autoCircuitBreak = true,
        )
        ZthPresetTier.BALANCED -> TierParams(
            displayNameRes = R.string.settings_zth_tier_name_balanced,
            descriptionRes = R.string.settings_zth_tier_balanced,
            circuitBreakFails = 3,
            hallucinationThreshold = 0.7f,
            swipeForced = true,
            cancelAllowed = true,
            llmFinalCheck = true,
            autoCircuitBreak = true,
        )
        ZthPresetTier.STRICT -> TierParams(
            displayNameRes = R.string.settings_zth_tier_name_strict,
            descriptionRes = R.string.settings_zth_tier_strict,
            circuitBreakFails = 2,
            hallucinationThreshold = 0.5f,
            swipeForced = true,
            cancelAllowed = false,
            llmFinalCheck = true,
            autoCircuitBreak = true,
        )
    }

    /** 性能等级 → 参数表。 */
    fun perfParams(perf: ZthPerformanceClass): PerfParams = when (perf) {
        ZthPerformanceClass.HIGH_END -> PerfParams(
            displayNameRes = R.string.settings_zth_perf_name_high,
            descriptionRes = R.string.settings_zth_perf_high,
        )
        ZthPerformanceClass.MID_RANGE -> PerfParams(
            displayNameRes = R.string.settings_zth_perf_name_mid,
            descriptionRes = R.string.settings_zth_perf_mid,
        )
        ZthPerformanceClass.LOW_END_SKIP_LLM -> PerfParams(
            displayNameRes = R.string.settings_zth_perf_name_low,
            descriptionRes = R.string.settings_zth_perf_low,
        )
    }

    /** 全部档位（按 tier 顺序）。 */
    val allTiers: List<ZthPresetTier> = ZthPresetTier.entries

    /** 全部性能等级（高→中→低）。 */
    val allPerfs: List<ZthPerformanceClass> = listOf(
        ZthPerformanceClass.HIGH_END,
        ZthPerformanceClass.MID_RANGE,
        ZthPerformanceClass.LOW_END_SKIP_LLM,
    )
}

/** Composable 便捷：读取档位显示名。 */
@Composable
fun ZthPresetTier.displayName(): String =
    stringResource(ZthDisplayMapper.tierParams(this).displayNameRes)

/** Composable 便捷：读取性能等级显示名。 */
@Composable
fun ZthPerformanceClass.displayName(): String =
    stringResource(ZthDisplayMapper.perfParams(this).displayNameRes)
