package org.minime.core.tokens

import org.minime.core.api.Immutable
import org.minime.core.schema.ColorValue
import org.minime.core.schema.TokenValue

/**
 * 配色子系统（§6.2 ColorTokens）。
 *
 * - [primitive]：原始色板（iOS 系统色，31 项）——主题作者改这里，语义层经别名换值（§5.1 规则 1）；
 * - [semantic]：语义层（31 项，多为 primitive 别名）——组件唯一消费面；
 * - [editor]/[terminal]/[chart]：开放词汇扩展组（`$extensions.minime.*`，§3.7），由主题包提供、可缺省。
 *
 * 访问器快速失败（`error`）：词汇表覆盖由一致性测试保证（默认包/fallback 均含全部路径），
 * 运行时缺失即引擎内部缺陷，不接受静默降级到任意色。
 */
@Immutable
data class ColorTokens(
    val primitive: Map<String, ColorValue>,
    val semantic: Map<String, ColorValue>,
    val editor: Map<String, TokenValue>,
    val terminal: Map<String, TokenValue>,
    val chart: Map<String, TokenValue>,
) {
    // ---------- 语义层强类型访问器（组件消费面，§3.4 色板） ----------

    val accent: ColorValue get() = semantic.color("accent")
    val bgCanvas: ColorValue get() = semantic.color("bg.canvas")
    val bgGrouped: ColorValue get() = semantic.color("bg.grouped")
    val bgSurface: ColorValue get() = semantic.color("bg.surface")
    val bgSurfaceSecondary: ColorValue get() = semantic.color("bg.surfaceSecondary")
    val bgSurfaceTertiary: ColorValue get() = semantic.color("bg.surfaceTertiary")
    val borderOpaque: ColorValue get() = semantic.color("border.opaque")
    val borderSeparator: ColorValue get() = semantic.color("border.separator")
    val fillPrimary: ColorValue get() = semantic.color("fill.primary")
    val fillSecondary: ColorValue get() = semantic.color("fill.secondary")
    val fillTertiary: ColorValue get() = semantic.color("fill.tertiary")
    val fillQuaternary: ColorValue get() = semantic.color("fill.quaternary")
    val gray1: ColorValue get() = semantic.color("gray1")
    val gray2: ColorValue get() = semantic.color("gray2")
    val gray3: ColorValue get() = semantic.color("gray3")
    val gray4: ColorValue get() = semantic.color("gray4")
    val gray5: ColorValue get() = semantic.color("gray5")
    val gray6: ColorValue get() = semantic.color("gray6")
    val indigo: ColorValue get() = semantic.color("indigo")
    val pink: ColorValue get() = semantic.color("pink")
    val purple: ColorValue get() = semantic.color("purple")
    val stateError: ColorValue get() = semantic.color("state.error")
    val stateSuccess: ColorValue get() = semantic.color("state.success")
    val stateWarning: ColorValue get() = semantic.color("state.warning")
    val teal: ColorValue get() = semantic.color("teal")
    val yellow: ColorValue get() = semantic.color("yellow")
    val textPrimary: ColorValue get() = semantic.color("text.primary")
    val textSecondary: ColorValue get() = semantic.color("text.secondary")
    val textTertiary: ColorValue get() = semantic.color("text.tertiary")
    val textQuaternary: ColorValue get() = semantic.color("text.quaternary")
    val textPlaceholder: ColorValue get() = semantic.color("text.placeholder")

    // ---------- 原始层查询（主题作者面：改值入口，无逐个访问器） ----------

    /** 按名取原始色（如 "systemBlue"）；缺失返回 null（原始层无强制覆盖承诺） */
    fun primitiveOrNull(name: String): ColorValue? = primitive[name]

    // ---------- 扩展组访问（开放词汇：按路径取，缺失返回 null） ----------

    /** 编辑器语法色（路径相对组根，如 "syntax.keyword"） */
    fun editorColor(path: String): ColorValue? = editor[path] as? ColorValue

    /** 终端 ANSI 色（路径相对组根，如 "ansi.red"） */
    fun terminalColor(path: String): ColorValue? = terminal[path] as? ColorValue

    /** 图表分类色（路径相对组根，如 "c1"） */
    fun chartColor(path: String): ColorValue? = chart[path] as? ColorValue
}
