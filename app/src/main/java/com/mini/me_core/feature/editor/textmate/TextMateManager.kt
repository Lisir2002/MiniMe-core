package com.mini.me_core.feature.editor.textmate

import android.content.Context
import android.util.Log
import com.mini.me_core.feature.editor.themes.ThemeCategory
import com.mini.me_core.feature.editor.themes.ThemeMetadata
import com.mini.me_core.feature.editor.themes.ThemePreviewColors
import com.mini.me_core.feature.editor.themes.ThemeJsonSupport
import io.github.rosemoe.sora.langs.textmate.TextMateColorScheme
import io.github.rosemoe.sora.langs.textmate.TextMateLanguage
import io.github.rosemoe.sora.langs.textmate.registry.FileProviderRegistry
import io.github.rosemoe.sora.langs.textmate.registry.GrammarRegistry
import io.github.rosemoe.sora.langs.textmate.registry.ThemeRegistry
import io.github.rosemoe.sora.langs.textmate.registry.model.ThemeModel
import io.github.rosemoe.sora.langs.textmate.registry.provider.AssetsFileResolver
import io.github.rosemoe.sora.widget.schemes.EditorColorScheme
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.withContext
import org.eclipse.tm4e.core.registry.IThemeSource
import org.json.JSONObject
import java.io.File
import java.io.InputStream

/**
 * TextMate 引擎管理器（单例）。
 *
 * 负责：
 *  - 注册 AssetsFileResolver（从 APK assets 读取 grammar/theme）
 *  - 加载内置 grammar（assets/editor/grammars/ 目录下全部 JSON）
 *  - 按 `editor/themes/index.json` 清单管理内置主题，按需懒加载
 *  - 管理 filesDir/themes/ 下的用户自定义/导入主题
 *  - 提供按 scopeName 创建 [TextMateLanguage] 的工厂方法
 *
 * 初始化时机：Application.onCreate 异步预热段调用 [initialize]，不阻塞首帧。
 * 任何编辑器实例共享同一个 GrammarRegistry / ThemeRegistry 单例。
 */
object TextMateManager {

    private const val TAG = "TextMateManager"
    private const val GRAMMAR_INDEX = "editor/languages.json"
    private const val THEME_INDEX = "editor/themes/index.json"
    private const val CUSTOM_THEME_DIR = "themes"

    /** 默认主题（深色/浅色回退）。 */
    const val DEFAULT_DARK_THEME = "dark-plus"
    const val DEFAULT_LIGHT_THEME = "light-plus"

    @Volatile
    private var initialized = false

    /** 最后一次初始化失败的错误信息，供 UI 层展示诊断。 */
    @Volatile
    var lastInitError: String? = null
        private set

    /** 初始化失败次数，用于诊断反复失败。 */
    var initFailCount = 0
        private set

    @Volatile
    var currentThemeName: String = DEFAULT_DARK_THEME
        private set

    /** 内置主题元数据（来自 index.json）。 */
    private val builtinThemeMeta = LinkedHashMap<String, ThemeMetadata>()

    /** 自定义/导入主题元数据（来自 filesDir/themes/）。 */
    private val customThemeMeta = LinkedHashMap<String, ThemeMetadata>()

    /** 已加载到 ThemeRegistry 的主题名集合，避免重复加载。 */
    private val loadedThemes = HashSet<String>()

    /** 主题名 → 预览色板缓存。 */
    private val previewCache = HashMap<String, ThemePreviewColors>()

    private val _themeChanges = MutableSharedFlow<String>(extraBufferCapacity = 4)
    /** 主题切换事件流（发射新主题名），活跃编辑器收集后刷新。 */
    val themeChanges: SharedFlow<String> = _themeChanges.asSharedFlow()

    private val _themeListVersion = MutableStateFlow(0)
    /** 主题列表变化计数器（新增/删除自定义主题后自增，UI 监听刷新）。 */
    val themeListVersion: SharedFlow<Int> = _themeListVersion.asStateFlow()

