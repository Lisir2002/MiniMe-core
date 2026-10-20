package org.minime.gallery

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.safeDrawingPadding
import androidx.compose.foundation.layout.width
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import org.minime.core.api.MiniMeEngine
import org.minime.core.ui.LocalThemeSnapshot
import org.minime.core.ui.MiniMeTokens
import org.minime.core.ui.runtime.ThemeManager
import org.minime.gallery.nav.GallerySection
import org.minime.gallery.sections.SectionContent

/**
 * Gallery 壳（白皮书 §10.2 信息架构）：
 *
 * - **宽屏（≥840dp）**：内容区 + 常驻右侧边栏（iPad idiom）；
 * - **窄屏（<840dp）**：内容区 + 顶栏菜单按钮 + 右侧抽屉（手机降级，§7.2.5）。
 * - 顶栏常驻主题入口（当前外观 + 一键切换）。
 *
 * 换肤路径：顶栏切换走 `ThemeManager.select`（§8.1）——后台编译 + 完成才发射，
 * 进行中动效不取消；壳本身不做任何主题状态缓存（单一真源 = ThemeManager）。
 */
@Composable
fun GalleryApp(
    manager: ThemeManager,
    modifier: Modifier = Modifier,
) {
    var section by remember { mutableStateOf(GallerySection.DEFAULT) }
    var drawerOpen by remember { mutableStateOf(false) }
    val snapshot = LocalThemeSnapshot.current
    val color = MiniMeTokens.LocalColor.current

    fun select(target: GallerySection) {
        section = target
        drawerOpen = false
    }

    BoxWithConstraints(
        modifier = modifier
            .fillMaxSize()
            .background(color.canvasColor)
            .safeDrawingPadding(),
    ) {
        val wide = maxWidth >= 840.dp

        if (wide) {
            // 宽屏：内容 + 常驻右侧边栏
            Row(modifier = Modifier.fillMaxSize()) {
                Column(modifier = Modifier.weight(1f)) {
                    GalleryTopBar(
                        title = section.title,
                        subtitle = section.subtitle,
                        meta = snapshot.meta,
                        compact = false,
                        onToggleDark = { manager.toggleDark() },
                        onOpenDrawer = { drawerOpen = true },
                    )
                    SectionContent(
                        section = section,
                        manager = manager,
                        modifier = Modifier.fillMaxSize(),
                    )
                }
                GallerySidebar(
                    current = section,
                    meta = snapshot.meta,
                    engineVersion = MiniMeEngine.VERSION,
                    onSelect = { select(it) },
                    modifier = Modifier.width(280.dp),
                )
            }
        } else {
            // 窄屏：内容 + 抽屉
            Box(modifier = Modifier.fillMaxSize()) {
                Column(modifier = Modifier.fillMaxSize()) {
                    GalleryTopBar(
                        title = section.title,
                        subtitle = section.subtitle,
                        meta = snapshot.meta,
                        compact = true,
                        onToggleDark = { manager.toggleDark() },
                        onOpenDrawer = { drawerOpen = true },
                    )
                    SectionContent(
                        section = section,
                        manager = manager,
                        modifier = Modifier.fillMaxSize(),
                    )
                }
                if (drawerOpen) {
                    GalleryDrawer(
                        current = section,
                        meta = snapshot.meta,
                        engineVersion = MiniMeEngine.VERSION,
                        onSelect = { select(it) },
                        onDismiss = { drawerOpen = false },
                    )
                }
            }
        }
    }
}

/** 亮 / 暗一键切换（其余修饰符原样保留，§10.2 顶栏主题入口） */
private fun ThemeManager.toggleDark() {
    val current = snapshot.value.meta.activeModifiers
    val dark = ThemeModifiers.DARK in current
    select(
        packId = ThemeManager.BUILT_IN,
        modifiers = if (dark) current - ThemeModifiers.DARK else current + ThemeModifiers.DARK,
    )
}
