package org.minime.core.ui.gallery.pages

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertNotNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import org.minime.core.schema.TokenType
import org.minime.core.tokens.TokenVocabulary

/**
 * 工坊规格表机器核对（R4.2）。
 *
 * 防三类静默错误：分类路径漂出词汇表、分组漏项 / 重复归属、范围表失配。
 */
class WorkshopSpecTest {

    @Test
    fun `all category paths are in vocabulary`() {
        WorkshopCategory.entries.forEach { category ->
            WorkshopSpec.pathsOf(category).forEach { path ->
                assertTrue(path in TokenVocabulary.ALL, "路径不在词汇表: $path（${category.title}）")
            }
        }
    }

    @Test
    fun `categories are disjoint and total 116`() {
        val all = WorkshopCategory.entries.flatMap { WorkshopSpec.pathsOf(it) }
        assertEquals(116, all.size, "六分类路径总数（色板62+字阶14+圆角6+间距11+动效13+玻璃10）")
        assertEquals(all.size, all.toSet().size, "分类间不得有重复路径")
    }

    @Test
    fun `groups partition category paths exactly`() {
        WorkshopCategory.entries.forEach { category ->
            val groups = WorkshopSpec.groupsOf(category)
            val grouped = groups.flatMap { it.paths }
            val expected = WorkshopSpec.pathsOf(category)

            assertEquals(expected.size, grouped.size, "${category.title} 分组总数应等于分类路径数")
            assertEquals(expected.toSet(), grouped.toSet(), "${category.title} 分组不得漏项 / 多项")
            groups.forEach { group ->
                assertTrue(group.paths.isNotEmpty(), "空分组: ${group.title}")
                assertEquals(group.paths.size, group.paths.toSet().size, "组内重复: ${group.title}")
            }
        }
    }

    @Test
    fun `slider and stepper ranges cover the editors`() {
        // dimension / duration 标量路径必须有滑杆域
        listOf("shape.radius.card", "spacing.gutter", "motion.duration.default").forEach { path ->
            assertNotNull(WorkshopSpec.sliderRange(path), "滑杆域缺失: $path")
        }
        // number 标量路径（tintAlpha）必须有步进域
        assertNotNull(WorkshopSpec.numberRange("glass.regular.tintAlpha"))
        // 复合字段域自洽：spring / glass / typography 全部字段有域
        listOf("stiffness", "damping", "initialVelocity").forEach { f ->
            assertNotNull(WorkshopSpec.fieldRange(TokenType.SPRING, f), "spring 字段域缺失: $f")
        }
        listOf("blur", "saturation", "tintAlpha").forEach { f ->
            assertNotNull(WorkshopSpec.fieldRange(TokenType.GLASS, f), "glass 字段域缺失: $f")
        }
        listOf("fontSize", "lineHeight", "letterSpacing").forEach { f ->
            assertNotNull(WorkshopSpec.fieldRange(TokenType.TYPOGRAPHY, f), "typography 字段域缺失: $f")
        }
        // 步长恒正
        listOf("shape.radius.card", "motion.duration.default").forEach { path ->
            assertTrue(WorkshopSpec.sliderStep(path) > 0f, "步长非正: $path")
        }
    }

    @Test
    fun `color category splits into primitive and semantic`() {
        val groups = WorkshopSpec.groupsOf(WorkshopCategory.PALETTE)
        assertEquals(2, groups.size)
        assertTrue(groups[0].paths.all { ".primitive." in it })
        assertTrue(groups[1].paths.all { ".semantic." in it })
        assertFalse(groups[0].paths.any { it in groups[1].paths })
    }

    @Test
    fun `motion category splits into spatial effects duration`() {
        val titles = WorkshopSpec.groupsOf(WorkshopCategory.MOTION).map { it.title }
        assertEquals(listOf("spatial 位移弹簧", "effects 效果弹簧", "duration 时长"), titles)
    }

    @Test
    fun `glass category has switch plus four levels with tintAlpha`() {
        val groups = WorkshopSpec.groupsOf(WorkshopCategory.GLASS)
        assertEquals(5, groups.size)
        assertEquals(listOf("glass.enabled", "glass.reducedTransparency"), groups[0].paths)
        listOf("ultraThin", "thin", "regular", "thick").forEachIndexed { i, level ->
            val group = groups[i + 1]
            assertEquals(level, group.title)
            assertEquals(setOf("glass.$level", "glass.$level.tintAlpha"), group.paths.toSet())
        }
    }
}
