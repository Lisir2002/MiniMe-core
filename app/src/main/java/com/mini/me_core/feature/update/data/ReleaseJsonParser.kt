package com.mini.me_core.feature.update.data

import com.google.gson.JsonParser
import com.google.gson.JsonObject
import com.mini.me_core.feature.update.domain.ReleaseAsset
import com.mini.me_core.feature.update.domain.ReleaseInfo
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/**
 * GitHub Release JSON 解析（纯逻辑、无 Android/网络依赖，可单测）。
 *
 * 输入为 /releases/latest 或 /releases?per_page=30 的 JSON 文本。
 */
internal object ReleaseJsonParser {

    const val FALLBACK_RELEASES_URL = "https://github.com/Lisir2002/MiniMe-core/releases"

    fun parseOne(body: String): ReleaseInfo {
        val obj = JsonParser.parseString(body).asJsonObject
        return parseRelease(obj)
    }

    fun parseList(body: String): List<ReleaseInfo> {
        val arr = JsonParser.parseString(body).asJsonArray
        return arr.mapNotNull { el ->
            runCatching { parseRelease(el.asJsonObject) }.getOrNull()
        }
    }

    internal fun parseRelease(obj: JsonObject): ReleaseInfo {
        val tag = obj.get("tag_name")?.asString
            ?: error("missing tag_name")
        val name = obj.get("name")?.asString?.takeIf { it.isNotBlank() } ?: tag
        val publishedAt = obj.get("published_at")?.asString?.let { parseIsoTime(it) } ?: 0L
        val body = obj.get("body")?.takeIf { !it.isJsonNull }?.asString
        val htmlUrl = obj.get("html_url")?.asString ?: FALLBACK_RELEASES_URL
        val isDraft = obj.get("draft")?.asBoolean ?: false
        val isPrerelease = obj.get("prerelease")?.asBoolean ?: false
        val assets = obj.getAsJsonArray("assets")?.mapNotNull { el ->
            runCatching {
                val a = el.asJsonObject
                ReleaseAsset(
                    name = a.get("name")?.asString ?: "",
                    downloadUrl = a.get("browser_download_url")?.asString ?: "",
                    sizeBytes = a.get("size")?.asLong ?: 0L,
                    contentType = a.get("content_type")?.asString ?: "",
                )
            }.getOrNull()
        } ?: emptyList()
        return ReleaseInfo(
            tag = tag,
            name = name,
            publishedAt = publishedAt,
            body = body,
            htmlUrl = htmlUrl,
            isDraft = isDraft,
            isPrerelease = isPrerelease,
            assets = assets,
        )
    }

    private fun parseIsoTime(s: String): Long = runCatching {
        // GitHub published_at 形如 2026-09-20T10:00:00Z（UTC，末尾字面量 Z）。
        val fmt = SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss'Z'", Locale.US)
        fmt.timeZone = java.util.TimeZone.getTimeZone("UTC")
        fmt.parse(s)?.time ?: 0L
    }.getOrDefault(0L)
}
