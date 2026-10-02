package com.mini.me_core.core.viewer.code

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/**
 * 代码查看器 ViewModel（兼容层）。
 *
 * 现已基于 sora-editor + TextMate 渲染，不再需要 native 高亮/符号提取。
 * 保留此类以维持调用方 API 兼容（ContainerFileViewer / AboutSection 仍传入）。
 */
class CodeViewerViewModel(app: Application) : AndroidViewModel(app) {

    data class UiState(
        val loading: Boolean = false,
        val error: String? = null,
    )

    private val _ui = MutableStateFlow(UiState())
    val ui: StateFlow<UiState> = _ui.asStateFlow()

    /** 兼容方法：打开文件（sora-editor 自行加载，此方法为空操作）。 */
    fun open(path: String) {
        // sora-editor 在 Composable 内自行加载文件，此处无需操作
    }

    override fun onCleared() {
        super.onCleared()
    }
}
