package com.mini.me_core.feature.capability.presentation

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.mini.me_core.feature.capability.domain.AssetBodyState
import com.mini.me_core.feature.capability.domain.AssetKind
import com.mini.me_core.feature.capability.domain.AssetListState
import com.mini.me_core.feature.capability.domain.CapabilityAssetsLoader
import com.mini.me_core.feature.agent.domain.execution.mcp.McpPromptDescriptor
import com.mini.me_core.feature.agent.domain.execution.mcp.McpResourceDescriptor
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch

/**
 * G4：分组详情页的 prompts / resources ViewModel。
 *
 * 职责只有「把 [CapabilityAssetsLoader] 的 suspend 调用接到 UI 生命周期上」，
 * **不含任何状态与映射逻辑**——那部分在 loader 里且已被单测覆盖。
 * 这里若再写一遍分支，就会出现两处同名字段各判一半的老问题
 * （正是旧 `CapabilityCenterViewModel` 的毛病）。
 *
 * 用 `viewModelScope` 而非单例 scope：这些请求服务于当前页面，页面关掉后
 * 没有理由继续占用协程——loader 里的 StateFlow 结果仍会保留，回来时直接可读。
 */
@HiltViewModel
class CapabilityGroupAssetsViewModel @Inject constructor(
    private val loader: CapabilityAssetsLoader
) : ViewModel() {

    fun promptsOf(serverName: String): StateFlow<AssetListState<McpPromptDescriptor>> =
        loader.promptsOf(serverName)

    fun resourcesOf(serverName: String): StateFlow<AssetListState<McpResourceDescriptor>> =
        loader.resourcesOf(serverName)

    fun bodyOf(kind: AssetKind, serverName: String, ref: String): StateFlow<AssetBodyState> =
        loader.bodyOf(kind, serverName, ref)

    fun loadPrompts(serverName: String, force: Boolean = false) {
        viewModelScope.launch { loader.loadPrompts(serverName, force) }
    }

    fun loadResources(serverName: String, force: Boolean = false) {
        viewModelScope.launch { loader.loadResources(serverName, force) }
    }

    fun fetchBody(
        kind: AssetKind,
        serverName: String,
        ref: String,
        arguments: Map<String, String> = emptyMap()
    ) {
        viewModelScope.launch { loader.fetchBody(kind, serverName, ref, arguments) }
    }
}
