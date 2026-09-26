package com.mini.me_core.feature.update.domain

/**
 * GitHub Release 附件资源（APK 等）。
 */
data class ReleaseAsset(
    val name: String,
    val downloadUrl: String,
    val sizeBytes: Long,
    val contentType: String,
)

/**
 * 一次 GitHub Release 的领域模型。
 *
 * 由 data 层从 `/releases/latest` 与 `/releases?per_page=30` 的 JSON 解析而来，
 * UI 层只依赖本模型，不感知网络细节。
 */
data class ReleaseInfo(
    val tag: String,
    val name: String,
    val publishedAt: Long,
    val body: String?,
    val htmlUrl: String,
    val isDraft: Boolean,
    val isPrerelease: Boolean,
    val assets: List<ReleaseAsset>,
) {
    /** 去除 v 前缀、去掉构建后缀后的纯版本号，例如 `v0.0.0.21` -> `0.0.0.21`。 */
    val versionName: String get() = VersionComparator.normalizeTag(tag)

    /** 首选 APK 附件：优先文件名以 .apk 结尾，否则取第一个附件。 */
    val apkAsset: ReleaseAsset?
        get() = assets.firstOrNull { it.name.endsWith(".apk", ignoreCase = true) }
            ?: assets.firstOrNull()

    /** APK 下载地址；无附件时为 null（UI 回退到浏览器打开 htmlUrl）。 */
    val downloadUrl: String? get() = apkAsset?.downloadUrl

    /** APK 大小（字节）；无附件时为 0。 */
    val fileSizeBytes: Long get() = apkAsset?.sizeBytes ?: 0L

    /** 是否有可直接下载安装的 APK 附件。 */
    val hasApk: Boolean get() = downloadUrl != null

    /**
     * 更新日志摘要：取 body 前若干非空行，用于历史列表预览。
     */
    fun summary(maxLines: Int = 2): String {
        val raw = body?.trim().orEmpty()
        if (raw.isEmpty()) return ""
        val lines = raw.lines().filter { it.isNotBlank() && !it.startsWith("#") && !it.startsWith("---") }
        return lines.take(maxLines).joinToString(" ").trim()
    }
}
