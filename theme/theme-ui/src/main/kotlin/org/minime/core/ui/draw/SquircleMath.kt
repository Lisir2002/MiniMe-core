package org.minime.core.ui.draw

import java.lang.Math.PI
import java.lang.Math.signum
import kotlin.math.min

/**
 * Squircle（连续圆角）路径命令（纯数据，零 Compose 依赖——可在 JVM 单测完整验证）。
 *
 * 由 [SquircleMath.outline] 产出，由绘制层（`SquirclePath`）逐条执行；
 * 数学与绘制分离：本文件不 import 任何 `androidx.*`，桌面 JVM 即可跑性质测试。
 */
sealed interface SquircleCmd {
    data class MoveTo(val x: Float, val y: Float) : SquircleCmd
    data class LineTo(val x: Float, val y: Float) : SquircleCmd
    data class CubicTo(
        val c1x: Float, val c1y: Float,
        val c2x: Float, val c2y: Float,
        val x: Float, val y: Float,
    ) : SquircleCmd
}

/**
 * Squircle（连续圆角）纯数学（白皮书 §3.6 妥协 2）。
 *
 * > Compose 原生只有几何圆角 → 需自实现 `ContinuousCornerShape`（超椭圆/贝塞尔近似）。
 * > 这是「iOS 感」的灵魂细节，**不可用几何圆角糊弄**。
 *
 * ## 模型：lamé 超椭圆
 *
 * 每个 90° 角用一段四分之一 lamé 超椭圆替代四分之一圆弧。以角顶点为原点、
 * 两边方向为局部轴时，超椭圆中心在 `(r, r)`，参数方程（`p = 2/n`）：
 *
 * ```
 * P(t) = (r + r·sgn(cos t)·|cos t|^p, r + r·sgn(sin t)·|sin t|^p)   t ∈ [π, 3π/2]
 * ```
 *
 * 性质（单测逐条断言，见 `SquircleMathTest`）：
 * - **端点即切点**：`t = π` 落在 v 边切点 `(0, r)`，`t = 3π/2` 落在 u 边切点 `(r, 0)`；
 * - **G1 连续**：端点切线方向与直边平行——与直边无可见接缝；
 * - **n = 2 退化为标准圆**（lamé 超椭圆即圆）——A/B 对照的 B 侧基准；
 * - **n > 2 更「方」**：中点向角顶点收拢（n=5 时中点距顶点 ≈ 0.13r，几何圆为 ≈ 0.29r），
 *   角部更饱满——这就是 iOS 连续圆角与几何圆角的肉眼差别。
 *
 * ## 已知取舍
 *
 * 端点处曲率不连续（G2 断）——lamé 近似的固有性质，肉眼不可见。
 * 若未来需要严格 G2，可换曲率连续样条（两段三次贝塞尔方案），
 * 但数学复杂度与出错面显著上升，当前不值得（偏离记录见 CHANGELOG R3）。
 *
 * ## 采样与曲线拟合
 *
 * 每角在参数域均匀采 `segments + 1` 点，切线用相邻采样点中心差分估计，
 * 再按 Hermite 形式转三次贝塞尔控制点。中心差分对任意 `n ≥ 2` 都稳定
 * （端点处参数速度趋于 0 但方向恒定，差分估计的是方向不是速率）。
 */
object SquircleMath {

    /** iOS 观感近似指数（lamé n）；2 = 几何圆，5 ≈ iOS 连续圆角 */
    const val DEFAULT_N = 5f

    /** 每角采样段数（4 段 × 4 角 = 16 段三次贝塞尔，视觉无损） */
    const val DEFAULT_SEGMENTS = 4

    /** lamé 指数下限（n=2 即圆；更小无意义） */
    const val MIN_N = 2f

    /**
     * 生成 squircle 矩形轮廓命令流（顺时针，从顶边起点出发）。
     *
     * @param width 宽（px，须 > 0）
     * @param height 高（px，须 > 0）
     * @param radius 圆角半径（px）；自动收敛到 `min(width, height) / 2`（胶囊形）
     * @param n lamé 指数；[MIN_N] 起，越大越方
     * @param segments 每角采样段数（≥ 1）
     * @return 命令流：[SquircleCmd.MoveTo] + 边（LineTo）+ 四角（CubicTo 序列）
     */
    fun outline(
        width: Float,
        height: Float,
        radius: Float,
        n: Float = DEFAULT_N,
        segments: Int = DEFAULT_SEGMENTS,
    ): List<SquircleCmd> {
        require(width > 0f && height > 0f) { "squircle 尺寸须为正: ${width}x$height" }
        require(n >= MIN_N) { "lamé 指数 n 须 ≥ $MIN_N（2 = 几何圆），实际 $n" }
        require(segments >= 1) { "每角采样段数须 ≥ 1，实际 $segments" }

        val r = radius.coerceIn(0f, min(width, height) / 2f)
        if (r <= 0f) {
            // 零圆角 = 普通矩形（一个 MoveTo + 四条 LineTo）
            return listOf(
                SquircleCmd.MoveTo(0f, 0f),
                SquircleCmd.LineTo(width, 0f),
                SquircleCmd.LineTo(width, height),
                SquircleCmd.LineTo(0f, height),
                SquircleCmd.LineTo(0f, 0f),
            )
        }

        val cmds = ArrayList<SquircleCmd>(2 + 4 * segments + 4)
        // 顶边起点（TL 角出口 → TR 角入口）
        cmds += SquircleCmd.MoveTo(r, 0f)
        cmds += SquircleCmd.LineTo(width - r, 0f)
        appendCorner(cmds, width, 0f, -1f, 0f, 0f, 1f, r, n, segments)    // TR：入边向左、出边向下
        cmds += SquircleCmd.LineTo(width, height - r)
        appendCorner(cmds, width, height, 0f, -1f, -1f, 0f, r, n, segments) // BR：入边向上、出边向左
        cmds += SquircleCmd.LineTo(r, height)
        appendCorner(cmds, 0f, height, 1f, 0f, 0f, -1f, r, n, segments)   // BL：入边向右、出边向上
        cmds += SquircleCmd.LineTo(0f, r)
        appendCorner(cmds, 0f, 0f, 0f, 1f, 1f, 0f, r, n, segments)        // TL：入边向下、出边向右
        cmds += SquircleCmd.LineTo(r, 0f)
        return cmds
    }

