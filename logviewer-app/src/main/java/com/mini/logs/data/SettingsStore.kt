package com.mini.logs.data

import android.content.Context
import android.content.SharedPreferences
import com.mini.me_core.core.util.LogLevel

/**
 * 设置存储。基于 SharedPreferences 持久化用户偏好。
 */
class SettingsStore(context: Context) {

    private val prefs: SharedPreferences =
        context.getSharedPreferences("logviewer_settings", Context.MODE_PRIVATE)

    // ── 外观 ──
    var themeMode: ThemeMode
        get() = ThemeMode.valueOf(prefs.getString("theme_mode", ThemeMode.SYSTEM.name) ?: ThemeMode.SYSTEM.name)
        set(value) = prefs.edit().putString("theme_mode", value.name).apply()

    var viewMode: ViewMode
        get() = ViewMode.valueOf(prefs.getString("view_mode", ViewMode.COMFORTABLE.name) ?: ViewMode.COMFORTABLE.name)
        set(value) = prefs.edit().putString("view_mode", value.name).apply()

    var fontSize: FontSize
        get() = FontSize.valueOf(prefs.getString("font_size", FontSize.MEDIUM.name) ?: FontSize.MEDIUM.name)
        set(value) = prefs.edit().putString("font_size", value.name).apply()

    var useMonospace: Boolean
        get() = prefs.getBoolean("use_monospace", false)
        set(value) = prefs.edit().putBoolean("use_monospace", value).apply()

    var showMilliseconds: Boolean
        get() = prefs.getBoolean("show_ms", true)
        set(value) = prefs.edit().putBoolean("show_ms", value).apply()

    // ── 行为 ──
    var autoTail: Boolean
        get() = prefs.getBoolean("auto_tail", false)
        set(value) = prefs.edit().putBoolean("auto_tail", value).apply()

    /** 日志来源：自动检测/仅外部公共存储/仅应用私有目录。 */
    var logSource: LogDirResolver.LogSource
        get() = LogDirResolver.LogSource.valueOf(
            prefs.getString("log_source", LogDirResolver.LogSource.AUTO.name)
                ?: LogDirResolver.LogSource.AUTO.name
        )
        set(value) = prefs.edit().putString("log_source", value.name).apply()

    var contextLines: Int
        get() = prefs.getInt("context_lines", 5)
        set(value) = prefs.edit().putInt("context_lines", value).apply()

    var maxLoadLines: Int
        get() = prefs.getInt("max_load_lines", 5000)
        set(value) = prefs.edit().putInt("max_load_lines", value).apply()

    var defaultFileMode: DefaultFileMode
        get() = DefaultFileMode.valueOf(prefs.getString("default_file", DefaultFileMode.TODAY.name) ?: DefaultFileMode.TODAY.name)
        set(value) = prefs.edit().putString("default_file", value.name).apply()

    // ── 数据 ──
    var lastSelectedFiles: Set<String>
        get() = prefs.getStringSet("last_selected_files", emptySet()) ?: emptySet()
        set(value) = prefs.edit().putStringSet("last_selected_files", value).apply()

    /** 书签数据：key = "sourceFile:lineNumber", value = 备注文本 */
    var bookmarks: Map<String, String>
        get() = prefs.getStringSet("bookmarks", emptySet())?.associate {
            val idx = it.indexOf('=')
            if (idx > 0) it.substring(0, idx) to it.substring(idx + 1) else it to ""
        } ?: emptyMap()
        set(value) {
            val set = value.map { "${it.key}=${it.value}" }.toSet()
            prefs.edit().putStringSet("bookmarks", set).apply()
        }

    /** 高亮标记：key = "sourceFile:lineNumber", value = 颜色索引 */
    var highlights: Map<String, Int>
        get() = prefs.getStringSet("highlights", emptySet())?.associate {
            val idx = it.indexOf('=')
            if (idx > 0) it.substring(0, idx) to (it.substring(idx + 1).toIntOrNull() ?: 0)
            else it to 0
        } ?: emptyMap()
        set(value) {
            val set = value.map { "${it.key}=${it.value}" }.toSet()
            prefs.edit().putStringSet("highlights", set).apply()
        }
}

enum class ThemeMode { SYSTEM, LIGHT, DARK, AMOLED }
enum class FontSize { SMALL, MEDIUM, LARGE, XLARGE }
enum class DefaultFileMode { TODAY, LAST, ASK }
