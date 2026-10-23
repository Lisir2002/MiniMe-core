package org.minime.core.ui.gallery.pages

/**
 * 工坊布局规格（R5.5 布局重构，charter §3 / D1–D6）——布局常量与联动映射，机器可测。
 *
 * 维护者指令（2026-10-23 21:41）：「固定顶部演示区域占内容区域的 1/5，其余部分是
 * 可滑动的参数调控内容」。本对象是该指令的唯一数字真源：演示带与参数流的比例、
 * 演示带样本组清单、分类 → 默认样本组联动映射。
 *
 * 与 R5.5 前病灶（六处 `weight` 嵌套切割）的本质区别：此处为**单层、维护者指定、
 * 演示带本是固定参照物**的比例，非多层动态预算（charter D6）。
 */
object WorkshopLayoutSpec {

    /** 演示带 = 内容区 1/5（固定，不随参数流滚动） */
    const val DEMO_BAND_RATIO = 0.2f

    /**
     * 参数流 = 内容区 4/5（可滚动：WCAG 读数 + 令牌树 + 手风琴编辑器）。
     *
     * 实现口径：演示带 `fillMaxHeight(0.2f)` + 分类 tab（wrap）+ 参数流 `weight(1f)`
     * 吃掉剩余。**不能用两个 fillMaxHeight fraction**——Column 内 fraction 的基数是
     * 「剩余空间」，0.2 + tab + 0.8 会三重扣减使比例漂移（R5.5 实测 0.367）。
     * 本常量为口径与测试断言基准（band / (band + flow) ≈ 0.2）。
     */
    const val PARAM_FLOW_RATIO = 0.8f

    /** 演示带 testTag（布局契约测试定位用） */
    const val DEMO_BAND_TAG = "workshop-demo-band"

    /** 参数流 testTag（布局契约测试定位用） */
    const val PARAM_FLOW_TAG = "workshop-param-flow"
}

/**
 * 演示带样本组（chip 行，六组；charter D4）。
 *
 * 命名与工坊分类 tab（色板 / 字阶 / 圆角 / 间距 / 动效 / 玻璃）刻意错开——
 * 同屏两行 tab 若重名，用户无法分辨哪行控制什么（R5-C P3 评审教训）。
 */
enum class DemoGroup(val title: String) {
    TEXT("文本"),
    BUTTONS("按钮"),
    CARDS("卡片"),
    SWATCH("配色"),
    GLASS("玻璃拟态"),
    MOTION("动效点播"),
}

/**
 * 分类 tab → 演示带默认样本组联动（charter D4：调什么看什么）。
 *
 * 选「色板」类 → 演示带默认显配色组；用户仍可手动 chip 切换到别的组
 * （手动切换优先，直到下次切类）。
 */
fun defaultDemoGroup(category: WorkshopCategory): DemoGroup = when (category) {
    WorkshopCategory.PALETTE -> DemoGroup.SWATCH
    WorkshopCategory.TYPOGRAPHY -> DemoGroup.TEXT
    WorkshopCategory.SHAPE -> DemoGroup.CARDS
    WorkshopCategory.SPACING -> DemoGroup.CARDS
    WorkshopCategory.MOTION -> DemoGroup.MOTION
    WorkshopCategory.GLASS -> DemoGroup.GLASS
}
