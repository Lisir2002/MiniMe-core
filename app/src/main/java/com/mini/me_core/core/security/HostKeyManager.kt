package com.mini.me_core.core.security

import android.content.Context
import com.mini.me_core.core.util.FileLogger
import dagger.hilt.android.qualifiers.ApplicationContext
import net.schmizz.sshj.common.KeyType
import net.schmizz.sshj.transport.verification.HostKeyVerifier
import java.io.File
import java.security.MessageDigest
import java.security.PublicKey
import java.util.Base64
import javax.inject.Inject
import javax.inject.Singleton

/**
 * 主机密钥验证决策结果（纯逻辑，便于单测，不含任何 Android/IO 依赖）。
 */
sealed interface HostKeyDecision {
    /** 已知主机且密钥匹配，允许连接。 */
    data object Match : HostKeyDecision

    /** 已知主机但密钥变更，必须阻断（疑似 MITM 或服务器重装）。 */
    data class Mismatch(val expectedType: String, val expectedFingerprintSha256: String) : HostKeyDecision

    /** 首次连接：需向用户回显 SHA256 指纹并取得显式信任后才可放行。 */
    data class FirstSeen(
        val hostKey: String,
        val fingerprintSha256: String,
        /** 待写入 known_hosts 的完整行（hostKey keyType base64）。 */
        val candidateLine: String,
    ) : HostKeyDecision

    /** known_hosts 文件存在但损坏/不可读：出于 fail-closed 原则拒绝连接。 */
    data class StoreUnreadable(val reason: String) : HostKeyDecision
}

/**
 * 主机密钥指纹规范化、比对与决策（纯逻辑对象）。
 */
object HostKeyPolicy {

    /** 标准 host:port 格式（端口 22 时省略端口段）。 */
    fun hostKeyFor(hostname: String, port: Int): String =
        if (port == 22 || port <= 0) hostname else "[$hostname]:$port"

    /** known_hosts 行格式：`<hostKey> <keyType> <base64-raw-key>`。 */
    fun fingerprintLine(keyType: String, encodedKey: ByteArray): String =
        "$keyType " + Base64.getEncoder().encodeToString(encodedKey)

    /** 回显给用户的 OpenSSH 风格 SHA256 指纹（不带 '=' 填充）。 */
    fun sha256Fingerprint(encodedKey: ByteArray): String {
        val digest = MessageDigest.getInstance("SHA-256").digest(encodedKey)
        return "SHA256:" + Base64.getEncoder().encodeToString(digest).trimEnd('=')
    }

    /** 常量时间字符串比较，避免计时侧信道。 */
    fun constantTimeEquals(a: String, b: String): Boolean =
        MessageDigest.isEqual(a.toByteArray(Charsets.UTF_8), b.toByteArray(Charsets.UTF_8))

    /**
     * 核心决策：
     *  - [storeReadable]=false（文件存在但损坏/IO 异常）→ [HostKeyDecision.StoreUnreadable]（DENY）；
     *  - 无该主机记录 → [HostKeyDecision.FirstSeen]（需显式信任，绝不静默信任）；
     *  - 记录匹配 → [HostKeyDecision.Match]；
     *  - 记录存在但不一致 → [HostKeyDecision.Mismatch]（阻断）。
     */
    fun decide(
        hostKey: String,
        entries: List<Pair<String, String>>,
        currentLine: String,
        storeReadable: Boolean,
    ): HostKeyDecision {
        if (!storeReadable) {
            return HostKeyDecision.StoreUnreadable("known_hosts 文件存在但读取/解析失败，出于安全原则拒绝连接。")
        }
        val existing = entries.filter { it.first == hostKey }
        if (existing.isEmpty()) {
            val rawBase64 = currentLine.substringAfter(" ").trim()
            val raw = runCatching { Base64.getDecoder().decode(rawBase64) }.getOrNull()
                ?: return HostKeyDecision.StoreUnreadable("无法解析对端主机密钥。")
            return HostKeyDecision.FirstSeen(hostKey, sha256Fingerprint(raw), currentLine)
        }
        val matched = existing.any { constantTimeEquals(it.second, currentLine) }
        return if (matched) {
            HostKeyDecision.Match
        } else {
            val savedLine = existing.first().second
            HostKeyDecision.Mismatch(
                expectedType = savedLine.substringBefore(" "),
                expectedFingerprintSha256 = runCatching {
                    sha256Fingerprint(Base64.getDecoder().decode(savedLine.substringAfter(" ").trim()))
                }.getOrDefault(""),
            )
        }
    }
}

/**
 * SSH 主机密钥验证管理器（TOFU，且 fail-closed）。
 *
 * 安全语义：
 *  1. known_hosts 中已有记录 → 常量时间比对密钥指纹；匹配放行，不一致抛 [HostKeyChangedException]。
 *  2. 首次连接（文件不存在 / 无该主机记录）→ 不静默信任：必须由 [trustCallback] 向用户回显
 *     SHA256 指纹并取得显式确认；未设置回调或用户拒绝时一律 DENY。
 *  3. known_hosts 文件存在但损坏/IO 异常 → 抛 [HostKeyStoreUnavailableException] 拒绝连接，
 *     绝不退化为「接受任意密钥」的 fail-open 行为。
 */
