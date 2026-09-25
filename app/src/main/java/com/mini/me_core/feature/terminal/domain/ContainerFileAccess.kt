package com.mini.me_core.feature.terminal.domain

import com.mini.me_core.core.util.FileLogger
import com.mini.me_core.feature.agent.domain.container.LinuxContainerEngine
import javax.inject.Inject
import javax.inject.Singleton

/**
 * 容器内一个文件/目录条目。
 */
data class ContainerFileEntry(
    val name: String,
    val path: String,
    val isDir: Boolean,
    val sizeBytes: Long,
    val modifiedAt: Long,
    val permissions: String,
    val owner: String,
) {
    val isHidden: Boolean get() = name.startsWith(".")
}

/**
 * 容器文件访问层：通过在容器内执行 ls/cp/mv/rm/cat/base64 等命令操作文件。
 *
 * 所有方法都是 suspend + 在 IO 线程执行，返回纯数据结果，不持有任何 UI 状态。
 */
@Singleton
class ContainerFileAccess @Inject constructor(
    private val engine: LinuxContainerEngine,
) {
    companion object {
        private const val TAG = "ContainerFileAccess"
        private const val LS_TIMEOUT = 15_000L
        private const val FILE_READ_TIMEOUT = 30_000L
        private const val OP_TIMEOUT = 30_000L
        const val MAX_EDIT_BYTES = 1L * 1024 * 1024 // 1MB
    }

    private suspend fun run(cmd: String, timeoutMs: Long = LS_TIMEOUT): String {
        return runCatching { engine.runCommandSync(cmd, null, timeoutMs) }
            .onFailure { FileLogger.w(TAG, "命令失败: $cmd", it) }
            .getOrDefault("")
            .trimEnd('\n')
    }

    /**
     * 列出目录内容。使用 `ls -la --time-style=+%s <dir>` 并用 NUL 分隔名（处理空格/特殊字符）。
     * 这里采用简单稳健方案：`ls -1` 逐行取文件名，再对每个条目 stat，避免特殊文件名解析崩溃。
     */
    suspend fun listDir(path: String, showHidden: Boolean = true): List<ContainerFileEntry> {
        val safePath = shellQuote(path)
        // 先取文件名列表（每行一个，用 -q 把不可打印字符替换为 ?，避免 readLine 崩溃）
        val listing = run("ls -1A $safePath")
        val names = listing.lines().filter { it.isNotBlank() }
        return names.mapNotNull { name ->
            val full = if (path.endsWith("/")) path + name else "$path/$name"
            stat(full, name)
        }.filter { showHidden || !it.isHidden }
    }

    private suspend fun stat(fullPath: String, displayName: String): ContainerFileEntry? {
        val safe = shellQuote(fullPath)
        // 格式：type|size|mtime_epoch|perms|owner
        val out = run("stat -c '%F|%s|%Y|%A|%U' $safe 2>/dev/null")
        val parts = out.split("|")
        if (parts.size < 5) return null
        val isDir = parts[0].contains("directory")
        return ContainerFileEntry(
            name = displayName,
            path = fullPath,
            isDir = isDir,
            sizeBytes = parts[1].toLongOrNull() ?: 0L,
            modifiedAt = parts[2].toLongOrNull() ?: 0L,
            permissions = parts[3],
            owner = parts[4],
        )
    }

    suspend fun mkdir(dirPath: String): Result<Unit> = runOp("mkdir -p ${shellQuote(dirPath)}")

    suspend fun createFile(filePath: String): Result<Unit> = runOp("touch ${shellQuote(filePath)}")

    suspend fun delete(paths: List<String>): Result<Unit> {
        if (paths.isEmpty()) return Result.success(Unit)
        val args = paths.joinToString(" ") { shellQuote(it) }
        return runOp("rm -rf -- $args")
    }

    suspend fun rename(from: String, to: String): Result<Unit> =
        runOp("mv -f ${shellQuote(from)} ${shellQuote(to)}")

    suspend fun copy(srcPaths: List<String>, destDir: String): Result<Unit> {
        if (srcPaths.isEmpty()) return Result.success(Unit)
        val args = srcPaths.joinToString(" ") { shellQuote(it) }
        return runOp("cp -r $args ${shellQuote(destDir)}")
    }

    suspend fun move(srcPaths: List<String>, destDir: String): Result<Unit> {
        if (srcPaths.isEmpty()) return Result.success(Unit)
        val args = srcPaths.joinToString(" ") { shellQuote(it) }
        return runOp("mv $args ${shellQuote(destDir)}")
    }

    suspend fun chmod(path: String, mode: String): Result<Unit> =
        runOp("chmod $mode ${shellQuote(path)}")

    /** 读取文本文件内容（base64 解码，避免特殊字符/编码问题）。 */
    suspend fun readFileText(path: String): Result<String> {
        val out = run("base64 ${shellQuote(path)} 2>/dev/null | tr -d '\\n'", FILE_READ_TIMEOUT)
        return runCatching {
            val bytes = android.util.Base64.decode(out, android.util.Base64.DEFAULT)
            String(bytes, Charsets.UTF_8)
        }
    }

    /** 写回文本文件（base64 编码后经 sh -c 写入，避免引号转义地狱）。 */
    suspend fun writeFileText(path: String, content: String): Result<Unit> {
        val b64 = android.util.Base64.encodeToString(content.toByteArray(Charsets.UTF_8), android.util.Base64.NO_WRAP)
        return runOp("echo '$b64' | base64 -d > ${shellQuote(path)}", FILE_READ_TIMEOUT)
    }

    /** 读取文件字节（用于图片预览/导出），base64 解码。 */
    suspend fun readFileBytes(path: String): Result<ByteArray> {
        val out = run("base64 ${shellQuote(path)} 2>/dev/null | tr -d '\\n'", FILE_READ_TIMEOUT)
        return runCatching { android.util.Base64.decode(out, android.util.Base64.DEFAULT) }
    }

    /** 文件大小（字节），用于 >1MB 警告。 */
    suspend fun fileSize(path: String): Long {
        val out = run("stat -c '%s' ${shellQuote(path)} 2>/dev/null")
        return out.trim().toLongOrNull() ?: 0L
    }

    /** 当前工作目录。 */
    suspend fun homeDir(): String {
        val out = run("echo \$HOME")
        return out.ifBlank { "/root" }
    }

    private suspend fun runOp(cmd: String, timeoutMs: Long = OP_TIMEOUT): Result<Unit> {
        val full = "($cmd) > /tmp/mm_fm_op.log 2>&1; echo EXIT=\\$?"
        val out = run(full, timeoutMs)
        val code = out.substringAfter("EXIT=", "").trim().toIntOrNull() ?: -1
        return if (code == 0) Result.success(Unit)
        else Result.failure(IllegalStateException("exit=$code: ${out.takeLast(200)}"))
    }

    private fun shellQuote(s: String): String = "'" + s.replace("'", "'\"'\"'") + "'"
}
