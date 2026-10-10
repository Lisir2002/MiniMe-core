package com.mini.me_core.feature.update.data

import java.security.MessageDigest

/**
 * 待安装 APK 的解析结果（Android 薄层把 PackageManager 结果拍平为该纯数据类）。
 *
 * @property packageName 待装 APK 声明的包名。
 * @property versionCode 待装 APK 的 versionCode。
 * @property signatureSha256Hex 待装 APK 签名证书的 SHA-256（小写 hex）；解析不到为 null。
 * @property fileSizeBytes 本地 APK 文件实际字节数。
 * @property fileSha256Hex 本地 APK 文件字节的 SHA-256（小写 hex）；未计算为 null。
 */
data class ParsedApkInfo(
    val packageName: String?,
    val versionCode: Long,
    val signatureSha256Hex: String?,
    val fileSizeBytes: Long,
    val fileSha256Hex: String?,
)

/** 安装前校验的期望基准（来自「当前已安装应用」与「Release 资产元数据」）。 */
data class ApkInstallExpectation(
    /** 必须等于当前应用包名（拒绝把自更新通道当成任意 APK 安装器）。 */
    val currentPackageName: String,
    /** 已安装版本的 versionCode；待装版本不得低于它（防降级回装被修补的旧签名 APK）。 */
    val installedVersionCode: Long,
    /** 官方签名证书 SHA-256 白名单（小写 hex）；空集合 = 白名单未配置，fail-closed。 */
    val allowedSignatureSha256Hex: Set<String> = emptySet(),
    /** Release 资产声明的大小字节数；<=0 表示未提供，跳过大小校验。 */
    val expectedSizeBytes: Long = 0L,
    /** Release 资产声明的 SHA-256（小写 hex）；null/空表示未提供，跳过摘要校验。 */
    val expectedSha256Hex: String? = null,
)

/** 安装前校验结论。 */
sealed interface ApkVerifyResult {
    data object Approved : ApkVerifyResult
    data class Rejected(val reason: String) : ApkVerifyResult
}

/**
 * APK 安装前校验器（纯 Kotlin，无 Android 依赖，可直接单元测试）。
 *
 * 任一不符即拒绝，调用方**不得**再调起 ACTION_VIEW 安装器；校验顺序固定：
 * 白名单 → 包名 → versionCode 防降级 → 大小 → 整包摘要。镜像站只加速下载，
 * 校验标准与直连完全一致——本类不感知下载来源。
 */
object ApkInstallVerifier {

    fun verify(parsed: ParsedApkInfo, expected: ApkInstallExpectation): ApkVerifyResult {
        val failure = checkSignature(parsed, expected)
            ?: checkPackage(parsed, expected)
            ?: checkVersionCode(parsed, expected)
            ?: checkSize(parsed, expected)
            ?: checkContentDigest(parsed, expected)
        return failure?.let { ApkVerifyResult.Rejected(it) } ?: ApkVerifyResult.Approved
    }

    /** 白名单（最先判）：白名单未配置 / 签名解析不到 / 不命中均拒绝。 */
    private fun checkSignature(parsed: ParsedApkInfo, expected: ApkInstallExpectation): String? {
        val actualSig = parsed.signatureSha256Hex?.trim()?.lowercase().orEmpty()
        if (expected.allowedSignatureSha256Hex.isEmpty() || actualSig.isEmpty()) {
            return "APK 签名白名单未配置或无法解析签名，已拒绝安装"
        }
        val matched = expected.allowedSignatureSha256Hex.any { allowed ->
            MessageDigest.isEqual(allowed.lowercase().toByteArray(), actualSig.toByteArray())
        }
        return if (matched) null else "APK 签名证书不在官方白名单内，已拒绝安装"
    }

    private fun checkPackage(parsed: ParsedApkInfo, expected: ApkInstallExpectation): String? {
        if (parsed.packageName.isNullOrBlank()) {
            return "无法解析 APK 包名，已拒绝安装"
        }
        return if (parsed.packageName != expected.currentPackageName) {
            "APK 包名 ${parsed.packageName} 与当前应用 ${expected.currentPackageName} 不符，已拒绝安装"
        } else null
    }

    private fun checkVersionCode(parsed: ParsedApkInfo, expected: ApkInstallExpectation): String? =
        if (parsed.versionCode < expected.installedVersionCode) {
            "APK 版本 ${parsed.versionCode} 低于已安装版本 ${expected.installedVersionCode}（防降级），已拒绝安装"
        } else null

    private fun checkSize(parsed: ParsedApkInfo, expected: ApkInstallExpectation): String? =
        if (expected.expectedSizeBytes > 0L && parsed.fileSizeBytes != expected.expectedSizeBytes) {
            "APK 大小 ${parsed.fileSizeBytes} 与 Release 声明 ${expected.expectedSizeBytes} 不符，已拒绝安装"
        } else null

    private fun checkContentDigest(parsed: ParsedApkInfo, expected: ApkInstallExpectation): String? {
        val expectedHash = expected.expectedSha256Hex?.trim()?.lowercase()?.replace(":", "").orEmpty()
        if (expectedHash.isEmpty()) return null
        val actual = parsed.fileSha256Hex?.lowercase().orEmpty()
        return if (actual.isEmpty() ||
            !MessageDigest.isEqual(expectedHash.toByteArray(), actual.toByteArray())
        ) {
            "APK 内容摘要与 Release 声明不符，已拒绝安装"
        } else null
    }
}