    private lateinit var appContext: Context

    /** extension → scopeName 映射（由 languages.json 构建）。 */
    private val extToScope = HashMap<String, String>(512)

    /** scopeName → 是否可用。 */
    private val availableScopes = HashSet<String>(256)

    /**
     * 初始化 TextMate 引擎。幂等，可重复调用。
     * 必须在后台线程调用（涉及 assets IO + grammar 解析）。
     */
    suspend fun initialize(app: Context) = withContext(Dispatchers.IO) {
        if (initialized) return@withContext
        synchronized(this) {
            if (initialized) return@withContext

            val t0 = System.currentTimeMillis()
            try {
                lastInitError = null
                appContext = app.applicationContext

                // 1. 注册 AssetsFileResolver
                FileProviderRegistry.getInstance().addFileProvider(
                    AssetsFileResolver(appContext.assets)
                )
                Log.d(TAG, "AssetsFileResolver 注册完成")

                // 2. 加载主题清单（index.json）
                loadThemeIndex()
                // 3. 加载自定义/导入主题清单
                reloadCustomThemes()

                // 4. 预加载当前主题（其余按需懒加载）
                ensureThemeLoaded(currentThemeName)
                ThemeRegistry.getInstance().setTheme(currentThemeName)
                Log.i(TAG, "主题清单加载完成：内置 ${builtinThemeMeta.size} 套，自定义 ${customThemeMeta.size} 套")

                // 5. 加载 grammar 索引
                GrammarRegistry.getInstance().loadGrammars(GRAMMAR_INDEX)
                Log.i(TAG, "Grammar 索引加载完成")

                // 6. 构建 extension → scopeName 映射
                buildExtensionMap(appContext)

                initialized = true
                initFailCount = 0
                val elapsed = System.currentTimeMillis() - t0
                Log.i(TAG, "✅ TextMate 引擎初始化完成，耗时 ${elapsed}ms，可用 scope=${availableScopes.size}，扩展名映射=${extToScope.size}")
            } catch (e: Exception) {
                initFailCount++
                lastInitError = "${e.javaClass.simpleName}: ${e.message}"
                Log.e(TAG, "❌ TextMate 引擎初始化失败 (第 $initFailCount 次): ${e.message}", e)
            }
        }
    }

    /** 读取 editor/themes/index.json，构建内置主题元数据索引。 */
    private fun loadThemeIndex() {
        runCatching {
            val raw = appContext.assets.open(THEME_INDEX).bufferedReader().use { it.readText() }
            val list = ThemeJsonSupport.parseIndex(raw)
            builtinThemeMeta.clear()
            list.forEach { builtinThemeMeta[it.name] = it }
        }.onFailure { Log.w(TAG, "加载主题清单 index.json 失败", it) }
    }

    /** 扫描 filesDir/themes/ 下的自定义主题文件，构建元数据索引。 */
    fun reloadCustomThemes() {
        if (!::appContext.isInitialized) return
        val dir = File(appContext.filesDir, CUSTOM_THEME_DIR)
        if (!dir.exists()) return
        customThemeMeta.clear()
        dir.listFiles { f -> f.extension.equals("json", ignoreCase = true) }?.forEach { f ->
            runCatching {
                val raw = f.readText()
                val root = JSONObject(raw)
                val name = root.optString("name", f.nameWithoutExtension)
                val displayName = root.optString("displayName", name)
                val type = root.optString("type", "dark")
                val isDark = type != "light"
                val author = root.optString("author", "user")
                customThemeMeta[name] = ThemeMetadata(
                    name = name,
                    displayName = displayName,
                    category = ThemeCategory.CUSTOM,
                    isDark = isDark,
                    author = author,
                    filePath = f.absolutePath,
                    isCustom = true,
                )
                previewCache[name] = ThemeJsonSupport.extractPreviewColors(raw)
            }.onFailure { Log.w(TAG, "解析自定义主题失败: ${f.name}", it) }
        }
        _themeListVersion.value++
    }

