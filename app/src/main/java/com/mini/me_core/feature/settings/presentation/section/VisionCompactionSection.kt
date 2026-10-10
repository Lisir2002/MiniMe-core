package com.mini.me_core.feature.settings.presentation.section

import com.mini.me_core.feature.settings.data.repository.CompactionModelSettingsRepository
import com.mini.me_core.feature.settings.data.repository.VisionModelSettingsRepository
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.launch

/**
 * P2-17 行为保持搬迁：识图 / 压缩专用模型选择域。
 * 状态与逻辑原属 SettingsViewModel，现由其内部装配；协程仍由同一 viewModelScope 驱动。
 */
internal class VisionCompactionSection(
    private val scope: CoroutineScope,
    private val visionModelSettingsRepository: VisionModelSettingsRepository,
    private val compactionModelSettingsRepository: CompactionModelSettingsRepository,
) {
    /** 识图专用模型选择：providerId 为空即「跟随当前聊天模型」。 */
    private val _visionProviderId = MutableStateFlow("")
    val visionProviderId: StateFlow<String> = _visionProviderId.asStateFlow()

    private val _visionModel = MutableStateFlow("")
    val visionModel: StateFlow<String> = _visionModel.asStateFlow()

    /** 压缩专用模型选择：providerId 为空即「跟随当前聊天模型」。 */
    private val _compactionProviderId = MutableStateFlow("")
    val compactionProviderId: StateFlow<String> = _compactionProviderId.asStateFlow()

    private val _compactionModel = MutableStateFlow("")
    val compactionModel: StateFlow<String> = _compactionModel.asStateFlow()

    init {
        scope.launch {
            visionModelSettingsRepository.providerIdFlow.collectLatest {
                _visionProviderId.value = it
            }
        }

        scope.launch {
            visionModelSettingsRepository.modelFlow.collectLatest {
                _visionModel.value = it
            }
        }

        scope.launch {
            compactionModelSettingsRepository.providerIdFlow.collectLatest {
                _compactionProviderId.value = it
            }
        }

        scope.launch {
            compactionModelSettingsRepository.modelFlow.collectLatest {
                _compactionModel.value = it
            }
        }
    }

    /** 设置识图专用模型；providerId 留空等同 clearVisionModel（跟随聊天模型）。 */
    fun setVisionModel(providerId: String, model: String) {
        scope.launch {
            visionModelSettingsRepository.setVisionModel(providerId, model)
        }
    }

    /** 清空识图专用模型——回退到跟随当前聊天模型。 */
    fun clearVisionModel() {
        scope.launch {
            visionModelSettingsRepository.clear()
        }
    }

    /** 设置压缩专用模型；providerId 留空等同 clearCompactionModel（跟随聊天模型）。 */
    fun setCompactionModel(providerId: String, model: String) {
        scope.launch {
            compactionModelSettingsRepository.setCompactionModel(providerId, model)
        }
    }

    /** 清空压缩专用模型——回退到跟随当前聊天模型。 */
    fun clearCompactionModel() {
        scope.launch {
            compactionModelSettingsRepository.clear()
        }
    }
}
