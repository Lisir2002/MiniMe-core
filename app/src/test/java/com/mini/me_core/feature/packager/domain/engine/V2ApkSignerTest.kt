package com.mini.me_core.feature.packager.domain.engine

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File
import java.io.FileInputStream
import java.io.FileOutputStream
import java.security.KeyStore
import java.security.PrivateKey
import java.security.cert.X509Certificate
import java.util.zip.ZipEntry
import java.util.zip.ZipOutputStream

/**
 * 打包器 V2 签名端到端回归测试。
 *
 * 构造最小未签名 APK，经 [V2ApkSigner] 签名后，用 Android SDK 的 apksigner
 * 做权威验证，守护「签名块格式必须被系统认可」这条底线。
 *
 * 证书用 JDK 自带 keytool 生成（PKCS12），不依赖 BouncyCastle，隔离测试环境
 * 依赖冲突；[V2ApkSigner.sign] 本身仅使用 java.security，可在纯 JVM 运行。
 *
 * 历史 bug：V2ApkSigner 的 ID-value pair 长度字段少算 4 字节（未含 ID 本身），
 * 且 V3 块错误复用 V2 signed_data 格式，导致 apksigner 报
 * "Malformed list of signers"、安装时提示「缺乏开发者证书」。
 */
class V2ApkSignerTest {

    private fun createMinimalUnsignedApk(file: File) {
        file.parentFile?.mkdirs()
        if (file.exists()) file.delete()
        ZipOutputStream(FileOutputStream(file)).use { zos ->
            listOf(
                "AndroidManifest.xml" to "<manifest/>".toByteArray(),
                "classes.dex" to ByteArray(64),
                "resources.arsc" to ByteArray(32)
            ).forEach { (name, content) ->
                val entry = ZipEntry(name)
                entry.method = ZipEntry.DEFLATED
                zos.putNextEntry(entry)
                zos.write(content)
                zos.closeEntry()
            }
        }
    }

    /** 用 keytool 生成 PKCS12 测试密钥库，返回 (PrivateKey, X509Certificate) */
    private fun generateTestKeystore(keystoreFile: File, password: String): Pair<PrivateKey, X509Certificate> {
        val keytool = File(System.getProperty("java.home"), "bin/keytool").takeIf { it.exists() }?.absolutePath
            ?: "keytool"
        val cmd = listOf(
            keytool, "-genkeypair",
            "-alias", "test",
            "-keyalg", "RSA", "-keysize", "2048",
            "-validity", "10000",
            "-dname", "CN=MiniMe Test, OU=Test, O=MiniMe, C=CN",
            "-keystore", keystoreFile.absolutePath,
            "-storetype", "PKCS12",
            "-storepass", password,
            "-keypass", password
        )
        val proc = ProcessBuilder(cmd).redirectErrorStream(true).start()
        val out = proc.inputStream.bufferedReader().readText()
        val exit = proc.waitFor()
        assertTrue("keytool 生成密钥库应成功，exit=$exit\n$out", exit == 0)

        val keystore = KeyStore.getInstance("PKCS12")
        FileInputStream(keystoreFile).use { fis -> keystore.load(fis, password.toCharArray()) }
        val privateKey = keystore.getKey("test", password.toCharArray()) as PrivateKey
        val certificate = keystore.getCertificate("test") as X509Certificate
        return Pair(privateKey, certificate)
    }

    private fun findApksigner(): String? {
        val androidHome = System.getenv("ANDROID_HOME")
            ?: System.getenv("ANDROID_SDK_ROOT")
            ?: return null
        val buildTools = File(androidHome, "build-tools")
        if (!buildTools.exists()) return null
        return buildTools.listFiles()
            ?.filter { it.isDirectory }
            ?.sortedByDescending { it.name }
            ?.mapNotNull { File(it, "apksigner").takeIf { f -> f.exists() }?.absolutePath }
            ?.firstOrNull()
    }

