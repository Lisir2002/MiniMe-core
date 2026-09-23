package com.mini.me_core.core.util

/**
 * 日志敏感信息脱敏器（纯 Kotlin，无 Android 依赖，可直接单元测试）。
 *
 * 日志文件写入**公共外部存储**（Documents/MiniMe-core/...），任何应用可读取，
 * 因此落盘前必须自动扫描并打码可能泄露的密钥 / 凭据。调用方（[FileLogger] / [AILogger]）
 * 在 `LogConfig.enableSanitizer` 为 true 时对「消息正文 + 堆栈」统一走 [sanitize]。
 *
 * 脱敏策略（命中即替换为 `[REDACTED]` 或 `[REDACTED: N chars]`，保留「值曾存在」的调试信息）：
 *  1. JSON 字段值：`"apiKey": "xxx"` / `"password":"xxx"` 等（key 不区分大小写）；
 *  2. HTTP 头：`Authorization: Bearer xxx` / `Authorization: Basic xxx`；
 *  3. URL query 参数：`?apiKey=xxx&token=yyy` 中的敏感参数值；
 *  4. 内联键值：`apiKey=xxx` / `token=xxx` 等；
 *  5. base64 媒体大数据（迁移自原 AILogger）：`data:image/...;base64,<超长串>` 与
 *     `"data": "<超长 base64>"`，避免把图片 / 音频原始字节写进日志。
 *
 * 设计取舍：
 * - 只替换「值」，保留 key 名与结构，日志仍可读；
 * - 宁可漏脱敏不可误脱敏正常业务日志（故 key 集合精确、不做模糊匹配）；
 * - 所有正则预先编译为顶层/成员常量，避免每条日志重复编译。
 */
object LogSanitizer {

    /** 需要脱敏的敏感 key 名单（JSON key / URL 参数名 / 内联键名共用，不区分大小写）。 */
    private val SENSITIVE_KEYS = listOf(
        "apiKey", "api_key", "apikey",
        "authorization",
        "token", "access_token", "refresh_token",
        "password", "passwd", "pwd",
        "private_key", "secret", "client_secret",
    )

    private val KEY_ALT = SENSITIVE_KEYS.joinToString(separator = "|")

    // 1) JSON 字符串字段值："apiKey": "实际值"
    //    捕获组 1=key（原样保留），组 2=值（打码）。
    private val JSON_FIELD_REGEX = Regex(
        "\"($KEY_ALT)\"\\s*:\\s*\"([^\"]*)\"",
        RegexOption.IGNORE_CASE
    )

    // 2) HTTP Authorization 头：Authorization: Bearer xxx / Basic xxx / 任意凭据
    //    组 1="Authorization: Bearer "（保留 scheme），组 2=凭据（打码）。
    private val AUTH_HEADER_REGEX = Regex(
        "(?i)\\b(authorization\\s*:\\s*(?:bearer|basic|token)\\s+)([^\\s,;\\)]+)"
    )

    // 3) URL query 参数：?key=value 或 &key=value（value 到 & / 空白 / 引号为止）。
    private val URL_QUERY_REGEX = Regex(
        "([?&]($KEY_ALT)=)([^&#\\s\"']*)",
        RegexOption.IGNORE_CASE
    )

    // 4) 内联键值：apiKey=xxx / token=xxx（到空白/逗号/分号/引号为止）。
    //    注意 (?<![?&]) 排除掉已经被 URL_QUERY 处理过的 ?key=/&key= 片段，避免重复。
    private val INLINE_KV_REGEX = Regex(
        "(?<![?&])\\b($KEY_ALT)\\s*=\\s*([^\\s,;\"']+)",
        RegexOption.IGNORE_CASE
    )

    // 5a) data: URI 内嵌 base64 媒体：data:image/png;base64,<512+ chars>
    private val DATA_URL_MEDIA_REGEX =
        Regex("data:((?:image|audio|video)/[A-Za-z0-9.+-]+);base64,([A-Za-z0-9+/=_-]{512,})")

    // 5b) JSON 里的 base64 大字段："data": "<512+ chars>"
    private val BASE64_FIELD_REGEX =
        Regex("\"(base64Data|data)\"\\s*:\\s*\"([A-Za-z0-9+/=_-]{512,})\"")

    /**
     * 对一段文本执行全部脱敏规则。空串 / blank 原样返回。
     * 线程安全：所有正则无状态，本方法不持有可变状态。
     */
    fun sanitize(text: String): String {
        if (text.isEmpty()) return text
        var out = text
        // 顺序：先处理结构化程度最高的 JSON / 头 / URL，再处理内联，最后处理大块 base64。
        out = JSON_FIELD_REGEX.replace(out) { m ->
            val key = m.groupValues[1]
            val value = m.groupValues[2]
            "\"$key\": \"[REDACTED${if (value.isNotEmpty()) ": ${value.length} chars" else ""}]\""
        }
        out = AUTH_HEADER_REGEX.replace(out) { m ->
            m.groupValues[1] + "[REDACTED]"
        }
        out = URL_QUERY_REGEX.replace(out) { m ->
            m.groupValues[1] + "[REDACTED]"
        }
        out = INLINE_KV_REGEX.replace(out) { m ->
            m.groupValues[1] + "=[REDACTED]"
        }
        out = DATA_URL_MEDIA_REGEX.replace(out) { m ->
            val mime = m.groupValues[1]
            val data = m.groupValues[2]
            "data:$mime;base64,[base64 omitted: ${data.length} chars]"
        }
        out = BASE64_FIELD_REGEX.replace(out) { m ->
            val key = m.groupValues[1]
            val data = m.groupValues[2]
            "\"$key\": \"[base64 omitted: ${data.length} chars]\""
        }
        return out
    }
}