    /**
     * 追加一个角的曲线序列（顺时针：从 u 边切点 → v 边切点）。
     *
     * @param vx,vy 角顶点（世界坐标）
     * @param ux,uy 入边方向（顶点沿入边指向形状内部的单位向量）
     * @param sx,sy 出边方向（同上）
     * @param r 圆角半径（已收敛）
     */
    private fun appendCorner(
        out: MutableList<SquircleCmd>,
        vx: Float, vy: Float,
        ux: Float, uy: Float,
        sx: Float, sy: Float,
        r: Float, n: Float, segments: Int,
    ) {
        // 1) 局部坐标采样（x 沿入边 u、y 沿出边 s，超椭圆中心 (r, r)）：
        //    参数 t 从 3π/2 降到 π，即从 u 边切点 (r, 0) 走到 s 边切点 (0, r)。
        //    参数域用 double 计算——float 的 sin(π)≈1e-7，|sin|^p 放大到 1e-3 量级，
        //    会让端点偏离切点（R3 实测教训）。
        val p = 2.0 / n
        val step = (Math.PI / 2.0) / segments
        val lx = FloatArray(segments + 1)
        val ly = FloatArray(segments + 1)
        for (i in 0..segments) {
            val t = 3.0 * Math.PI / 2.0 - i * step
            val c = snapZero(Math.cos(t))
            val s = snapZero(Math.sin(t))
            lx[i] = (r + r * Math.signum(c) * powAbs(c, p)).toFloat()
            ly[i] = (r + r * Math.signum(s) * powAbs(s, p)).toFloat()
        }

        // 2) 局部 → 世界：P_world = V + x·u + y·s
        val wx = FloatArray(segments + 1)
        val wy = FloatArray(segments + 1)
        for (i in 0..segments) {
            wx[i] = vx + lx[i] * ux + ly[i] * sx
            wy[i] = vy + lx[i] * uy + ly[i] * sy
        }

        // 3) Hermite → 三次贝塞尔（切线 = 相邻世界坐标点的中心差分 / 参数步长）
        val stepF = step.toFloat()
        for (i in 0 until segments) {
            val (tx0, ty0) = tangentAt(wx, wy, i, stepF)
            val (tx1, ty1) = tangentAt(wx, wy, i + 1, stepF)
            out += SquircleCmd.CubicTo(
                wx[i] + tx0 * stepF / 3f, wy[i] + ty0 * stepF / 3f,
                wx[i + 1] - tx1 * stepF / 3f, wy[i + 1] - ty1 * stepF / 3f,
                wx[i + 1], wy[i + 1],
            )
        }
    }

    /** 采样点 i 处的 dP/dt（中心差分；端点单侧） */
    private fun tangentAt(px: FloatArray, py: FloatArray, i: Int, step: Float): Pair<Float, Float> {
        val last = px.size - 1
        if (last < 1) return 0f to 0f
        val (ix, iy) = when (i) {
            0 -> 1 to 0
            last -> last to last - 1
            else -> (i + 1) to (i - 1)
        }
        val div = when (i) {
            0, last -> step
            else -> 2f * step
        }
        return ((px[ix] - px[iy]) / div) to ((py[ix] - py[iy]) / div)
    }

    /** |x|^p（double 精度；x=0 且 p<1 时按 0 处理——端点参数速度趋于 0 是正确行为） */
    private fun powAbs(x: Double, p: Double): Double {
        val a = Math.abs(x)
        return if (a == 0.0) 0.0 else pow(a, p)
    }

    private fun pow(a: Double, p: Double): Double {
        val v = Math.pow(a, p)
        return if (v.isNaN()) 0.0 else v
    }

    /** 吸附浮点零噪声（double 的 sin(π)≈1e-16，取幂前清零，避免端点漂移） */
    private fun snapZero(v: Double): Double = if (Math.abs(v) < 1e-12) 0.0 else v
}
