package org.minime.gallery.sections

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import org.minime.core.compiler.ThemeCompiler
import org.minime.core.tokens.TokenVocabulary

/**
 * 令牌总览的机器核对（白皮书 §10.3）：
 *
 * 1. **双向集合相等**——展示清单与引擎词汇表 172 条一一对应：
 *    词汇表新增而清单未跟 → 缺项失败；清单多写 → 多项失败；
 * 2. **端到端可读**——用默认主题包真实编译出快照，清单每条路径都能取到值
 *    （证明「总览区每个值都从 snapshot.raw 读取」这条硬纪律 2 真实成立）；
 * 3. **十子系统全覆盖**——每个子系统都有展示条目。
 */
class TokenCoverageTest {

    private fun compileDefaultPack() = ThemeCompiler().compileJson(
        json = checkNotNull(
            javaClass.getResourceAsStream("/default-theme.json")?.use { it.bufferedReader().readText() }
        ) { "默认主题包应在 classpath（theme-engine 资源）" },
    )

    @Test
    fun `展示清单与词汇表 172 条双向集合相等`() {
        val specPaths = TokenOverviewSpec.ALL_PATHS
        val vocabPaths = TokenVocabulary.ALL.keys

        // 无重复
        assertEquals(specPaths.size, specPaths.distinct().size, "展示清单存在重复路径")

        // 缺项（词汇表有、清单无）
        val missing = vocabPaths - specPaths.toSet()
        assertTrue(missing.isEmpty(), "词汇表路径未展示: $missing")

        // 多项（清单有、词汇表无）
        val extra = specPaths.toSet() - vocabPaths
        assertTrue(extra.isEmpty(), "清单包含词汇表外路径: $extra")

        assertEquals(TokenVocabulary.SIZE, specPaths.size)
    }

    @Test
    fun `默认主题包端到端可读（每条路径都有值）`() {
        val snapshot = compileDefaultPack().snapshot
        val unreadable = TokenOverviewSpec.ALL_PATHS.filter { it !in snapshot.raw }
        assertTrue(unreadable.isEmpty(), "快照缺少清单路径: $unreadable")
    }

    @Test
    fun `十子系统全覆盖且计数正确`() {
        val snapshot = compileDefaultPack().snapshot
        val rawPaths = snapshot.raw.keys

        TokenOverviewSpec.ORDER.forEach { subsystem ->
            val spec = TokenOverviewSpec.PATHS.getValue(subsystem)
            assertTrue(spec.isNotEmpty(), "子系统 $subsystem 无展示条目")
            assertTrue(
                spec.all { it.startsWith("$subsystem.") },
                "子系统 $subsystem 混入外系统路径",
            )
            // 快照中该系统前缀下的标准路径，必须全部在清单内（防漏展）
            val rawStandard = rawPaths.filter {
                it.startsWith("$subsystem.") && TokenVocabulary.isKnown(it)
            }
            val missed = rawStandard - spec.toSet()
            assertTrue(missed.isEmpty(), "子系统 $subsystem 漏展: $missed")
        }
    }
}
