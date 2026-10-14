package com.mini.me_core.core.theme.components

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkVertically
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ExpandMore
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.mini.me_core.core.theme.ScaledAnimation
import com.mini.me_core.core.theme.tokens.LocalAppTheme
import com.mini.me_core.core.theme.tokens.PrimitiveSpacing

/**
 * 可折叠分区：标题行（点击展开/收起，箭头旋转）+ 动画展开的内容区。
 *
 * 实证：全项目 31 处文件存在 expandable/AnimatedVisibility 手搓实现（TaskAccordion、
 * ChatInputToolbar、ProviderEditorScreen 高级参数区等），动画参数与箭头形态不一。
 * 本组件统一为受控组件 + ScaledAnimation 缩放动画（跟随全局「动效强度」设置）。
 *
 * 内边距约定：本组件不施加水平内边距，由调用方容器统一控制（与设置页表单
 * `Column(padding(horizontal = Lg))` 的既有约定一致）；标题行自带 Md 垂直内边距，
 * 保证约 44dp 的触摸目标。独立使用（无外层容器缩进）时可通过 modifier 追加水平内边距。
 *
 * @param title 标题
 * @param expanded 是否展开（受控）
 * @param onToggle 展开状态切换回调
 * @param modifier 修饰符
 * @param subtitle 副标题（可选）
 * @param icon 左侧图标（可选）
 * @param trailing 标题行尾随组件（可选，置于箭头左侧）
 * @param content 展开后的内容
 */
@Composable
fun AppExpandableSection(
    title: String,
    expanded: Boolean,
    onToggle: (Boolean) -> Unit,
    modifier: Modifier = Modifier,
    subtitle: String? = null,
    icon: ImageVector? = null,
    trailing: (@Composable () -> Unit)? = null,
    content: @Composable () -> Unit,
) {
    val colors = LocalAppTheme.current.colors
    val arrowRotation by animateFloatAsState(
        targetValue = if (expanded) 180f else 0f,
        animationSpec = ScaledAnimation.tween(250),
        label = "expandableArrowRotation",
    )

    Column(modifier = modifier.fillMaxWidth()) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .clickable { onToggle(!expanded) }
                .padding(vertical = PrimitiveSpacing.Md),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(PrimitiveSpacing.Md),
        ) {
            if (icon != null) {
                Icon(
                    imageVector = icon,
                    contentDescription = null,
                    tint = colors.textSecondary,
                    modifier = Modifier.size(20.dp),
                )
            }
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = title,
                    style = MaterialTheme.typography.bodyLarge,
                    fontWeight = FontWeight.SemiBold,
                    color = colors.textPrimary,
                )
                if (subtitle != null) {
                    Text(
                        text = subtitle,
                        style = MaterialTheme.typography.bodySmall,
                        color = colors.textSecondary,
                    )
                }
            }
            if (trailing != null) {
                trailing()
            }
            Icon(
                imageVector = Icons.Filled.ExpandMore,
                contentDescription = null,
                tint = colors.textTertiary,
                modifier = Modifier
                    .size(20.dp)
                    .rotate(arrowRotation),
            )
        }
        AnimatedVisibility(
            visible = expanded,
            enter = expandVertically(
                animationSpec = ScaledAnimation.tween(250),
                expandFrom = Alignment.Top,
            ) + fadeIn(animationSpec = ScaledAnimation.tween(250)),
            exit = shrinkVertically(
                animationSpec = ScaledAnimation.tween(200),
                shrinkTowards = Alignment.Top,
            ) + fadeOut(animationSpec = ScaledAnimation.tween(200)),
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(bottom = PrimitiveSpacing.Md),
                verticalArrangement = Arrangement.spacedBy(PrimitiveSpacing.Sm),
            ) {
                content()
            }
        }
    }
}

// ──────────────────────────────────────────────
// Previews
// ──────────────────────────────────────────────

/** Preview：可折叠分区（展开/收起两态；水平缩进由外层容器施加）。 */
@androidx.compose.ui.tooling.preview.Preview(showBackground = true, widthDp = 400, heightDp = 300)
@Composable
private fun AppExpandableSectionPreview() {
    com.mini.me_core.core.theme.MiniMeTheme(darkTheme = false) {
        Column(modifier = Modifier.padding(horizontal = PrimitiveSpacing.Lg)) {
            AppExpandableSection(
                title = "高级参数",
                subtitle = "通常无需修改",
                expanded = true,
                onToggle = {},
                content = {
                    Text(
                        text = "温度：0.7",
                        style = MaterialTheme.typography.bodyMedium,
                        color = LocalAppTheme.current.colors.textSecondary,
                    )
                    Text(
                        text = "最大 Token：4096",
                        style = MaterialTheme.typography.bodyMedium,
                        color = LocalAppTheme.current.colors.textSecondary,
                    )
                },
            )
            AppExpandableSection(
                title = "折叠态",
                expanded = false,
                onToggle = {},
                content = {},
            )
        }
    }
}
