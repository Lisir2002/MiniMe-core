package org.minime.core.motion

/**
 * 动效模式类别（白皮书 §6.4 五大类）。
 *
 * R5 只落 MVP（16 模式）；二期 10 模式在 [MotionCatalog.ALL] 登记不实现——
 * 目录是目标态全量契约，实现进度由 [MotionPattern.mvp] 标注。
 */
enum class MotionCategory(val title: String) {
    TRANSITION("过渡"),
    FEEDBACK("反馈"),
    LOADING("加载与进度"),
    GESTURE("手势驱动"),
    MORPH("状态变形"),
}

/**
 * 动效槽位（§6.3 六槽 + 无槽位）。
 *
 * 槽位命名与 M3 Expressive `MotionScheme`（speed × type）同构——主题包可互相转换。
 * [NONE] 仅用于无动画模式（触觉同帧 `haptic.sync`）。
 */
enum class MotionSlot(val path: String) {
    SPATIAL_FAST("motion.spatial.fast"),
    SPATIAL_DEFAULT("motion.spatial.default"),
    SPATIAL_SLOW("motion.spatial.slow"),
    EFFECTS_FAST("motion.effects.fast"),
    EFFECTS_DEFAULT("motion.effects.default"),
    EFFECTS_SLOW("motion.effects.slow"),
    NONE(""),
    ;

    /** 是否承载动画（false = 无动画模式，如触觉） */
    val isAnimated: Boolean get() = this != NONE
}

/** 时长档位令牌（§6.3 duration 七档；值在主题包数据面，规格表只引用令牌名——铁律 8） */
enum class DurationToken(val path: String) {
    INSTANT("motion.duration.instant"),
    FAST("motion.duration.fast"),
    DEFAULT("motion.duration.default"),
    SLOW("motion.duration.slow"),
    MAX("motion.duration.max"),
    SPINNER("motion.duration.spinner"),
    SHIMMER("motion.duration.shimmer"),
    ;

    /** 循环动画周期档（spinner/shimmer）——周期非有限时长，豁免铁律 1 的 ≤400ms 硬上限 */
    val isCyclic: Boolean get() = this == SPINNER || this == SHIMMER
}

/**
 * reducedMotion 降级行为（§6.4.6 铁律 7 降级表）。
 *
 * - [SHORT_TWEEN_HALF]：spatial.*（位移/缩放/旋转）→ 短 tween 无回弹，时长减半；
 * - [STATIC]：spinner → 静态/呼吸淡入；skeleton.shimmer → 静态灰底；
 * - [DIRECT]：视差/容器变换 → 直切（无动效）；
 * - [NONE]：不降级（触觉等非动画模式）。
 */
enum class ReducedMotion {
    SHORT_TWEEN_HALF,
    STATIC,
    DIRECT,
    NONE,
}

/** 铁律 1 时长上限类目：反馈 ≤200ms、过渡 ≤300ms、任何动效 ≤400ms */
enum class DurationCap(val maxMs: Int) {
    FEEDBACK(200),
    TRANSITION(300),
    ANY(400),
}

/**
 * 动效模式规格（§6.4 目录行）。
 *
 * 规格三要素（机器可验，[MotionCatalogTest]）：
 * 1. **槽位** [slot]——「无主」模式禁止合入（NONE 仅触觉模式）；
 * 2. **时长覆写令牌** [durationToken]——铁律 9 双标注的另一半（与槽位并注）；
 * 3. **降级行为** [reducedMotion]——铁律 7 降级表逐模式落项。
 *
 * 铁律 8（规格即令牌）：规格数值不复制进本表——参数值全在令牌数据面，
 * 本表只持令牌名（[paramTokens]）与标称时长 [specDurationMs]（须与
 * [durationToken] 档位值一致，单测双锚：规格表 ↔ 默认主题包）。
 */
