package org.minime.core.ui

import android.content.Context
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import org.minime.core.compiler.ThemeLoader
import org.minime.core.ui.runtime.FileThemePackRepository
import org.minime.core.ui.runtime.SharedPreferencesThemeStore
import org.minime.core.ui.runtime.ThemeManager

/**
 * Android 接入入口（R2）——把平台实现接到 [ThemeManager]，并给出冷启动时序约定。
 *
 * 典型用法（`Application.onCreate`）：
 * ```kotlin
 * val manager = MiniMeThemeBootstrap.create(this)
 * manager.initialize()          // 同步：此时还没绘制任何帧
 *
 * setContent {
 *     MiniMeTheme(manager) { App() }   // 首帧即目标主题
 * }
 * ```
 *
 * **时序是硬要求**：`initialize()` 必须在 `setContent` 之前完成，
 * 否则首帧会先绘制 fallback 再跳变（§8.2「首帧不闪」）。
 */
object MiniMeThemeBootstrap {

    /**
     * 创建 [ThemeManager]（尚未初始化）。
     *
     * @param context 应用上下文
     * @param scope 后台编译作用域；默认 `SupervisorJob + Default`（编译失败不影响其它协程）
     * @param builtInJsonProvider 内置默认包 JSON（默认读 classpath `/default-theme.json`）
     */
    fun create(
        context: Context,
        scope: CoroutineScope = CoroutineScope(SupervisorJob() + Dispatchers.Default),
        builtInJsonProvider: () -> String? = { loadBundledDefaultJson() },
    ): ThemeManager = ThemeManager(
        store = SharedPreferencesThemeStore(context.applicationContext),
        packs = FileThemePackRepository(context.applicationContext),
        scope = scope,
        builtInJson = builtInJsonProvider(),
        computeDispatcher = Dispatchers.Default,
    )

    /** 内置默认包 JSON（classpath 资源；Android 上随库打进 APK） */
    fun loadBundledDefaultJson(): String? =
        ThemeLoader::class.java.getResourceAsStream("/default-theme.json")
            ?.use { it.bufferedReader(Charsets.UTF_8).readText() }
}
