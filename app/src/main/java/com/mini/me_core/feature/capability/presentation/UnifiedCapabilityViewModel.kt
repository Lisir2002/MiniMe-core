package com.mini.me_core.feature.capability.presentation

import androidx.lifecycle.ViewModel
import com.mini.me_core.feature.capability.domain.CapabilityOverview
import com.mini.me_core.feature.capability.domain.CapabilityRegistry
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject
import kotlinx.coroutines.flow.StateFlow

/**
 * F2 统一能力页 ViewModel。
 *
 * 只做一件事：把 [CapabilityRegistry.overview] 原样透出。聚合逻辑全在 registry
 * （`@Singleton`，进程级），这里刻意**不做二次加工**——避免同一份数据在两层里
 * 各有一份映射，日后改动只改到一半（正是旧 `CapabilityCenterViewModel` 的问题：
 * 它既映射 UI 模型又自己聚合计数，构造期快照与响应式聚合混在一起）。
 *
 * 若聚合器需要改为随页面消亡（例如引入短生命周期资源），把逻辑搬回这里即可，
 * UI 侧零改动。
 */
@HiltViewModel
class UnifiedCapabilityViewModel @Inject constructor(
    capabilityRegistry: CapabilityRegistry
) : ViewModel() {

    /**
     * 直接透出聚合器的 `StateFlow`，**不再套一层 `stateIn`**：套了会每次访问都新建
     * 一个 StateFlow（`stateIn` 是转换而非读属性），既无收益又让订阅语义变得含糊。
     */
    val overview: StateFlow<CapabilityOverview> = capabilityRegistry.overview
}
