package com.mini.me_core.feature.workspace.domain

import com.mini.me_core.core.container.api.SshConnectionPort
import com.mini.me_core.core.container.api.friendlySshError
import com.mini.me_core.core.util.FileLogger
import com.mini.me_core.feature.workspace.data.repository.WorkspaceRepository
import com.mini.me_core.feature.workspace.domain.WorkspacePathMapper.Companion.CONTAINER_ROOT
import kotlinx.coroutines.runBlocking
import java.io.File
import java.nio.file.FileAlreadyExistsException
import java.nio.file.NoSuchFileException
import javax.inject.Inject

private const val TAG = "RemoteSftpFileAccess"

/** 远程命令执行失败（非零退出或 stderr 有内容）时抛出，供调用方感知并重试/报错。 */
open class RemoteCommandFailedException(
    val exitCode: Int,
    /** 脱敏后的 stderr 片段（截断，避免回显超长内容）。 */
    val stderr: String,
    message: String,
) : RuntimeException(message)

/** 远程写文件失败的类型化异常（非零退出/解码失败）。 */
class RemoteWriteFailedException(
    val remotePath: String,
    exitCode: Int,
    stderr: String,
) : RemoteCommandFailedException(exitCode, stderr, "远程写入失败(code=$exitCode): $remotePath")

private data class ExecResult(
    val stdout: String,
    val stderr: String,
    val exitCode: Int,
)

/**
 * 远程写/命令结果的判定（纯逻辑，便于单测）：
 * 非零退出即失败并抛类型化异常，绝不静默当成功；stderr 截断为脱敏片段回传。
 */
internal object RemoteExecDecider {
    const val TAIL_LIMIT = 300
    fun tail(s: String): String = s.trim().take(TAIL_LIMIT)

    /** 写操作结果判定：exitCode!=0 抛 [RemoteWriteFailedException]。 */
    fun requireWriteSuccess(exitCode: Int, stderr: String, remotePath: String) {
        if (exitCode != 0) throw RemoteWriteFailedException(remotePath, exitCode, tail(stderr))
    }

    /** 一般命令（mkdirs 等）结果判定：exitCode!=0 抛 [RemoteCommandFailedException]。 */
    fun requireSuccess(exitCode: Int, stderr: String, ctx: String) {
        if (exitCode != 0) throw RemoteCommandFailedException(exitCode, tail(stderr), "$ctx (code=$exitCode)")
    }
}

/**
 * [FileAccessProvider] 的远程实现：用 SSH exec channel 执行命令读写远程文件。
 *
 * **不用 SFTP**：sshj 0.38.0 的 SFTP 实现有长期未修的 Buffer 溢出 bug（issue #461），
 * 在 Android 上必现 `ArrayIndexOutOfBoundsException: dstPos=-4`，且崩溃会拖垮整个 SSH transport。
 * exec channel 不走 SFTPEngine，无此问题。`Bash` 工具已验证 exec 可靠。
 *
 * 路径映射：AI 给的 `~/workspace/...` 映射到 [RemoteSshConnection.config] 的 `remoteWorkspacePath` + 相对路径。
 * 其它绝对路径（如 `/etc/...`）直接作为远程绝对路径使用。
 *
 * 文本文件用 `cat`/重定向读写，二进制文件用 `base64` 中转。
 */
