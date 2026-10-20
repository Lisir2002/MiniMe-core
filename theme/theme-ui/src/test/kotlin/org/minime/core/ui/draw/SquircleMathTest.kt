package org.minime.core.ui.draw

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertThrows
import kotlin.math.abs
import kotlin.math.hypot
import kotlin.math.sqrt

/**
 * Squircle 数学性质测试（R3 验收：squircle vs 几何圆角 A/B 的数学依据）。
 *
 * 覆盖：端点即切点 / G1 连续 / n=2 退化圆 / n>2 更方 / 半径收敛 / 零半径矩形 /
 * 边界不越界 / 参数校验。全部为纯 JVM 测试（本文件零 androidx 依赖）。
 */
class SquircleMathTest {

    // ---------- 结构：端点即切点 ----------

    @Test
    fun `outline 起点为顶边左切点，四角首尾即切点`() {
        val w = 100f; val h = 80f; val r = 16f
        val cmds = SquircleMath.outline(w, h, r, n = 5f, segments = 4)

        // 第一个命令：MoveTo(r, 0)
        val first = cmds.first()
        assertTrue(first is SquircleCmd.MoveTo && first.x == r && first.y == 0f, "起点应为顶边左切点")

        // 收集所有 Cubic 序列，按 4 段一组切分
        val cubics = cmds.filterIsInstance<SquircleCmd.CubicTo>()
        assertEquals(16, cubics.size, "4 角 × 4 段")

        // 命令序列：MoveTo(r,0) → LineTo(w-r,0) → TR×4 → LineTo(w,h-r) → BR×4 → LineTo(r,h) → BL×4 → LineTo(0,h-r) → TL×4 → LineTo(r,0)
        val lines = cmds.filterIsInstance<SquircleCmd.LineTo>()
        assertEquals(5, lines.size)

        // TR 角（前 4 段）：入口为第 1 条 LineTo 终点 (w-r, 0)，出口为末段终点 (w, r)
        val tr = cubics.take(4)
        assertTrue(abs(lines[0].x - (w - r)) < 0.01f && abs(lines[0].y) < 0.01f,
            "TR 入口应为 (w-r, 0)，实际 (${lines[0].x}, ${lines[0].y})")
        val trEnd = tr.last()
        assertTrue(abs(trEnd.x - w) < 0.01f && abs(trEnd.y - r) < 0.01f,
            "TR 出口应为 (w, r)，实际 (${trEnd.x}, ${trEnd.y})")

        // BR 角（5–8 段）：入口 (w, h-r)，出口 (w-r, h)
        val br = cubics.subList(4, 8)
        assertTrue(abs(lines[1].x - w) < 0.01f && abs(lines[1].y - (h - r)) < 0.01f, "BR 入口应为 (w, h-r)")
        val brEnd = br.last()
        assertTrue(abs(brEnd.x - (w - r)) < 0.01f && abs(brEnd.y - h) < 0.01f, "BR 出口应为 (w-r, h)")

        // BL 角（9–12 段）：入口 (r, h)，出口 (0, h-r)
        val bl = cubics.subList(8, 12)
        assertTrue(abs(lines[2].x - r) < 0.01f && abs(lines[2].y - h) < 0.01f, "BL 入口应为 (r, h)")
        assertTrue(abs(bl.last().x) < 0.01f && abs(bl.last().y - (h - r)) < 0.01f, "BL 出口应为 (0, h-r)")

        // TL 角（13–16 段）：入口 (0, r)，出口 (r, 0)
        val tl = cubics.subList(12, 16)
        assertTrue(abs(lines[3].x) < 0.01f && abs(lines[3].y - r) < 0.01f, "TL 入口应为 (0, r)")
        assertTrue(abs(tl.last().x - r) < 0.01f && abs(tl.last().y) < 0.01f, "TL 出口应为 (r, 0)")
    }

    // ---------- G1 连续：端点切线与直边平行 ----------

    @Test
    fun `TR 角端点切线与直边平行（G1 连续）`() {
        val w = 100f; val h = 100f; val r = 20f
        val cmds = SquircleMath.outline(w, h, r, n = 5f, segments = 4)
        val tr = cmds.filterIsInstance<SquircleCmd.CubicTo>().take(4)

        // 入口控制点相对起点的方向 ≈ +x（沿顶边行进方向）
        val entryDx = tr.first().c1x - (w - r)
        val entryDy = tr.first().c1y - 0f
        assertTrue(entryDx > 0f, "入口应继续向 +x 行进")
        assertTrue(abs(entryDy) < abs(entryDx) * 0.2f,
            "入口切线应平行顶边，实际方向 ($entryDx, $entryDy)")

        // 出口：从末端控制点指向终点的方向 ≈ +y（沿右边行进方向）
        val last = tr.last()
        val exitDx = last.x - last.c2x
        val exitDy = last.y - last.c2y
        assertTrue(exitDy > 0f, "出口应继续向 +y 行进")
        assertTrue(abs(exitDx) < abs(exitDy) * 0.2f,
            "出口切线应平行右边，实际方向 ($exitDx, $exitDy)")
    }

