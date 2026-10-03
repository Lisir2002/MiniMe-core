package com.mini.me_core.feature.editor.themes.editor

import android.app.Application
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.mini.me_core.feature.editor.textmate.TextMateManager
import com.mini.me_core.feature.editor.themes.ThemeCategory
import com.mini.me_core.feature.editor.themes.ThemeMetadata
import com.mini.me_core.feature.editor.themes.ThemePreviewColors
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.json.JSONObject
import java.io.File
import javax.inject.Inject

/**
 * 可视化主题编辑器 ViewModel。
 */
@HiltViewModel
class ThemeEditorViewModel @Inject constructor(
    private val app: Application,
) : ViewModel() {

    private val _baseThemeName = MutableStateFlow<String?>(null)
    val baseThemeName: StateFlow<String?> = _baseThemeName.asStateFlow()

    private val _snapshot = MutableStateFlow<ThemeEditorModel.Snapshot?>(null)
    val snapshot: StateFlow<ThemeEditorModel.Snapshot?> = _snapshot.asStateFlow()

    private val _overrides = MutableStateFlow<Map<String, String>>(emptyMap())
    val overrides: StateFlow<Map<String, String>> = _overrides.asStateFlow()

    private val _error = MutableStateFlow<String?>(null)
    val error: StateFlow<String?> = _error.asStateFlow()

    /** 可选的基础主题列表（内置 + 自定义）。 */
    fun baseThemes(): List<ThemeMetadata> = TextMateManager.listThemes()

    /** 加载指定基础主题。 */
    fun loadBase(name: String?) {
        viewModelScope.launch {
            val resolved = name ?: TextMateManager.listThemes().firstOrNull { !it.isDark }?.name
            ?: TextMateManager.DEFAULT_LIGHT_THEME
            _baseThemeName.value = resolved
            val raw = withContext(Dispatchers.IO) { readThemeFile(resolved) }
            if (raw == null) {
                _error.value = "无法读取基础主题"
                return@launch
            }
            _snapshot.value = ThemeEditorModel.loadSnapshot(raw)
            _overrides.value = emptyMap()
        }
    }

    fun setColor(fieldKey: String, hex: String) {
        _overrides.value = _overrides.value.toMutableMap().apply { put(fieldKey, hex) }
    }

    fun resetField(fieldKey: String) {
        _overrides.value = _overrides.value.toMutableMap().apply { remove(fieldKey) }
    }

    fun resetAll() { _overrides.value = emptyMap() }

    /** 当前预览色板（合并覆盖）。 */
    fun previewColors(): ThemePreviewColors {
        val snap = _snapshot.value ?: return ThemePreviewColors.EMPTY
        val finalValues = snap.values.toMutableMap().apply { putAll(_overrides.value) }
        fun c(k: String) = finalValues[k] ?: "#000000"
        return ThemePreviewColors(
            background = c("editor_background"),
            foreground = c("editor_foreground"),
            keyword = c("keyword"),
            string = c("string"),
            comment = c("comment"),
            number = c("number"),
            function = c("function"),
        )
    }

    /** 保存为自定义主题到 filesDir。 */
    suspend fun save(name: String, displayName: String): SaveResult {
        val snap = _snapshot.value ?: return SaveResult.Failure("无基础主题")
        val finalValues = snap.values.toMutableMap().apply { putAll(_overrides.value) }
        val json = ThemeEditorModel.buildThemeJson(snap, _overrides.value, name.slugify(), displayName)
        val dir = File(app.filesDir, "themes").apply { mkdirs() }
        val dest = File(dir, "${name.slugify()}.json")
        if (dest.exists()) return SaveResult.Failure("主题名已存在")
        dest.writeText(json)
        val root = JSONObject(json)
        val meta = ThemeMetadata(
            name = name.slugify(),
            displayName = displayName,
            category = ThemeCategory.CUSTOM,
            isDark = root.optString("type", "dark") != "light",
            author = "custom",
            filePath = dest.absolutePath,
            isCustom = true,
        )
        TextMateManager.registerCustomTheme(meta)
        return SaveResult.Success(name.slugify())
    }

    /** 导出当前编辑中的主题 JSON 到 Download 目录（返回文件路径）。 */
    suspend fun export(name: String): String {
        val snap = _snapshot.value!!
        val json = ThemeEditorModel.buildThemeJson(snap, _overrides.value, name.slugify(), name)
        val dir = java.io.File(
            android.os.Environment.getExternalStoragePublicDirectory(android.os.Environment.DIRECTORY_DOWNLOADS),
            "MiniMe-themes"
        ).apply { mkdirs() }
        val f = File(dir, "${name.slugify()}.json")
        f.writeText(json)
        return f.absolutePath
    }

    private fun readThemeFile(name: String): String? {
        val meta = TextMateManager.getTheme(name) ?: return null
        return runCatching {
            meta.assetPath?.let { app.assets.open(it).bufferedReader().use { r -> r.readText() } }
                ?: meta.filePath?.let { File(it).readText() }
        }.getOrNull()
    }

    private fun String.slugify() =
        trim().lowercase().replace(Regex("[^a-z0-9]+"), "-").trim('-').ifEmpty { "custom-theme" }

    sealed class SaveResult {
        data class Success(val name: String) : SaveResult()
        data class Failure(val message: String) : SaveResult()
    }
}
