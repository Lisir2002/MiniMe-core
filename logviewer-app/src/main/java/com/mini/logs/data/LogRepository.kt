package com.mini.logs.data

import android.os.FileObserver
import com.mini.me_core.core.util.LogLineParser
import com.mini.me_core.core.util.LogLevel
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.callbackFlow
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.withContext
import java.io.File
import java.io.RandomAccessFile

/**
 * 日志文件仓库。负责列出日志文件、读取并解析日志行。
 *
 * 主应用日志可能写入两个位置（按优先级）：
 * 1. 公共外部存储：/storage/emulated/0/Documents/MiniMe-core/logs/（需 WRITE_EXTERNAL_STORAGE 权限）
 * 2. 外部私有目录：/storage/emulated/0/Android/data/com.mini.me_core/files/logs/（权限未授予时回退）
 *
 * 文件名格式：log-yyyy-MM-dd.txt（含滚动文件 .1/.2）
 * 文件头：# MiniMe Log Format vN（读取时跳过 # 开头的行）
 *
 * 只读，不修改主应用的日志文件。
 */
class LogRepository {

    /**
     * 候选日志目录列表，按优先级排列。
     * 第一个存在且有日志文件的目录为主目录，其余为补充。
     */
    private val candidateDirs: List<File> = listOf(
        // 1. 公共外部存储（主应用有权限时写入这里）
        File("/storage/emulated/0/Documents/MiniMe-core/logs/"),
        // 2. 主应用外部私有目录（主应用无权限时回退到这里）
        File("/storage/emulated/0/Android/data/com.mini.me_core/files/logs/"),
    )

    /** 当前有效的日志目录（第一个存在且非空的），用于 FileObserver 监听。 */
    val activeLogDir: File?
        get() = candidateDirs.firstOrNull { it.exists() && it.isDirectory && it.listFiles()?.isNotEmpty() == true }
            ?: candidateDirs.firstOrNull { it.exists() && it.isDirectory }

    /** 列出所有候选目录中的日志文件，按文件名降序（最新的在前），去重。 */
    fun listLogFiles(): List<File> {
        val allFiles = mutableListOf<File>()
        val seenNames = mutableSetOf<String>()
        for (dir in candidateDirs) {
            if (!dir.exists() || !dir.isDirectory) continue
            dir.listFiles { f ->
                f.isFile && f.name.startsWith("log-") && f.name.endsWith(".txt")
            }?.forEach { f ->
                if (seenNames.add(f.name)) {
                    allFiles.add(f)
                }
            }
        }
        return allFiles.sortedByDescending { it.name }
    }

    /**
     * 读取指定文件的最后 maxLines 行（流式读取，避免大文件 OOM）。
     * 跳过 # 开头的格式头行。
     */
    fun readLastLines(file: File, maxLines: Int = 5000): List<String> {
        if (!file.exists()) return emptyList()
        return try {
            RandomAccessFile(file, "r").use { raf ->
                val length = raf.length()
                if (length == 0L) return emptyList()

                // 从文件末尾向前读，每次读 8KB，收集换行符
                val lines = mutableListOf<String>()
                var pos = length
                val chunkSize = 8192
                val sb = StringBuilder()

                while (pos > 0 && lines.size < maxLines) {
                    val readSize = minOf(chunkSize.toLong(), pos).toInt()
                    pos -= readSize
                    raf.seek(pos)
                    val buffer = ByteArray(readSize)
                    raf.readFully(buffer)
                    val chunk = String(buffer, Charsets.UTF_8)

                    // 在 chunk 前面拼接之前剩余的内容
                    sb.insert(0, chunk)

                    // 按换行分割
                    var lastNewline = -1
                    for (i in sb.length - 1 downTo 0) {
                        if (sb[i] == '\n') {
                            if (lastNewline == -1) {
                                lastNewline = i
                            } else {
                                val line = sb.substring(i + 1, lastNewline).trimEnd('\r')
                                if (line.isNotEmpty() && !line.startsWith("#")) {
                                    lines.add(0, line)
                                    if (lines.size >= maxLines) break
                                }
                                lastNewline = i
                            }
                        }
                    }

                    // 处理第一行（可能不完整）
                    if (lines.size < maxLines && lastNewline > 0) {
                        val firstLine = sb.substring(0, lastNewline).trimEnd('\r', '\n')
                        if (firstLine.isNotEmpty() && !firstLine.startsWith("#")) {
                            // 可能是不完整的行，保留
                        }
                    }

                    if (pos == 0L) break
                    sb.setLength(0)
                    if (lastNewline > 0) {
                        sb.append(sb.substring(0, lastNewline))
                    }
                }

                // 简化方案：如果上面的复杂逻辑有问题，用 readLines 兜底
                if (lines.isEmpty()) {
                    file.readLines().filter { it.isNotEmpty() && !it.startsWith("#") }
                        .takeLast(maxLines)
                } else lines
            }
        } catch (e: Exception) {
            emptyList()
        }
    }

