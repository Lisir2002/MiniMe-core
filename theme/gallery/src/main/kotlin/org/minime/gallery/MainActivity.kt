package org.minime.gallery

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import org.minime.core.ui.MiniMeTheme
import androidx.compose.ui.graphics.toArgb
import org.minime.core.ui.m3.toComposeColor

/**
 * Gallery 主 Activity（R3 壳）。
 *
 * 时序（§8.2 硬要求）：
 * 1. `Application.onCreate` 已同步完成 `ThemeManager.initialize()`；
 * 2. 本 Activity 把窗口背景替换为当前快照的 `bg.canvas`（暗色主题不闪白）；
 * 3. `setContent` 内 `MiniMeTheme(manager)` 提供十子系统 + M3 映射。
 *
 * `configChanges` 已在 manifest 声明（方向 / 尺寸不走重建），
 * 换肤不依赖 Activity 重建（§8.1 约定 2：进行中动效不取消）。
 */
class MainActivity : ComponentActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()

        val manager = (application as GalleryApplication).themeManager
        // Compose 接管前的窗口底色 = 当前主题画布色（防止暗色主题冷启动闪白）。
        // 引擎内部即 ARGB 位模式（ColorValue.argb），直接取 int，不经字符串往返。
        window.setBackgroundDrawable(
            android.graphics.drawable.ColorDrawable(manager.snapshot.value.color.bgCanvas.toComposeColor().toArgb())
        )

        setContent {
            MiniMeTheme(manager) {
                GalleryApp(manager = manager)
            }
        }
    }
}
