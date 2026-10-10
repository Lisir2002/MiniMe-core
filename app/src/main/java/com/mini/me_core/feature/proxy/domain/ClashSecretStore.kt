package com.mini.me_core.feature.proxy.domain

import com.mini.me_core.core.security.CredentialEncryptor
import com.mini.me_core.core.util.FileLogger
import com.mini.me_core.datalayer.store.KVStore
import com.mini.me_core.feature.proxy.data.PROXY_NS
import kotlinx.coroutines.CompletableDeferred
import java.io.File

/**
 * P2-16 拆分产物：control secret 的生成 / 加密 / 存取。
 *
 * 逻辑原样搬迁自 [ClashProxyManager]（P0-5 安全加固一字不动）：
 *  - KVStore 密文优先 → 解密返回；
 *  - 否则旧版明文文件 `filesDir/minime/proxy/secret` 仍在 → 读明文、加密写 KV、删明文文件；
 *  - 都没有 → 生成新随机 secret，加密写 KV。
 *
 * 鉴权令牌以密文落在 KVStore，避免明文落在 filesDir。
 */
internal class ClashSecretStore(
    private val kv: KVStore,
    private val credentialEncryptor: CredentialEncryptor,
    private val configDir: () -> File,
) {
    private val tag = "ClashProxyManager"

    /** env 注入 / 控制器读取的安全鉴权令牌（app 运行时生成并落盘，跨进程一致）。 */
    @Volatile
    var secret: String = ""
        private set

    /** secret 异步加载完成信号（加密存储需在协程中解密；suspend 调用方据此等待）。 */
    val ready = CompletableDeferred<Unit>()

    /** P0-5：等待加密 secret 加载完成（所有用到 secret 的 suspend 入口先调用）。 */
    suspend fun ensureLoaded() {
        if (secret.isEmpty()) ready.await()
    }

    /**
     * P0-5：加载或创建 control secret，加密存储在 KVStore（与订阅 secret 同一套 CredentialEncryptor）。
     * 迁移路径见类注释。
     */
    suspend fun loadOrCreate(): String {
        // 1. 已迁移到加密存储
        kv.getString(PROXY_NS, ClashProxyManager.CONTROL_SECRET_KEY)?.takeIf { it.isNotBlank() }?.let { cipher ->
            runCatching { credentialEncryptor.decrypt(cipher, "proxy_control_secret") }
                .getOrNull()
                ?.takeIf { it.isNotBlank() }
                ?.let { return it }
            FileLogger.w(tag, "KV 中的 control secret 解密失败，回退到明文文件/重建")
        }
        // 2. 旧版明文文件迁移
        val dir = configDir().apply { mkdirs() }
        val legacy = File(dir, ClashProxyManager.SECRET_FILE)
        if (legacy.isFile) {
            val plain = runCatching { legacy.readText().trim() }.getOrNull().orEmpty()
            if (plain.isNotBlank()) {
                runCatching {
                    val cipher = credentialEncryptor.encrypt(plain)
                    kv.putString(PROXY_NS, ClashProxyManager.CONTROL_SECRET_KEY, cipher)
                    legacy.delete()
                }
                FileLogger.i(tag, "已把旧明文 control secret 迁移到加密存储并删除明文文件")
                return plain
            }
        }
        // 3. 生成新 secret
        val s = generateRandomSecret()
        runCatching {
            val cipher = credentialEncryptor.encrypt(s)
            kv.putString(PROXY_NS, ClashProxyManager.CONTROL_SECRET_KEY, cipher)
        }.onFailure { FileLogger.w(tag, "加密保存 control secret 失败: ${it.message}") }
        FileLogger.i(tag, "已生成新的代理 control secret（加密存储）")
        return s
    }

    /** 由门面在异步加载成功后回填 secret 字段。 */
    fun setLoadedSecret(value: String) {
        secret = value
    }

    private fun generateRandomSecret(): String {
        val pool = "abcdefghijklmnopqrstuvwxyz0123456789"
        return (0 until 24).joinToString("") { pool[kotlin.random.Random.nextInt(pool.length)].toString() }
    }
}