@Singleton
class HostKeyManager @Inject constructor(
    @ApplicationContext private val context: Context
) {
    private companion object {
        const val TAG = "HostKeyManager"
        const val KNOWN_HOSTS_FILE = "ssh_known_hosts"
    }

    /**
     * 首次连接的信任回调：实现方必须向用户回显 [fingerprintSha256] 并询问用户决策。
     * 返回 [HostKeyTrustDecision]：TRUST_ALWAYS 落盘 known_hosts；TRUST_ONCE 仅本会话内存信任；
     * REJECT/回调未设置 → 拒绝连接（fail-closed）。
     * 在无法弹窗（后台/自动重连）的调用路径下保持 null 即可，默认 fail-closed。
     */
    var trustCallback: HostKeyTrustCallback? = null

    /** 「本次信任（TRUST_ONCE）」的主机集合：仅进程内存内有效，不落盘，下次仍会回显指纹。 */
    private val sessionTrustedHosts = java.util.Collections.newSetFromMap(
        java.util.concurrent.ConcurrentHashMap<String, Boolean>()
    )

    private val knownHostsFile: File
        get() = File(context.filesDir, KNOWN_HOSTS_FILE)

    fun createVerifier(): HostKeyVerifier {
        return object : HostKeyVerifier {
            override fun verify(hostname: String, port: Int, key: PublicKey): Boolean {
                return this@HostKeyManager.verifyKey(hostname, port, key)
            }

            override fun findExistingAlgorithms(hostname: String, port: Int): List<String> {
                val file = knownHostsFile
                if (!file.exists()) return emptyList()
                val entries = runCatching { readKnownHosts(file) }.getOrNull() ?: return emptyList()
                val hostKey = HostKeyPolicy.hostKeyFor(hostname, port)
                return entries
                    .filter { it.first == hostKey }
                    .mapNotNull { entry ->
                        entry.second.substringBefore(" ").trim().takeIf { it.isNotEmpty() }
                    }
                    .distinct()
            }
        }
    }

    private fun verifyKey(hostname: String, port: Int, key: PublicKey): Boolean {
        val file = knownHostsFile
        val hostKey = HostKeyPolicy.hostKeyFor(hostname, port)
        val (storeReadable, entries) = loadKnownHostsState(file)
        val keyType = KeyType.fromKey(key)
        val currentLine = HostKeyPolicy.fingerprintLine(keyType.toString(), key.encoded)

        return when (val decision = HostKeyPolicy.decide(hostKey, entries, currentLine, storeReadable)) {
            HostKeyDecision.Match -> {
                FileLogger.v(TAG, "主机密钥验证通过: $hostKey")
                true
            }

            is HostKeyDecision.Mismatch -> {
                FileLogger.w(TAG, "主机密钥不匹配! host=$hostKey savedType=${decision.expectedType}")
                throw HostKeyChangedException(
                    host = hostname,
                    port = port,
                    expectedType = decision.expectedType,
                    message = "SSH 主机密钥已变更! 主机 $hostname:$port 的密钥与之前记录的不一致" +
                        "（记录指纹 ${decision.expectedFingerprintSha256.ifBlank { "未知" }}）。" +
                        "这可能是服务器重新安装系统（正常）或中间人攻击（危险）的迹象。" +
                        "请确认后手动删除 known_hosts 文件重新连接。"
                )
            }

            is HostKeyDecision.FirstSeen -> handleFirstSeen(hostname, port, hostKey, decision, file)

            is HostKeyDecision.StoreUnreadable -> {
                FileLogger.w(TAG, "known_hosts 不可用: ${decision.reason}")
                throw HostKeyStoreUnavailableException(decision.reason)
            }
        }
    }

    /**
     * 读取 known_hosts 并区分两种状态（fail-closed）：
     *  - 文件不存在：视为可读、空列表（首次使用）；
     *  - 文件存在但读取/解析异常：返回 readable=false，由上层 DENY。
     */
    private fun loadKnownHostsState(file: File): Pair<Boolean, List<Pair<String, String>>> {
        if (!file.exists()) return true to emptyList()
        val parsed = runCatching { readKnownHosts(file) }
        if (parsed.isFailure) {
            FileLogger.w(TAG, "known_hosts 读取/解析失败（fail-closed 拒绝连接）: ${parsed.exceptionOrNull()?.message}")
            return false to emptyList()
        }
        return true to parsed.getOrThrow()
    }

    /** 首连决策：本次信任（内存放行）/ 永久信任（落盘）/ 拒绝（默认 fail-closed）。 */
    private fun handleFirstSeen(
        hostname: String,
        port: Int,
        hostKey: String,
        decision: HostKeyDecision.FirstSeen,
        file: File,
    ): Boolean {
        // 「本次信任」已在内存中放行过 → 不再打扰用户，且不落盘。
        if (sessionTrustedHosts.contains(decision.hostKey)) {
            FileLogger.v(TAG, "主机 $hostKey 已在本会话信任（未持久化），放行。")
            return true
        }
        val cb = trustCallback
        val decisionResult = if (cb == null) HostKeyTrustDecision.REJECT
        else runCatching { cb.onFirstConnect(decision.hostKey, decision.fingerprintSha256) }
            .getOrDefault(HostKeyTrustDecision.REJECT)

        return when (decisionResult) {
            HostKeyTrustDecision.TRUST_ONCE -> {
                sessionTrustedHosts.add(decision.hostKey)
                FileLogger.i(TAG, "用户选择本次信任主机密钥（不落盘）: $hostKey (${decision.fingerprintSha256})")
                true
            }
            HostKeyTrustDecision.TRUST_ALWAYS -> {
                val saved = runCatching {
                    file.parentFile?.mkdirs()
                    file.appendText(decision.candidateLine + "\n")
                }.onFailure { FileLogger.w(TAG, "保存主机密钥失败: ${it.message}") }
                // 密钥未能落盘 → 视为不可信，拒绝连接（下次还会走 FirstSeen）。
                if (saved.isFailure) return false
                FileLogger.i(TAG, "用户永久信任并保存主机密钥: $hostKey (${decision.fingerprintSha256})")
                true
            }
            HostKeyTrustDecision.REJECT -> {
                FileLogger.w(TAG, "首次连接 $hostKey 未取得信任（指纹 ${decision.fingerprintSha256}），拒绝连接。")
                throw HostKeyUntrustedException(
                    host = hostname,
                    port = port,
                    fingerprintSha256 = decision.fingerprintSha256,
                    message = "首次连接 $hostname:$port，主机指纹 ${decision.fingerprintSha256} 未经显式信任，已拒绝连接。"
                )
            }
        }
    }

    /**
     * 读取 known_hosts 文件，返回 (hostKey, fingerprint) 列表。IO/格式异常向上抛出，由调用方判定 fail-closed。
     */
    private fun readKnownHosts(file: File): List<Pair<String, String>> {
        val result = mutableListOf<Pair<String, String>>()
        file.forEachLine { line ->
            val trimmed = line.trim()
            if (trimmed.isEmpty() || trimmed.startsWith("#")) return@forEachLine
            // 格式: hostname key-type base64-key
            val parts = trimmed.split(" ")
            if (parts.size >= 3) {
                val host = parts[0]
                val fingerprint = parts.drop(1).joinToString(" ")
                result.add(host to fingerprint)
            }
        }
        return result
    }

    /**
     * 清除所有已保存的主机密钥。用于"重置 SSH 信任"场景。
     */
    fun clearAllKeys() {
        if (knownHostsFile.exists()) {
            knownHostsFile.delete()
            FileLogger.i(TAG, "已清除所有 saved host keys")
        }
    }

    /**
     * 清除指定主机的密钥记录。
     */
    fun clearHostKey(hostname: String, port: Int = 22) {
        val file = knownHostsFile
        if (!file.exists()) return
        val hostKey = HostKeyPolicy.hostKeyFor(hostname, port)
        val entries = runCatching { readKnownHosts(file) }.getOrElse { return }
        val filtered = entries.filter { it.first != hostKey }
        file.writeText(filtered.joinToString("\n") { "${it.first} ${it.second}" } + "\n")
        FileLogger.i(TAG, "已清除主机密钥: $hostKey")
    }
}

