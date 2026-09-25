package com.mini.me_core.feature.settings.presentation

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.mini.me_core.datalayer.store.KVStore
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import javax.inject.Inject

/**
 * F5.6 开发者选项 ViewModel。
 *
 * 调试开关持久化在 KVStore（namespace="dev_options"）。
 * 设置存储浏览读取 settings namespace 的全部键值（只读展示）。
 */
@HiltViewModel
class DevOptionsViewModel @Inject constructor(
    private val kv: KVStore,
    private val memoryMonitor: com.mini.me_core.core.performance.MemoryMonitor,
    private val batteryOptimizer: com.mini.me_core.core.performance.BatteryOptimizer,
) : ViewModel() {

    private companion object {
        const val NS = "dev_options"
        const val KEEP_SCREEN_ON = "keep_screen_on"
        const val FORCE_DARK = "force_dark"
        const val SLOW_NETWORK = "slow_network"
        const val LAYOUT_BOUNDS = "layout_bounds"
        const val GPU_OVERDRAW = "gpu_overdraw"
    }

    data class DevToggles(
        val keepScreenOn: Boolean = false,
        val forceDark: Boolean = false,
        val slowNetwork: Boolean = false,
        val layoutBounds: Boolean = false,
        val gpuOverdraw: Boolean = false,
    )

    private val _toggles = MutableStateFlow(loadToggles())
    val toggles: StateFlow<DevToggles> = _toggles.asStateFlow()

    private val _kvEntries = MutableStateFlow<List<Pair<String, String>>>(emptyList())
    val kvEntries: StateFlow<List<Pair<String, String>>> = _kvEntries.asStateFlow()

    /** F6.2：当前内存快照（开发者选项展示，2s 刷新）。 */
    private val _memory = MutableStateFlow(memoryMonitor.sample())
    val memory: StateFlow<com.mini.me_core.core.performance.MemoryMonitor.MemorySnapshot> =
        _memory.asStateFlow()

    /** F6.7：电池优化开关 + 当前是否实际处于省电模式。 */
    private val _batterySaverEnabled = MutableStateFlow(batteryOptimizer.isUserEnabled())
    val batterySaverEnabled: StateFlow<Boolean> = _batterySaverEnabled.asStateFlow()
    val batterySaverActive: StateFlow<Boolean> = batteryOptimizer.batterySaverActive

    fun setBatterySaverEnabled(v: Boolean) {
        batteryOptimizer.setUserEnabled(v)
        _batterySaverEnabled.value = v
    }

    init {
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
        forceDark = kv.getBool(NS, FORCE_DARK) ?: false,
        slowNetwork = kv.getBool(NS, SLOW_NETWORK) ?: false,
        layoutBounds = kv.getBool(NS, LAYOUT_BOUNDS) ?: false,
        gpuOverdraw = kv.getBool(NS, GPU_OVERDRAW) ?: false,
    )

    fun setKeepScreenOn(v: Boolean) = update(KEEP_SCREEN_ON, v) { copy(keepScreenOn = v) }
    fun setForceDark(v: Boolean) = update(FORCE_DARK, v) { copy(forceDark = v) }
    fun setSlowNetwork(v: Boolean) = update(SLOW_NETWORK, v) { copy(slowNetwork = v) }
    fun setLayoutBounds(v: Boolean) = update(LAYOUT_BOUNDS, v) { copy(layoutBounds = v) }
    fun setGpuOverdraw(v: Boolean) = update(GPU_OVERDRAW, v) { copy(gpuOverdraw = v) }

    private fun update(key: String, v: Boolean, copy: DevToggles.() -> DevToggles) {
        kv.putBool(NS, key, v)
        _toggles.value = _toggles.value.copy()
        _toggles.value = copy(_toggles.value)
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
