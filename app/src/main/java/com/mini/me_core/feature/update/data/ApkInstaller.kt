package com.mini.me_core.feature.update.data

import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import androidx.core.content.FileProvider
import com.mini.me_core.core.util.FileLogger
import java.io.File
import java.security.MessageDigest

/**
 * 调起系统包安装器安装已下载的 APK——**安装前强制安全校验**（[ApkInstallVerifier]）。
 *
 * 校验全部通过才调起 [Intent.ACTION_VIEW]；任一不符返回 [InstallResult.Rejected]，
 * 绝不把不可信 APK（含经第三方镜像下载的）交给系统安装器。
 *
 * 复用项目现有 FileProvider（authority = `${applicationId}.fileprovider`）。
 */
object ApkInstaller {

    private const val TAG = "ApkInstaller"

    sealed interface InstallResult {
        data object Started : InstallResult
        data class Rejected(val reason: String) : InstallResult
    }

    /**
     * 安装 [filePath] 指向的本地 APK。
     *
     * @param expectedSizeBytes Release 资产声明的大小（<=0 跳过大小校验）。
     * @param expectedSha256 Release 资产声明的整包 SHA-256（null/空跳过摘要校验）。
     *   直连与镜像线路使用完全相同的校验标准。
     */
    fun install(
        context: Context,
        filePath: String,
        expectedSizeBytes: Long = 0L,
        expectedSha256: String? = null,
    ): InstallResult {
        val apkFile = File(filePath)
        if (!apkFile.exists()) {
            return InstallResult.Rejected("APK 文件不存在: $filePath")
        }
        // 解析当前已安装应用的基准（包名 + versionCode）。
        val currentPkg = runCatching {
            context.packageManager.getPackageInfo(context.packageName, 0)
        }.getOrNull()
            ?: return InstallResult.Rejected("无法读取当前应用版本信息，已拒绝安装")

        val parsed = runCatching { parseApk(context, apkFile) }.getOrElse {
            FileLogger.w(TAG, "解析待装 APK 失败", it)
            return InstallResult.Rejected("无法解析待装 APK，已拒绝安装")
        }

        val expectation = ApkInstallExpectation(
            currentPackageName = context.packageName,
            installedVersionCode = versionCodeOf(currentPkg),
            // 白名单未配置（维护者待填）时空集合 → fail-closed 拒绝安装。
            allowedSignatureSha256Hex = if (UpdateSignatureWhitelist.WHITELIST_FILLED)
                UpdateSignatureWhitelist.OFFICIAL_SIGNATURE_SHA256 else emptySet(),
            expectedSizeBytes = expectedSizeBytes,
            expectedSha256Hex = expectedSha256,
        )

        return when (val r = ApkInstallVerifier.verify(parsed, expectation)) {
            ApkVerifyResult.Approved -> launchInstaller(context, apkFile)
            is ApkVerifyResult.Rejected -> {
                FileLogger.w(TAG, "安装前校验拒绝: ${r.reason}")
                InstallResult.Rejected(r.reason)
            }
        }
    }

    private fun launchInstaller(context: Context, apkFile: File): InstallResult =
        runCatching {
            val authority = "${context.packageName}.fileprovider"
            val uri: Uri = FileProvider.getUriForFile(context, authority, apkFile)
            val intent = Intent(Intent.ACTION_VIEW).apply {
                setDataAndType(uri, "application/vnd.android.package-archive")
                addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            }
            context.startActivity(intent)
            InstallResult.Started
        }.getOrElse {
            FileLogger.w(TAG, "调起系统安装器失败", it)
            InstallResult.Rejected("无法调起系统安装器")
        }

    @Suppress("DEPRECATION")
    private fun parseApk(context: Context, apkFile: File): ParsedApkInfo {
        val flags = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
            PackageManager.GET_SIGNING_CERTIFICATES
        } else {
            PackageManager.GET_SIGNATURES
        }
        val archive = context.packageManager.getPackageArchiveInfo(apkFile.absolutePath, flags)
            ?: error("getPackageArchiveInfo 返回 null")
        val pkg = archive.packageName
        val vc = versionCodeOf(archive)

        val certBytes: ByteArray? = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
            archive.signingInfo?.apkContentsSigners?.firstOrNull()?.toByteArray()
        } else {
            archive.signatures?.firstOrNull()?.toByteArray()
        }
        val certSha256 = certBytes?.let { sha256Hex(it) }

        val fileSha256 = sha256HexOfFile(apkFile)
        return ParsedApkInfo(
            packageName = pkg,
            versionCode = vc,
            signatureSha256Hex = certSha256,
            fileSizeBytes = apkFile.length(),
            fileSha256Hex = fileSha256,
        )
    }

    private fun versionCodeOf(info: android.content.pm.PackageInfo): Long =
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
            info.longVersionCode
        } else {
            @Suppress("DEPRECATION") info.versionCode.toLong()
        }

    private fun sha256Hex(input: ByteArray): String =
        MessageDigest.getInstance("SHA-256").digest(input)
            .joinToString("") { "%02x".format(it) }

    private fun sha256HexOfFile(file: File): String? = runCatching {
        val md = MessageDigest.getInstance("SHA-256")
        file.inputStream().use { input ->
            val buffer = ByteArray(64 * 1024)
            while (true) {
                val n = input.read(buffer)
                if (n < 0) break
                md.update(buffer, 0, n)
            }
        }
        md.digest().joinToString("") { "%02x".format(it) }
    }.getOrNull()
}