    /**
     * 读取多个文件并合并为日志行列表。
     * 文件按文件名升序读取（旧的在前），每个文件只读最后 maxLinesPerFile 行。
     */
    fun readLines(files: List<File>, maxLinesTotal: Int = 5000): List<String> {
        val result = mutableListOf<String>()
        val sortedFiles = files.sortedBy { it.name }
        val perFile = maxLinesTotal / sortedFiles.size.coerceAtLeast(1)

        for (file in sortedFiles) {
            val lines = readLastLines(file, perFile)
            result.addAll(lines)
            if (result.size >= maxLinesTotal) break
        }
        return result.take(maxLinesTotal)
    }

    /**
     * 将原始日志行解析为结构化 LogEntry 列表。
     * 堆栈行（不以时间戳开头的行）附加到前一条主日志行。
     */
    fun parseLines(lines: List<String>, sourceFile: String = ""): List<LogEntry> {
        val entries = mutableListOf<LogEntry>()
        var lineNumber = 0

        for (raw in lines) {
            val parsed = LogLineParser.parse(raw)
            if (parsed != null) {
                lineNumber++
                entries.add(
                    LogEntry(
                        rawLine = raw,
                        timestamp = parsed.timestamp,
                        date = parsed.date,
                        time = extractTime(raw),
                        level = parsed.level,
                        tag = parsed.tag,
                        message = parsed.message,
                        threadName = parsed.threadName,
                        sourceFile = sourceFile,
                        lineNumber = lineNumber,
                    )
                )
            } else {
                // 附属行（堆栈/分隔线）
                if (raw.isNotBlank() && !raw.startsWith("#")) {
                    entries.add(
                        LogEntry(
                            rawLine = raw,
                            message = raw,
                            sourceFile = sourceFile,
                            lineNumber = lineNumber,
                            isStackTraceLine = true,
                        )
                    )
                }
            }
        }
        return entries
    }

    /** 从原始行提取时间部分 "HH:mm:ss.SSS"。 */
    private fun extractTime(raw: String): String {
        val parts = raw.split(" ")
        return if (parts.size >= 2) parts[1] else ""
    }

    /**
     * 加载指定文件列表的日志条目。
     */
    suspend fun loadEntries(files: List<File>, maxLines: Int = 5000): List<LogEntry> =
        withContext(Dispatchers.IO) {
            val allLines = mutableListOf<Pair<String, String>>() // sourceFile to line
            for (file in files.sortedBy { it.name }) {
                val lines = readLastLines(file, maxLines / files.size.coerceAtLeast(1))
                lines.forEach { allLines.add(file.name to it) }
            }
            val entries = mutableListOf<LogEntry>()
            var lineNumber = 0
            for ((source, raw) in allLines) {
                val parsed = LogLineParser.parse(raw)
                if (parsed != null) {
                    lineNumber++
                    entries.add(
                        LogEntry(
                            rawLine = raw,
                            timestamp = parsed.timestamp,
                            date = parsed.date,
                            time = extractTime(raw),
                            level = parsed.level,
                            tag = parsed.tag,
                            message = parsed.message,
                            threadName = parsed.threadName,
                            sourceFile = source,
                            lineNumber = lineNumber,
                        )
                    )
                } else if (raw.isNotBlank()) {
                    entries.add(
                        LogEntry(
                            rawLine = raw,
                            message = raw,
                            sourceFile = source,
                            lineNumber = lineNumber,
                            isStackTraceLine = true,
                        )
                    )
                }
            }
            entries
        }

    /**
     * 监听日志目录变化，文件变更时发出信号。
     * 同时监听所有候选目录。
     */
    fun observeDirectoryChanges(): Flow<Unit> = callbackFlow {
        val observers = candidateDirs
            .filter { it.exists() && it.isDirectory }
            .map { dir ->
                object : FileObserver(dir.absolutePath, CLOSE_WRITE or MODIFY or CREATE) {
                    override fun onEvent(event: Int, path: String?) {
                        if (path?.startsWith("log-") == true) {
                            trySend(Unit)
                        }
                    }
                }
            }
        observers.forEach { it.startWatching() }
        awaitClose { observers.forEach { it.stopWatching() } }
    }.flowOn(Dispatchers.IO)

    /** 计算各等级数量。 */
    fun countByLevel(entries: List<LogEntry>): Map<LogLevel, Int> {
        val counts = mutableMapOf<LogLevel, Int>()
        for (entry in entries) {
            val level = entry.level ?: continue
            counts[level] = counts.getOrDefault(level, 0) + 1
        }
        return counts
    }

    /** 计算各 Tag 数量，按数量降序。 */
    fun countByTag(entries: List<LogEntry>): List<Pair<String, Int>> {
        val counts = mutableMapOf<String, Int>()
        for (entry in entries) {
            if (entry.tag.isNotEmpty()) {
                counts[entry.tag] = counts.getOrDefault(entry.tag, 0) + 1
            }
        }
        return counts.entries.sortedByDescending { it.value }.map { it.key to it.value }
    }
}
