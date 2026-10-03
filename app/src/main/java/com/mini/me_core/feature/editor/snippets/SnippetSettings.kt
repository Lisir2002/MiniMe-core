package com.mini.me_core.feature.editor.snippets

import android.content.Context
import android.content.SharedPreferences

/**
 * 片段功能偏好设置（单例）。
 *
 * 持久化到独立的 SharedPreferences 文件，避免与业务 KVStore 耦合。
 */
object SnippetSettings {

    private const val PREFS = "editor_snippets"
    private const val KEY_ENABLED = "snippet_completion_enabled"

    @Volatile
    private var prefs: SharedPreferences? = null

    fun initialize(appContext: Context) {
        if (prefs == null) {
            prefs = appContext.applicationContext.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
        }
    }

    /** 片段补全是否开启（默认开启）。 */
    var completionEnabled: Boolean
        get() = prefs?.getBoolean(KEY_ENABLED, true) ?: true
        set(value) {
            prefs?.edit()?.putBoolean(KEY_ENABLED, value)?.apply()
        }
}
