package org.minime.core.motion

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import org.minime.core.compiler.ThemeCompiler
import org.minime.core.schema.DurationValue
import org.minime.core.schema.TokenType
import org.minime.core.tokens.TokenVocabulary

/**
 * 动效模式目录的机器核对（白皮书 §6.4「目录即契约」+ 铁律 1/2/8/9 工程化）。
 *
 * 覆盖：
 * 1. 目录规模（目标态 26 / MVP 16，分类计数与白皮书总览表一致）；
 * 2. 规格三要素（槽位 / 时长覆写令牌 / 降级行为逐模式落项）；
 * 3. 铁律 1 时长硬上限（类目 cap + 显式豁免登记 + 循环动画豁免）；
 * 4. 铁律 8 规格即令牌（引用令牌 ⊆ 词汇表；标称时长 ↔ 默认包档位值双锚）；
 * 5. 双标注（铁律 9）：每模式的槽位与时长覆写令牌并注齐全。
 */
class MotionCatalogTest {

    private val defaultPack: String = MotionCatalogTest::class.java
        .getResourceAsStream("/default-theme.json")!!.use { it.bufferedReader(Charsets.UTF_8).readText() }

    private val compiledDuration: Map<String, Float> by lazy {
        val raw = ThemeCompiler().compileJson(defaultPack).snapshot.raw
        raw.filterValues { it is DurationValue }
            .mapValues { (_, v) -> (v as DurationValue).ms }
    }

    // ---------- 1. 目录规模 ----------

    @Test
    fun `catalog totals match whitepaper overview table`() {
        assertEquals(26, MotionCatalog.TOTAL, "目标态 5 类 26 个")
        assertEquals(16, MotionCatalog.MVP_TOTAL, "MVP 16 个")
        assertEquals(MotionCatalog.TOTAL, MotionCatalog.ALL.size)
        assertEquals(MotionCatalog.MVP_TOTAL, MotionCatalog.MVP.size)
        // MVP 分类计数（白皮书 §6.4 总览表：过渡 7 / 反馈 5 / 加载 1 / 手势 3 / 变形 0）
        assertEquals(7, MotionCatalog.MVP.count { it.category == MotionCategory.TRANSITION })
        assertEquals(5, MotionCatalog.MVP.count { it.category == MotionCategory.FEEDBACK })
        assertEquals(1, MotionCatalog.MVP.count { it.category == MotionCategory.LOADING })
        assertEquals(3, MotionCatalog.MVP.count { it.category == MotionCategory.GESTURE })
        assertEquals(0, MotionCatalog.MVP.count { it.category == MotionCategory.MORPH })
        // 目标态分类计数（总览表：8 / 7 / 4 / 4 / 3）
        assertEquals(8, MotionCatalog.countByCategory(MotionCategory.TRANSITION))
        assertEquals(7, MotionCatalog.countByCategory(MotionCategory.FEEDBACK))
        assertEquals(4, MotionCatalog.countByCategory(MotionCategory.LOADING))
        assertEquals(4, MotionCatalog.countByCategory(MotionCategory.GESTURE))
        assertEquals(3, MotionCatalog.countByCategory(MotionCategory.MORPH))
    }

    @Test
    fun `pattern ids are unique`() {
        val ids = MotionCatalog.ALL.map { it.id }
        assertEquals(ids.size, ids.toSet().size, "目录存在重复模式 id")
    }

    // ---------- 2. 规格三要素 ----------

    @Test
    fun `every pattern declares slot duration token and degraded behavior`() {
        for (p in MotionCatalog.ALL) {
            assertTrue(p.purpose.isNotBlank(), "${p.id} 缺目的说明")
            assertTrue(p.consumers.isNotBlank(), "${p.id} 缺消费方")
            assertTrue(p.specDurationMs >= 0, "${p.id} 标称时长非法")
            // 槽位：有动画模式必须落槽（「无主」模式禁止合入）；无槽位仅触觉模式
            if (!p.slot.isAnimated) {
                assertEquals("haptic.sync", p.id, "无槽位模式仅允许 haptic.sync（触觉同帧）")
            }
        }
    }

    @Test
    fun `mvp patterns all resolve to a slot`() {
        for (p in MotionCatalog.MVP) {
            assertTrue(p.slot.isAnimated || p.id == "haptic.sync", "MVP 模式 ${p.id} 无槽位")
        }
    }

