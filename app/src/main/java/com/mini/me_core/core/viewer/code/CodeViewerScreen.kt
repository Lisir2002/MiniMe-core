package com.mini.me_core.core.viewer.code

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import com.mini.me_core.feature.editor.SoraCodeViewer

/**
 * 代码查看器内容区（无 Scaffold / 无顶栏）。
 *
 * 基于 sora-editor + TextMate 全量语法高亮。查看/编辑共用同一实例，
 * 通过 [editable] 无感切换，不重建视图。顶栏由宿主统一提供。
 */
@Composable
fun CodeViewerScreen(
    path: String,
    viewModel: CodeViewerViewModel,
    editable: Boolean = false,
    onEditorReady: ((io.github.rosemoe.sora.widget.CodeEditor) -> Unit)? = null,
    onModified: (() -> Unit)? = null,
) {
    Box(modifier = Modifier.fillMaxSize()) {
        SoraCodeViewer(
            filePath = path,
            editable = editable,
            modifier = Modifier.fillMaxSize(),
            onEditorReady = onEditorReady,
            onModified = onModified,
        )
    }
}
