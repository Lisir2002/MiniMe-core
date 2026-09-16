package com.mini.me_core.newui.designsystem.component

import com.mini.me_core.newui.designsystem.theme.appPalette
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Archive
import androidx.compose.material.icons.rounded.KeyboardArrowDown
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.mini.me_core.newui.designsystem.token.generated.AppColor
import com.mini.me_core.newui.designsystem.token.generated.AppRadius
import com.mini.me_core.newui.designsystem.token.generated.AppSizing
import com.mini.me_core.newui.designsystem.token.generated.AppSpacing
import com.mini.me_core.newui.designsystem.token.generated.AppStroke

/** Diff 行类型：上下文 / 新增 / 删除。 */
enum class AppDiffLineType { Context, Add, Remove }

/** Diff 一行。 */
data class AppDiffLine(
    val type: AppDiffLineType,
    val text: String,
)

/**
 * 文件 Diff 查看卡（分子组 · AppDiffCard）：展示单个文件的行级改动。
 *
 * - 文件头：文件图标 + 路径，右侧 `+additions / -deletions` 绿红计数。
 * - 行级 diff：新增行浅绿底 + 绿色 `+` 槽，删除行浅红底 + 红色 `-` 槽，上下文行无底色；
 *   等宽字体。默认折叠，点表头展开；展开后限高、超出内部滚动。
 *
 * @since 0.1.0-experimental
 */
@Composable
fun AppDiffCard(
    filePath: String,
    additions: Int,
    deletions: Int,
    lines: List<AppDiffLine>,
    modifier: Modifier = Modifier,
    initiallyExpanded: Boolean = false,
) {
    val shape = RoundedCornerShape(AppRadius.Md)
    var expanded by remember { mutableStateOf(initiallyExpanded) }
    Column(
        modifier = modifier
            .clip(shape)
            .background(appPalette().card)
            .border(AppStroke.Thin, appPalette().separator, shape),
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .clip(shape)
                .clickable { expanded = !expanded }
                .padding(horizontal = AppSpacing.Md, vertical = AppSpacing.Sm),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Icon(
                imageVector = Icons.Rounded.Archive,
                contentDescription = null,
                tint = appPalette().labelSecondary,
                modifier = Modifier.size(AppSizing.IconXs),
            )
            Spacer(Modifier.width(AppSpacing.Sm))
            Text(
                text = filePath,
                style = MaterialTheme.typography.bodyMedium,
                fontWeight = FontWeight.Medium,
                color = appPalette().ink,
                modifier = Modifier.weight(1f),
            )
            Text(
                text = "+$additions",
                style = MaterialTheme.typography.labelMedium,
                color = AppColor.StatusSuccess,
            )
            Spacer(Modifier.width(AppSpacing.Xs))
            Text(
                text = "-$deletions",
                style = MaterialTheme.typography.labelMedium,
                color = AppColor.StatusDanger,
            )
            Spacer(Modifier.width(AppSpacing.Xs))
            Icon(
                imageVector = Icons.Rounded.KeyboardArrowDown,
                contentDescription = if (expanded) "收起 diff" else "展开 diff",
                tint = appPalette().labelTertiary,
                modifier = Modifier
                    .size(AppSizing.IconXs)
                    .rotate(if (expanded) 180f else 0f),
            )
        }
        if (expanded) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .heightIn(max = 220.dp)
                    .verticalScroll(rememberScrollState()),
            ) {
                lines.forEach { line -> DiffRow(line) }
            }
        }
    }
}

@Composable
private fun DiffRow(line: AppDiffLine) {
    val bg: Color = when (line.type) {
        AppDiffLineType.Add -> AppColor.StatusSuccess.copy(alpha = 0.10f)
        AppDiffLineType.Remove -> AppColor.StatusDanger.copy(alpha = 0.10f)
        AppDiffLineType.Context -> Color.Transparent
    }
    val gutter: String = when (line.type) {
        AppDiffLineType.Add -> "+"
        AppDiffLineType.Remove -> "-"
        AppDiffLineType.Context -> " "
    }
    val gutterColor: Color = when (line.type) {
        AppDiffLineType.Add -> AppColor.StatusSuccess
        AppDiffLineType.Remove -> AppColor.StatusDanger
        AppDiffLineType.Context -> appPalette().labelTertiary
    }
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .background(bg)
            .padding(horizontal = AppSpacing.Md, vertical = 1.dp),
        verticalAlignment = Alignment.Top,
    ) {
        Text(
            text = gutter,
            style = MaterialTheme.typography.bodySmall.copy(fontFamily = FontFamily.Monospace),
            color = gutterColor,
            modifier = Modifier.width(14.dp),
        )
        Spacer(Modifier.width(AppSpacing.Sm))
        Text(
            text = line.text,
            style = MaterialTheme.typography.bodySmall.copy(fontFamily = FontFamily.Monospace),
            color = appPalette().ink,
        )
    }
}