/** 首次连接的用户决策结果。 */
enum class HostKeyTrustDecision {
    /** 仅本次连接放行（内存级，不落盘 known_hosts，下次仍回显指纹）。 */
    TRUST_ONCE,

    /** 永久信任：写入 known_hosts，后续不再提示。 */
    TRUST_ALWAYS,

    /** 拒绝连接（fail-closed）。 */
    REJECT
}

/** 首次连接信任回调：实现方向用户回显指纹，返回用户的显式决策。 */
fun interface HostKeyTrustCallback {
    /** @return 用户决策；未设置回调或异常时按 REJECT 处理。 */
    fun onFirstConnect(hostKey: String, fingerprintSha256: String): HostKeyTrustDecision
}

/** 主机密钥变更异常：服务器密钥与之前记录的不一致。 */
class HostKeyChangedException(
    val host: String,
    val port: Int,
    val expectedType: String,
    message: String
) : SecurityException(message)

/** 首次连接未经显式信任（fail-closed）。 */
class HostKeyUntrustedException(
    val host: String,
    val port: Int,
    val fingerprintSha256: String,
    message: String
) : SecurityException(message)

/** known_hosts 文件存在但损坏/不可读（fail-closed，拒绝连接）。 */
class HostKeyStoreUnavailableException(message: String) : SecurityException(message)
