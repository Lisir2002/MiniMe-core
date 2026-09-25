package com.mini.me_core.feature.browser.domain

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import org.json.JSONArray
import javax.inject.Inject
import javax.inject.Singleton

/**
 * F4.6 网页翻译。
 *
 * 使用 Google Translate 免费 web 端点（无需 API Key）翻译文本；
 * 可在设置中选择目标语言。整页翻译与划词翻译共用同一入口。
 */
@Singleton
class WebTranslator @Inject constructor(
    private val okHttp: OkHttpClient
) {
    /** 目标语言（默认跟随系统，由 UI 覆盖）。 */
    @Volatile
    var targetLang: String = "zh-CN"

    /** 自动翻译开关。 */
    @Volatile
    var autoTranslate: Boolean = false

    /** 支持的目标语言列表（代码 → 显示名资源由 UI 映射）。 */
    val supportedLanguages = listOf(
        "zh-CN" to "简体中文", "zh-TW" to "繁體中文", "en" to "English",
        "ja" to "日本語", "ko" to "한국어", "fr" to "Français", "de" to "Deutsch",
        "es" to "Español", "ru" to "Русский", "pt" to "Português",
        "it" to "Italiano", "ar" to "العربية", "hi" to "हिन्दी", "th" to "ไทย",
        "vi" to "Tiếng Việt", "id" to "Bahasa Indonesia", "tr" to "Türkçe",
        "nl" to "Nederlands", "pl" to "Polski", "uk" to "Українська"
    )

    /** 翻译一段文本；失败返回原文。 */
    suspend fun translate(text: String, target: String = targetLang): String = withContext(Dispatchers.IO) {
        if (text.isBlank()) return@withContext text
        runCatching {
            val url = "https://translate.googleapis.com/translate_a/single?client=gtx&sl=auto&tl=$target&dt=t&q=" +
                java.net.URLEncoder.encode(text, "UTF-8")
            val req = Request.Builder().url(url)
                .header("User-Agent", "Mozilla/5.0 MiniMe-Browser")
                .build()
            okHttp.newCall(req).execute().use { resp ->
                val body = resp.body?.string() ?: return@withContext text
                val arr = JSONArray(body).getJSONArray(0)
                val sb = StringBuilder()
                for (i in 0 until arr.length()) {
                    sb.append(arr.getJSONArray(i).optString(0))
                }
                sb.toString().ifBlank { text }
            }
        }.getOrDefault(text)
    }
}
