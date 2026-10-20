package org.minime.core.compiler

import org.minime.core.api.MiniMeEngine
import org.minime.core.fallback.FallbackTheme
import org.minime.core.schema.DtcgParser
import org.minime.core.schema.ThemePack
import org.minime.core.schema.TokenValue
import org.minime.core.tokens.SnapshotBuilder
import org.minime.core.tokens.ThemeMeta
import org.minime.core.tokens.ThemeSnapshot

/**
 * 主题编译器（白皮书 §5.1/§5.2：DTCG Format 标准 + MiniMe Resolver 稳定子集）。
 *
 * 流水线（顺序即优先级——任一步失败即整体拒绝导入）：
 * ```
 * validatePack          §5.2 行 1：engineVersion / resolutionOrder 引用 / 扩展冲突 / schema 声明
 *   ↓
 * SetMerger.merge       规则 2/3：按序合并（后者覆盖）+ 修饰符禁别名 + 扩展组常驻并入
 *   ↓
 * validateVocabularyTypes  §5.2 行 2：词汇表令牌类型一致
 *   ↓
 * AliasResolver         规则 1：先合并后解析（断链/循环/类型不符/复合字段别名）
 *   ↓
 * effective = fallback + resolved     优先级链第一二级（词汇表缺项回填，不拒绝）
 *   ↓
 * validateGlassConsumers  §5.2 行 4：玻璃 opaque（glass.enabled 生效时）
 *   ↓
 * wcagWarnings           §5.2 行 5：警告不阻断
 *   ↓
 * SnapshotBuilder.build  十子系统快照
 * ```
 */
class ThemeCompiler {

    /**
     * 编译主题包模型。
     *
     * @param pack 已解析的主题包（[DtcgParser.parse] 产出）
     * @param activeModifiers 运行时激活的修饰符（如 ["dark"]；互斥单选由调用方按系统上下文保证）
     */
    fun compile(pack: ThemePack, activeModifiers: List<String> = emptyList()): CompileResult {
        // 1. 结构校验（合并前）
        ThemeValidator.validatePack(pack)

        // 2. 合并（规则 2/3）
        val merged = SetMerger.merge(pack, activeModifiers)

        // 3. 词汇表类型一致
        ThemeValidator.validateVocabularyTypes(merged)

        // 4. 别名拓扑解析（规则 1）
        val resolved = AliasResolver(merged).resolveAll()

        // 5. 生效表 = fallback 基座 + 主题包编译值（词汇表缺项回填）
        val effective = LinkedHashMap<String, TokenValue>(FallbackTheme.rawValues)
        effective.putAll(resolved)

        // 6. 玻璃 opaque 校验（生效时）
        ThemeValidator.validateGlassConsumers(effective, pack)

        // 7. WCAG 警告（不阻断）
        val warnings = ThemeValidator.wcagWarnings(effective, pack)

        // 8. 快照装配
        val meta = ThemeMeta(
            packName = pack.manifest.name,
            packVersion = pack.manifest.version,
            engineVersionRange = pack.manifest.engineVersion,
            activeModifiers = activeModifiers,
        )
        val snapshot = SnapshotBuilder.build(effective, meta)
        return CompileResult(snapshot, warnings)
    }

    /** 便捷入口：JSON 字符串 → 编译结果（解析失败按 §5.2 行 1/体积行拒绝） */
    fun compileJson(json: String, activeModifiers: List<String> = emptyList()): CompileResult =
        compile(DtcgParser().parse(json), activeModifiers)

    companion object {
        /** 引擎版本（与 [MiniMeEngine.VERSION] 同源，供编译期引用） */
        val ENGINE_VERSION: String = MiniMeEngine.VERSION
    }
}
