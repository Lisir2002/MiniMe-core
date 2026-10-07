package com.mini.me_core.feature.packager.domain.engine

import com.android.apksig.ApkSigner
import com.android.apksig.util.DataSources
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
import java.io.RandomAccessFile
import java.math.BigInteger
import java.security.KeyPair
import java.security.KeyPairGenerator
import java.security.KeyStore
import java.security.PrivateKey
import java.security.Security
import java.security.cert.X509Certificate
import java.util.Date

/**
 * APK 签名器（V1 + V2 方案）
 *
 * 使用 Android apksig 库实现 APK 的 V1（JAR 签名）和 V2（APK Signature Scheme v2）签名。
 * V2 签名从 Android 7.0（API 24）开始支持，Android 11（API 30）起强制要求。
 * 同时启用 V1 签名以兼容 Android 6.0 及以下设备。
 */
class ApkSigner {

    companion object {
        private const val TAG = "ApkSigner"

        init {
            // 确保 BouncyCastle 提供者已注册
            if (Security.getProvider(BouncyCastleProvider.PROVIDER_NAME) == null) {
                Security.addProvider(BouncyCastleProvider())
            }
        }

        /** 默认签名别名 */
        private const val DEFAULT_ALIAS = "minime_packager"

        /** 默认签名密码 */
        private const val DEFAULT_PASSWORD = "minime_packager_2026"

        /**
         * 对 APK 进行 V1 + V2 签名
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
            FileLogger.d(TAG, "开始 APK V1+V2 签名: ${unsignedApk.name} (${unsignedApk.length()} bytes)")

            // 加载或生成签名密钥
            val (privateKey, certificate) = loadOrGenerateKeystore(keystoreFile)
            FileLogger.d(TAG, "签名密钥加载完成: alias=$DEFAULT_ALIAS")

            // 构建签名配置
            val signerConfig = ApkSigner.SignerConfig.Builder(
                DEFAULT_ALIAS,
                privateKey,
                listOf(certificate)
            ).build()

            // 确保输出目录存在
            signedApk.parentFile?.mkdirs()
            if (signedApk.exists()) signedApk.delete()

            // 使用 apksig 进行 V1+V2 签名
            val inputDataSource = DataSources.asDataSource(RandomAccessFile(unsignedApk, "r"))
            val apkSigner = ApkSigner.Builder(listOf(signerConfig))
                .setInputApk(inputDataSource)
                .setOutputApk(signedApk)
                .setV1SigningEnabled(true)  // 兼容 Android 6.0 及以下
                .setV2SigningEnabled(true)  // Android 7.0+，Android 11+ 强制
                .setV3SigningEnabled(false) // V3 签名暂不启用
                .build()

            apkSigner.sign()

            FileLogger.d(TAG, "APK V1+V2 签名完成: ${signedApk.name} (${signedApk.length()} bytes)")

            // 验证签名
            verifySignature(signedApk)
        }

        /**
         * 验证 APK 签名
         */
        private fun verifySignature(apkFile: File) {
            try {
                val result = com.android.apksig.ApkVerifier.Builder(apkFile)
                    .build()
                    .verify()

                if (result.isVerified) {
                    FileLogger.d(TAG, "签名验证通过: V1=${result.isVerifiedUsingV1Scheme}, V2=${result.isVerifiedUsingV2Scheme}")
                } else {
                    FileLogger.w(TAG, "签名验证失败: ${result.errors}")
                }
            } catch (e: Exception) {
                FileLogger.w(TAG, "签名验证异常: ${e.message}")
            }
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