data class MotionPattern(
    val id: String,
    val category: MotionCategory,
    val purpose: String,
    val slot: MotionSlot,
    val durationToken: DurationToken,
    /** 规格标称时长（ms，锚定 durationToken 档位值；降级态按铁律 7 减半） */
    val specDurationMs: Int,
    val durationCap: DurationCap,
    val reducedMotion: ReducedMotion,
    /** 参数令牌路径（interaction.* 等；须在词汇表内——铁律 8） */
    val paramTokens: List<String>,
    /**
     * 弹簧阻尼比覆写令牌（白皮书 §6.4.2「低阻尼 spring」）。
     *
     * 槽位 spring 的 damping 是该槽位的**默认**阻尼；个别模式需要不同阻尼
     * （如 `action.error` 的 dampingRatio ≈ 0.4 自然衰减），必须在目录里**显式标注**
     * 覆写令牌名（铁律 9 双标注的第三项），由消费方解析令牌取真实值——
     * 禁止消费方按模式 id 特判（那会让目录失去唯一真源地位）。
     */
    val dampingOverrideToken: String? = null,
    /** 是否 R5 MVP（false = 二期登记项） */
    val mvp: Boolean,
    val consumers: String,
)

/**
 * 动效模式目录（§6.4 目标态全量 + R5 实现进度）。
 *
 * **目录即契约**：与组件目录（§7.2）同纪律——所有动效从本目录取规格，
 * 目录外新增动效 = 白皮书变更；每个模式落到六槽令牌之一，「无主」模式禁止合入。
 *
 * 人格（ios/standard/custom，§6.3）：RC1 决策——**不建人格实体**，
 * 人格是消费方对槽位的组合选择（避免与六槽 spring 双真源）；本目录按槽位声明规格。
 */
object MotionCatalog {

