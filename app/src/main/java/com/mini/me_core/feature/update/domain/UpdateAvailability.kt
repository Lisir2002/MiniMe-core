package com.mini.me_core.feature.update.domain

/**
 * 更新可用性状态：用于关于页/版本更新入口的红点徽标，以及静默检查结果。
 */
sealed interface UpdateAvailability {
    /** 尚未检查（无缓存、未启动过网络请求）。 */
    data object Idle : UpdateAvailability

    /** 已是最新版本。 */
    data object UpToDate : UpdateAvailability

    /** 发现新版本，[latestTag] 为最新版本 tag。 */
    data class UpdateAvailable(val latestTag: String) : UpdateAvailability
}
