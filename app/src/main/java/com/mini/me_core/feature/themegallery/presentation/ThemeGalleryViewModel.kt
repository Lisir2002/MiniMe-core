package com.mini.me_core.feature.themegallery.presentation

import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import org.minime.core.ui.MiniMeThemeBootstrap
import org.minime.core.ui.gallery.ThemeModifiers
import org.minime.core.ui.gallery.nav.GallerySection
import org.minime.core.ui.runtime.ThemeManager

/**
 * 新主题引擎演示页 ViewModel（R4 接入期第一批）。
 *
 * 职责边界：**只**承载自研主题引擎（theme/ 子树，org.minime.*）的演示态，
 * 与主程序旧主题引擎（com.mini.me_core.core.theme，KVStore 存储）完全隔离：
 *
 * - **实例隔离**：ThemeManager 由演示页独享（路由组合期经 hiltViewModel() 构造，
 *   离开页面 VM 清除即失去唯一引用），旧引擎的 ThemeManager 生命周期不受影响；
 * - **存储零交集**：引擎侧 SharedPreferences「minime_theme」（key: pack_id /
 *   modifiers），旧引擎走 KVStore/SQLDelight（namespace theme_settings / settings），
 *   互不读写；
 * - **时序约定**：init 同步 [ThemeManager.initialize]() 先于首帧绘制
 *   （白皮书 §8.2「首帧不闪」）；后台编译作用域传 viewModelScope，
 *   离开演示页即取消在途编译，不向主程序泄漏后台任务。
 *
 * 重进演示页时经 SharedPreferences 恢复上次选择（引擎既定行为）。
 */
@HiltViewModel
class ThemeGalleryViewModel @Inject constructor(
    application: android.app.Application,
) : AndroidViewModel(application) {

    /**
     * 引擎主题管理器（演示页唯一引用；初始化在构造期同步完成）。
     *
     * scope = viewModelScope：VM 清除时取消在途编译。
     */
    val manager: ThemeManager = MiniMeThemeBootstrap.create(
        context = getApplication<android.app.Application>().applicationContext,
        scope = viewModelScope,
    ).also { it.initialize() }

    /** 演示页 UI 状态（当前展示分区）。 */
    data class UiState(
        val section: GallerySection = GallerySection.DEFAULT,
    )

    private val _uiState = MutableStateFlow(UiState())
    val uiState: StateFlow<UiState> = _uiState.asStateFlow()

    /** 切换展示分区（宽屏侧边栏 / 窄屏抽屉导航）。 */
    fun selectSection(section: GallerySection) {
        _uiState.update { it.copy(section = section) }
    }

    /**
     * 亮 / 暗一键切换（其余修饰符原样保留）。
     *
     * 逻辑复刻引擎参考用法（GalleryEmbedRobolectricTest.toggleDark）：
     * 从当前快照取激活修饰符，翻转 [ThemeModifiers.DARK] 后经
     * [ThemeManager.select] 重新选包，走 §8.1 换肤路径。
     */
    fun toggleDark() {
        val current = manager.snapshot.value.meta.activeModifiers
        val dark = ThemeModifiers.DARK in current
        manager.select(
            packId = ThemeManager.BUILT_IN,
            modifiers = if (dark) current - ThemeModifiers.DARK else current + ThemeModifiers.DARK,
        )
    }
}
