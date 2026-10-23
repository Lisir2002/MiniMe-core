package org.minime.core.compiler

import org.minime.core.api.CompileErrorCode
import org.minime.core.api.ThemeCompileException
import org.minime.core.schema.RawToken
import org.minime.core.schema.ResolutionEntry
import org.minime.core.schema.SetModel
import org.minime.core.schema.ThemePack
import org.minime.core.schema.TokenTypeCheck
import org.minime.core.schema.TokenValue
import org.minime.core.tokens.TokenVocabulary

/**
 * 运行时覆写层（R4 工坊，白皮书 §10.7「主题工坊」的引擎侧底座）。
 *
 * 优先级链第三级：**fallback 基座 < 主题包编译快照 < 运行时覆写**。
 *
 * 核心纪律——**禁第二套合并逻辑**：覆写不新写合并/解析代码，而是把覆写项装配为
 * 一个合成 set（[OVERLAY_SET]，全字面量）挂到主题包 `resolutionOrder` **最末**，
 * 之后原样走既有编译流水线（[SetMerger] 按序合并 → [AliasResolver] 先合并后解析）。
 * 于是 §5.1 规则 1 在运行时自然成立：**覆写 primitive 后，引用它的语义别名随之换值**
 * （如覆写 `color.primitive.systemBlue` → `color.semantic.accent` 立即跟随），
 * 复合型字段别名同理（如覆写 `glass.regular.tintAlpha` → `glass.regular.tint` 字段跟随）。
 *
 * 作用域约束（R4.1）：
 * - 只允许覆写**标准词汇表**路径（[TokenVocabulary.ALL] 的 173 条）——工坊编辑的是
 *   十子系统标准令牌；开放词汇（editor/terminal/chart 扩展组）由主题包整体提供，
 *   覆写它们涉及扩展组合并语义，留待后续批次；
 * - 覆写值类型须与词汇表声明类型一致（[TokenTypeCheck] 共享判定，与别名解析同一真源）；
 * - 覆写集是**内存态**（本对象不落盘）——导出 diff 主题包由工坊侧另行负责（R4.3），
 *   默认包永不被污染（纪律：覆写不写回 [ThemePackRepository]）。
 */
object OverlayApplier {

    /** 合成覆写集名（挂在 resolutionOrder 最末，优先级最高） */
    const val OVERLAY_SET: String = "overrides"

    /**
     * 把运行时覆写装配为覆写后的主题包模型。
     *
     * @param base 已解析的基础主题包（[DtcgParser.parse] 产出；不含覆写集）
     * @param entries 覆写项（路径 → 字面值；空 map = 无覆写，原样返回 [base]）
     * @return 挂上覆写集的新主题包（[base] 的 manifest / 其它 set / 扩展组原样保留）
     * @throws ThemeCompileException 路径不在标准词汇表（SCHEMA_INVALID）或值类型不符（TYPE_MISMATCH）
     */
    fun apply(base: ThemePack, entries: Map<String, TokenValue>): ThemePack {
        if (entries.isEmpty()) return base

        if (OVERLAY_SET in base.sets) {
            // 防御：基础包自带同名集会让 resolutionOrder 出现重复引用（校验报错难定位），在此明示
            throw ThemeCompileException(
                CompileErrorCode.SCHEMA_INVALID,
                "基础主题包含名为 \"$OVERLAY_SET\" 的集，与运行时覆写合成集冲突（请重命名该集）",
            )
        }

        val tokens = LinkedHashMap<String, RawToken>(entries.size)
        for ((path, value) in entries) {
            val type = TokenVocabulary.typeOf(path)
                ?: throw ThemeCompileException(
                    CompileErrorCode.SCHEMA_INVALID,
                    "覆写路径不在标准词汇表: \"$path\"（R4.1 仅支持 ${TokenVocabulary.SIZE} 条标准令牌；开放词汇覆写待后续批次）",
                )
            TokenTypeCheck.requireMatches(path, type, value)
            // 全字面量（无别名）：合并期即终值，解析期原样穿过（规则 3 同款确定性）
            tokens[path] = RawToken(path, type, literal = value)
        }

        val overlaySet = SetModel(OVERLAY_SET, tokens)
        return base.copy(
            sets = base.sets + (OVERLAY_SET to overlaySet),
            resolutionOrder = base.resolutionOrder + ResolutionEntry(set = OVERLAY_SET, modifier = null),
        )
    }
}
