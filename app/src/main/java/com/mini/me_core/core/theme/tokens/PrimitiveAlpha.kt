package com.mini.me_core.core.theme.tokens

/**
 * Primitive 透明度 Token。
 *
 * 为背景图支持和毛玻璃效果准备的透明度层级。
 * 所有 surface 颜色在背景图模式下通过这些透明度值自动注入 alpha 通道。
 */
object PrimitiveAlpha {
    /** 完全透明 */
    const val Transparent = 0f

    /** 极低透明度（5%）：悬停态叠加层 */
    const val Hover = 0.05f

    /** 低透明度（10%）：装饰性背景、按下态叠加 */
    const val Pressed = 0.10f

    /** 中低透明度（25%）：次要卡片，背景图模式下 */
    const val Sunken = 0.25f

    /** 中透明度（40%）：背景图遮罩层默认浓度 */
    const val Scrim = 0.40f

    /** 中高透明度（60%）：强调容器，背景图模式下 */
    const val Accent = 0.60f

    /** 高透明度（75%）：卡片内嵌区域，背景图模式下 */
    const val CardSunken = 0.75f

    /** 很高透明度（85%）：主卡片，背景图模式下默认 */
    const val Card = 0.85f

    /** 几乎不透明（95%）：弹出层/Sheet，背景图模式下 */
    const val Overlay = 0.95f

    /** 完全不透明 */
    const val Opaque = 1.0f

    /** 禁用状态透明度（38%，Material 标准） */
    const val Disabled = 0.38f
}