class RemoteSftpFileAccess @Inject constructor(
    private val connection: SshConnectionPort,
    private val workspaceRepository: WorkspaceRepository
) : FileAccessProvider {

    /** 当前选中工作区在远程服务器上的真实路径（如 /data/.../test/111）。 */
    private fun currentWorkspaceRoot(): String {
        val cfg = connection.config ?: throw IllegalStateException("SSH 未连接")
        // currentPath() 远程模式返回选中工作区的远程绝对路径；未选中时回退到 remoteWorkspacePath
        val path = workspaceRepository.currentPath()
        return if (path.isNotBlank() && path != "/") path else cfg.remoteWorkspacePath.trimEnd('/')
    }

    /** 把 AI 路径映射到远程服务器上的真实路径。
     *  ~/workspace 映射到当前选中工作区（与本地模式 WorkspacePathMapper 行为一致），
     *  其它绝对路径直接作为远程绝对路径使用。 */
    private fun toRemotePath(path: String): String {
        val root = currentWorkspaceRoot().trimEnd('/')
        // CONTAINER_ROOT 是 ~/workspace，展开 ~ 后做前缀匹配
        val wsRoot = (connection.remoteHome ?: "~").trimEnd('/') + "/workspace"
        val p = path.trim().let {
            if (it.startsWith("~/")) {
                val home = connection.remoteHome
                if (home != null) home.trimEnd('/') + "/" + it.removePrefix("~/") else it
            } else it
        }
        return when {
            p == wsRoot || p == "$wsRoot/" || p == CONTAINER_ROOT || p == "$CONTAINER_ROOT/" -> root
            p.startsWith("$wsRoot/") ->
                root + "/" + p.removePrefix("$wsRoot/")
            p.startsWith("/") -> p
            else -> root + "/" + p
        }
    }

    /** 把远程路径还原为 AI 视角的容器路径（回显用）。 */
    private fun toDisplayPathFromRemote(remotePath: String): String {
        val root = currentWorkspaceRoot().trimEnd('/')
        return when {
            remotePath == root -> CONTAINER_ROOT
            remotePath.startsWith("$root/") -> CONTAINER_ROOT + "/" + remotePath.removePrefix("$root/")
            else -> remotePath
        }
    }

    /** 单引号转义：远程路径含单引号时用 `'\''` 绕过，保证 shell 命令安全。 */
    private fun shellQuote(s: String): String = "'" + s.replace("'", "'\\''") + "'"

    /**
     * 同步执行远程命令，返回 (stdout, stderr, exitCode)。
     *
     * 必须消费 errorStream（stderr）：否则远程报错会留在管道里、或误判为成功（假成功）。
     * 先读 stderr（小、阻塞到命令结束）再读 stdout，避免 stderr 管道填满造成反压；
     * session 与所有流都在 try/finally 中关闭，杜绝句柄泄漏。
     *
     * 此处阻塞是因为 FileAccessProvider 接口为同步设计，被 Agent 工具层调用；
     * 工具执行本身运行在后台协程中，runBlocking 仅桥接到 IO 线程执行 SSH 命令，
     * 不阻塞主线程。
     */
    private fun execResult(command: String): ExecResult = runBlocking {
        kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.IO) {
            val session = try {
                connection.startExecSession(command)
            } catch (e: Exception) {
                throw RuntimeException(friendlySshError(e), e)
            }
            var stdout = ""
            var stderr = ""
            var exitCode = -1
            try {
                stderr = runCatching {
                    session.errorStream.bufferedReader().use { it.readText() }
                }.getOrDefault("")
                stdout = runCatching {
                    session.inputStream.bufferedReader().use { it.readText() }
                }.getOrDefault("")
                exitCode = runCatching { session.exitStatus }.getOrNull() ?: -1
            } finally {
                runCatching { session.close() }
            }
            ExecResult(stdout, stderr, exitCode)
        }
    }

    /**
     * 同步执行远程命令并返回完整 stdout。非零退出时抛 [RemoteCommandFailedException]（带回脱敏 stderr），
     * 不再仅打 warn 把失败当成功。
     */
    private fun execSync(command: String): String {
        val r = execResult(command)
        if (r.exitCode != 0) {
            val tail = r.stderr.trim().take(300)
            FileLogger.w(TAG, "远程命令退出码=${r.exitCode}${if (tail.isNotEmpty()) "，stderr: $tail" else ""}")
            throw RemoteCommandFailedException(r.exitCode, tail, "远程命令执行失败(code=${r.exitCode})")
        }
        return r.stdout
    }

    /**
     * 同步执行远程命令，返回退出码（不抛异常）。
     * 仅用于「退出码即结果」的探测类命令（test -e/-d/-f）；写/改类操作禁止用它吞掉失败。
     */
    private fun execExitCode(command: String): Int = try {
        execResult(command).exitCode
    } catch (e: Exception) {
        FileLogger.w(TAG, "命令执行异常: ${friendlySshError(e)}", e)
        -1
    }

    override fun readFile(path: String): String {
        val remote = toRemotePath(path)
        return runCatching { execSync("cat ${shellQuote(remote)}") }
            .getOrElse {
                FileLogger.e(TAG, "readFile 失败: $remote", it)
                throw NoSuchFileException(File(remote))
            }
    }

    override fun readLines(path: String): Sequence<String> {
        val remote = toRemotePath(path)
        return runCatching { execSync("cat ${shellQuote(remote)}") }
            .getOrElse { throw NoSuchFileException(File(remote)) }
            .lines().asSequence()
    }

    override fun writeFile(path: String, content: String, overwrite: Boolean) {
        val remote = toRemotePath(path)
        if (exists(path) && !overwrite) throw FileAlreadyExistsException(File(remote))
        // 确保父目录存在；父目录创建失败必须上抛，不能静默继续写（否则写落到不存在路径）。
        val parent = remote.substringBeforeLast('/', "")
        if (parent.isNotEmpty()) {
            val mkdirR = execResult("mkdir -p ${shellQuote(parent)}")
            RemoteExecDecider.requireSuccess(mkdirR.exitCode, mkdirR.stderr, "远程创建父目录失败: $parent")
        }
        // 用 base64 中转写入：内容编码为单行 base64（无换行、无引号、无特殊字符），远程解码落盘。
        // 相比 printf %s 直接把原始内容作命令行参数传递，base64 不受换行/引号/二进制内容的破坏，
        // 与 readBytes/copyToLocal 的 base64 中转方式对称。
        val b64 = java.util.Base64.getEncoder().encodeToString(content.toByteArray(Charsets.UTF_8))
        val redirect = if (overwrite) ">" else ">>"
        val r = execResult("printf %s ${shellQuote(b64)} | base64 -d $redirect ${shellQuote(remote)}")
        if (r.exitCode != 0) {
            // 非零退出绝不当成功：上抛类型化异常，调用方（Agent 工具/UI）可感知并重试或报错。
            FileLogger.w(TAG, "writeFile 退出码=${r.exitCode}: $remote${if (r.stderr.isNotBlank()) " stderr: ${RemoteExecDecider.tail(r.stderr)}" else ""}")
            RemoteExecDecider.requireWriteSuccess(r.exitCode, r.stderr, remote)
        }
    }

    override fun exists(path: String): Boolean {
        val remote = toRemotePath(path)
        return execExitCode("test -e ${shellQuote(remote)}") == 0
    }

    override fun isDirectory(path: String): Boolean {
        val remote = toRemotePath(path)
        return execExitCode("test -d ${shellQuote(remote)}") == 0
    }

    override fun isFile(path: String): Boolean {
        val remote = toRemotePath(path)
        return execExitCode("test -f ${shellQuote(remote)}") == 0
    }

    override fun fileSize(path: String): Long {
        val remote = toRemotePath(path)
        return runCatching {
            execSync("stat -c %s ${shellQuote(remote)} 2>/dev/null").trim().toLongOrNull() ?: 0L
        }.getOrDefault(0L)
    }

    override fun lastModified(path: String): Long {
        val remote = toRemotePath(path)
        return runCatching {
            // stat -c %Y 返回 epoch 秒，转毫秒
            (execSync("stat -c %Y ${shellQuote(remote)} 2>/dev/null").trim().toLongOrNull() ?: 0L) * 1000L
        }.getOrDefault(0L)
    }

    override fun permissions(path: String): String {
        val remote = toRemotePath(path)
        return runCatching {
            // stat -c %A 返回符号权限如 -rwxr-xr-x，取后 9 位
            val sym = execSync("stat -c %A ${shellQuote(remote)} 2>/dev/null").trim()
            sym.takeLast(9).takeIf { it.length == 9 } ?: "---"
        }.getOrDefault("---")
    }

    override fun listFiles(path: String): List<FileEntry> {
        val remote = toRemotePath(path)
        return runCatching {
            // 用 stat 一次性输出 name|type|size|mtime|perms，避免多次 exec
            // %n=文件名 %F=类型 %s=大小 %Y=mtime %A=权限
            val output = execSync(
                "stat -c '%n|%F|%s|%Y|%A' ${shellQuote(remote)}/* 2>/dev/null"
            )
            if (output.isBlank()) return emptyList()
            output.lines().mapNotNull { line ->
                val parts = line.split("|")
                if (parts.size < 5) return@mapNotNull null
                val name = parts[0].substringAfterLast('/')
                if (name.isBlank()) return@mapNotNull null
                FileEntry(
                    name = name,
                    isDirectory = parts[1].contains("directory", ignoreCase = true),
                    size = parts[2].toLongOrNull() ?: 0L,
                    lastModified = (parts[3].toLongOrNull() ?: 0L) * 1000L,
                    localFile = null,
                    permissions = parts[4].takeLast(9).ifBlank { "---" }
                )
            }
        }.getOrElse {
            FileLogger.w(TAG, "listFiles 失败: $remote", it)
            emptyList()
        }
    }

    override fun readBytes(path: String): ByteArray {
        val remote = toRemotePath(path)
        return runCatching {
            // 二进制用 base64 中转
            val b64 = execSync("base64 ${shellQuote(remote)} 2>/dev/null")
            java.util.Base64.getMimeDecoder().decode(b64)
        }.getOrElse {
            FileLogger.e(TAG, "readBytes 失败: $remote", it)
            throw NoSuchFileException(File(remote))
        }
    }

    override fun copyToLocal(path: String): File {
        val remote = toRemotePath(path)
        val tempFile = File.createTempFile("minime_remote_", ".copy").apply { deleteOnExit() }
        return runCatching {
            // base64 解码到本地临时文件
            val b64 = execSync("base64 ${shellQuote(remote)} 2>/dev/null")
            tempFile.writeBytes(java.util.Base64.getMimeDecoder().decode(b64))
            tempFile
        }.getOrElse {
            tempFile.delete()
            FileLogger.e(TAG, "copyToLocal 失败: $remote", it)
            throw NoSuchFileException(File(remote))
        }
    }

    override fun delete(path: String) {
        val remote = toRemotePath(path)
        // -r 递归删目录，-f 忽略不存在
        execExitCode("rm -rf ${shellQuote(remote)}")
    }

    override fun mkdirs(path: String) {
        val remote = toRemotePath(path)
        val r = execResult("mkdir -p ${shellQuote(remote)}")
        RemoteExecDecider.requireSuccess(r.exitCode, r.stderr, "远程创建目录失败: $remote")
    }

    override fun parentPath(path: String): String? {
        val remote = toRemotePath(path)
        val parent = remote.substringBeforeLast('/', "")
        if (parent.isEmpty()) return null
        return toDisplayPathFromRemote(parent)
    }

    override fun toDisplayPath(path: String): String = toDisplayPathFromRemote(toRemotePath(path))
}
