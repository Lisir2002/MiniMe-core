package com.mini.me_core.feature.themegallery.presentation

import androidx.test.core.app.ApplicationProvider
import org.junit.Assert
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.Shadows
import org.robolectric.annotation.Config
import android.os.Looper
import org.minime.core.ui.gallery.ThemeModifiers
import org.minime.core.ui.runtime.ThemeManager

/**
 * 新主题引擎演示页 ViewModel 的隔离与初始化验收（R4 接入期第一批）。
 *
 * 覆盖（对应计划验证序列的 JVM 等价项）：
 * 1. **内置包 classpath 可达**——快照来自真实默认包「MiniMe Pure White 1.0.0」，
 *    而非 fallback「MiniMe Built-in Fallback」（R1 风险：default-theme.json 未进
 *    release classpath 会恒 fallback，此处先行拦截 debug classpath）；
 * 2. **构造即初始化 + toggleDark 双向**——ThemeManager 在 VM 构造期同步
 *    initialize（白皮书 §8.2 首帧不闪），亮/暗切换真实改快照；
 * 3. **选择跨进入恢复**——引擎选择持久化 SharedPreferences「minime_theme」，
 *    重进演示页（新建 VM）恢复上次修饰符（隔离验收项②的新→旧方向）。
 *
 * :app 不加 Compose UI 测试（isIncludeAndroidResources=false 下 Robolectric 加载
 * 合并清单必败）；UI 组装等价覆盖由引擎侧 GalleryEmbedRobolectricTest 承担。
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [28])
class ThemeGalleryViewModelTest {

    /** 默认内置包名（theme-engine/src/main/resources/default-theme.json manifest.name）。 */
    private val builtInPackName = "MiniMe Pure White"

    /** fallback 快照包名（SnapshotBuilder.fallback）——出现即说明内置包未加载。 */
    private val fallbackPackName = "MiniMe Built-in Fallback"

    private fun newViewModel(): ThemeGalleryViewModel =
        ThemeGalleryViewModel(ApplicationProvider.getApplicationContext())

    /**
     * 轮询等待修饰符条件成立（ThemeManager.select 后台编译后才发射新快照，
     * §8.1「完成才允许切换」——不能即时断言）。
     *
     * **必须显式推进主线程队列**：VM 的 viewModelScope 走 `Dispatchers.Main`，
     * 在 Robolectric 下协程恢复依赖 Handler post 到 main looper，而测试线程即主线程——
     * 只 `Thread.sleep` 会让 sleep 与调度互相阻塞，后台编译永不起步（实测表现为
     * 断言超时）。故每轮先 `idle()` 再 sleep。
     */
    private fun awaitModifiers(
        manager: ThemeManager,
        timeoutMs: Long = 10_000,
        predicate: (List<String>) -> Boolean,
    ) {
        val deadline = System.currentTimeMillis() + timeoutMs
        while (System.currentTimeMillis() < deadline) {
            Shadows.shadowOf(Looper.getMainLooper()).idle()
            if (predicate(manager.snapshot.value.meta.activeModifiers)) return
            Thread.sleep(20)
        }
        throw AssertionError(
            "等待修饰符条件超时，当前激活修饰符: ${manager.snapshot.value.meta.activeModifiers}"
        )
    }

    @Test
    fun 内置包经classpath加载而非fallback() {
        val viewModel = newViewModel()
        val meta = viewModel.manager.snapshot.value.meta
        Assert.assertEquals(
            "快照应来自默认内置包（classpath /default-theme.json），非 fallback",
            builtInPackName,
            meta.packName,
        )
        Assert.assertNotEquals(fallbackPackName, meta.packName)
        Assert.assertEquals("1.0.0", meta.packVersion)
    }

    @Test
    fun 构造即初始化且toggleDark双向生效() {
        val viewModel = newViewModel()
        val manager = viewModel.manager

        // 默认内置包为亮色：构造期 initialize 已同步恢复
        Assert.assertFalse(
            "初始快照不应含 dark",
            ThemeModifiers.DARK in manager.snapshot.value.meta.activeModifiers,
        )

        // 亮 → 暗（select 后台编译，轮询等待发射）
        viewModel.toggleDark()
        awaitModifiers(manager) { ThemeModifiers.DARK in it }

        // 暗 → 亮（缓存命中路径，仍以轮询兜底）
        viewModel.toggleDark()
        awaitModifiers(manager) { ThemeModifiers.DARK !in it }
    }

    @Test
    fun 选择经SharedPreferences跨进入恢复() {
        // 第一次进入：切暗色并等待落盘（saveSelection 在编译成功后）
        val first = newViewModel()
        first.toggleDark()
        awaitModifiers(first.manager) { ThemeModifiers.DARK in it }

        // 第二次进入（模拟离开后重进）：initialize 同步读 SharedPreferences 恢复
        val second = newViewModel()
        Assert.assertTrue(
            "重进演示页应恢复上次暗色选择",
            ThemeModifiers.DARK in second.manager.snapshot.value.meta.activeModifiers,
        )
    }
}
