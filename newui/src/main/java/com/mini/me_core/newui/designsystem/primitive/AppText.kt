package com.mini.me_core.newui.designsystem.primitive

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import com.mini.me_core.newui.designsystem.theme.appPalette

/**
 * 统一文字（§3.2 排版尺度 · AppText）：基于 M3 [Text] 的薄封装。
 *
 * 设计来源：§3.2 排版尺度——字号 / 字重 / 行高全部走 [MaterialTheme.typography] 令牌，
 * 颜色默认取 [appPalette] 的一级文字 [appPalette.ink]，禁止业务方散落 `Color.Black` / `.sp` 硬编码。
 *
 * 参数语义：
 * - [text]：待渲染文本。
 * - [modifier]：外层布局修饰符，由调用方控制尺寸/对齐。
 * - [style]：排版令牌，默认 bodyMedium（正文）；强调/标题请用 [AppTextTitle]，辅助说明用 [AppTextCaption]。
 * - [color]：文字颜色，默认一级文字 ink；次级文字请用 [AppTextCaption] 或显式传 [appPalette].labelSecondary。
 * - [fontWeight]：覆盖默认字重；null 表示沿用 [style] 自带字重。
 * - [maxLines]：最大行数，超出按 [overflow] 截断。
 * - [overflow]：溢出处理策略，默认 [TextOverflow.Clip]；需要省略号请传 [TextOverflow.Ellipsis]。
 *
 * @since 0.1.0-experimental
 */
@Composable
fun AppText(
    text: String,
    modifier: Modifier = Modifier,
    style: TextStyle = MaterialTheme.typography.bodyMedium,
    color: Color = appPalette().ink,
    fontWeight: FontWeight? = null,
    maxLines: Int = Int.MAX_VALUE,
    overflow: TextOverflow = TextOverflow.Clip,
) {
    Text(
        text = text,
        modifier = modifier,
        style = style,
        color = color,
        fontWeight = fontWeight,
        maxLines = maxLines,
        overflow = overflow,
    )
}

/**
 * 标题便捷变体（§3.2.1 标题尺度）：titleMedium + SemiBold，用于区块/卡片主标题。
 *
 * @since 0.1.0-experimental
 */
@Composable
fun AppTextTitle(
    text: String,
    modifier: Modifier = Modifier,
    color: Color = appPalette().ink,
    maxLines: Int = Int.MAX_VALUE,
    overflow: TextOverflow = TextOverflow.Clip,
) {
    AppText(
        text = text,
        modifier = modifier,
        style = MaterialTheme.typography.titleMedium,
        color = color,
        fontWeight = FontWeight.SemiBold,
        maxLines = maxLines,
        overflow = overflow,
    )
}

/**
 * 正文便捷变体（§3.2.2 正文尺度）：bodyMedium，长段落/对话气泡正文默认规格。
 *
 * @since 0.1.0-experimental
 */
@Composable
fun AppTextBody(
    text: String,
    modifier: Modifier = Modifier,
    color: Color = appPalette().ink,
    maxLines: Int = Int.MAX_VALUE,
    overflow: TextOverflow = TextOverflow.Clip,
) {
    AppText(
        text = text,
        modifier = modifier,
        style = MaterialTheme.typography.bodyMedium,
        color = color,
        maxLines = maxLines,
        overflow = overflow,
    )
}

/**
 * 辅助说明便捷变体（§3.2.3 次要文字尺度）：bodySmall + labelSecondary，用于时间戳/副标题/占位提示。
 *
 * @since 0.1.0-experimental
 */
@Composable
fun AppTextCaption(
    text: String,
    modifier: Modifier = Modifier,
    color: Color = appPalette().labelSecondary,
    maxLines: Int = Int.MAX_VALUE,
    overflow: TextOverflow = TextOverflow.Clip,
) {
    AppText(
        text = text,
        modifier = modifier,
        style = MaterialTheme.typography.bodySmall,
        color = color,
        maxLines = maxLines,
        overflow = overflow,
    )
}