    /** 从 languages.json 读取所有 language 条目，构建 scope 索引和扩展名→scopeName 映射。 */
    private fun buildExtensionMap(app: Context) {
        runCatching {
            val json = app.assets.open(GRAMMAR_INDEX).bufferedReader().use { it.readText() }
            val arr = JSONObject(json).getJSONArray("languages")
            var validCount = 0
            var extCount = 0
            for (i in 0 until arr.length()) {
                val obj = arr.getJSONObject(i)
                val scope = obj.optString("scopeName", "")
                if (scope.isNotEmpty()) {
                    availableScopes.add(scope)
                    validCount++
                    val extensions = obj.optJSONArray("extensions")
                    if (extensions != null) {
                        for (j in 0 until extensions.length()) {
                            val ext = extensions.getString(j).lowercase().trimStart('.')
                            extToScope[ext] = scope
                            extCount++
                        }
                    }
                }
            }
            Log.i(TAG, "语言注册表验证: $validCount/${arr.length()} 个有效 scopeName，$extCount 条扩展名映射")
        }.onFailure { Log.w(TAG, "构建 scope 索引失败", it) }
    }

    // ──────────────────────────────────────────────
    // 主题查询与加载
    // ──────────────────────────────────────────────

    /** 列出全部主题（内置 + 自定义），可按分类筛选。 */
    fun listThemes(category: ThemeCategory? = null): List<ThemeMetadata> {
        val all = builtinThemeMeta.values + customThemeMeta.values
        return if (category == null) all.toList() else all.filter { it.category == category }
    }

    /** 按名称获取主题元数据，未找到返回 null。 */
    fun getTheme(name: String): ThemeMetadata? =
        builtinThemeMeta[name] ?: customThemeMeta[name]

    /** 获取主题预览色板（未加载时从主题文件现场提取并缓存）。 */
    fun getPreviewColors(name: String): ThemePreviewColors {
        previewCache[name]?.let { return it }
        val meta = getTheme(name) ?: return ThemePreviewColors.EMPTY
        val raw = runCatching {
            meta.assetPath?.let { appContext.assets.open(it).bufferedReader().use { r -> r.readText() } }
                ?: meta.filePath?.let { File(it).readText() }
        }.getOrNull() ?: return ThemePreviewColors.EMPTY
        return ThemeJsonSupport.extractPreviewColors(raw).also { previewCache[name] = it }
    }

    /**
     * 确保指定主题已加载到 ThemeRegistry（按需懒加载，已加载则直接返回）。
     * 内置主题从 assets 加载，自定义主题从 filesDir 加载。
     */
    fun ensureThemeLoaded(name: String): Boolean {
        if (loadedThemes.contains(name)) return true
        val meta = getTheme(name) ?: run {
            Log.w(TAG, "未找到主题: $name")
            return false
        }
        return runCatching {
            val input: InputStream = when {
                meta.assetPath != null -> FileProviderRegistry.getInstance()
                    .tryGetInputStream(meta.assetPath) ?: return false
                meta.filePath != null -> File(meta.filePath).inputStream()
                else -> return false
            }
            val source = IThemeSource.fromInputStream(input, meta.assetPath ?: meta.filePath, null)
            val model = ThemeModel(source, name).apply { isDark = meta.isDark }
            ThemeRegistry.getInstance().loadTheme(model)
            loadedThemes.add(name)
            Log.d(TAG, "主题懒加载完成: $name")
            true
        }.getOrElse {
            Log.w(TAG, "加载主题失败: $name", it)
            false
        }
    }