    /** 目标态全量（5 类 26 个） */
    val ALL: List<MotionPattern> = listOf(
        // ---------- 过渡动效（Transitions）MVP 7/8 ----------
        MotionPattern(
            id = "overlay.enter/exit",
            category = MotionCategory.TRANSITION,
            purpose = "浮层的来去（缩放显空间层次）",
            slot = MotionSlot.SPATIAL_DEFAULT,
            durationToken = DurationToken.DEFAULT,
            specDurationMs = 250,
            durationCap = DurationCap.TRANSITION,
            reducedMotion = ReducedMotion.SHORT_TWEEN_HALF,
            paramTokens = listOf("interaction.overlayScale"),
            mvp = true,
            consumers = "弹窗族（R6）",
        ),
        MotionPattern(
            id = "sheet.present/dismiss",
            category = MotionCategory.TRANSITION,
            purpose = "Sheet 进出（上滑 + grabber 跟手）",
            slot = MotionSlot.SPATIAL_DEFAULT,
            durationToken = DurationToken.SLOW,
            specDurationMs = 300,
            durationCap = DurationCap.TRANSITION,
            reducedMotion = ReducedMotion.SHORT_TWEEN_HALF,
            paramTokens = emptyList(),
            mvp = true,
            consumers = "ModalSheet/ActionSheet（R7）",
        ),
        MotionPattern(
            id = "page.push/pop",
            category = MotionCategory.TRANSITION,
            purpose = "层级深化的方向感（右滑入 + 背景视差）",
            slot = MotionSlot.SPATIAL_DEFAULT,
            durationToken = DurationToken.SLOW,
            specDurationMs = 300,
            durationCap = DurationCap.TRANSITION,
            reducedMotion = ReducedMotion.SHORT_TWEEN_HALF,
            paramTokens = listOf("interaction.parallaxFactor"),
            mvp = true,
            consumers = "Scaffold 导航（R8）+ 画廊侧抽屉（R5 P1 收敛，Q3 裁定）",
        ),
        MotionPattern(
            id = "menu.expand/collapse",
            category = MotionCategory.TRANSITION,
            purpose = "菜单展开方向（逐项淡入）",
            slot = MotionSlot.SPATIAL_FAST,
            durationToken = DurationToken.FAST,
            specDurationMs = 150,
            durationCap = DurationCap.TRANSITION,
            reducedMotion = ReducedMotion.SHORT_TWEEN_HALF,
            paramTokens = listOf("interaction.stagger.menu"),
            mvp = true,
            consumers = "DropdownMenu/Popover（R7）",
        ),
        MotionPattern(
            id = "title.collapse",
            category = MotionCategory.TRANSITION,
            purpose = "Large Title → inline（随滚动插值）",
            slot = MotionSlot.SPATIAL_DEFAULT,
            durationToken = DurationToken.DEFAULT,
            specDurationMs = 250,
            durationCap = DurationCap.TRANSITION,
            reducedMotion = ReducedMotion.DIRECT,
            paramTokens = emptyList(),
            mvp = true,
            consumers = "Scaffold（R8）",
        ),
        MotionPattern(
            id = "banner.slideIn/out",
            category = MotionCategory.TRANSITION,
            purpose = "提示浮出与退场",
            slot = MotionSlot.SPATIAL_DEFAULT,
            durationToken = DurationToken.DEFAULT,
            specDurationMs = 250,
            durationCap = DurationCap.TRANSITION,
            reducedMotion = ReducedMotion.SHORT_TWEEN_HALF,
            paramTokens = emptyList(),
            mvp = true,
            consumers = "Banner/Toast（R8）",
        ),
        MotionPattern(
            id = "list.itemAppear",
            category = MotionCategory.TRANSITION,
            purpose = "数据刷新的来处（淡入 + 上移）",
            slot = MotionSlot.SPATIAL_FAST,
            durationToken = DurationToken.FAST,
            specDurationMs = 150,
            durationCap = DurationCap.TRANSITION,
            reducedMotion = ReducedMotion.SHORT_TWEEN_HALF,
            paramTokens = listOf("interaction.itemEnterOffset", "interaction.stagger.list", "interaction.maxStaggerItems"),
            mvp = true,
            consumers = "列表族（R9）",
        ),
        MotionPattern(
            id = "list.reorder",
            category = MotionCategory.TRANSITION,
            purpose = "排序/筛选位置连续性",
            slot = MotionSlot.SPATIAL_DEFAULT,
            durationToken = DurationToken.DEFAULT,
            specDurationMs = 250,
            durationCap = DurationCap.ANY,
            reducedMotion = ReducedMotion.SHORT_TWEEN_HALF,
            paramTokens = emptyList(),
            mvp = false,
            consumers = "列表族（二期）",
        ),

        // ---------- 反馈动效（Feedback）MVP 5/7 ----------
        MotionPattern(
            id = "press.scale",
            category = MotionCategory.FEEDBACK,
            purpose = "按压即确认（铁律 9 双标注原型：槽位 spatial.fast + 时长覆写 instant）",
            slot = MotionSlot.SPATIAL_FAST,
            durationToken = DurationToken.INSTANT,
            specDurationMs = 100,
            durationCap = DurationCap.FEEDBACK,
            reducedMotion = ReducedMotion.SHORT_TWEEN_HALF,
            paramTokens = listOf("interaction.pressScale"),
            mvp = true,
            consumers = "全部可点组件（R6–R9）",
        ),
        MotionPattern(
            id = "action.success",
            category = MotionCategory.FEEDBACK,
            purpose = "成功确认（对勾绘制 + 触觉轻震）",
            slot = MotionSlot.EFFECTS_DEFAULT,
            durationToken = DurationToken.SLOW,
            specDurationMs = 300,
            durationCap = DurationCap.FEEDBACK,
            reducedMotion = ReducedMotion.SHORT_TWEEN_HALF,
            paramTokens = emptyList(),
            mvp = true,
            consumers = "Dialog/InlineBanner（R6/R8）",
        ),
        MotionPattern(
            id = "action.error",
            category = MotionCategory.FEEDBACK,
            purpose = "错误警示（低阻尼 spring 自然衰减，铁律 6 禁关键帧）",
            slot = MotionSlot.SPATIAL_FAST,
            durationToken = DurationToken.SLOW,
            specDurationMs = 300,
            durationCap = DurationCap.FEEDBACK,
            reducedMotion = ReducedMotion.SHORT_TWEEN_HALF,
            paramTokens = listOf("interaction.shakeDistance", "interaction.shakeCycles", "interaction.shakeDamping"),
            dampingOverrideToken = "interaction.shakeDamping",
            mvp = true,
            consumers = "表单校验（R6+）",
        ),
        MotionPattern(
            id = "ripple.bounded",
            category = MotionCategory.FEEDBACK,
            purpose = "点按落点波纹（M3 兼容遗产品）",
            slot = MotionSlot.EFFECTS_DEFAULT,
            durationToken = DurationToken.SLOW,
            specDurationMs = 300,
            durationCap = DurationCap.FEEDBACK,
            reducedMotion = ReducedMotion.SHORT_TWEEN_HALF,
            paramTokens = emptyList(),
            mvp = true,
            consumers = "兼容层（R6）",
        ),
        MotionPattern(
            id = "haptic.sync",
            category = MotionCategory.FEEDBACK,
            purpose = "动效与触觉同帧（轻/中/重三档，禁止动效结束才震）",
            slot = MotionSlot.NONE,
            durationToken = DurationToken.INSTANT,
            specDurationMs = 0,
            durationCap = DurationCap.FEEDBACK,
            reducedMotion = ReducedMotion.NONE,
            paramTokens = listOf("interaction.haptic.light", "interaction.haptic.medium", "interaction.haptic.heavy"),
            mvp = true,
            consumers = "全部交互（R6+）",
        ),
        MotionPattern(
            id = "selection.slide",
            category = MotionCategory.FEEDBACK,
            purpose = "选中态迁移（滑块/底色过渡；白皮书 ~200ms 向档位取近保 cap）",
            slot = MotionSlot.SPATIAL_DEFAULT,
            durationToken = DurationToken.FAST,
            specDurationMs = 150,
            durationCap = DurationCap.FEEDBACK,
            reducedMotion = ReducedMotion.SHORT_TWEEN_HALF,
            paramTokens = emptyList(),
            mvp = false,
            consumers = "Segmented/Switch/Slider（二期）",
        ),
        MotionPattern(
            id = "toggle.flip",
            category = MotionCategory.FEEDBACK,
            purpose = "开关状态翻转（滑块 51×31；白皮书 ~200ms 向档位取近保 cap）",
            slot = MotionSlot.SPATIAL_FAST,
            durationToken = DurationToken.FAST,
            specDurationMs = 150,
            durationCap = DurationCap.FEEDBACK,
            reducedMotion = ReducedMotion.SHORT_TWEEN_HALF,
            paramTokens = emptyList(),
            mvp = false,
            consumers = "Switch（二期）",
        ),

        // ---------- 加载与进度（Loading）MVP 1/4 ----------
        MotionPattern(
            id = "spinner.indeterminate",
            category = MotionCategory.LOADING,
            purpose = "未知进度等待（匀速旋转——铁律 2 唯一合法 linear）",
            slot = MotionSlot.EFFECTS_DEFAULT,
            durationToken = DurationToken.SPINNER,
            specDurationMs = 1000,
            durationCap = DurationCap.ANY,
            reducedMotion = ReducedMotion.STATIC,
            paramTokens = emptyList(),
            mvp = true,
            consumers = "LoadingOverlay/ProgressIndicator（R6）",
        ),
        MotionPattern(
            id = "progress.determinate",
            category = MotionCategory.LOADING,
            purpose = "已知进度（平滑跟随，禁跳变）",
            slot = MotionSlot.EFFECTS_DEFAULT,
            durationToken = DurationToken.DEFAULT,
            specDurationMs = 250,
            durationCap = DurationCap.ANY,
            reducedMotion = ReducedMotion.DIRECT,
            paramTokens = emptyList(),
            mvp = false,
            consumers = "ProgressIndicator（二期）",
        ),
        MotionPattern(
            id = "skeleton.shimmer",
            category = MotionCategory.LOADING,
            purpose = "骨架占位（微光扫过）",
            slot = MotionSlot.EFFECTS_SLOW,
            durationToken = DurationToken.SHIMMER,
            specDurationMs = 1200,
            durationCap = DurationCap.ANY,
            reducedMotion = ReducedMotion.STATIC,
            paramTokens = emptyList(),
            mvp = false,
            consumers = "列表占位（二期）",
        ),
        MotionPattern(
            id = "pullToRefresh.spin",
            category = MotionCategory.LOADING,
            purpose = "下拉刷新（下拉旋转 + 释放回弹）",
            slot = MotionSlot.SPATIAL_DEFAULT,
            durationToken = DurationToken.DEFAULT,
            specDurationMs = 250,
            durationCap = DurationCap.ANY,
            reducedMotion = ReducedMotion.SHORT_TWEEN_HALF,
            paramTokens = emptyList(),
            mvp = false,
            consumers = "刷新控件（二期）",
        ),

        // ---------- 手势驱动（Gesture-driven）MVP 3/4 ----------
        MotionPattern(
            id = "drag.follow",
            category = MotionCategory.GESTURE,
            purpose = "手指 1:1 跟手 + 释放弹簧落定（初速度交接）",
            slot = MotionSlot.SPATIAL_DEFAULT,
            durationToken = DurationToken.DEFAULT,
            specDurationMs = 250,
            durationCap = DurationCap.ANY,
            reducedMotion = ReducedMotion.SHORT_TWEEN_HALF,
            paramTokens = emptyList(),
            mvp = true,
            consumers = "Sheet/SwipeActions（R7）",
        ),
        MotionPattern(
            id = "swipe.dismiss",
            category = MotionCategory.GESTURE,
            purpose = "滑动关闭（快滑 5% 阈值 / 正常拖拽 50% 阈值）",
            slot = MotionSlot.SPATIAL_DEFAULT,
            durationToken = DurationToken.DEFAULT,
            specDurationMs = 250,
            durationCap = DurationCap.ANY,
            reducedMotion = ReducedMotion.SHORT_TWEEN_HALF,
            paramTokens = listOf("interaction.flickVelocity", "interaction.flickDismissRatio", "interaction.dragDismissRatio"),
            mvp = true,
            consumers = "Sheet/列表行（R7/R9）",
        ),
        MotionPattern(
            id = "overscroll.rubberBand",
            category = MotionCategory.GESTURE,
            purpose = "边缘橡皮筋（阻尼系数）",
            slot = MotionSlot.SPATIAL_DEFAULT,
            durationToken = DurationToken.FAST,
            specDurationMs = 150,
            durationCap = DurationCap.ANY,
            reducedMotion = ReducedMotion.DIRECT,
            paramTokens = listOf("interaction.rubberBandDamping"),
            mvp = true,
            consumers = "滚动容器（R9）",
        ),
        MotionPattern(
            id = "scroll.parallax",
            category = MotionCategory.GESTURE,
            purpose = "滚动深度线索（header 位移）",
            slot = MotionSlot.SPATIAL_DEFAULT,
            durationToken = DurationToken.DEFAULT,
            specDurationMs = 250,
            durationCap = DurationCap.ANY,
            reducedMotion = ReducedMotion.DIRECT,
            paramTokens = listOf("interaction.parallaxFactor"),
            mvp = false,
            consumers = "Scaffold（二期）",
        ),

        // ---------- 状态变形（Morph）MVP 0/3 ----------
        MotionPattern(
            id = "expand.collapse",
            category = MotionCategory.MORPH,
            purpose = "详情展开/收起（高度约束动画——铁律 3 唯一例外的标准路径）",
            slot = MotionSlot.SPATIAL_DEFAULT,
            durationToken = DurationToken.DEFAULT,
            specDurationMs = 250,
            durationCap = DurationCap.ANY,
            reducedMotion = ReducedMotion.SHORT_TWEEN_HALF,
            paramTokens = emptyList(),
            mvp = false,
            consumers = "ExpandableRow（二期）",
        ),
        MotionPattern(
            id = "fab.morph",
            category = MotionCategory.MORPH,
            purpose = "FAB → Sheet 容器变换",
            slot = MotionSlot.SPATIAL_DEFAULT,
            durationToken = DurationToken.SLOW,
            specDurationMs = 300,
            durationCap = DurationCap.ANY,
            reducedMotion = ReducedMotion.DIRECT,
            paramTokens = emptyList(),
            mvp = false,
            consumers = "FAB（二期）",
        ),
        MotionPattern(
            id = "badge.pop",
            category = MotionCategory.MORPH,
            purpose = "角标出现（scale 0→1.1→1）",
            slot = MotionSlot.SPATIAL_FAST,
            durationToken = DurationToken.FAST,
            specDurationMs = 150,
            durationCap = DurationCap.ANY,
            reducedMotion = ReducedMotion.SHORT_TWEEN_HALF,
            paramTokens = listOf("interaction.badgePopScale"),
            mvp = false,
            consumers = "Badge（二期）",
        ),
    )

