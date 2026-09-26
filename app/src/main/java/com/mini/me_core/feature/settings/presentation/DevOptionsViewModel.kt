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
 * 开发者选项 ViewModel。
 *
 * 调试开关持久化在 KVStore（namespace="dev_options"）。
 * 设置存储浏览读取 settings namespace 的全部键值（只读展示）。
 */
@HiltViewModel
class DevOptionsViewModel @Inject constructor(
    private val kv: KVStore,
    private val memoryMonitor: com.mini.me_core.core.performance.MemoryMonitor,
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

    private val _toggles = MutableStateFlow(loadToggles())
    val toggles: StateFlow<DevToggles> = _toggles.asStateFlow()

    private val _kvEntries = MutableStateFlow<List<Pair<String, String>>>(emptyList())
    val kvEntries: StateFlow<List<Pair<String, String>>> = _kvEntries.asStateFlow()

    /** 当前内存快照（开发者选项展示，2s 刷新）。 */
    private val _memory = MutableStateFlow(memoryMonitor.sample())
    val memory: StateFlow<com.mini.me_core.core.performance.MemoryMonitor.MemorySnapshot> =
        _memory.asStateFlow()

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
