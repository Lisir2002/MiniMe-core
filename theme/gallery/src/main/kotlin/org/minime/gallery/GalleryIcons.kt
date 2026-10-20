package org.minime.gallery

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.size
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Fill
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp

/**
 * Gallery 自绘图标（§10.9 自举：自身 UI 全部自绘 + 令牌驱动）。
 *
 * 风格：1.75dp 圆头描边（SF Symbols 近亲），全部用绘制原语拼装——
 * 不引第三方图标库（那会引入第二套设计语言）。图标集随分区增长。
 */
enum class GalleryIcon {
    /** 令牌总览：横向滑杆 */
    Tokens,

    /** 组件目录：2×2 圆角方阵 */
    Components,

    /** 页面原型：叠层 */
    Prototypes,

    /** 动效检验：波形 */
    Motion,

    /** 主题工坊：纵向滑杆 */
    Workshop,

    /** 调试面板：仪表盘 */
    Debug,

    /** 汉堡菜单 */
    Menu,

    /** 关闭（×） */
    Close,

    /** 亮色（太阳） */
    Sun,

    /** 暗色（月亮） */
    Moon,
}

/**
 * 绘制 [GalleryIcon]（[color] 取自当前主题令牌，size 由调用方给定）。
 *
 * @param contentDescription 无障碍标签（§10.9-3：Gallery 是产品页面，自身须过 a11y；
 *       纯装饰图标传 null）
 */
@Composable
fun GalleryIcon(
    icon: GalleryIcon,
    color: Color,
    modifier: Modifier = Modifier,
    iconSize: Dp = 22.dp,
    contentDescription: String? = null,
) {
    Canvas(
        modifier = if (contentDescription != null) {
            modifier.size(iconSize).semantics { this.contentDescription = contentDescription }
        } else {
            modifier.size(iconSize)
        }
    ) {
        val stroke = Stroke(width = this.size.minDimension * 0.085f, cap = StrokeCap.Round)
        val w = size.width
        val h = size.height
        val pad = w * 0.14f
        fun line(x1: Float, y1: Float, x2: Float, y2: Float) {
            drawLine(color, Offset(x1, y1), Offset(x2, y2), stroke.width, StrokeCap.Round)
        }
        when (icon) {
            GalleryIcon.Tokens -> {
                // 三条横线 + 三个圆点（滑杆）
                line(pad, h * 0.26f, w - pad, h * 0.26f)
                line(pad, h * 0.5f, w - pad, h * 0.5f)
                line(pad, h * 0.74f, w - pad, h * 0.74f)
                drawCircle(color, w * 0.09f, Offset(w * 0.66f, h * 0.26f), style = stroke)
                drawCircle(color, w * 0.09f, Offset(w * 0.34f, h * 0.5f), style = stroke)
                drawCircle(color, w * 0.09f, Offset(w * 0.66f, h * 0.74f), style = stroke)
            }
            GalleryIcon.Components -> {
                val cell = (w - pad * 2 - w * 0.12f) / 2f
                listOf(
                    Offset(pad, pad),
                    Offset(pad + cell + w * 0.12f, pad),
                    Offset(pad, pad + cell + w * 0.12f),
                    Offset(pad + cell + w * 0.12f, pad + cell + w * 0.12f),
                ).forEach { topLeft ->
                    drawRoundRect(
                        color, topLeft = topLeft,
                        size = Size(cell, cell),
                        cornerRadius = CornerRadius(cell * 0.28f),
                        style = stroke,
                    )
                }
            }
            GalleryIcon.Prototypes -> {
                drawRoundRect(
                    color, topLeft = Offset(pad, pad),
                    size = Size(w - pad * 2, h - pad * 2),
                    cornerRadius = CornerRadius(w * 0.1f),
                    style = stroke,
                )
                line(pad + w * 0.16f, h * 0.4f, w - pad - w * 0.16f, h * 0.4f)
                line(pad + w * 0.16f, h * 0.58f, w - pad - w * 0.34f, h * 0.58f)
            }
            GalleryIcon.Motion -> {
                // 正弦波（两段 cubic 近似）
                val path = Path()
                path.moveTo(pad, h * 0.5f)
                path.cubicTo(pad + w * 0.12f, h * 0.2f, w * 0.38f, h * 0.2f, w * 0.5f, h * 0.5f)
                path.cubicTo(w * 0.62f, h * 0.8f, w * 0.88f, h * 0.8f, w - pad, h * 0.5f)
                drawPath(path, color, style = stroke)
            }
            GalleryIcon.Workshop -> {
                // 纵向滑杆
                line(w * 0.28f, pad, w * 0.28f, h - pad)
                line(w * 0.5f, pad, w * 0.5f, h - pad)
                line(w * 0.72f, pad, w * 0.72f, h - pad)
                drawCircle(color, w * 0.09f, Offset(w * 0.28f, h * 0.34f), style = stroke)
                drawCircle(color, w * 0.09f, Offset(w * 0.5f, h * 0.66f), style = stroke)
                drawCircle(color, w * 0.09f, Offset(w * 0.72f, h * 0.34f), style = stroke)
            }
            GalleryIcon.Debug -> {
                // 仪表盘弧 + 指针
                drawArc(
                    color, startAngle = 150f, sweepAngle = 240f, useCenter = false,
                    topLeft = Offset(pad, pad), size = Size(w - pad * 2, h - pad * 2),
                    style = stroke,
                )
                line(w * 0.5f, h * 0.5f, w * 0.68f, h * 0.36f)
                drawCircle(color, w * 0.06f, Offset(w * 0.5f, h * 0.5f), style = Fill)
            }
            GalleryIcon.Menu -> {
                line(pad, h * 0.32f, w - pad, h * 0.32f)
                line(pad, h * 0.5f, w - pad, h * 0.5f)
                line(pad, h * 0.68f, w - pad, h * 0.68f)
            }
            GalleryIcon.Close -> {
                line(pad, pad, w - pad, h - pad)
                line(w - pad, pad, pad, h - pad)
            }
            GalleryIcon.Sun -> {
                drawCircle(color, w * 0.24f, Offset(w * 0.5f, h * 0.5f), style = stroke)
                for (i in 0 until 8) {
                    val a = Math.toRadians(i * 45.0)
                    val c = Offset(w * 0.5f, h * 0.5f)
                    val r0 = w * 0.32f
                    val r1 = w * 0.44f
                    line(
                        (c.x + r0 * Math.cos(a)).toFloat(), (c.y + r0 * Math.sin(a)).toFloat(),
                        (c.x + r1 * Math.cos(a)).toFloat(), (c.y + r1 * Math.sin(a)).toFloat(),
                    )
                }
            }
            GalleryIcon.Moon -> {
                // 月牙：外弧 + 内弧
                val path = Path()
                path.moveTo(w * 0.62f, h * 0.18f)
                path.cubicTo(w * 0.34f, h * 0.26f, w * 0.24f, h * 0.44f, w * 0.24f, h * 0.56f)
                path.cubicTo(w * 0.24f, h * 0.74f, w * 0.4f, h * 0.86f, w * 0.6f, h * 0.84f)
                path.cubicTo(w * 0.46f, h * 0.68f, w * 0.44f, h * 0.4f, w * 0.62f, h * 0.18f)
                path.close()
                drawPath(path, color, style = stroke)
            }
        }
    }
}
