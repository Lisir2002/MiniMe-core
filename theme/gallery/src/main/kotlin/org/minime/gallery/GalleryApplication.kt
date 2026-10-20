package org.minime.gallery

import android.app.Application
import org.minime.core.ui.MiniMeThemeBootstrap
import org.minime.core.ui.runtime.ThemeManager

/**
 * Gallery Application（R3）。
 *
 * 唯一职责：在**任何界面绘制之前**创建并同步初始化 [ThemeManager]——
 * §8.2「首帧不闪」：`initialize()` 同步恢复上次选择（或编译内置包），
 * 首帧即目标主题。Application 持有唯一实例，Activity 只读。
 */
class GalleryApplication : Application() {

    /** 主题管理器（懒创建：Application.onCreate 里即就绪） */
    val themeManager: ThemeManager by lazy {
        MiniMeThemeBootstrap.create(this).also { it.initialize() }
    }

    override fun onCreate() {
        super.onCreate()
        // 触发懒创建——保证 initialize() 在任何 Activity setContent 前完成
        themeManager
    }
}
