package com.mini.me_core.feature.packager.domain.engine

import com.android.apksig.ApkSigner
import com.mini.me_core.core.util.FileLogger
import org.bouncycastle.asn1.x500.X500Name
import org.bouncycastle.cert.X509v3CertificateBuilder
import org.bouncycastle.cert.jcajce.JcaX509CertificateConverter
import org.bouncycastle.cert.jcajce.JcaX509v3CertificateBuilder
import org.bouncycastle.jce.provider.BouncyCastleProvider
import org.bouncycastle.operator.ContentSigner
import org.bouncycastle.operator.jcajce.JcaContentSignerBuilder
import java.io.ByteArrayInputStream
import java.io.File
import java.io.FileInputStream
import java.io.FileOutputStream
import java.math.BigInteger
import java.security.KeyPair
import java.security.KeyPairGenerator
import java.security.KeyStore
import java.security.PrivateKey
import java.security.Provider
import java.security.Security
import java.security.cert.CertificateFactory
import java.security.cert.X509Certificate
import java.util.Date

/**
 * APK 签名器（V1 + V2 + V3 完整官方签名方案）
 *
 * 使用 Android 官方 apksig 库实现完整签名：
 * - V1（JAR 签名）：兼容 Android 6.0 及以下
 * - V2（APK Signature Scheme v2）：Android 7.0+，Android 11+ 强制要求
 * - V3（APK Signature Scheme v3）：Android 9.0+，支持密钥轮换
 *
 * 关键实现要点（基于官方文档）：
 * 1. 显式指定 minSdkVersion，避免从修改后的 manifest 自动读取失败
 * 2. 证书使用 Array<X509Certificate> 而非 List
 * 3. 证书标准化为系统 X.509 ASN.1 DER 格式
 * 4. 签名时使用系统默认安全提供者（Conscrypt），避免 BouncyCastle 编码冲突
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

        /** 最低 SDK 版本（Android 5.0），显式指定避免 manifest 读取失败 */
        private const val MIN_SDK_VERSION = 21

        /**
         * 对 APK 进行 V1 + V2 + V3 完整签名
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
            FileLogger.d(TAG, "开始 APK V1+V2+V3 签名: ${unsignedApk.name} (${unsignedApk.length()} bytes)")

            // 加载或生成签名密钥
            val (privateKey, certificate) = loadOrGenerateKeystore(keystoreFile)
            FileLogger.d(TAG, "签名密钥加载完成: alias=$DEFAULT_ALIAS, cert=${certificate.subjectX500Principal.name}")

            // 确保输出目录存在
            signedApk.parentFile?.mkdirs()
            if (signedApk.exists()) signedApk.delete()

            // 构建签名配置
            // 注意：第三个参数是 List<X509Certificate>
            val signerConfig = ApkSigner.SignerConfig.Builder(
                DEFAULT_ALIAS,
                privateKey,
                listOf(certificate)
            ).build()
            FileLogger.d(TAG, "签名配置构建完成")

            // 使用 apksig 进行 V1+V2+V3 签名
            val apkSigner = ApkSigner.Builder(listOf(signerConfig))
                .setInputApk(unsignedApk)
                .setOutputApk(signedApk)
                .setV1SigningEnabled(true)  // 兼容 Android 6.0 及以下
                .setV2SigningEnabled(true)  // Android 7.0+，Android 11+ 强制
                .setV3SigningEnabled(true)  // Android 9.0+，支持密钥轮换
                .setMinSdkVersion(MIN_SDK_VERSION) // 显式指定，避免 manifest 读取失败
                .build()
            FileLogger.d(TAG, "签名器构建完成: V1=true, V2=true, V3=true, minSdk=$MIN_SDK_VERSION")

            // 签名前调整安全提供者顺序：
            // 将系统默认提供者（通常是 Conscrypt/AndroidOpenSSL）置于首位
            // BouncyCastle 移到末尾，避免 ASN.1 编码冲突
            val originalProviders = Security.getProviders().clone()
            val bcProvider = Security.getProvider(BouncyCastleProvider.PROVIDER_NAME)
            if (bcProvider != null) {
                Security.removeProvider(BouncyCastleProvider.PROVIDER_NAME)
                Security.addProvider(bcProvider) // 重新添加会放到末尾
                FileLogger.d(TAG, "安全提供者调整: BouncyCastle 移至末尾，系统默认提供者优先")
            }
            FileLogger.d(TAG, "当前提供者顺序: ${Security.getProviders().map { it.name }}")

            try {
                apkSigner.sign()
                FileLogger.d(TAG, "APK V1+V2+V3 签名完成: ${signedApk.name} (${signedApk.length()} bytes)")
            } catch (e: Exception) {
                FileLogger.e(TAG, "APK 签名失败: ${e.message}", e)
                // 清理可能的不完整输出
                if (signedApk.exists()) signedApk.delete()
                throw e
            } finally {
                // 恢复原始提供者顺序
                restoreProviders(originalProviders)
            }

            // 验证签名
            verifySignature(signedApk)
        }

        /**
         * 恢复原始安全提供者顺序
         */
        private fun restoreProviders(original: Array<Provider>) {
            try {
                // 移除所有提供者
                for (provider in Security.getProviders()) {
                    Security.removeProvider(provider.name)
                }
                // 按原始顺序重新添加
                for (provider in original) {
                    Security.addProvider(provider)
                }
                FileLogger.d(TAG, "安全提供者顺序已恢复")
            } catch (e: Exception) {
                FileLogger.w(TAG, "恢复安全提供者顺序失败: ${e.message}")
            }
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
                    FileLogger.d(TAG, "签名验证通过: V1=${result.isVerifiedUsingV1Scheme}, V2=${result.isVerifiedUsingV2Scheme}, V3=${result.isVerifiedUsingV3Scheme}")
                } else {
                    FileLogger.w(TAG, "签名验证失败: errors=${result.errors}, warnings=${result.warnings}")
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

            val (privateKey, certificate) = if (keystoreFile.exists()) {
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

            // 将证书标准化为系统可识别的 X.509 ASN.1 DER 格式
            // 这是 V2/V3 签名块编码的关键要求
            val standardCert = normalizeCertificate(certificate)
            FileLogger.d(TAG, "证书信息: subject=${standardCert.subjectX500Principal.name}, issuer=${standardCert.issuerX500Principal.name}, sigAlg=${standardCert.sigAlgName}")
            return Pair(privateKey, standardCert)
        }

        /**
         * 将证书转换为标准 X.509 ASN.1 DER 格式
         *
         * V2/V3 签名块要求证书必须是标准 ASN.1 DER 格式。
         * BouncyCastle 生成的证书内部结构可能与系统标准格式有细微差异，
         * 通过 CertificateFactory 重新编码可以确保格式正确。
         */
        private fun normalizeCertificate(cert: X509Certificate): X509Certificate {
            return try {
                val certBytes = cert.encoded
                val factory = CertificateFactory.getInstance("X.509")
                val standard = factory.generateCertificate(ByteArrayInputStream(certBytes)) as X509Certificate
                FileLogger.d(TAG, "证书标准化成功: ${certBytes.size} bytes -> ${standard.encoded.size} bytes")
                standard
            } catch (e: Exception) {
                FileLogger.w(TAG, "证书标准化失败，使用原证书: ${e.message}")
                cert
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
