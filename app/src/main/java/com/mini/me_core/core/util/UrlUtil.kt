package com.mini.me_core.core.util

/**
 * Builds an absolute request URL from a user-configured base URL and an API path
 * such as "v1/chat/completions". Tolerates trailing slashes and a base URL that
 * already ends with the version segment (e.g. "https://host/v1") so it isn't duplicated.
 *
 * 解耦说明：纯字符串工具，被 agent/settings/t2i 多个 feature 共用，上移至 core.util。
 */
fun joinUrl(baseUrl: String, path: String): String {
    val base = baseUrl.trim().trimEnd('/')
    val cleanPath = path.trimStart('/')

    val lastSegment = base.substringAfterLast('/', "")

    return if (lastSegment.isNotEmpty() && cleanPath.startsWith("$lastSegment/")) {
        "$base/${cleanPath.removePrefix("$lastSegment/")}"
    } else {
        "$base/$cleanPath"
    }
}