    /** R5 MVP 模式（16 个：过渡 7 / 反馈 5 / 加载 1 / 手势 3） */
    val MVP: List<MotionPattern> = ALL.filter { it.mvp }

    /** 目标态模式总数（5 类 26 个，白皮书 §6.4 总览表锚点） */
    const val TOTAL: Int = 26

    /** MVP 模式数（分批实施计划 R5 行锚点） */
    const val MVP_TOTAL: Int = 16

    /**
     * 铁律 1 类目上限的**显式豁免登记**（id → 理由）。
     *
     * 白皮书 §6.4.2 的三个 ~300ms 反馈模式与铁律 1「反馈 ≤200ms」存在张力：
     * R5 不擅自改白皮书数值，以显式登记处理——新增模式想超类目 cap 必须登记理由，
     * 否则 [MotionCatalogTest] 的 cap 断言失败（例外可审计，不静默）。
     */
    val DURATION_CAP_EXEMPTIONS: Map<String, String> = mapOf(
        // 成功确认的仪式感时长（白皮书 §6.4.2 明示 ~300ms；与 press.scale 的即时反馈分工不同）
        "action.success" to "白皮书 §6.4.2 明示 ~300ms：成功确认仪式感时长，即时反馈由 press.scale 承担",
        // 铁律 6 低阻尼 spring 自然衰减时长由物理决定（stiffness/damping），归入铁律 1 第三档 ≤400ms
        "action.error" to "铁律 6 低阻尼 spring 物理衰减（dampingRatio≈0.4 两次往复），时长由物理决定，适用 ≤400ms 档",
        // M3 兼容遗产品的观感契约与 M3 ripple 标准时长对齐优先
        "ripple.bounded" to "M3 兼容遗产品：观感与 M3 ripple 标准时长（~300ms）对齐优先",
        // 无动画时长（触觉触发时机，与动效同帧）
        "haptic.sync" to "无动画时长（触觉同帧触发时机）",
    )

    /** 按 id 取模式（目录外 id = 白皮书未登记，调用方快速失败） */
    fun byId(id: String): MotionPattern =
        ALL.firstOrNull { it.id == id } ?: error("动效模式未登记于目录（§6.4）: $id")

    /** 分类 → 模式数（目标态全量） */
    fun countByCategory(category: MotionCategory): Int = ALL.count { it.category == category }

    /** 全部参数令牌去重（供词汇表覆盖断言与工坊展示） */
    fun allParamTokens(): Set<String> = ALL.flatMap { it.paramTokens }.toSet()

    /** 断言辅助：目录引用的全部令牌路径（槽位 + 时长 + 参数；铁律 8 要求 ⊆ 词汇表） */
    internal fun referencedTokenPaths(): Set<String> =
        ALL.flatMap { p ->
            buildList {
                if (p.slot.isAnimated) add(p.slot.path)
                add(p.durationToken.path)
                addAll(p.paramTokens)
                p.dampingOverrideToken?.let { add(it) }
            }
        }.toSet()
}