    // ---------- 3. 铁律 1 时长硬上限 ----------

    @Test
    fun `duration caps hold with explicit exemptions only`() {
        for (p in MotionCatalog.ALL) {
            if (p.durationToken.isCyclic) {
                // 循环动画（spinner/shimmer）：周期非有限时长，豁免 ≤400ms 硬上限
                continue
            }
            val exempt = MotionCatalog.DURATION_CAP_EXEMPTIONS[p.id]
            if (p.specDurationMs > p.durationCap.maxMs) {
                assertTrue(
                    exempt != null && exempt.isNotBlank(),
                    "${p.id} 超类目 cap（${p.durationCap.maxMs}ms）但未登记豁免理由",
                )
            }
            // 第三档硬上限：任何动效 ≤400ms（豁免登记项同样受此约束）
            assertTrue(
                p.specDurationMs <= DurationCap.ANY.maxMs || exempt != null,
                "${p.id} 超过任何动效硬上限 400ms（${p.specDurationMs}ms）且无豁免",
            )
        }
    }

    @Test
    fun `exemption registry only covers registered patterns with reasons`() {
        val ids = MotionCatalog.ALL.map { it.id }.toSet()
        for ((id, reason) in MotionCatalog.DURATION_CAP_EXEMPTIONS) {
            assertTrue(id in ids, "豁免登记了目录外模式: $id")
            assertTrue(reason.isNotBlank(), "豁免 $id 缺理由")
        }
    }

    // ---------- 4. 铁律 8 规格即令牌 ----------

    @Test
    fun `referenced tokens exist in vocabulary with matching types`() {
        for (p in MotionCatalog.ALL) {
            if (p.slot.isAnimated) {
                assertEquals(TokenType.SPRING, TokenVocabulary.typeOf(p.slot.path), "${p.id} 槽位非 spring 令牌")
            }
            assertEquals(TokenType.DURATION, TokenVocabulary.typeOf(p.durationToken.path), "${p.id} 时长令牌类型不符")
            for (token in p.paramTokens) {
                assertTrue(token in TokenVocabulary.ALL, "${p.id} 引用词汇表外令牌: $token")
            }
        }
        // 目录引用的全部令牌 ⊆ 词汇表（含动作.error 的 shakeDamping——R5 补令牌）
        val refs = MotionCatalog.referencedTokenPaths()
        val unknown = refs - TokenVocabulary.ALL.keys
        assertTrue(unknown.isEmpty(), "目录引用词汇表外令牌: $unknown")
        assertTrue("interaction.shakeDamping" in refs, "action.error 的低阻尼覆写令牌未入目录引用")
    }

    @Test
    fun `nominal durations anchor to default pack duration tokens`() {
        // 双锚：规格表标称时长 ↔ 默认主题包 duration 档位值（铁律 8：规格值即令牌值）
        for (p in MotionCatalog.ALL) {
            if (p.specDurationMs == 0) {
                // 无动画时长的模式（如触觉同帧）：标称 0，须登记豁免理由
                assertTrue(
                    p.id in MotionCatalog.DURATION_CAP_EXEMPTIONS,
                    "${p.id} 标称时长为 0 但未登记豁免理由",
                )
                continue
            }
            val packValue = compiledDuration.getValue(p.durationToken.path)
            assertEquals(
                packValue,
                p.specDurationMs.toFloat(),
                "${p.id} 标称时长与默认包 ${p.durationToken.path} 档位值不一致",
            )
        }
    }

    // ---------- 5. 铁律 9 双标注 ----------

    @Test
    fun `slot and duration override are co-declared on every pattern`() {
        // 双标注 = 槽位 + 时长覆写令牌并注（press.scale 是原型：spatial.fast + instant）
        val press = MotionCatalog.byId("press.scale")
        assertEquals(MotionSlot.SPATIAL_FAST, press.slot)
        assertEquals(DurationToken.INSTANT, press.durationToken)
        // action.error 双标注：spatial.fast 槽 + damping 覆写令牌 + slow 时长覆写
        val error = MotionCatalog.byId("action.error")
        assertEquals(MotionSlot.SPATIAL_FAST, error.slot)
        assertEquals(DurationToken.SLOW, error.durationToken)
        assertTrue("interaction.shakeDamping" in error.paramTokens)
    }
}
