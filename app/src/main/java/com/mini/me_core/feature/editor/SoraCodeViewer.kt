package com.mini.me_core.feature.editor

import android.content.Context
import android.util.TypedValue
import android.view.View
import android.view.ViewGroup
import android.widget.FrameLayout
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.size
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.BrokenImage
import androidx.compose.material.icons.rounded.KeyboardArrowLeft
import androidx.compose.material.icons.rounded.KeyboardArrowRight
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import io.github.rosemoe.sora.widget.CodeEditor
import io.github.rosemoe.sora.widget.component.EditorAutoCompletion
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
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
    var isBinary by remember { mutableStateOf(false) }
    var loadError by remember { mutableStateOf<String?>(null) }

    // 自适应高亮引擎
    val highlighter = remember { com.mini.me_core.feature.editor.core.AdaptiveHighlighter(scope) }

    // Markdown 高亮引擎门面（行内代码着色 + 共享 LRU 缓存）
    val markdownHighlighter = remember { com.mini.me_core.feature.markdown.MarkdownHighlighter() }

    // TextMate 引擎只需初始化一次（幂等，内部有已初始化判断）
    LaunchedEffect(Unit) {
        val appCtx = context.applicationContext
        com.mini.me_core.feature.editor.textmate.TextMateManager
            .initialize(appCtx)
        // 代码片段子系统：内置片段加载 + 自定义片段 + 偏好
        withContext(Dispatchers.IO) {
            com.mini.me_core.feature.editor.snippets.SnippetRepository.initialize(appCtx)
            com.mini.me_core.feature.editor.snippets.CustomSnippetStore.initialize(appCtx)
        }
        com.mini.me_core.feature.editor.snippets.SnippetSettings.initialize(appCtx)
    }

    // 加载文件（二进制检测 + 性能优化：单次读取、行数顺便统计）
    LaunchedEffect(filePath) {
        loaded = false
        isBinary = false
        loadError = null
        withContext(Dispatchers.IO) {
            val file = File(filePath)
            if (!file.exists()) {
                loadError = "文件不存在"
                loaded = true
                return@withContext
            }
            val raw = file.readBytes()
            // 二进制检测：前 8KB 中 NULL 字节占比 > 30% 判定为二进制
            val sampleSize = minOf(raw.size, 8192)
            var nullCount = 0
            for (i in 0 until sampleSize) {
                if (raw[i] == 0x00.toByte()) nullCount++
            }
            if (sampleSize > 0 && nullCount * 100 / sampleSize > 30) {
                isBinary = true
                loaded = true
                return@withContext
            }
            // UTF-8 BOM 剥离
            val content = if (raw.size >= 3 &&
                raw[0] == 0xEF.toByte() && raw[1] == 0xBB.toByte() && raw[2] == 0xBF.toByte()
            ) {
                raw.copyOfRange(3, raw.size).toString(Charsets.UTF_8)
            } else {
                raw.toString(Charsets.UTF_8)
            }
            // 语言检测（取前 4KB 样本）
            val detected = com.mini.me_core.feature.editor.detect.LanguageDetector
                .detect(file, content.take(4096))
            // 行数统计：遍历一次同时统计（比单独 count 更高效）
            var lines = 1
            for (c in content) {
                if (c == '\n') lines++
            }
            text = content
            scopeName = detected
            lineCount = lines
        }
        loaded = true
    }

    // 文本或语言变化时设置到编辑器
    LaunchedEffect(loaded, text, scopeName, isBinary, loadError) {
        if (!loaded || isBinary || loadError != null) return@LaunchedEffect
        editor.setText(text)
        editor.isEditable = editable
        // 先统一设置 colorScheme，确保所有文件（无论是否有高亮）背景色一致
        editor.colorScheme = com.mini.me_core.feature.editor.textmate.TextMateManager
            .createColorScheme()
        try {
            if (scopeName == "text.html.markdown" || scopeName == "text.html.markdown.enhanced") {
                // Markdown 文件：使用带行内代码着色的 MarkdownLanguage。
                // 不调用 highlighter.attach()——其内部会重新创建并 setEditorLanguage，
                // 覆盖掉 MarkdownLanguage；sora-editor 本身已有按需渲染，滚动降级非必须。
                val mdLang = markdownHighlighter.createLanguage()
                editor.setEditorLanguage(mdLang)
            } else {
                // 普通文件：TextMateLanguage + 自适应高亮引擎（大文件自动降级）
                val inner = com.mini.me_core.feature.editor.textmate.TextMateManager
                    .createLanguage(scopeName, autoCompletion = editable)
                // 编辑模式下用片段补全包装器包裹原生语言
                val lang = if (editable) {
                    com.mini.me_core.feature.editor.snippets.SnippetLanguageWrapper(inner, scopeName)
                } else {
                    inner
                }
                editor.setEditorLanguage(lang)
                // 绑定当前文件，使 $TM_FILENAME 等变量可解析
                com.mini.me_core.feature.editor.snippets.SnippetEditorBridge.bindFile(editor, filePath)
                // 接入自适应高亮引擎（大文件自动降级）
                highlighter.attach(editor, scopeName, lineCount)
            }
        } catch (e: Exception) {
            android.util.Log.w("SoraCodeViewer", "设置 TextMate 高亮失败: $scopeName，回退纯文本", e)
        }
        onEditorReady?.invoke(editor)
    }

    // Markdown 文件：监听 TextMate 主题名变化，变化时清空行内代码颜色缓存
    LaunchedEffect(scopeName) {
        val isMarkdown = scopeName == "text.html.markdown" || scopeName == "text.html.markdown.enhanced"
        if (!isMarkdown) return@LaunchedEffect
        var lastTheme = com.mini.me_core.feature.editor.textmate.TextMateManager.currentThemeName
        while (true) {
            kotlinx.coroutines.delay(500)
            val current = com.mini.me_core.feature.editor.textmate.TextMateManager.currentThemeName
            if (current != lastTheme) {
                lastTheme = current
                markdownHighlighter.onThemeChanged()
            }
        }
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
            highlighter.detach()
            markdownHighlighter.onDestroy()
            editor.release()
        }
    }

    // 二进制文件或加载错误时显示提示，否则显示编辑器
    if (isBinary) {
        androidx.compose.foundation.layout.Box(
            modifier = modifier.fillMaxSize(),
            contentAlignment = androidx.compose.ui.Alignment.Center
        ) {
            androidx.compose.foundation.layout.Column(
                horizontalAlignment = androidx.compose.ui.Alignment.CenterHorizontally
            ) {
                androidx.compose.material3.Icon(
                    imageVector = androidx.compose.material.icons.Icons.Rounded.BrokenImage,
                    contentDescription = null,
                    modifier = Modifier.size(48.dp),
                    tint = androidx.compose.material3.MaterialTheme.colorScheme.onSurfaceVariant
                )
                androidx.compose.foundation.layout.Spacer(Modifier.height(12.dp))
                androidx.compose.material3.Text(
                    text = "二进制文件",
                    style = androidx.compose.material3.MaterialTheme.typography.titleMedium
                )
                androidx.compose.foundation.layout.Spacer(Modifier.height(4.dp))
                androidx.compose.material3.Text(
                    text = "此文件为二进制格式，不支持文本查看",
                    style = androidx.compose.material3.MaterialTheme.typography.bodyMedium,
                    color = androidx.compose.material3.MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }
    } else if (loadError != null) {
        androidx.compose.foundation.layout.Box(
            modifier = modifier.fillMaxSize(),
            contentAlignment = androidx.compose.ui.Alignment.Center
        ) {
            androidx.compose.material3.Text(
                text = loadError ?: "加载失败",
                style = androidx.compose.material3.MaterialTheme.typography.bodyLarge,
                color = androidx.compose.material3.MaterialTheme.colorScheme.error
            )
        }
    } else {
        var snippetActive by remember { mutableStateOf(false) }
        LaunchedEffect(editor, editable) {
            while (true) {
                snippetActive = editable &&
                    com.mini.me_core.feature.editor.snippets.SnippetEditorBridge.isSnippetActive(editor)
                delay(150)
            }
        }
        Column(modifier = modifier) {
            AndroidView(
                factory = { editor },
                modifier = Modifier
                    .weight(1f)
                    .fillMaxWidth(),
            )
            if (snippetActive) {
                SnippetNavBar(
                    onPrev = {
                        com.mini.me_core.feature.editor.snippets.SnippetEditorBridge.prevPlaceholder(editor)
                    },
                    onNext = {
                        com.mini.me_core.feature.editor.snippets.SnippetEditorBridge.nextPlaceholder(editor)
                    },
                )
            }
        }
    }
}

/**
 * 片段展开会话中的占位符跳转栏（移动端无 Tab 键的替代）。
 * 仅在 [SnippetEditorBridge.isSnippetActive] 为真时显示。
 */
@Composable
private fun SnippetNavBar(onPrev: () -> Unit, onNext: () -> Unit) {
    androidx.compose.material3.Surface(tonalElevation = 2.dp) {
        androidx.compose.foundation.layout.Row(
            modifier = Modifier
                .fillMaxWidth()
                .height(44.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            androidx.compose.material3.TextButton(onClick = onPrev, modifier = Modifier.weight(1f)) {
                Icon(
                    imageVector = Icons.Rounded.KeyboardArrowLeft,
                    contentDescription = stringResource(com.mini.me_core.R.string.snippet_nav_prev),
                )
                Spacer(Modifier.size(4.dp))
                Text(stringResource(com.mini.me_core.R.string.snippet_nav_prev))
            }
            androidx.compose.material3.TextButton(onClick = onNext, modifier = Modifier.weight(1f)) {
                Text(stringResource(com.mini.me_core.R.string.snippet_nav_next))
                Spacer(Modifier.size(4.dp))
                Icon(
                    imageVector = Icons.Rounded.KeyboardArrowRight,
                    contentDescription = stringResource(com.mini.me_core.R.string.snippet_nav_next),
                )
            }
        }
    }
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
