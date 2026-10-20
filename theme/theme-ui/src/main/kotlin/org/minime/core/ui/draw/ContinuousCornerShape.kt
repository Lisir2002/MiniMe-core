package org.minime.core.ui.draw

import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Outline
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.LayoutDirection

/**
 * squircle 路径缓存（R3）。
 *
 * `Path` 构建成本在滚动/动画帧里不可忽略（采样 + 贝塞尔换算 + 原生对象分配），
 * 而形状参数在运行期高度重复（同一组件反复裁剪同尺寸同半径）——
 * 按 (w, h, r, n, segments) 缓存 [Path] 实例，重复请求直接命中。
 *
 * 契约：
 * - **只读复用**：调用方不得修改返回的 `Path`（修改即污染缓存，后续命中方拿到脏路径）；
 * - 线程安全：synchronized（Compose 绘制在渲染线程，双保险）；
 * - 上限驱逐：access-ordered LRU，[MAX_ENTRIES] 条封顶（滚动中尺寸连续变化时旧条目自然淘汰）。
 */
internal object SquirclePathCache {

    private const val MAX_ENTRIES = 64

    private data class Key(val w: Float, val h: Float, val r: Float, val n: Float, val segments: Int)

    private val lock = Any()
    private val cache = object : LinkedHashMap<Key, Path>(16, 0.75f, true) {
        override fun removeEldestEntry(eldest: MutableMap.MutableEntry<Key, Path>?): Boolean =
            size > MAX_ENTRIES
    }

    /** 取（或构建）squircle 路径；返回实例**只读** */
    fun obtain(w: Float, h: Float, r: Float, n: Float, segments: Int): Path = synchronized(lock) {
        val key = Key(w, h, r, n, segments)
        cache.getOrPut(key) { build(w, h, r, n, segments) }
    }

    private fun build(w: Float, h: Float, r: Float, n: Float, segments: Int): Path {
        val path = Path()
        for (cmd in SquircleMath.outline(w, h, r, n, segments)) {
            when (cmd) {
                is SquircleCmd.MoveTo -> path.moveTo(cmd.x, cmd.y)
                is SquircleCmd.LineTo -> path.lineTo(cmd.x, cmd.y)
                is SquircleCmd.CubicTo -> path.cubicTo(cmd.c1x, cmd.c1y, cmd.c2x, cmd.c2y, cmd.x, cmd.y)
            }
        }
        path.close()
        return path
    }

    /** 测试与调试：清空缓存 */
    fun clear() = synchronized(lock) { cache.clear() }
}

/**
 * 连续圆角形状（白皮书 §3.6 妥协 2 的实现落点）。
 *
 * 用法（与系统 `RoundedCornerShape` 同位置替换）：
 * ```kotlin
 * Modifier.clip(ContinuousCornerShape(12.dp))
 * ```
 *
 * 与几何圆角的差别：同半径下角部更饱满（lamé 超椭圆，n = [SquircleMath.DEFAULT_N]）；
 * A/B 对照见 Gallery 令牌总览 shape 区（R3 验收项）。
 *
 * 对称性：轮廓关于两轴都对称 ⇒ `LayoutDirection` 无关（RTL 不需要镜像），
 * [createOutline] 忽略该参数。
 */
class ContinuousCornerShape(
    private val radius: Dp,
    private val n: Float = SquircleMath.DEFAULT_N,
) : Shape {

    override fun createOutline(
        size: Size,
        layoutDirection: LayoutDirection,
        density: Density,
    ): Outline {
        val r = with(density) { radius.toPx() }.coerceIn(0f, minOf(size.width, size.height) / 2f)
        if (r <= 0f || size.minDimension <= 0f) {
            return Outline.Rectangle(Rect(0f, 0f, size.width, size.height))
        }
        return Outline.Generic(
            SquirclePathCache.obtain(size.width, size.height, r, n, SquircleMath.DEFAULT_SEGMENTS)
        )
    }
}

/** 发丝线边缘（[drawHairline]） */
enum class HairlineEdge { Top, Bottom }

/**
 * 发丝线绘制原语（§3.2 层级语言：发丝线替代投影）。
 *
 * 语义：沿容器某条边绘 1px 级细线（iOS separator）。厚度取自 `elevation.hairline`
 * 令牌（px 单位）由调用方传入——原语不自己读令牌（保持可在非组合上下文调用）。
 */
fun Modifier.drawHairline(
    edge: HairlineEdge,
    color: Color,
    thicknessPx: Float = 1f,
): Modifier = this.then(
    Modifier.drawBehind {
        if (thicknessPx <= 0f) return@drawBehind
        val t = thicknessPx.coerceAtMost(size.minDimension)
        val topLeft = when (edge) {
            HairlineEdge.Top -> Offset.Zero
            HairlineEdge.Bottom -> Offset(0f, size.height - t)
        }
        drawRect(color = color, topLeft = topLeft, size = Size(size.width, t))
    }
)
