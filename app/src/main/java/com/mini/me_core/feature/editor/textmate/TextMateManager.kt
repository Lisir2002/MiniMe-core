package com.mini.me_core.feature.editor.textmate

import android.content.Context
import android.util.Log
import io.github.rosemoe.sora.langs.textmate.TextMateColorScheme
import io.github.rosemoe.sora.langs.textmate.TextMateLanguage
import io.github.rosemoe.sora.langs.textmate.registry.FileProviderRegistry
import io.github.rosemoe.sora.langs.textmate.registry.GrammarRegistry
import io.github.rosemoe.sora.langs.textmate.registry.ThemeRegistry
import io.github.rosemoe.sora.langs.textmate.registry.model.ThemeModel
import io.github.rosemoe.sora.langs.textmate.registry.provider.AssetsFileResolver
import io.github.rosemoe.sora.widget.schemes.EditorColorScheme
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.eclipse.tm4e.core.registry.IThemeSource
import org.json.JSONObject
import java.io.InputStream

/**
 * TextMate 引擎管理器（单例）。
 *
 * 负责：
 *  - 注册 AssetsFileResolver（从 APK assets 读取 grammar/theme）
 *  - 加载内置 grammar（assets/editor/grammars/ 目录下全部 JSON，全量 ~260 种）
 *  - 加载内置主题（assets/editor/themes/ 目录下 JSON 主题文件）
 *  - 提供按 scopeName 创建 [TextMateLanguage] 的工厂方法
 *
 * 初始化时机：Application.onCreate 异步预热段调用 [initialize]，不阻塞首帧。
 * 任何编辑器实例共享同一个 GrammarRegistry / ThemeRegistry 单例。
 */
object TextMateManager {

    private const val TAG = "TextMateManager"
    private const val GRAMMAR_INDEX = "editor/languages.json"
    private const val THEME_DIR = "editor/themes"

    /** 内置主题清单：name → assets 路径 + 是否深色 */
    private val builtinThemes = listOf(
        ThemeInfo("dark-plus", "editor/themes/dark-plus.json", isDark = true),
        ThemeInfo("light-plus", "editor/themes/light-plus.json", isDark = false),
        ThemeInfo("github-dark", "editor/themes/github-dark-default.json", isDark = true),
        ThemeInfo("github-light", "editor/themes/github-light-default.json", isDark = false),
        ThemeInfo("dracula", "editor/themes/dracula.json", isDark = true),
    )

    @Volatile
    private var initialized = false

    @Volatile
    var currentThemeName: String = "dark-plus"
        private set

    /** extension → scopeName 映射（由 languages.json 构建）。 */
    private val extToScope = HashMap<String, String>(512)

    /** scopeName → 是否可用。 */
    private val availableScopes = HashSet<String>(256)

    /**
     * 初始化 TextMate 引擎。幂等，可重复调用。
     * 必须在后台线程调用（涉及 assets IO + grammar 解析）。
     */
    suspend fun initialize(appContext: Context) = withContext(Dispatchers.IO) {
        if (initialized) return@withContext
        synchronized(this) {
            if (initialized) return@withContext

            val t0 = System.currentTimeMillis()
            try {
                // 1. 注册 AssetsFileResolver
                FileProviderRegistry.getInstance().addFileProvider(
                    AssetsFileResolver(appContext.applicationContext.assets)
                )

                // 2. 加载主题
                val themeRegistry = ThemeRegistry.getInstance()
                for (info in builtinThemes) {
                    runCatching { loadTheme(info) }
                        .onFailure { Log.w(TAG, "加载主题失败: ${info.name}", it) }
                }
                themeRegistry.setTheme(currentThemeName)
                Log.i(TAG, "主题加载完成: ${builtinThemes.size} 套")

                // 3. 加载 grammar 索引
                GrammarRegistry.getInstance().loadGrammars(GRAMMAR_INDEX)
                Log.i(TAG, "Grammar 加载完成")

                // 4. 构建 extension → scopeName 映射
                buildExtensionMap(appContext)

                initialized = true
                val elapsed = System.currentTimeMillis() - t0
                Log.i(TAG, "TextMate 引擎初始化完成，耗时 ${elapsed}ms，可用 scope=${availableScopes.size}")
            } catch (e: Exception) {
                Log.e(TAG, "TextMate 引擎初始化失败", e)
            }
        }
    }

    /** 从 languages.json 读取所有 language 条目，构建扩展名→scopeName 映射。 */
    private fun buildExtensionMap(appContext: Context) {
        runCatching {
            val json = appContext.assets.open(GRAMMAR_INDEX).bufferedReader().use { it.readText() }
            val arr = JSONObject(json).getJSONArray("languages")
            for (i in 0 until arr.length()) {
                val obj = arr.getJSONObject(i)
                val scope = obj.optString("scopeName", "")
                if (scope.isNotEmpty()) availableScopes.add(scope)
            }
        }.onFailure { Log.w(TAG, "构建 scope 索引失败", it) }
    }

    private fun loadTheme(info: ThemeInfo) {
        val inputStream: InputStream = FileProviderRegistry.getInstance()
            .tryGetInputStream(info.path) ?: return
        val model = ThemeModel(
            IThemeSource.fromInputStream(
                inputStream, info.path, null
            ),
            info.name
        ).apply { isDark = info.isDark }
        ThemeRegistry.getInstance().loadTheme(model)
    }

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

    /** 设置当前主题（深色/浅色切换）。 */
    fun setTheme(name: String) {
        currentThemeName = name
        ThemeRegistry.getInstance().setTheme(name)
    }

    /** 创建与当前主题绑定的 ColorScheme，用于设置到 CodeEditor。 */
    fun createColorScheme(): EditorColorScheme {
        return TextMateColorScheme.create(ThemeRegistry.getInstance())
    }

    fun isInitialized(): Boolean = initialized

    private data class ThemeInfo(
        val name: String,
        val path: String,
        val isDark: Boolean,
    )
}
