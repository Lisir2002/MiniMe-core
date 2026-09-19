package com.mini.me_core.core.util

import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import java.io.File
import java.io.RandomAccessFile

/**
 * 轮询式文件尾部观察器（类似 Unix `tail -f`）。
 *
 * 与 [android.os.FileObserver] 的事件驱动不同，这里按固定间隔轮询目标文件长度：
 * 一旦发现文件比上次变长，就用 [RandomAccessFile] 从上次位置读到文件末尾，把新增字节解码成行后通过
 * [onNewLines] 回调吐出。这样在外部存储 / MediaStore 等 FileObserver 事件不可靠的场景下也能稳定尾随。
 *
 * 行拼接：新增字节末尾可能落在一条尚未写完的行中间（没有换行符），此时把最后一个片段暂存到
 * [pendingFragment]，下次轮询时拼到新字节前面，避免把一条日志劈成两行。
 *
 * 异常处理：文件被删除、截断（如 clearAllLogs）或跨天换文件时，重置读取基线，不回吐旧行。
 *
 * 生命周期：[start] 后在 [scope] 的协程里循环轮询，[stop] 取消；页面退出 / ViewModel.onCleared
 * 时务必调用 [stop]，避免泄漏。轮询循环跑在 [Dispatchers.IO]，[onNewLines] 回调也在 IO 线程，
 * 调用方如需更新 UI 状态请自行切到主线程。
 */
internal class FileWatcher(
    private val file: File,
    private val onNewLines: (List<String>) -> Unit,
    private val pollIntervalMs: Long = 1000L,
) {
    private var job: Job? = null
    private var lastLength: Long = -1L
    private var pendingFragment: String = ""

    /** 启动轮询。重复调用安全（已在运行则忽略）。 */
    fun start(scope: CoroutineScope) {
        if (job != null) return
        lastLength = if (file.exists()) file.length() else 0L
        pendingFragment = ""
        job = scope.launch(Dispatchers.IO) {
            while (isActive) {
                delay(pollIntervalMs)
                pollOnce()
            }
        }
    }

    /** 停止轮询并释放协程。 */
    fun stop() {
        job?.cancel()
        job = null
    }

    private fun pollOnce() {
        runCatching {
            if (!file.exists()) {
                // 文件被删除/重建（如 clearAllLogs、跨天新文件）：重置基线，不吐旧行
                lastLength = 0L
                pendingFragment = ""
                return@runCatching
            }
            val currentLength = file.length()
            when {
                currentLength < lastLength -> {
                    // 文件被截断：重置基线
                    lastLength = currentLength
                    pendingFragment = ""
                }
                currentLength == lastLength -> Unit
                else -> {
                    val delta = currentLength - lastLength
                    lastLength = currentLength
                    val bytes = ByteArray(delta.toInt())
                    RandomAccessFile(file, "r").use { raf ->
                        raf.seek(currentLength - delta)
                        raf.readFully(bytes)
                    }
                    val lines = splitDelta(String(bytes, Charsets.UTF_8))
                    if (lines.isNotEmpty()) onNewLines(lines)
                }
            }
        }
    }

    /**
     * 把新增字节切成完整行；末尾不完整的行暂存到 [pendingFragment]，下次拼接。
     * 与 [String.lines] 行为一致：末尾换行符不产生空尾行。
     */
    private fun splitDelta(text: String): List<String> {
        val combined = pendingFragment + text
        if (combined.isEmpty()) return emptyList()
        val endsWithNewline = combined.endsWith("\n") || combined.endsWith("\r\n")
        val lines = combined.lines()
        return if (endsWithNewline) {
            pendingFragment = ""
            lines
        } else {
            pendingFragment = lines.lastOrNull().orEmpty()
            lines.dropLast(1)
        }
    }
}
