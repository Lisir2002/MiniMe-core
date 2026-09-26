package com.mini.me_core.feature.qqbot.domain

import com.mini.me_core.feature.agent.domain.container.CommandEngine
import com.mini.me_core.feature.agent.domain.container.CommandResult
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Outcome of a single command executed inside the terminal container.
 *
 * @property output   combined stdout+stderr (bounded by the engine).
 * @property exitCode process exit code; null when it could not be determined.
 * @property timedOut true when the engine killed the command for exceeding its timeout.
 */
data class ContainerCommandResult(
    val output: String,
    val exitCode: Int?,
    val timedOut: Boolean
) {
    /** True when the command exited with code 0. */
    val isSuccess: Boolean get() = exitCode == 0
}

/**
 * User-opaque bridge to the terminal Linux container.
 *
 * Wraps the shared [CommandEngine] so the QQ bot feature can run commands, read/write
 * files, and inspect listening ports / running processes without the user seeing a
 * terminal session. All calls execute inside the proot container when it is installed;
 * failures are surfaced as [ContainerCommandResult] rather than thrown, keeping the bot
 * lifecycle robust against transient container issues.
 */
@Singleton
class ContainerBridge @Inject constructor(
    private val commandEngine: CommandEngine,
    private val logManager: QBotLogManager
) {

    private companion object {
        const val TAG = "ContainerBridge"
        /** Default command timeout when the caller does not specify one. */
        const val DEFAULT_TIMEOUT_MS = 15_000L
    }

    /** True when the container runtime is installed and ready. */
    fun isContainerReady(): Boolean = commandEngine.isContainerInstalled()

    /**
     * Execute [command] inside the container and wait for it to finish.
     *
     * @param command    shell command line to run.
     * @param timeoutMs  maximum execution time; the command is killed afterwards.
     */
    suspend fun executeCommand(
        command: String,
        timeoutMs: Long = DEFAULT_TIMEOUT_MS
    ): ContainerCommandResult = withContext(Dispatchers.IO) {
        val result: CommandResult = runCatching {
            commandEngine.runCommandSyncWithExit(command, timeoutMs = timeoutMs)
        }.getOrElse { e ->
            logManager.log(QBotLogLevel.ERROR, TAG, "exec failed: ${e.message ?: e.javaClass.simpleName}")
            CommandResult(output = "", exitCode = null, timedOut = false)
        }
        ContainerCommandResult(
            output = result.output.trimEnd(),
            exitCode = result.exitCode,
            timedOut = result.timedOut
        )
    }

    /**
     * Read the contents of [path] inside the container.
     *
     * @return file text, or null when the file cannot be read.
     */
    suspend fun readFile(path: String): String? {
        val escaped = shellEscape(path)
        val result = executeCommand("cat $escaped 2>/dev/null", timeoutMs = 10_000L)
        return if (result.isSuccess) result.output else null
    }

    /**
     * Write [content] to [path] inside the container, creating parent directories.
     *
     * The content is base64-encoded before being piped through `base64 -d`, which avoids
     * any shell-escaping issues with quotes / newlines / special characters.
     *
     * @return true when the file was written successfully.
     */
    suspend fun writeFile(path: String, content: String): Boolean {
        val dir = path.substringBeforeLast('/', "").ifEmpty { "." }
        val b64 = java.util.Base64.getEncoder().encodeToString(content.toByteArray(Charsets.UTF_8))
        val escapedDir = shellEscape(dir)
        val escapedPath = shellEscape(path)
        val command = "mkdir -p $escapedDir && echo '$b64' | base64 -d > $escapedPath"
        val result = executeCommand(command, timeoutMs = 10_000L)
        if (!result.isSuccess) {
            logManager.log(QBotLogLevel.ERROR, TAG, "writeFile failed for $path: ${result.output}")
        }
        return result.isSuccess
    }

    /**
     * Check whether TCP [port] is currently listening inside the container.
     *
     * Prefers `ss -tln`; falls back to parsing `/proc/net/tcp` and `/proc/net/tcp6`
     * (LISTEN state `0A`) when `ss` is not available in the minimal rootfs.
     */
    suspend fun checkPort(port: Int): Boolean {
        val hexPort = port.toString(16).uppercase()
        val command = buildString {
            append("if command -v ss >/dev/null 2>&1; then ")
            append("ss -tln 2>/dev/null | grep -qE '[:.]$port[[:space:]]'; ")
            append("else ")
            append("awk 'NR>1 {split($2,a,\":\"); if (toupper(a[2])==\"$hexPort\" && $4==\"0A\") {found=1}} END {exit found?0:1}' ")
            append("/proc/net/tcp /proc/net/tcp6 2>/dev/null; ")
            append("fi")
        }
        val result = executeCommand(command, timeoutMs = 5_000L)
        return result.isSuccess
    }

    /**
     * Check whether a process whose command line matches [processName] is running.
     *
     * Uses `pgrep -f` when available, otherwise greps `ps -ef`.
     */
    suspend fun isProcessRunning(processName: String): Boolean {
        val escaped = shellEscape(processName)
        val command = buildString {
            append("if command -v pgrep >/dev/null 2>&1; then pgrep -f $escaped >/dev/null 2>&1; ")
            append("else ps -ef 2>/dev/null | grep -v grep | grep -q $escaped; fi")
        }
        val result = executeCommand(command, timeoutMs = 5_000L)
        return result.isSuccess
    }

    /** Wrap [text] in single quotes for safe shell interpolation. */
    private fun shellEscape(text: String): String =
        "'" + text.replace("'", "'\"'\"'") + "'"
}
