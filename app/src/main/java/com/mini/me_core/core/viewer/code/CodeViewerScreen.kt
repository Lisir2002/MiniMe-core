package com.mini.me_core.core.viewer.code

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import com.mini.me_core.feature.editor.SoraCodeViewer

/**
 * 代码查看器内容区（无 Scaffold / 无顶栏）。
 *
 * 基于 sora-editor + TextMate 全量语法高亮（替代原 tree-sitter native 实现）。
 * 顶栏由宿主统一提供，遵循 UI 规范「顶栏唯一」。
 */
@Composable
fun CodeViewerScreen(
    path: String,
    viewModel: CodeViewerViewModel,
) {
    Box(modifier = Modifier.fillMaxSize()) {
        SoraCodeViewer(
            filePath = path,
            editable = false,
            modifier = Modifier.fillMaxSize(),
        )
    }
}
