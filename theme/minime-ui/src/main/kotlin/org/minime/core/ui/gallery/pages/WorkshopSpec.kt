package org.minime.core.ui.gallery.pages

import org.minime.core.schema.TokenType
import org.minime.core.tokens.TokenVocabulary

/**
 * 工坊六大分类（charter §4「顶栏 + 分类 tab」）。
 *
 * 与 [TokenOverviewSpec.PATHS] 共用词汇表清单（六分类 = 六子系统的展示子集）；
 * size / interaction / elevation / opacity 不在本批工坊范围（组件目录 R6+ 再入）。
 */
enum class WorkshopCategory(val title: String, val subsystem: String) {
    PALETTE("色板", "color"),
    TYPOGRAPHY("字阶", "typography"),
    SHAPE("圆角", "shape"),
    SPACING("间距", "spacing"),
    MOTION("动效", "motion"),
    GLASS("玻璃", "glass"),
}

/** 分类内二级分组（令牌树的一级折叠单元；值为词汇表路径，保持总览清单顺序） */
data class WorkshopGroup(val title: String, val paths: List<String>)

/**
 * 工坊规格表（机器可测：分类覆盖 / 分组完备 / 范围表）。
 *
 * 与 [TokenOverviewSpec] 的关系：PATHS 是**展示**清单（含 opacity 梯子合并等展示态），
 * 工坊要的是**可覆写**全集——两者集合相等（TokenCoverageTest 保证），此处按词汇表
 * 类型与路径语义重新分组，不改动展示清单本身。
 */
object WorkshopSpec {

    /** 分类下的全部可覆写路径（词汇表 172 条中属该子系统者） */
    fun pathsOf(category: WorkshopCategory): List<String> =
        TokenOverviewSpec.PATHS.getValue(category.subsystem)

    /** 分类 → 二级分组（每个路径恰好归入一组；由 [WorkshopSpecTest] 机器断言） */
    fun groupsOf(category: WorkshopCategory): List<WorkshopGroup> {
        val paths = pathsOf(category)
        return when (category) {
            WorkshopCategory.PALETTE -> listOf(
                WorkshopGroup("primitive 原始层", paths.filter { ".primitive." in it }),
                WorkshopGroup("semantic 语义层", paths.filter { ".semantic." in it }),
            )

            WorkshopCategory.TYPOGRAPHY -> listOf(
                WorkshopGroup("字阶", paths.filter { it.startsWith("typography.") && ".fontFamily." !in it }),
                WorkshopGroup("字体族", paths.filter { ".fontFamily." in it }),
            )

            WorkshopCategory.SHAPE -> listOf(
                WorkshopGroup("圆角阶梯", paths),
            )

            WorkshopCategory.SPACING -> listOf(
                WorkshopGroup("数值阶梯", paths.filter { it.removePrefix("spacing.").all(Char::isDigit) }),
                WorkshopGroup("语义间距", paths.filterNot { it.removePrefix("spacing.").all(Char::isDigit) }),
            )

            WorkshopCategory.MOTION -> listOf(
                WorkshopGroup("spatial 位移弹簧", paths.filter { ".spatial." in it }),
                WorkshopGroup("effects 效果弹簧", paths.filter { ".effects." in it }),
                WorkshopGroup("duration 时长", paths.filter { ".duration." in it }),
            )

            WorkshopCategory.GLASS -> buildList {
                add(WorkshopGroup("开关", paths.filter { it == "glass.enabled" || it == "glass.reducedTransparency" }))
                listOf("ultraThin", "thin", "regular", "thick").forEach { level ->
                    add(
                        WorkshopGroup(
                            level,
                            paths.filter { it == "glass.$level" || it == "glass.$level.tintAlpha" },
                        ),
                    )
                }
            }
        }
    }

    // ---------- 编辑器范围表（滑杆 / 步进的取值域与步长） ----------

    /** 标量路径的滑杆域（dimension / duration）；null = 该路径不用滑杆 */
    fun sliderRange(path: String): ClosedFloatingPointRange<Float>? = when {
        path.startsWith("shape.radius.") -> 0f..48f
        path.startsWith("spacing.") -> 0f..48f
        path.startsWith("typography.") && path.endsWith(".fontSize") -> 8f..48f
        path.startsWith("typography.") && path.endsWith(".lineHeight") -> 0f..60f
        path.startsWith("typography.") && path.endsWith(".letterSpacing") -> -1f..3f
        typeOf(path) == TokenType.DURATION -> 0f..1500f
        else -> null
    }

    /** 标量路径的步进域（number）；null = 不用步进器 */
    fun numberRange(path: String): ClosedFloatingPointRange<Float>? = when {
        path.endsWith(".tintAlpha") -> 0f..1f
        else -> null
    }

    /** 标量滑杆步长（dimension / duration） */
    fun sliderStep(path: String): Float = when {
        path.startsWith("typography.") && path.endsWith(".letterSpacing") -> 0.1f
        typeOf(path) == TokenType.DURATION -> 10f
        else -> 1f
    }

    /** 标量步进步长（number） */
    fun numberStep(path: String): Float = 0.01f

    /** 复合型字段的滑杆域（spring / glass / typography 逐字段编辑） */
    fun fieldRange(type: TokenType, field: String): ClosedFloatingPointRange<Float>? = when (type) {
        TokenType.SPRING -> when (field) {
            "stiffness" -> 50f..2000f
            "damping" -> 0.1f..2f
            "initialVelocity" -> 0f..5f
            else -> null
        }

        TokenType.GLASS -> when (field) {
            "blur" -> 0f..60f
            "saturation" -> 0f..3f
            "tintAlpha" -> 0f..1f
            else -> null
        }

        TokenType.TYPOGRAPHY -> when (field) {
            "fontSize" -> 8f..48f
            "lineHeight" -> 0f..60f
            "letterSpacing" -> -1f..3f
            else -> null
        }

        else -> null
    }

    /** 复合型字段的滑杆步长 */
    fun fieldStep(type: TokenType, field: String): Float = when (type) {
        TokenType.SPRING -> when (field) {
            "stiffness" -> 10f
            "damping" -> 0.01f
            "initialVelocity" -> 0.1f
            else -> 1f
        }

        TokenType.GLASS -> when (field) {
            "blur" -> 1f
            "saturation" -> 0.05f
            "tintAlpha" -> 0.01f
            else -> 1f
        }

        TokenType.TYPOGRAPHY -> when (field) {
            "letterSpacing" -> 0.1f
            else -> 1f
        }

        else -> 1f
    }

    /** 词汇表类型查询（编辑器分发依据；与 TokensOverviewSection 同一真源） */
    fun typeOf(path: String): TokenType? = TokenVocabulary.typeOf(path)

    /** 路径的语义名（树与编辑器标题用） */
    fun displayName(path: String): String = path.substringAfterLast('.')
}

/** Float 紧凑格式（去尾零，最多两位）——工坊各页读数共用（避开总览页私有同名） */
internal fun Float.wsFmt(): String {
    val r = Math.round(this * 100f) / 100f
    return if (r == r.toInt().toFloat()) r.toInt().toString() else r.toString()
}