    @Test
    fun signedApk_passesApksignerVerify() {
        val tmpDir = File(System.getProperty("java.io.tmpdir"), "v2signer-e2e-${System.nanoTime()}")
        tmpDir.mkdirs()
        try {
            val unsigned = File(tmpDir, "unsigned.apk")
            val signed = File(tmpDir, "signed.apk")
            val keystore = File(tmpDir, "test.keystore")
            createMinimalUnsignedApk(unsigned)

            val (privateKey, certificate) = generateTestKeystore(keystore, "testpass")

            // 直接调用 V2 签名器（纯 java.security，无 BC 依赖）
            V2ApkSigner.sign(
                unsignedApk = unsigned,
                signedApk = signed,
                privateKey = privateKey,
                certificate = certificate
            )

            assertTrue("签名后 APK 应存在", signed.exists())
            assertTrue("签名后应比未签名大（含签名块）", signed.length() > unsigned.length())

            // 签名块魔数应存在（手动搜子数组，ByteArray.indexOf 只接受单字节）
            val magic = "APK Sig Block 42".toByteArray(Charsets.UTF_8)
            val bytes = signed.readBytes()
            val hasMagic = (0..bytes.size - magic.size).any { i ->
                bytes.copyOfRange(i, i + magic.size).contentEquals(magic)
            }
            assertTrue("APK 应包含 V2 签名块魔数", hasMagic)

            // 用 Android SDK apksigner 做权威验证
            val apksigner = findApksigner()
            if (apksigner == null) {
                println("[跳过] 当前环境未找到 apksigner（需 ANDROID_HOME/build-tools），仅完成签名块存在性检查")
                return
            }

            // 测试 APK 的 AndroidManifest.xml 为纯文本占位（非二进制 AXML），
            // apksigner 无法从中解析 minSdkVersion，显式指定 26（项目 minSdk）。
            // 诊断：保留签名后的 APK 供外部逐字段分析。
            val debugApk = File("/tmp/v2signer-debug-signed.apk")
            signed.copyTo(debugApk, overwrite = true)
            println("诊断 APK 已保留: ${debugApk.absolutePath} (${debugApk.length()} bytes)")
            val process = ProcessBuilder(
                apksigner, "verify", "--min-sdk-version", "26",
                "--verbose", "--print-certs", signed.absolutePath
            )
                .redirectErrorStream(true)
                .start()
            val output = process.inputStream.bufferedReader().readText()
            val exit = process.waitFor()
            println("apksigner verify 输出:\n$output")

            assertTrue("apksigner verify 应退出 0，实际 $exit\n$output", exit == 0)
            assertFalse("输出不应包含 DOES NOT VERIFY\n$output", output.contains("DOES NOT VERIFY"))
            assertTrue("输出应表明使用 v2 签名验证通过\n$output", output.contains("Verified using v2"))
        } finally {
            tmpDir.deleteRecursively()
        }
    }

    /**
     * 用打包器真实模板 APK（template-base.apk）做端到端签名验证。
     *
     * 最小化测试只覆盖 <1MB 的简单 ZIP，本测试覆盖含二进制 AXML、resources.arsc、
     * classes.dex 的真实 APK 结构，确保 EOCD 定位、中央目录切分、多文件 ZIP
     * 在真实数据上同样正确。模板不存在时跳过（CI 环境可能无 assets）。
     */
    @Test
    fun realTemplateApk_passesApksignerVerify() {
        val template = File("/home/user/projects/MiniMe-core/app/src/main/assets/packager/template-base.apk")
        if (!template.exists()) {
            println("[跳过] 模板 APK 不存在: ${template.absolutePath}")
            return
        }

        val tmpDir = File(System.getProperty("java.io.tmpdir"), "v2signer-template-${System.nanoTime()}")
        tmpDir.mkdirs()
        try {
            val unsigned = File(tmpDir, "template-unsigned.apk")
            val signed = File(tmpDir, "template-signed.apk")
            val keystore = File(tmpDir, "test.keystore")
            template.copyTo(unsigned, overwrite = true)

            val (privateKey, certificate) = generateTestKeystore(keystore, "testpass")

            V2ApkSigner.sign(
                unsignedApk = unsigned,
                signedApk = signed,
                privateKey = privateKey,
                certificate = certificate
            )

            assertTrue("签名后 APK 应存在", signed.exists())
            assertTrue("签名后应比未签名大（含签名块）", signed.length() > unsigned.length())

            val apksigner = findApksigner()
            if (apksigner == null) {
                println("[跳过] 当前环境未找到 apksigner")
                return
            }

            val debugApk = File("/tmp/v2signer-template-signed.apk")
            signed.copyTo(debugApk, overwrite = true)
            println("模板签名 APK 已保留: ${debugApk.absolutePath} (${debugApk.length()} bytes)")

            val process = ProcessBuilder(
                apksigner, "verify", "--min-sdk-version", "26",
                "--verbose", signed.absolutePath
            )
                .redirectErrorStream(true)
                .start()
            val output = process.inputStream.bufferedReader().readText()
            val exit = process.waitFor()
            println("apksigner verify 输出:\n$output")

            assertTrue("apksigner verify 应退出 0，实际 $exit\n$output", exit == 0)
            assertFalse("输出不应包含 DOES NOT VERIFY\n$output", output.contains("DOES NOT VERIFY"))
            assertTrue("输出应表明使用 v2 签名验证通过\n$output", output.contains("Verified using v2"))
        } finally {
            tmpDir.deleteRecursively()
        }
    }
}
