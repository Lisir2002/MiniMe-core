package com.mini.me_core.feature.packager.domain.engine

import com.mini.me_core.core.util.FileLogger
import org.bouncycastle.asn1.x500.X500Name
import org.bouncycastle.cert.X509v3CertificateBuilder
import org.bouncycastle.cert.jcajce.JcaX509CertificateConverter
import org.bouncycastle.cert.jcajce.JcaX509v3CertificateBuilder
import org.bouncycastle.jce.provider.BouncyCastleProvider
import org.bouncycastle.operator.ContentSigner
import org.bouncycastle.operator.jcajce.JcaContentSignerBuilder
import java.io.File
import java.io.FileInputStream
import java.io.FileOutputStream
import java.math.BigInteger
import java.security.KeyPair
import java.security.KeyPairGenerator
import java.security.KeyStore
import java.security.PrivateKey
import java.security.Security
import java.security.cert.X509Certificate
import java.util.Date

/**
 * APK 签名器（V2 + V3 手动实现方案）
 *
 * 完全不依赖 apksig 库，基于 AOSP 公开标准手动实现 APK Signature Scheme v2 和 v3。
 * 解决 apksig 库在 Android 环境下 "Failed to encode signature block" 的根本性兼容问题。
 *
 * 签名方案：
 * - V2（APK Signature Scheme v2）：Android 7.0+，Android 11+ 强制要求
 * - V3（APK Signature Scheme v3）：Android 9.0+，支持密钥轮换
 *
 * 注意：当前实现不包含 V1（JAR 签名），最低支持 Android 7.0（API 24）。
 * 如需兼容 Android 6.0 及以下，需额外实现 V1 签名。
 */
class ApkSigner {

    companion object {
        private const val TAG = "ApkSigner"

        init {
            // 确保 BouncyCastle 提供者已注册（用于生成证书）
            if (Security.getProvider(BouncyCastleProvider.PROVIDER_NAME) == null) {
                Security.addProvider(BouncyCastleProvider())
            }
        }

        /** 默认签名别名 */
        private const val DEFAULT_ALIAS = "minime_packager"

        /** 默认签名密码 */
        private const val DEFAULT_PASSWORD = "minime_packager_2026"

        /**
         * 对 APK 进行 V2 + V3 签名
         *
         * @param unsignedApk 未签名的 APK
         * @param signedApk 签名后的 APK
         * @param keystoreFile 签名密钥库文件（如果不存在会自动生成）
         */
        fun signApk(
            unsignedApk: File,
            signedApk: File,
            keystoreFile: File
        ) {
            FileLogger.d(TAG, "开始 APK V2+V3 签名: ${unsignedApk.name} (${unsignedApk.length()} bytes)")

            // 加载或生成签名密钥
            val (privateKey, certificate) = loadOrGenerateKeystore(keystoreFile)
            FileLogger.d(TAG, "签名密钥加载完成: alias=$DEFAULT_ALIAS, cert=${certificate.subjectX500Principal.name}")

            // 确保输出目录存在
            signedApk.parentFile?.mkdirs()
            if (signedApk.exists()) signedApk.delete()

            // 使用手动实现的 V2+V3 签名器
            V2ApkSigner.sign(
                unsignedApk = unsignedApk,
                signedApk = signedApk,
                privateKey = privateKey,
                certificate = certificate
            )

            FileLogger.d(TAG, "APK V2+V3 签名完成: ${signedApk.name} (${signedApk.length()} bytes)")
        }

        /**
         * 加载或生成签名密钥库
         */
        private fun loadOrGenerateKeystore(keystoreFile: File): Pair<PrivateKey, X509Certificate> {
            val password = DEFAULT_PASSWORD.toCharArray()

            return if (keystoreFile.exists()) {
                // 加载已有密钥库
                FileLogger.d(TAG, "加载已有签名密钥库: ${keystoreFile.name}")
                val keystore = KeyStore.getInstance("PKCS12")
                FileInputStream(keystoreFile).use { fis ->
                    keystore.load(fis, password)
                }
                val privateKey = keystore.getKey(DEFAULT_ALIAS, password) as PrivateKey
                val certificate = keystore.getCertificate(DEFAULT_ALIAS) as X509Certificate
                Pair(privateKey, certificate)
            } else {
                // 生成新的密钥对和证书
                FileLogger.d(TAG, "生成新的签名密钥库: ${keystoreFile.name}")
                val keyPair = generateKeyPair()
                val certificate = generateSelfSignedCertificate(keyPair)

                // 保存到密钥库
                val keystore = KeyStore.getInstance("PKCS12")
                keystore.load(null, password)
                keystore.setKeyEntry(
                    DEFAULT_ALIAS,
                    keyPair.private,
                    password,
                    arrayOf(certificate)
                )
                keystoreFile.parentFile?.mkdirs()
                FileOutputStream(keystoreFile).use { fos ->
                    keystore.store(fos, password)
                }

                Pair(keyPair.private, certificate)
            }
        }

        /**
         * 生成 RSA 密钥对（2048 位）
         */
        private fun generateKeyPair(): KeyPair {
            val generator = KeyPairGenerator.getInstance("RSA")
            generator.initialize(2048)
            return generator.generateKeyPair()
        }

        /**
         * 生成自签名证书（有效期 25 年）
         */
        private fun generateSelfSignedCertificate(keyPair: KeyPair): X509Certificate {
            val owner = X500Name("CN=MiniMe Packager, OU=Development, O=MiniMe, L=Suzhou, ST=Jiangsu, C=CN")
            val serial = BigInteger.valueOf(System.currentTimeMillis())
            val notBefore = Date(System.currentTimeMillis() - 24 * 60 * 60 * 1000) // 前一天
            val notAfter = Date(System.currentTimeMillis() + 25L * 365 * 24 * 60 * 60 * 1000) // 25 年后

            val builder: X509v3CertificateBuilder = JcaX509v3CertificateBuilder(
                owner,
                serial,
                notBefore,
                notAfter,
                owner,
                keyPair.public
            )

            val signer: ContentSigner = JcaContentSignerBuilder("SHA256WithRSA")
                .setProvider(BouncyCastleProvider.PROVIDER_NAME)
                .build(keyPair.private)

            return JcaX509CertificateConverter()
                .setProvider(BouncyCastleProvider.PROVIDER_NAME)
                .getCertificate(builder.build(signer))
        }
    }
}
