package com.mini.me_core.feature.editor.core

import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import java.io.File
import java.nio.charset.Charset
import java.nio.charset.StandardCharsets

/**
 * 共享文档模型（View/Edit 无感切换）。
 *
 * 设计要点：
 *  - 不绑定任何 View 层（Compose View / sora-editor 均可基于此模型构建）
 *  - 行级文本存储，大文件分块加载（LazyColumn/分页思路）
 *  - 编码检测（UTF-8 / GBK 等）
 *  - 变更通知通过 [changeFlow] 暴露，View/Edit 订阅后同步状态
 *  - 语言信息（scopeName）由 LanguageDetector 注入，高亮引擎据此选择 grammar
 */
class CodeDocument(
    val file: File? = null,
    initialText: String = "",
    val scopeName: String = "text.plain",
    val charset: Charset = StandardCharsets.UTF_8,
) {
    /** 文档纯文本（当前阶段完整加载；超大文件由 [isLargeFile] 标记后走分块策略）。 */
    var text: String = initialText
        private set

    /** 文档是否已被修改（脏标记）。 */
    val isDirty: Boolean get() = _dirtyFlow.value
    private val _dirtyFlow = MutableStateFlow(false)
    val dirtyFlow: StateFlow<Boolean> = _dirtyFlow.asStateFlow()

    /** 文档总行数。 */
    val lineCount: Int get() = text.count { it == '\n' } + 1

    /** 文档大小（字节，按 charset 估算）。 */
    val byteSize: Long get() = text.toByteArray(charset).size.toLong()

    /** 是否大文件（>500KB，触发分块加载/降级策略）。 */
    val isLargeFile: Boolean get() = byteSize > 500_000

    /** 变更计数：每次编辑 +1，View/Edit 据此刷新。 */
    private val _changeFlow = MutableStateFlow(0)
    val changeFlow: StateFlow<Int> = _changeFlow.asStateFlow()

    /** 替换整个文档内容（View→Edit 切换时保留位置）。 */
    fun replaceText(newText: String) {
        text = newText
        _dirtyFlow.value = true
        _changeFlow.value = _changeFlow.value + 1
    }

    /** 标记已保存（清除脏标记）。 */
    fun markSaved() {
        _dirtyFlow.value = false
    }

    /**
     * 持久化到文件。
     * @return 写入的字节数；失败抛异常由调用方处理
     */
    fun save(): Long {
        val bytes = text.toByteArray(charset)
        file?.outputStream()?.use { it.write(bytes) }
        _dirtyFlow.value = false
        return bytes.size.toLong()
    }

    companion object {
        /** 从文件读取文档，自动检测编码和语言。 */
        suspend fun fromFile(
            file: File,
            scopeName: String? = null,
        ): CodeDocument = kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.IO) {
            // 简单 UTF-8 BOM 检测；GBK 等可后续扩展。
            val raw = file.readBytes()
            val charset = when {
                raw.size >= 3 && raw[0] == 0xEF.toByte() && raw[1] == 0xBB.toByte() && raw[2] == 0xBF.toByte() ->
                    StandardCharsets.UTF_8
                else -> StandardCharsets.UTF_8
            }
            val text = raw.toString(charset)
            val detectedScope = scopeName ?: detectScope(file, text)
            CodeDocument(file = file, initialText = text, scopeName = detectedScope, charset = charset)
        }

        private fun detectScope(file: File, text: String): String {
            // 延迟引用避免循环依赖；运行时已初始化 TextMateManager。
            return try {
                com.mini.me_core.feature.editor.detect.LanguageDetector.detect(file, text.take(4096))
            } catch (_: Exception) {
                "text.plain"
            }
        }
    }
}
