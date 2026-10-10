package com.mini.me_core.feature.update.data

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * [ApkInstallVerifier] 纯单测：伪造 PackageInfo/证书指纹，覆盖
 * 签名不符 / 包名不符 / 版本降级 / 大小不符 / 摘要不符 / 全部符合通过。
 */
class ApkInstallVerifierTest {

    private val goodSig = "a1b2c3d4e5f6a7b8c9d0e1f2a3b4c5d6e7f8a9b0c1d2e3f4a5b6c7d8e9f0a1b2"
    private val otherSig = "f0e1d2c3b4a5968778695a4b3c2d1e0f112233445566778899aabbccddeeff00"
    private val pkg = "com.mini.me_core"

    private fun goodParsed(
        sig: String = goodSig,
        pkgName: String? = pkg,
        versionCode: Long = 30,
        size: Long = 1000,
        sha: String? = "00".repeat(32),
    ) = ParsedApkInfo(
        packageName = pkgName,
        versionCode = versionCode,
        signatureSha256Hex = sig,
        fileSizeBytes = size,
        fileSha256Hex = sha,
    )

    private fun goodExpectation(
        pkgName: String = pkg,
        installedVc: Long = 20,
        sigSet: Set<String> = setOf(goodSig),
        size: Long = 1000,
        sha: String? = "00".repeat(32),
    ) = ApkInstallExpectation(
        currentPackageName = pkgName,
        installedVersionCode = installedVc,
        allowedSignatureSha256Hex = sigSet,
        expectedSizeBytes = size,
        expectedSha256Hex = sha,
    )

    @Test
    fun `whitelist empty rejects everything fail-closed`() {
        val r = ApkInstallVerifier.verify(goodParsed(), goodExpectation(sigSet = emptySet()))
        assertTrue(r is ApkVerifyResult.Rejected)
    }

    @Test
    fun `signature mismatch rejected`() {
        val r = ApkInstallVerifier.verify(goodParsed(sig = otherSig), goodExpectation())
        assertTrue(r is ApkVerifyResult.Rejected)
    }

    @Test
    fun `signature case-insensitive match`() {
        val r = ApkInstallVerifier.verify(goodParsed(sig = goodSig.uppercase()), goodExpectation())
        assertEquals(ApkVerifyResult.Approved, r)
    }

    @Test
    fun `package name mismatch rejected`() {
        val r = ApkInstallVerifier.verify(goodParsed(pkgName = "com.evil"), goodExpectation())
        assertTrue(r is ApkVerifyResult.Rejected)
    }

    @Test
    fun `downgrade version rejected`() {
        val r = ApkInstallVerifier.verify(goodParsed(versionCode = 10), goodExpectation(installedVc = 20))
        assertTrue(r is ApkVerifyResult.Rejected)
    }

    @Test
    fun `equal versionCode still allowed`() {
        val r = ApkInstallVerifier.verify(goodParsed(versionCode = 20), goodExpectation(installedVc = 20))
        assertEquals(ApkVerifyResult.Approved, r)
    }

    @Test
    fun `size mismatch rejected`() {
        val r = ApkInstallVerifier.verify(goodParsed(size = 999), goodExpectation())
        assertTrue(r is ApkVerifyResult.Rejected)
    }

    @Test
    fun `content sha256 mismatch rejected`() {
        val r = ApkInstallVerifier.verify(
            goodParsed(sha = "11".repeat(32)),
            goodExpectation(sha = "00".repeat(32)),
        )
        assertTrue(r is ApkVerifyResult.Rejected)
    }

    @Test
    fun `size and sha not provided skips those checks`() {
        val r = ApkInstallVerifier.verify(
            goodParsed(size = 1, sha = null),
            goodExpectation(size = 0L, sha = null),
        )
        assertEquals(ApkVerifyResult.Approved, r)
    }

    @Test
    fun `all checks pass`() {
        val r = ApkInstallVerifier.verify(goodParsed(), goodExpectation())
        assertEquals(ApkVerifyResult.Approved, r)
    }
}
