package org.minime.core.ui.runtime

import org.minime.core.tokens.ThemeSnapshot

/**
 * 子系统实例复用（§8.2「重组最小化」，换肤性能主路径）。
 *
 * 原理：十子系统均为 `@Immutable` data class，结构化相等即语义相等。
 * 换肤时逐子系统比较新旧快照，**相等的子系统直接复用旧实例**——
 * Compose 的 `CompositionLocal` 比对用 `==`，实例不变则其消费子树跳过重组。
 *
 * 典型收益：亮→暗切换时 motion / interaction / size / spacing 等子系统往往完全不变，
 * 这些子树零重组，只有 color / glass 相关的子树重绘。
 *
 * 规则：**只复用，不改造**。若新值确实不同则原样采用新实例（语义正确优先于性能）。
 */
object SnapshotReuser {

    /**
     * 用旧快照中相等的子系统实例替换新快照中的对应实例。
     *
     * @param previous 上一次生效的快照（首次换肤为 null）
     * @param next 本次编译出的新快照
     * @return 复用后的快照（与 [next] 语义完全等价，仅部分子系统实例引用自 [previous]）
     */
    fun reuse(previous: ThemeSnapshot?, next: ThemeSnapshot): ThemeSnapshot {
        if (previous == null) return next
        return next.copy(
            color = pick(previous.color, next.color),
            typography = pick(previous.typography, next.typography),
            shape = pick(previous.shape, next.shape),
            size = pick(previous.size, next.size),
            spacing = pick(previous.spacing, next.spacing),
            motion = pick(previous.motion, next.motion),
            interaction = pick(previous.interaction, next.interaction),
            elevation = pick(previous.elevation, next.elevation),
            opacity = pick(previous.opacity, next.opacity),
            glass = pick(previous.glass, next.glass),
            // raw 是调试/覆写查询面，不参与重组判定；保持新值
        )
    }

    /**
     * 统计有多少个子系统被复用（验收与诊断用：R2「换肤 ≤1 组合 pass」的辅助证据）。
     */
    fun reusedCount(previous: ThemeSnapshot?, next: ThemeSnapshot): Int {
        if (previous == null) return 0
        var n = 0
        if (previous.color == next.color) n++
        if (previous.typography == next.typography) n++
        if (previous.shape == next.shape) n++
        if (previous.size == next.size) n++
        if (previous.spacing == next.spacing) n++
        if (previous.motion == next.motion) n++
        if (previous.interaction == next.interaction) n++
        if (previous.elevation == next.elevation) n++
        if (previous.opacity == next.opacity) n++
        if (previous.glass == next.glass) n++
        return n
    }

    private inline fun <reified T : Any> pick(previous: T, next: T): T = if (previous == next) previous else next
}
