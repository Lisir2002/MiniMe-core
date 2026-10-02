package com.mini.me_core.feature.editor

import android.content.Context
import android.util.TypedValue
import android.view.View
import android.view.ViewGroup
import android.widget.FrameLayout
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import io.github.rosemoe.sora.widget.CodeEditor
import io.github.rosemoe.sora.widget.component.EditorAutoCompletion
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File

/**
 * sora-editor 代码查看/编辑统一组件。
 *
 * 查看/编辑共用同一个 [CodeEditor] 实例，通过 [editable] 切换模式（不重建视图）。
 *
 * @param filePath 文件路径
 * @param editable true=编辑模式，false=只读查看
 * @param fontSizeSp 字体大小（sp）
 * @param onEditorReady 编辑器就绪回调（暴露 CodeEditor 供外部做搜索/跳转行号等）
 * @param onModified 文档变更回调（编辑模式下内容改变）
 * @param onSaveNeeded 请求保存回调
 */
@Composable
fun SoraCodeViewer(
    filePath: String,
    editable: Boolean = false,
    fontSizeSp: Int = 14,
    onEditorReady: ((CodeEditor) -> Unit)? = null,
    onModified: (() -> Unit)? = null,
    modifier: Modifier = Modifier,
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()

    // 编辑器实例保存在 remember 中，切换模式不重建
    val editor = remember {
        CodeEditor(context).apply {
            layoutParams = FrameLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.MATCH_PARENT
            )
            // 基础配置
            typefaceText = android.graphics.Typeface.MONOSPACE
            typefaceLineNumber = android.graphics.Typeface.MONOSPACE
            nonPrintablePaintingFlags =
                CodeEditor.FLAG_DRAW_WHITESPACE_LEADING or
                CodeEditor.FLAG_DRAW_LINE_SEPARATOR
        }
    }

    // 文件内容加载状态
    var text by remember { mutableStateOf("") }
    var scopeName by remember { mutableStateOf("text.plain") }
    var lineCount by remember { mutableIntStateOf(0) }
    var loaded by remember { mutableStateOf(false) }

    // 加载文件
    LaunchedEffect(filePath) {
        withContext(Dispatchers.IO) {
            val file = File(filePath)
            if (file.exists()) {
                val raw = file.readBytes()
                // 简单 UTF-8 BOM 剥离
                val content = if (raw.size >= 3 &&
                    raw[0] == 0xEF.toByte() && raw[1] == 0xBB.toByte() && raw[2] == 0xBF.toByte()
                ) {
                    raw.copyOfRange(3, raw.size).toString(Charsets.UTF_8)
                } else {
                    raw.toString(Charsets.UTF_8)
                }
                val detected = com.mini.me_core.feature.editor.detect.LanguageDetector
                    .detect(file, content.take(4096))
                text = content
                scopeName = detected
                lineCount = content.count { it == '\n' } + 1
            }
        }
        loaded = true
    }

    // 文本或语言变化时设置到编辑器
    LaunchedEffect(loaded, text, scopeName) {
        if (!loaded) return@LaunchedEffect
        // 确保 TextMate 引擎已初始化
        com.mini.me_core.feature.editor.textmate.TextMateManager
            .initialize(context.applicationContext)

        editor.setText(text)
        editor.isEditable = editable
        try {
            val lang = com.mini.me_core.feature.editor.textmate.TextMateManager
                .createLanguage(scopeName, autoCompletion = editable)
            editor.setEditorLanguage(lang)
            editor.colorScheme = com.mini.me_core.feature.editor.textmate.TextMateManager
                .createColorScheme()
        } catch (e: Exception) {
            android.util.Log.w("SoraCodeViewer", "设置 TextMate 高亮失败: $scopeName", e)
        }
        onEditorReady?.invoke(editor)
    }

    // 编辑模式切换
    LaunchedEffect(editable) {
        editor.isEditable = editable
        editor.getComponent(EditorAutoCompletion::class.java).isEnabled = editable
    }

    // 字体大小
    LaunchedEffect(fontSizeSp) {
        editor.setTextSize(fontSizeSp.toFloat())
    }

    // 释放
    DisposableEffect(Unit) {
        onDispose {
            editor.release()
        }
    }

    AndroidView(
        factory = { editor },
        modifier = modifier,
    )
}

/** 获取编辑器当前文本（保存时调用）。 */
fun CodeEditor.currentText(): String = text.toString()

/** 跳转指定行（0-based）。setSelection 会自动滚动到可见位置。 */
fun CodeEditor.jumpToLine(line: Int) {
    setSelection(line, 0)
}

/** 保存到文件。 */
suspend fun CodeEditor.saveToFile(path: String): Boolean = withContext(Dispatchers.IO) {
    runCatching {
        File(path).writeText(text.toString())
        true
    }.getOrDefault(false)
}
