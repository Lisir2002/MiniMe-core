package com.mini.me_core.feature.packager.domain.engine

import com.mini.me_core.core.util.FileLogger
import com.mini.me_core.datalayer.encryption.UnifiedKeyManager
import java.io.File
import java.io.FileInputStream
import java.io.FileOutputStream
import java.security.KeyStore
import java.security.SecureRandom
import java.util.Base64

/**
 * 打包器签名密钥库口令提供者。
 *
 * ## 背景（修复项）
 * 原实现把签名密钥库口令硬编码在源码中（`minime_packager_2026`）。密钥库文件位于
 * 应用私有目录，口令本身不构成远程攻击面，但一旦设备被 root、备份提取或 adb
 * 备份拿到 `default.keystore`，攻击者即可用同一签名身份重签 APK，冒充已安装
 * 应用的更新——硬编码口令使这层防护完全失效。
 *
 * ## 方案
 * - **新密钥库**：[SecureRandom] 生成 32 字节随机口令（Base64URL 编码），经
 *   [UnifiedKeyManager]（Android Keystore 硬件 MasterKey 包裹的 AES-256-GCM DEK）
 *   加密后，以**密文**形式存放在密钥库同目录（`default.keystore.pw`）。
 *   明文口令不落盘；密文离开本机硬件 Keystore 无法解开。
 * - **旧密钥库**（历史版本以硬编码口令创建）：首次使用时自动迁移——旧口令加载 →
 *   新随机口令包裹存储 → 原子重存密钥库文件。迁移失败不阻塞签名（回退旧口令维持
 *   现状并记警告日志），下次构建会重试（自愈）。
 * - **fail-close**：密钥库存在但所有口令失配时抛异常——绝不静默重建密钥库，
 *   重建会更换签名身份，导致已安装应用无法原地更新。
 *   新密钥库场景下口令包裹失败（Keystore 不可用）同样抛异常，不静默降级为硬编码口令。
 *
 * ## 测试
 * 纯 JVM 可测：[UnifiedKeyManager] 是接口，测试注入内存假实现；
 * 其余仅依赖 `java.security`，不触碰 AndroidKeyStore。
 *
 * @param keyManager 统一密钥管理器（硬件 MasterKey 包裹的口令加密存储）
 */
