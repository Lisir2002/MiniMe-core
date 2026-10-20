package org.minime.core.compiler

import org.minime.core.api.CompileErrorCode
import org.minime.core.api.ThemeCompileException
import org.minime.core.schema.RawField
import org.minime.core.schema.RawToken
import org.minime.core.schema.SetModel
import org.minime.core.schema.ThemePack

/**
 * 集合合并器（MiniMe Resolver 稳定子集 §5.1 规则 2/3 的落地）。
 *
 * 规则 2——**互斥修饰符单选、可叠加修饰符追加**：按 resolutionOrder 顺序合并，
 * 后者覆盖前者。「互斥单选」由调用方（R2 ThemeManager 按系统上下文）保证——
 * 编译器只校验 [activeModifiers] 是已声明修饰符的子集且无重复。
 *
 * 规则 3——**修饰符不得引用 modifier**：修饰符集内只允许写具体令牌值
 * （标量字面量 / 复合型全字段字面量），不允许任何别名——顶层或复合字段均不可。
 * 这保证「先合并后解析」（规则 1）的确定性：合并期不存在待解析引用。
 *
 * 扩展组（`$extensions.minime.<group>`）为**包级常驻**：不参与 resolutionOrder、
 * 无修饰符交互（白皮书 §3.7：编辑器/终端/图表开放词汇由主题包整体提供），
 * 合并于 set 之后进入同一张表——扩展组内允许别名（如 `chart.c1 = {color.semantic.accent}`），
 * 与核心令牌同一解析时序；路径冲突已由 ThemeValidator.validateExtensions 先行拒绝。
 */
object SetMerger {

    /**
     * 按 resolutionOrder 合并选中集 + 扩展组为一张扁平原始令牌表。
     *
     * @param pack 主题包（sets 内含公共集与修饰符集）
     * @param activeModifiers 运行时激活的修饰符名（如 ["dark", "highContrast"]）
     * @return 合并后的原始令牌表（路径 → RawToken，后合覆盖先合）
     */
    fun merge(pack: ThemePack, activeModifiers: List<String>): Map<String, RawToken> {
        val active = activeModifiers.toSet()
        val modifierNames = pack.resolutionOrder.mapNotNull { it.modifier }.toSet()

        // 激活的修饰符必须是已声明（出现在 resolutionOrder modifier 条目中）且无重复
        val duplicates = activeModifiers.groupBy { it }.filterValues { it.size > 1 }.keys
        if (duplicates.isNotEmpty()) {
            throw ThemeCompileException(
                CompileErrorCode.MODIFIER_INVALID,
                "激活修饰符重复: ${duplicates.sorted()}",
            )
        }
        val unknown = active - modifierNames
        if (unknown.isNotEmpty()) {
            throw ThemeCompileException(
                CompileErrorCode.MODIFIER_INVALID,
                "激活了未声明的修饰符: ${unknown.sorted()}（已声明: ${modifierNames.sorted()}）",
            )
        }

        val merged = LinkedHashMap<String, RawToken>()
        for (entry in pack.resolutionOrder) {
            val name = entry.set ?: entry.modifier!!
            val isModifier = entry.modifier != null
            if (isModifier && name !in active) continue // 修饰符未激活 → 跳过（差异集不参与合并）

            val set: SetModel = pack.sets[name]
                ?: throw ThemeCompileException(
                    CompileErrorCode.SCHEMA_INVALID,
                    "resolutionOrder 引用了不存在的集: \"$name\"",
                )
            if (isModifier) checkModifierLiterals(set)
            for ((path, token) in set.tokens) {
                merged[path] = token // 后者覆盖前者（规则 2）
            }
        }

        // 扩展组：包级常驻，合并于 set 之后（路径冲突已由 validateExtensions 拒绝）
        for (group in pack.extensions.values) {
            for ((path, token) in group.tokens) {
                merged[path] = token
            }
        }
        return merged
    }

    /** 规则 3：修饰符集只允许字面量——顶层别名与复合字段别名均拒绝 */
    private fun checkModifierLiterals(set: SetModel) {
        for ((path, token) in set.tokens) {
            if (token.alias != null) {
                throw ThemeCompileException(
                    CompileErrorCode.MODIFIER_INVALID,
                    "修饰符集 \"${set.name}\" 的令牌 \"$path\" 含别名 ${token.alias}（规则 3：修饰符不得引用，只允许字面量）",
                )
            }
            token.fields?.forEach { (field, raw: RawField) ->
                if (raw.alias != null) {
                    throw ThemeCompileException(
                        CompileErrorCode.MODIFIER_INVALID,
                        "修饰符集 \"${set.name}\" 的令牌 \"$path\" 字段 \"$field\" 含别名 ${raw.alias}（规则 3：修饰符不得引用，只允许字面量）",
                    )
                }
            }
        }
    }
}
