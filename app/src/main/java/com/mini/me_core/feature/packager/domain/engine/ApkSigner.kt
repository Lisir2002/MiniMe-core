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
import java.security.MessageDigest
import java.security.PrivateKey
import java.security.Security
import java.security.cert.X509Certificate
import java.util.Base64
import java.util.Date
import java.util.zip.ZipEntry
import java.util.zip.ZipInputStream
import java.util.zip.ZipOutputStream

/**
 * APK 签名器（V1 方案 / JAR 签名）
 *
 * 使用 BouncyCastle 实现 APK 的 V1 签名。
 * V1 签名基于 JAR 签名机制，在 META-INF/ 目录下生成：
 * - MANIFEST.MF：所有文件的 digest
 * - CERT.SF：MANIFEST.MF 各条目的 digest
 * - CERT.RSA：签名 + 证书
 *
 * P0 阶段使用 V1 签名，兼容性最好。P1 阶段可扩展 V2/V3 签名。
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

        private const val META_INF = "META-INF/"
        private const val MANIFEST_MF = "$META_INF/MANIFEST.MF"
        private const val CERT_SF = "$META_INF/CERT.SF"
        private const val CERT_RSA = "$META_INF/CERT.RSA"

        /** 默认签名别名 */
        private const val DEFAULT_ALIAS = "minime_packager"

        /** 默认签名密码 */
        private const val DEFAULT_PASSWORD = "minime_packager_2026"

        /**
         * 对 APK 进行签名
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
            FileLogger.d(TAG, "开始 APK 签名: ${unsignedApk.name} (${unsignedApk.length()} bytes)")
            // 加载或生成签名密钥
            val (privateKey, certificate) = loadOrGenerateKeystore(keystoreFile)

            // 读取未签名 APK 的所有文件
            val files = mutableMapOf<String, ByteArray>()
            ZipInputStream(FileInputStream(unsignedApk)).use { zis ->
                var entry: ZipEntry?
                while (zis.nextEntry.also { entry = it } != null) {
                    val name = entry!!.name
                    // 跳过已有的签名文件
                    if (!name.startsWith(META_INF) || name == MANIFEST_MF) {
                        files[name] = zis.readBytes()
                    }
                    zis.closeEntry()
                }
            }
            FileLogger.d(TAG, "读取到 ${files.size} 个文件待签名")

            // 生成 MANIFEST.MF
            val manifestContent = generateManifest(files)
            FileLogger.d(TAG, "生成 MANIFEST.MF 完成")

            // 生成 CERT.SF
            val sfContent = generateSignatureFile(manifestContent)
            FileLogger.d(TAG, "生成 CERT.SF 完成")

            // 生成 CERT.RSA（签名 + 证书）
            val rsaContent = generateSignatureBlock(sfContent, privateKey, certificate)
            FileLogger.d(TAG, "生成 CERT.RSA 完成")

            // 写入签名后的 APK
            signedApk.parentFile?.mkdirs()
            if (signedApk.exists()) signedApk.delete()

            ZipOutputStream(FileOutputStream(signedApk)).use { zos ->
                // 写入 MANIFEST.MF（必须第一个）
                writeEntry(zos, MANIFEST_MF, manifestContent.toByteArray(Charsets.UTF_8))
                // 写入 CERT.SF
                writeEntry(zos, CERT_SF, sfContent.toByteArray(Charsets.UTF_8))
                // 写入 CERT.RSA
                writeEntry(zos, CERT_RSA, rsaContent)

                // 写入原始文件（排除 META-INF 下的签名相关文件）
                for ((name, data) in files) {
                    if (name != MANIFEST_MF && !name.startsWith(META_INF)) {
                        writeEntry(zos, name, data)
                    }
                }
            }
            FileLogger.d(TAG, "APK 签名完成: ${signedApk.name} (${signedApk.length()} bytes)")
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

        /**
         * 生成 MANIFEST.MF 内容
         */
        private fun generateManifest(files: Map<String, ByteArray>): String {
            val sb = StringBuilder()
            sb.append("Manifest-Version: 1.0\r\n")
            sb.append("Created-By: MiniMe Packager 1.0\r\n")
            sb.append("\r\n")

            // 按文件名排序，保证确定性
            val sortedNames = files.keys.filter {
                it != MANIFEST_MF && !it.startsWith(META_INF)
            }.sorted()

            for (name in sortedNames) {
                val data = files[name] ?: continue
                val digest = sha256Base64(data)
                sb.append("Name: $name\r\n")
                sb.append("SHA-256-Digest: $digest\r\n")
                sb.append("\r\n")
            }

            return sb.toString()
        }

        /**
         * 生成 CERT.SF 内容（签名文件）
         */
        private fun generateSignatureFile(manifestContent: String): String {
            val sb = StringBuilder()
            sb.append("Signature-Version: 1.0\r\n")
            sb.append("Created-By: MiniMe Packager 1.0\r\n")
            sb.append("SHA-256-Digest-Manifest: ${sha256Base64(manifestContent.toByteArray(Charsets.UTF_8))}\r\n")
            sb.append("\r\n")

            // 对 MANIFEST.MF 中的每个条目计算 digest
            val sections = manifestContent.split("\r\n\r\n").filter { it.isNotBlank() }
            for (section in sections) {
                if (section.startsWith("Manifest-Version")) continue
                val sectionWithNewline = "$section\r\n\r\n"
                val nameLine = section.lines().firstOrNull { it.startsWith("Name:") }
                val name = nameLine?.substringAfter("Name: ")?.trim() ?: continue
                val digest = sha256Base64(sectionWithNewline.toByteArray(Charsets.UTF_8))
                sb.append("Name: $name\r\n")
                sb.append("SHA-256-Digest: $digest\r\n")
                sb.append("\r\n")
            }

            return sb.toString()
        }

        /**
         * 生成 CERT.RSA 内容（PKCS7 签名块）
         */
        private fun generateSignatureBlock(
            sfContent: String,
            privateKey: PrivateKey,
            certificate: X509Certificate
        ): ByteArray {
            // 使用 BouncyCastle 的 CMSSignedData 生成 PKCS7 签名
            val cmsBuilder = org.bouncycastle.cms.CMSSignedDataGenerator()
            val signerInfoGenerator = org.bouncycastle.cms.jcajce.JcaSignerInfoGeneratorBuilder(
                org.bouncycastle.operator.jcajce.JcaDigestCalculatorProviderBuilder()
                    .setProvider(BouncyCastleProvider.PROVIDER_NAME)
                    .build()
            )
                .build(
                    JcaContentSignerBuilder("SHA256WithRSA")
                        .setProvider(BouncyCastleProvider.PROVIDER_NAME)
                        .build(privateKey),
                    certificate
                )

            cmsBuilder.addSignerInfoGenerator(signerInfoGenerator)
            cmsBuilder.addCertificate(org.bouncycastle.cert.jcajce.JcaX509CertificateHolder(certificate))

            val processable = org.bouncycastle.cms.CMSProcessableByteArray(
                sfContent.toByteArray(Charsets.UTF_8)
            )

            val signedData = cmsBuilder.generate(processable, true)
            return signedData.encoded
        }

        /**
         * 计算 SHA-256 并返回 Base64 编码
         */
        private fun sha256Base64(data: ByteArray): String {
            val digest = MessageDigest.getInstance("SHA-256")
            val hash = digest.digest(data)
            return Base64.getEncoder().encodeToString(hash)
        }

        /**
         * 写入 ZIP 条目
         */
        private fun writeEntry(zos: ZipOutputStream, name: String, data: ByteArray) {
            val entry = ZipEntry(name)
            entry.time = System.currentTimeMillis()
            zos.putNextEntry(entry)
            zos.write(data)
            zos.closeEntry()
        }
    }
}
