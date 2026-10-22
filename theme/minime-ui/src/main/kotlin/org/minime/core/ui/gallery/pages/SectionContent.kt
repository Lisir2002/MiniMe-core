package org.minime.core.ui.gallery.pages

import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import org.minime.core.ui.runtime.ThemeManager
import org.minime.core.ui.gallery.nav.GallerySection

/**
 * 分区内容调度（§10.2 六分区）。
 *
 * R3：令牌总览 + 调试面板（基础版）已建成；其余为占位页（标注交付批次），
 * 随 R4–R9 逐批替换——「画廊与批次同速生长」（分批实施计划总原则 3）。
 */
@Composable
fun SectionContent(
    section: GallerySection,
    manager: ThemeManager,
    modifier: Modifier = Modifier,
) {
    when (section) {
        GallerySection.TOKENS -> TokensOverviewSection(modifier = modifier)
        GallerySection.DEBUG -> DebugPanelSection(manager = manager, modifier = modifier)
        GallerySection.COMPONENTS -> PlaceholderSection(GallerySection.COMPONENTS, modifier)
        GallerySection.PROTOTYPES -> PlaceholderSection(GallerySection.PROTOTYPES, modifier)
        GallerySection.MOTION -> PlaceholderSection(GallerySection.MOTION, modifier)
        // 主题工坊（R4）：覆写层运行时 + 类型分发编辑器 + WCAG 实时读数
        GallerySection.WORKSHOP -> WorkshopSection(manager = manager, modifier = modifier)
    }
}