    /**
     * 注册一个已保存到 filesDir 的自定义主题（导入/新建后调用）。
     * 立即加载到 ThemeRegistry 并刷新列表。
     */
    fun registerCustomTheme(meta: ThemeMetadata) {
        customThemeMeta[meta.name] = meta
        runCatching {
            meta.filePath?.let {
                val source = IThemeSource.fromInputStream(File(it).inputStream(), it, null)
                ThemeRegistry.getInstance().loadTheme(
                    ThemeModel(source, meta.name).apply { isDark = meta.isDark }
                )
                loadedThemes.add(meta.name)
            }
        }.onFailure { Log.w(TAG, "注册自定义主题失败: ${meta.name}", it) }
        _themeListVersion.value++
    }

    /** 删除一个自定义主题（同时删除文件与注册表项）。内置主题不可删除。 */
    fun deleteCustomTheme(name: String) {
        val meta = customThemeMeta[name] ?: return
        runCatching { meta.filePath?.let { File(it).delete() } }
        customThemeMeta.remove(name)
        previewCache.remove(name)
        _themeListVersion.value++
    }

    // ──────────────────────────────────────────────
    // 主题切换
    // ──────────────────────────────────────────────

    /** 设置当前主题（深色/浅色切换）。若主题未加载则先懒加载。 */
    fun setTheme(name: String) {
        if (!ensureThemeLoaded(name)) {
            Log.w(TAG, "主题不可用，回退默认: $name")
            if (name != DEFAULT_DARK_THEME) setTheme(DEFAULT_DARK_THEME)
            return
        }
        if (currentThemeName == name) return
        currentThemeName = name
        ThemeRegistry.getInstance().setTheme(name)
        _themeChanges.tryEmit(name)
        Log.i(TAG, "切换主题: $name")
    }

    /**
     * 根据系统深色/浅色模式自动选择主题。
     * 由 [com.mini.me_core.feature.settings.data.repository.EditorThemeRepository] 注入用户偏好的暗/亮主题名。
     */
    fun setThemeBySystemMode(isDarkMode: Boolean, darkTheme: String = DEFAULT_DARK_THEME, lightTheme: String = DEFAULT_LIGHT_THEME) {
        val themeName = if (isDarkMode) darkTheme else lightTheme
        if (themeName != currentThemeName) {
            setTheme(themeName)
            Log.i(TAG, "跟随系统模式切换主题: $themeName (dark=$isDarkMode)")
        }
    }

    /** 创建与当前主题绑定的 ColorScheme，用于设置到 CodeEditor。 */
    fun createColorScheme(): EditorColorScheme {
        ensureThemeLoaded(currentThemeName)
        return TextMateColorScheme.create(ThemeRegistry.getInstance())
    }

    // ──────────────────────────────────────────────
    // 语言工厂（保留原有能力）
    // ──────────────────────────────────────────────

    /**
     * 按 scopeName 创建 TextMateLanguage。
     * @param autoCompletion 是否启用自动补全（纯查看场景传 false 更省资源）
     */
    fun createLanguage(scopeName: String, autoCompletion: Boolean = false): TextMateLanguage {
        return TextMateLanguage.create(scopeName, autoCompletion)
    }

    /**
     * 按文件名扩展名推断 scopeName。
     * @return scopeName，未识别返回 null（调用方应回退到纯文本）
     */
    fun scopeForExtension(ext: String): String? {
        val key = ext.lowercase().trimStart('.')
        return extToScope[key]
    }

    /**
     * 按完整文件名推断 scopeName（支持多段扩展名，如 .blade.php、.html.erb、.adoc.txt）。
     */
    fun scopeForFileName(fileName: String): String? {
        val lower = fileName.lowercase()
        extToScope[lower]?.let { return it }
        var remaining = lower
        while (remaining.contains('.')) {
            remaining = remaining.substringAfter('.')
            extToScope[remaining]?.let { return it }
        }
        return null
    }

    fun isInitialized(): Boolean = initialized

    /** 供诊断使用：返回已加载主题名集合。 */
    fun loadedThemeNames(): Set<String> = loadedThemes.toSet()
}