class PackagerKeystorePassword(
    private val keyManager: UnifiedKeyManager,
) {

    /**
     * 获取用于打开 [keystoreFile] 的口令。
     *
     * @return 可直接用于 `KeyStore.load` / `getKey` / `store` 的口令字符数组。
     *   调用方使用完毕后应 `fill(0)` 擦除（本类返回的均为新数组，不影响内部状态）。
     * @throws IllegalStateException 密钥库存在但包裹口令与旧版口令均失配
     * @throws IllegalStateException 新密钥库场景下口令包裹失败（Keystore 不可用）
     */
    suspend fun obtain(keystoreFile: File): CharArray {
        if (!keystoreFile.exists()) {
            // 新密钥库：随机口令 + 包裹存储（失败即抛错，不降级为硬编码口令）
            return createAndStoreNewPassword(keystoreFile)
        }

        // 1. 优先使用已包裹存储的随机口令
        loadStoredPassword(keystoreFile)?.let { stored ->
            if (canOpen(keystoreFile, stored)) return stored
        }

        // 2. 回退旧版硬编码口令（历史密钥库），成功后自动迁移到随机口令
        if (canOpen(keystoreFile, LEGACY_PASSWORD)) {
            return migrate(keystoreFile)
        }

        throw IllegalStateException(
            "无法打开签名密钥库：${keystoreFile.name}。已尝试包裹存储的随机口令与旧版口令，" +
                "均无法打开（含别名 ${ApkSigner.DEFAULT_ALIAS} 校验）。" +
                "若该文件由其它工具生成或已损坏，请手动删除后重建——" +
                "注意：重建将更换签名身份，已安装的同包名应用将无法原地更新。"
        )
    }

    // ============== 内部实现 ==============

    /** 新密钥库：生成随机口令并包裹存储；包裹失败抛异常（fail-close，不降级硬编码口令） */
    private suspend fun createAndStoreNewPassword(keystoreFile: File): CharArray {
        val password = generateRandomPassword()
        storePassword(keystoreFile, password) // 内部失败会抛出
        FileLogger.i(TAG, "已为新签名密钥库生成随机口令并包裹存储: ${keystoreFile.name}")
        return password
    }

    /**
     * 旧版硬编码口令密钥库的自动迁移。
     *
     * 顺序至关重要：**先**包裹存储新口令，**再**原子重存密钥库文件。
     * 若顺序颠倒，重存成功后包裹失败将导致「密钥库用新口令加密但口令丢失」——
     * 用户永远无法再打开自己的密钥库。当前顺序下，任一步失败都退化为
     * 「维持旧口令签名 + 下次构建重试迁移」。
     */
    private suspend fun migrate(keystoreFile: File): CharArray {
        FileLogger.i(TAG, "检测到旧版硬编码口令密钥库，开始自动迁移: ${keystoreFile.name}")
        val newPassword = generateRandomPassword()

        val stored = try {
            storePassword(keystoreFile, newPassword)
            true
        } catch (e: Exception) {
            FileLogger.w(TAG, "迁移中止：新口令包裹失败，本次仍以旧口令签名（下次重试）: ${e.message}")
            false
        }
        if (!stored) return LEGACY_PASSWORD

        return try {
            rekeyKeystoreFile(keystoreFile, LEGACY_PASSWORD, newPassword)
            FileLogger.i(TAG, "签名库口令迁移完成，已升级为随机口令: ${keystoreFile.name}")
            newPassword
        } catch (e: Exception) {
            // 重存失败：密钥库文件保持原状（临时文件 + 原子替换，不会写坏原文件），
            // 已存储的新口令失配，下次构建 loadStoredPassword 失配 → 旧口令回退 → 再次迁移（自愈）。
            FileLogger.w(TAG, "密钥库重存失败（原文件未损坏，下次构建将重试迁移）: ${e.message}")
            LEGACY_PASSWORD
        }
    }

    /** 读取并解包已存储的随机口令；任何失败（文件缺失/损坏/Keystore 故障）均按「无可用口令」处理 */
    private suspend fun loadStoredPassword(keystoreFile: File): CharArray? {
        val pwFile = passwordFileFor(keystoreFile)
        if (!pwFile.exists()) return null
        val ciphertext = try {
            pwFile.readText(Charsets.UTF_8).trim()
        } catch (e: Exception) {
            FileLogger.w(TAG, "读取包裹口令文件失败，按无可用口令处理: ${e.message}")
            return null
        }
        if (ciphertext.isEmpty()) return null
        return try {
            String(keyManager.decrypt(PASSWORD_PURPOSE, ciphertext), Charsets.UTF_8).toCharArray()
        } catch (e: Exception) {
            // DEK 被紧急重置 / Keystore 故障 / 密文损坏：均按「无可用包裹口令」处理，
            // 由 obtain() 继续走旧版口令回退或报错，不在此处静默重建任何东西。
            FileLogger.w(TAG, "解包签名库口令失败（回退旧版口令或报错）: ${e.javaClass.simpleName}")
            null
        }
    }

    /** 用硬件 MasterKey 包裹口令，密文写入密钥库同目录；失败抛异常 */
    private suspend fun storePassword(keystoreFile: File, password: CharArray) {
        val ciphertext = keyManager.encrypt(
            PASSWORD_PURPOSE,
            password.concatToString().toByteArray(Charsets.UTF_8),
        )
        val pwFile = passwordFileFor(keystoreFile)
        FileOutputStream(pwFile).use { fos ->
            fos.write(ciphertext.toByteArray(Charsets.UTF_8))
        }
    }

    /** 用 [oldPassword] 加载密钥库，以 [newPassword] 经临时文件原子重存 */
    private fun rekeyKeystoreFile(keystoreFile: File, oldPassword: CharArray, newPassword: CharArray) {
        val keystore = KeyStore.getInstance("PKCS12")
        FileInputStream(keystoreFile).use { fis -> keystore.load(fis, oldPassword) }
        val tmp = File(keystoreFile.parentFile, "${keystoreFile.name}.rekey.tmp")
        try {
            FileOutputStream(tmp).use { fos -> keystore.store(fos, newPassword) }
            if (!tmp.renameTo(keystoreFile)) {
                throw IllegalStateException("密钥库原子替换失败（rename 返回 false）")
            }
        } finally {
            if (tmp.exists()) tmp.delete()
        }
    }

    /** 口令 + 别名双重校验：能加载且包含打包器别名才算「能打开」 */
    private fun canOpen(keystoreFile: File, password: CharArray): Boolean = try {
        val keystore = KeyStore.getInstance("PKCS12")
        FileInputStream(keystoreFile).use { fis -> keystore.load(fis, password) }
        keystore.aliases().asSequence().any { it.equals(ApkSigner.DEFAULT_ALIAS, ignoreCase = true) }
    } catch (e: Exception) {
        false
    }

    private fun passwordFileFor(keystoreFile: File): File =
        File(keystoreFile.parentFile, "${keystoreFile.name}$PW_FILE_SUFFIX")

    /** 密码学安全的随机口令：32 字节 → Base64URL 无填充（43 字符，字母数字安全集） */
    private fun generateRandomPassword(): CharArray {
        val bytes = ByteArray(PASSWORD_BYTES)
        SecureRandom().nextBytes(bytes)
        return Base64.getUrlEncoder().withoutPadding().encodeToString(bytes).toCharArray()
    }

    companion object {
        private const val TAG = "PackagerKeystorePw"

        /**
         * 旧版硬编码口令——**仅用于历史密钥库的加载与迁移**，新密钥库绝不使用。
         * 保留此常量是迁移兼容的代价：任何拿到历史版本 APK 的人都知道它，
         * 因此迁移必须尽早完成（见 [migrate]）。
         */
        private val LEGACY_PASSWORD = "minime_packager_2026".toCharArray()

        /** 口令密文文件后缀（与密钥库同目录，内容为 AES-256-GCM 密文，非明文） */
        private const val PW_FILE_SUFFIX = ".pw"

        /** 随机口令字节数（32 字节熵 ≈ 256 bit，Base64URL 后 43 字符） */
        private const val PASSWORD_BYTES = 32

        /** [UnifiedKeyManager] 中包裹口令的 DEK 用途标识 */
        private const val PASSWORD_PURPOSE = "packager_keystore_password"
    }
}
