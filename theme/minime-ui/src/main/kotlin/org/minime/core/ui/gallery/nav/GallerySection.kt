package org.minime.core.ui.gallery.nav

/**
 * Gallery 六个分区（白皮书 §10.2）。
 *
 * 侧边栏顺序即验收顺序（§10.2）：令牌总览 → 组件目录 → 页面原型 → 动效检验 →
 * 主题工坊 → 调试面板。
 *
 * R3 只建成「令牌总览」与「调试面板（基础版）」；其余分区随批次落地
 * （[plannedBatch] 标注），占位页随每批 RC 替换为真实内容——「画廊与批次同速生长」
 * （分批实施计划总原则 3）。
 */
enum class GallerySection(
    val title: String,
    val subtitle: String,
    /** 该分区的完整交付批次（R3 的占位页 → 对应批次替换） */
    val plannedBatch: String,
) {
    TOKENS("令牌总览", "十子系统 · 与 §6.1.4 机器核对", "R3"),
    COMPONENTS("组件目录", "八大类 60 组件 · 七态矩阵", "R6–R9"),
    PROTOTYPES("页面原型", "P1–P8 · 导航矩阵", "R8"),
    MOTION("动效检验", "26 模式 · 慢放 / 逐帧", "R5"),
    WORKSHOP("主题工坊", "令牌覆写 · 导入导出", "R4"),
    DEBUG("调试面板", "主题 / 降级链 / 性能档", "R3"),

    ;

    companion object {
        /** 默认分区（冷启动落在第一个） */
        val DEFAULT = TOKENS
    }
}
