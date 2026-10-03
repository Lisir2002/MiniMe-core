package com.mini.me_core.feature.settings.data.repository

import com.mini.me_core.datalayer.store.KVStore
import com.mini.me_core.feature.editor.textmate.TextMateManager
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.map
import javax.inject.Inject
import javax.inject.Singleton

/**
 * 编辑器（TextMate）主题偏好持久化仓库。
 *
 * 持久化：
 *  - 深色模式主题名 / 浅色模式主题名
 *  - 是否跟随系统深色/浅色模式
 *
 * 自定义/导入主题本身以 JSON 文件形式存放在 filesDir/themes/，
 * 由 [TextMateManager.reloadCustomThemes] 扫描，不在此冗余存储清单。
 *
 * KVStore 类型约定：putString 配 getString/observeString；putBool 配 getBool/observeBool。
 */
@Singleton
class EditorThemeRepository @Inject constructor(
    private val kv: KVStore,
) {
    private companion object {
        const val NS = "editor_theme"
        const val DARK_THEME_KEY = "dark_theme_name"
        const val LIGHT_THEME_KEY = "light_theme_name"
        const val FOLLOW_SYSTEM_KEY = "follow_system"
    }

    /** 深色模式下使用的主题名。 */
    val darkThemeFlow: Flow<String> = kv.observeString(NS, DARK_THEME_KEY).map {
        it ?: TextMateManager.DEFAULT_DARK_THEME
    }

    /** 浅色模式下使用的主题名。 */
    val lightThemeFlow: Flow<String> = kv.observeString(NS, LIGHT_THEME_KEY).map {
        it ?: TextMateManager.DEFAULT_LIGHT_THEME
    }

    /** 是否跟随系统深色/浅色模式自动切换主题。默认 true。 */
    val followSystemFlow: Flow<Boolean> = kv.observeBool(NS, FOLLOW_SYSTEM_KEY).map { it ?: true }

    /** 合并后的主题偏好快照流。 */
    val editorThemePrefs: Flow<EditorThemePrefs> =
        combine(darkThemeFlow, lightThemeFlow, followSystemFlow) { dark, light, follow ->
            EditorThemePrefs(darkThemeName = dark, lightThemeName = light, followSystem = follow)
        }

    suspend fun setDarkTheme(name: String) {
        kv.putString(NS, DARK_THEME_KEY, name)
    }

    suspend fun setLightTheme(name: String) {
        kv.putString(NS, LIGHT_THEME_KEY, name)
    }

    suspend fun setFollowSystem(follow: Boolean) {
        kv.putBool(NS, FOLLOW_SYSTEM_KEY, follow)
    }
}

/** 编辑器主题偏好快照。 */
data class EditorThemePrefs(
    val darkThemeName: String,
    val lightThemeName: String,
    val followSystem: Boolean,
)