    // ---------- n=2 退化为几何圆 ----------

    @Test
    fun `n=2 时四角退化为标准圆弧（与几何圆偏差趋零）`() {
        val w = 100f; val h = 100f; val r = 20f
        val cmds = SquircleMath.outline(w, h, r, n = 2f, segments = 8)
        val cubics = cmds.filterIsInstance<SquircleCmd.CubicTo>()

        // TR 角圆心 (w-r, r)，半径 r；所有曲线端点应落在圆上
        val cx = w - r; val cy = r
        var maxDev = 0f
        for (c in cubics.take(8)) {
            val points = listOf(c.x to c.y)
            for ((x, y) in points) {
                val d = abs(hypot(x - cx, y - cy) - r)
                maxDev = maxOf(maxDev, d)
            }
        }
        // 贝塞尔对圆弧的固有拟合误差（8 段时 ~1e-5 量级）+ 差分切线引入的微小偏差
        assertTrue(maxDev < r * 0.01f, "n=2 与几何圆最大偏差应 < 1% 半径，实际 $maxDev")
    }

    @Test
    fun `n=5 时角部比几何圆更方（中点内收）`() {
        val w = 100f; val h = 100f; val r = 20f
        val cmds = SquircleMath.outline(w, h, r, n = 5f, segments = 8)
        val tr = cmds.filterIsInstance<SquircleCmd.CubicTo>().take(8)
        // 角中点：第 4 段终点（t=5π/4 附近）
        val mid = tr[3]
        val vertex = w to 0f // 角顶点 (w, 0)
        val dSquircle = hypot(mid.x - vertex.first, mid.y - vertex.second)

        // 几何圆中点：(w - r(1-√2/2), r(1-√2/2))
        val k = 1f - sqrt(2f) / 2f
        val dCircle = hypot(r * k, r * k)

        assertTrue(dSquircle < dCircle * 0.8f,
            "squircle 中点应比几何圆中点更靠近顶点（更方），squircle=$dSquircle circle=$dCircle")
    }

    // ---------- 半径收敛与边界 ----------

    @Test
    fun `半径超过半边长时收敛为胶囊形且不越界`() {
        val w = 100f; val h = 40f
        val cmds = SquircleMath.outline(w, h, radius = 80f, n = 5f, segments = 4)
        for (cmd in cmds) {
            val (x, y) = when (cmd) {
                is SquircleCmd.MoveTo -> cmd.x to cmd.y
                is SquircleCmd.LineTo -> cmd.x to cmd.y
                is SquircleCmd.CubicTo -> cmd.x to cmd.y
            }
            assertTrue(x in -0.01f..(w + 0.01f) && y in -0.01f..(h + 0.01f),
                "坐标越界: ($x, $y) 尺寸 ${w}x$h")
        }
    }

    @Test
    fun `所有命令坐标均在尺寸范围内（任意 n 与半径）`() {
        val w = 137f; val h = 61f
        for (n in listOf(2f, 3f, 5f, 8f)) {
            for (r in listOf(0f, 5f, 20f, 30.5f)) {
                for (cmd in SquircleMath.outline(w, h, r, n = n)) {
                    val (x, y) = when (cmd) {
                        is SquircleCmd.MoveTo -> cmd.x to cmd.y
                        is SquircleCmd.LineTo -> cmd.x to cmd.y
                        is SquircleCmd.CubicTo -> cmd.x to cmd.y
                    }
                    assertTrue(x in -0.01f..(w + 0.01f) && y in -0.01f..(h + 0.01f),
                        "n=$n r=$r 坐标越界: ($x, $y)")
                }
            }
        }
    }

    @Test
    fun `零半径输出普通矩形`() {
        val cmds = SquircleMath.outline(50f, 30f, radius = 0f)
        assertEquals(5, cmds.size)
        assertTrue(cmds.none { it is SquircleCmd.CubicTo })
        assertTrue(cmds[1] is SquircleCmd.LineTo)
    }

    // ---------- 参数校验 ----------

    @Test
    fun `非法参数快速失败`() {
        assertThrows<IllegalArgumentException> { SquircleMath.outline(0f, 10f, 4f) }
        assertThrows<IllegalArgumentException> { SquircleMath.outline(10f, -1f, 4f) }
        assertThrows<IllegalArgumentException> { SquircleMath.outline(10f, 10f, 4f, n = 1.5f) }
        assertThrows<IllegalArgumentException> { SquircleMath.outline(10f, 10f, 4f, segments = 0) }
    }

    // ---------- 确定性 ----------

    @Test
    fun `同参数输出完全一致（可缓存性）`() {
        val a = SquircleMath.outline(100f, 100f, 16f, n = 5f, segments = 4)
        val b = SquircleMath.outline(100f, 100f, 16f, n = 5f, segments = 4)
        assertEquals(a, b)
        val c = SquircleMath.outline(100f, 100f, 16f, n = 2f, segments = 4)
        assertTrue(a != c, "不同 n 应输出不同轮廓")
    }
}
