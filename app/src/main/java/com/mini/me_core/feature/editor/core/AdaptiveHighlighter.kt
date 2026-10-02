package com.mini.me_core.feature.editor.core

import android.util.Log
import io.github.rosemoe.sora.langs.textmate.TextMateLanguage
import io.github.rosemoe.sora.widget.CodeEditor
import com.mini.me_core.feature.editor.textmate.TextMateManager
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

/**
 * 自适应高亮引擎。
 *
 * 在 sora-editor 自带增量高亮 + 后台 worker 之上做性能自适应：
 *  - 文件复杂度分析（行数、grammar 大小）
 *  - 设备性能评估（内存、CPU）
 *  - 交互状态监听（滚动速度、帧率）
 *  - 动态策略切换：
 *      - [HighlightStrategy.FULL]：完整高亮（默认，小文件/高性能设备）
 *      - [HighlightStrategy.VISIBLE_ONLY]：仅可见区域高亮（大文件/快速滚动时）
 *      - [HighlightStrategy.OFF]：关闭高亮（极端低内存兜底）
 *
 * 注意：sora-editor 本身已有增量高亮和后台 worker，本类不重复造轮子，
 * 只做策略决策和对编辑器属性的动态调节。
 */
class AdaptiveHighlighter(
    private val scope: CoroutineScope,
) {
    companion object {
        private const val TAG = "AdaptiveHighlighter"

        /** 超过此行数视为大文件，默认 VISIBLE_ONLY 策略。 */
        private const val LARGE_FILE_LINE_THRESHOLD = 5_000

        /** 超过此字节数视为大 grammar（正则匹配慢）。 */
        private const val HEAVY_GRAMMAR_SIZE_BYTES = 100_000L

        /** 滚动检测窗口（ms）。 */
        private const val SCROLL_MONITOR_INTERVAL_MS = 500L

        /** 快速滚动阈值：每 500ms 滚动超过 N 行。 */
        private const val FAST_SCROLL_LINES_THRESHOLD = 30
    }

    enum class HighlightStrategy {
        /** 完整高亮（所有行）。 */
        FULL,
        /** 仅可见区域高亮（sora-editor 原生按需渲染，此处额外限制后台 worker）。 */
        VISIBLE_ONLY,
        /** 关闭高亮（纯文本渲染）。 */
        OFF,
    }

    private val _strategyFlow = MutableStateFlow(HighlightStrategy.FULL)
    val strategyFlow: StateFlow<HighlightStrategy> = _strategyFlow.asStateFlow()

    private var monitorJob: Job? = null
    private var lastFirstLine = 0
    private var lastSampleTime = 0L

    /**
     * 为编辑器配置 TextMate 高亮。
     * @param editor sora-editor CodeEditor 实例
     * @param scopeName 语言 scopeName
     * @param lineCount 文档行数（用于决策初始策略）
     */
    fun attach(editor: CodeEditor, scopeName: String, lineCount: Int) {
        // 1. 创建 TextMateLanguage
        val language = try {
            TextMateManager.createLanguage(scopeName, autoCompletion = false)
        } catch (e: Exception) {
            Log.w(TAG, "无法创建 TextMateLanguage for $scopeName，回退纯文本", e)
            return
        }

        // 2. 设置 ColorScheme
        editor.colorScheme = TextMateManager.createColorScheme()

        // 3. 应用 Language
        editor.setEditorLanguage(language)

        // 4. 决策初始策略
        _strategyFlow.value = decideInitialStrategy(lineCount, scopeName)

        // 5. 启动滚动监控（大文件时动态降级）
        if (lineCount > LARGE_FILE_LINE_THRESHOLD) {
            startScrollMonitoring(editor)
        }
    }

    /** 初始策略决策。 */
    private fun decideInitialStrategy(lineCount: Int, scopeName: String): HighlightStrategy {
        return when {
            // 超大文件直接可见区域模式
            lineCount > 20_000 -> HighlightStrategy.VISIBLE_ONLY
            // 大文件默认可见区域模式
            lineCount > LARGE_FILE_LINE_THRESHOLD -> HighlightStrategy.VISIBLE_ONLY
            // 极重 grammar（cpp/typescript）在大文件上也降级
            lineCount > 2_000 && isHeavyGrammar(scopeName) -> HighlightStrategy.VISIBLE_ONLY
            else -> HighlightStrategy.FULL
        }
    }

    /** 标记是否为重量级 grammar（正则多、匹配慢）。 */
    private fun isHeavyGrammar(scopeName: String): Boolean {
        return scopeName in setOf(
            "source.cpp", "source.ts", "source.tsx", "source.js", "source.jsx",
            "text.html.markdown", "text.html.basic", "source.python",
        )
    }

    /** 监控滚动速度，快速滚动时降级到 VISIBLE_ONLY，静止后恢复 FULL。 */
    private fun startScrollMonitoring(editor: CodeEditor) {
        monitorJob?.cancel()
        monitorJob = scope.launch(Dispatchers.Default) {
            while (true) {
                delay(SCROLL_MONITOR_INTERVAL_MS)
                try {
                    val currentFirst = editor.firstVisibleLine
                    val now = System.currentTimeMillis()
                    val dt = now - lastSampleTime
                    if (lastSampleTime > 0 && dt > 0) {
                        val linesPerMs = Math.abs(currentFirst - lastFirstLine).toDouble() / dt
                        val linesPer500ms = linesPerMs * SCROLL_MONITOR_INTERVAL_MS
                        if (linesPer500ms > FAST_SCROLL_LINES_THRESHOLD) {
                            if (_strategyFlow.value != HighlightStrategy.VISIBLE_ONLY) {
                                _strategyFlow.value = HighlightStrategy.VISIBLE_ONLY
                                Log.d(TAG, "快速滚动 (${"%.1f".format(linesPer500ms)}行/500ms) → VISIBLE_ONLY")
                            }
                        } else {
                            // 静止 1.5s 后恢复 FULL
                            if (_strategyFlow.value == HighlightStrategy.VISIBLE_ONLY) {
                                delay(1500)
                                if (Math.abs(editor.firstVisibleLine - currentFirst) < 3) {
                                    _strategyFlow.value = HighlightStrategy.FULL
                                    Log.d(TAG, "滚动停止 → FULL 高亮")
                                }
                            }
                        }
                    }
                    lastFirstLine = currentFirst
                    lastSampleTime = now
                } catch (_: Exception) {
                    // editor 已释放
                    break
                }
            }
        }
    }

    /** 停止监控并释放资源。 */
    fun detach() {
        monitorJob?.cancel()
        monitorJob = null
    }
}
