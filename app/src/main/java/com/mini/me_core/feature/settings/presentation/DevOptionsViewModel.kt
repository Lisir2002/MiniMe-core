package com.mini.me_core.feature.settings.presentation

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.mini.me_core.datalayer.store.KVStore
import com.mini.me_core.feature.settings.data.repository.NormFlowSettingsRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import javax.inject.Inject

/**
 * 开发者选项 ViewModel。
 *
 * 调试开关持久化在 KVStore（namespace="dev_options"）。
 * 设置存储浏览读取 settings namespace 的全部键值（只读展示）。
 */
@HiltViewModel
class DevOptionsViewModel @Inject constructor(
    private val kv: KVStore,
    private val memoryMonitor: com.mini.me_core.core.performance.MemoryMonitor,
    private val normFlowSettingsRepository: NormFlowSettingsRepository,
) : ViewModel() {

    private companion object {
        const val NS = "dev_options"
        const val KEEP_SCREEN_ON = "keep_screen_on"
        const val COMPOSE_LAYOUT_INSPECTOR = "compose_layout_inspector"
        const val STRICT_MODE = "strict_mode"
        const val WEBVIEW_DEBUG = "webview_debug"
    }

    data class DevToggles(
        val keepScreenOn: Boolean = false,
        val composeLayoutInspector: Boolean = false,
        val strictMode: Boolean = false,
        val webViewDebug: Boolean = false,
    )

    /**
     * G1：能力治理灰度开关。
     *
     * 这三个开关此前**只有仓储层的 setter、没有任何调用点**——用户无法开启，
     * 意味着批次 B/F1/F2 的成果默认状态下全部不可见。放在开发者选项而非普通设置，
     * 是因为它们都会改变对外行为（哪些工具进模型上下文、哪些能力对外可达、
     * 页面形态切换），属于需要用户明确知情的高影响开关。
     *
     * 数据源是 [NormFlowSettingsRepository] 的既有 Flow（而非另开 KV 读一遍），
     * 这样开关状态与消费侧（`McpManager` / `CapabilityRegistry` / `ToolLoadingPolicy`）
     * 读的是同一份来源，不会出现「界面显示已开、实际读到的还是旧值」。
     */
    data class FeatureToggles(
        /** B2：工具延迟加载——按需工具不常驻模型上下文。 */
        val deferredToolLoadingEnabled: Boolean = false,
        /** F1：内置能力 MCP 化——43 个内置工具成为三台进程内 MCP server。 */
        val builtinAsMcpEnabled: Boolean = false,
        /** F2/F4：统一能力页——内置分组 / MCP 服务 / 技能合并为一张列表。 */
        val unifiedCapabilityPageEnabled: Boolean = false,
        /**
         * 规范流总开关。
         *
         * 刻意放进同一模型：延迟加载的**生效条件**是
         * `normFlowEnabled && deferredToolLoadingEnabled`（见
         * `NormFlowSettingsRepository.isDeferredToolLoadingActive`），
         * 所以只开延迟加载、不开总开关时开关显示「已开」但实际无效果。
         * UI 必须能据此提示，否则就是一个骗人的开关。
         */
        val normFlowEnabled: Boolean = true,
    )

    private val _toggles = MutableStateFlow(loadToggles())
    val toggles: StateFlow<DevToggles> = _toggles.asStateFlow()

    private val _featureToggles = MutableStateFlow(FeatureToggles())
    val featureToggles: StateFlow<FeatureToggles> = _featureToggles.asStateFlow()

    private val _kvEntries = MutableStateFlow<List<Pair<String, String>>>(emptyList())
    val kvEntries: StateFlow<List<Pair<String, String>>> = _kvEntries.asStateFlow()

    /** 当前内存快照（开发者选项展示，2s 刷新）。 */
    private val _memory = MutableStateFlow(memoryMonitor.sample())
    val memory: StateFlow<com.mini.me_core.core.performance.MemoryMonitor.MemorySnapshot> =
        _memory.asStateFlow()

    init {
        // 灰度开关初值：读仓储的 Flow 首值。三个开关默认 false，
        // 与「存量行为完全一致」的灰度约定相同。
        viewModelScope.launch {
            normFlowSettingsRepository.deferredToolLoadingEnabledFlow.collect { v ->
                _featureToggles.value = _featureToggles.value.copy(deferredToolLoadingEnabled = v)
            }
        }
        viewModelScope.launch {
            normFlowSettingsRepository.builtinAsMcpEnabledFlow.collect { v ->
                _featureToggles.value = _featureToggles.value.copy(builtinAsMcpEnabled = v)
            }
        }
        viewModelScope.launch {
            normFlowSettingsRepository.unifiedCapabilityPageEnabledFlow.collect { v ->
                _featureToggles.value = _featureToggles.value.copy(unifiedCapabilityPageEnabled = v)
            }
        }
        viewModelScope.launch {
            normFlowSettingsRepository.normFlowEnabledFlow.collect { v ->
                _featureToggles.value = _featureToggles.value.copy(normFlowEnabled = v)
            }
        }
        refreshKv()
        viewModelScope.launch {
            while (true) {
                _memory.value = memoryMonitor.sample()
                kotlinx.coroutines.delay(2_000L)
            }
        }
    }

    private fun loadToggles() = DevToggles(
        keepScreenOn = kv.getBool(NS, KEEP_SCREEN_ON) ?: false,
        composeLayoutInspector = kv.getBool(NS, COMPOSE_LAYOUT_INSPECTOR) ?: false,
        strictMode = kv.getBool(NS, STRICT_MODE) ?: false,
        webViewDebug = kv.getBool(NS, WEBVIEW_DEBUG) ?: false,
    )

    fun setKeepScreenOn(v: Boolean) = update(KEEP_SCREEN_ON, v) { copy(keepScreenOn = v) }
    fun setComposeLayoutInspector(v: Boolean) =
        update(COMPOSE_LAYOUT_INSPECTOR, v) { copy(composeLayoutInspector = v) }
    fun setStrictMode(v: Boolean) =
        update(STRICT_MODE, v) { copy(strictMode = v) }
    fun setWebViewDebug(v: Boolean) =
        update(WEBVIEW_DEBUG, v) { copy(webViewDebug = v) }

    private fun update(key: String, v: Boolean, copy: DevToggles.() -> DevToggles) {
        kv.putBool(NS, key, v)
        _toggles.value = copy(_toggles.value)
    }

    fun setDeferredToolLoadingEnabled(v: Boolean) = viewModelScope.launch {
        normFlowSettingsRepository.setDeferredToolLoadingEnabled(v)
    }

    fun setBuiltinAsMcpEnabled(v: Boolean) = viewModelScope.launch {
        normFlowSettingsRepository.setBuiltinAsMcpEnabled(v)
    }

    fun setUnifiedCapabilityPageEnabled(v: Boolean) = viewModelScope.launch {
        normFlowSettingsRepository.setUnifiedCapabilityPageEnabled(v)
    }

    fun refreshKv() {
        viewModelScope.launch {
            _kvEntries.value = withContext(Dispatchers.IO) {
                kv.getAll("settings").map { e ->
                    val value = when (e.type) {
                        "bool" -> (e.boolVal?.let { it != 0L }).toString()
                        "int" -> e.intVal?.toString() ?: ""
                        "json" -> e.jsonVal ?: ""
                        else -> e.stringVal ?: ""
                    }
                    e.key to value
                }.sortedBy { it.first }
            }
        }
    }
}
