package com.mini.me_core.feature.editor.themes

import android.app.Application
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.mini.me_core.feature.editor.textmate.TextMateManager
import com.mini.me_core.feature.editor.themes.import.ThemeImporter
import com.mini.me_core.feature.settings.data.repository.EditorThemePrefs
import com.mini.me_core.feature.settings.data.repository.EditorThemeRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import javax.inject.Inject

/**
 * 编辑器主题管理器 ViewModel。
 *
 * 桥接 UI 与 [TextMateManager] / [EditorThemeRepository] / [ThemeImporter]。
 */
@HiltViewModel
class EditorThemesViewModel @Inject constructor(
    private val app: Application,
    private val repo: EditorThemeRepository,
) : ViewModel() {

    private val importer = ThemeImporter(app)

    private val _category = MutableStateFlow<ThemeCategory?>(null)
    val category: StateFlow<ThemeCategory?> = _category.asStateFlow()

    private val _query = MutableStateFlow("")
    val query: StateFlow<String> = _query.asStateFlow()

    private val _listVersion = MutableStateFlow(0)

    private val _toast = MutableStateFlow<String?>(null)
    val toast: StateFlow<String?> = _toast.asStateFlow()

    /** 主题偏好（跟随系统 / 暗 / 亮主题名）。 */
    val prefs: StateFlow<EditorThemePrefs> = repo.editorThemePrefs
        .stateIn(viewModelScope, SharingStarted.Eagerly, EditorThemePrefs(
            TextMateManager.DEFAULT_DARK_THEME, TextMateManager.DEFAULT_LIGHT_THEME, true
        ))

    /** 当前应用的主题名。 */
    private val _currentTheme = MutableStateFlow(TextMateManager.currentThemeName)
    val currentTheme: StateFlow<String> = _currentTheme.asStateFlow()

    fun setCategory(cat: ThemeCategory?) { _category.value = cat }
    fun setQuery(q: String) { _query.value = q }

    /** 当前列表（按分类 + 搜索过滤）。 */
    val themes: StateFlow<List<ThemeMetadata>> =
        combine(_category, _query, _listVersion) { cat, q, _ ->
            TextMateManager.listThemes(cat).filter { m ->
                q.isBlank() || m.displayName.contains(q, ignoreCase = true) ||
                    m.name.contains(q, ignoreCase = true)
            }
        }.stateIn(viewModelScope, SharingStarted.Eagerly, emptyList())

    /** 应用指定主题为当前编辑器主题。 */
    fun applyTheme(name: String) {
        viewModelScope.launch {
            TextMateManager.setTheme(name)
            _currentTheme.value = name
            // 记录到偏好（跟随系统关闭时即作为固定主题）
            val meta = TextMateManager.getTheme(name) ?: return@launch
            if (meta.isDark) repo.setDarkTheme(name) else repo.setLightTheme(name)
        }
    }

    fun setFollowSystem(follow: Boolean) {
        viewModelScope.launch { repo.setFollowSystem(follow) }
    }

    fun setDarkTheme(name: String) {
        viewModelScope.launch { repo.setDarkTheme(name) }
    }

    fun setLightTheme(name: String) {
        viewModelScope.launch { repo.setLightTheme(name) }
    }

    /** 删除自定义主题。 */
    fun deleteTheme(name: String) {
        viewModelScope.launch {
            TextMateManager.deleteCustomTheme(name)
            _listVersion.value++
        }
    }

    /** 导入主题文件。 */
    fun importTheme(displayName: String, content: ByteArray, isPlist: Boolean) {
        viewModelScope.launch {
            val result = importer.import(displayName, content, isPlist)
            when (result) {
                is com.mini.me_core.feature.editor.themes.import.ThemeImportResult.Success -> {
                    _listVersion.value++
                    _toast.value = "OK:${result.displayName}"
                }
                is com.mini.me_core.feature.editor.themes.import.ThemeImportResult.Failure -> {
                    _toast.value = "ERR:" + result.errors.joinToString("; ")
                }
            }
        }
    }

    fun toastShown() { _toast.value = null }

    fun refreshList() { _listVersion.value++ }
}
