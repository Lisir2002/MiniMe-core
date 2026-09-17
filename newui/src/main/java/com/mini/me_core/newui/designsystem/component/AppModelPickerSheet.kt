package com.mini.me_core.newui.designsystem.component

import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Check
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Text
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.mini.me_core.newui.designsystem.theme.appPalette
import com.mini.me_core.newui.designsystem.token.generated.AppRadius
import com.mini.me_core.newui.designsystem.token.generated.AppSizing
import com.mini.me_core.newui.designsystem.token.generated.AppSpacing
import com.mini.me_core.newui.designsystem.token.generated.AppStroke

/** 一个可选模型。能力项与生产 ModelMetadata 同构：识图 / 工具调用 / 推理。 */
data class AppModelOption(
    val id: String,
    val name: String,
    val provider: String,
    val badge: String? = null,
    val caption: String? = null,
    val supportsVision: Boolean = false,
    val supportsTools: Boolean = false,
    val supportsReasoning: Boolean = false,
)

/** 模型选择底部弹层：按 provider 分组，紧凑列表，单选高亮 + 选中对勾弹跳。 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AppModelPickerSheet(
    providers: List<Pair<String, List<AppModelOption>>>,
    selectedId: String?,
    onSelect: (AppModelOption) -> Unit,
    onDismiss: () -> Unit,
) {
    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = rememberModalBottomSheetState(),
        containerColor = appPalette().card,
        dragHandle = null,
    ) {
        Column(Modifier.padding(horizontal = AppSpacing.Lg)) {
            Row(
                Modifier.fillMaxWidth().padding(top = AppSpacing.Md, bottom = AppSpacing.Xs),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text("选择模型", style = MaterialTheme.typography.titleMedium, color = appPalette().ink, fontWeight = FontWeight.SemiBold)
                Spacer(Modifier.weight(1f))
                providers.sumOf { it.second.size }.let { n ->
                    Text("$n 个模型", style = MaterialTheme.typography.labelSmall, color = appPalette().labelTertiary)
                }
            }
            providers.forEach { (provider, models) ->
                Text(
                    provider,
                    style = MaterialTheme.typography.labelSmall,
                    color = appPalette().labelTertiary,
                    modifier = Modifier.padding(top = AppSpacing.Sm, bottom = AppSpacing.Tiny),
                )
                models.forEach { m ->
                    ModelRow(
                        model = m,
                        selected = m.id == selectedId,
                        onClick = { onSelect(m) },
                    )
                }
            }
            Spacer(Modifier.padding(AppSpacing.Lg))
        }
    }
}

@Composable
private fun ModelRow(model: AppModelOption, selected: Boolean, onClick: () -> Unit) {
    val bg by animateColorAsState(
        if (selected) appPalette().primary.copy(alpha = 0.10f) else appPalette().surface,
        label = "bg",
    )
    val border by animateColorAsState(
        if (selected) appPalette().primary.copy(alpha = 0.45f) else appPalette().separator,
        label = "bd",
    )
    val checkScale by animateFloatAsState(if (selected) 1f else 0.6f, label = "chk")
    val interaction = remember { MutableInteractionSource() }
    Row(
        Modifier
            .fillMaxWidth()
            .padding(vertical = AppSpacing.Tiny)
            .clip(RoundedCornerShape(AppRadius.Md))
            .background(bg)
            .border(AppStroke.Thin, border, RoundedCornerShape(AppRadius.Md))
            .clickable(interactionSource = interaction, indication = null) { onClick() }
            .padding(horizontal = AppSpacing.Md, vertical = AppSpacing.Sm),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(Modifier.weight(1f)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(model.name, style = MaterialTheme.typography.bodyLarge, color = appPalette().ink, fontWeight = FontWeight.Medium)
                if (!model.badge.isNullOrBlank()) {
                    Spacer(Modifier.width(AppSpacing.Xs))
                    Text(
                        model.badge!!,
                        style = MaterialTheme.typography.labelSmall,
                        color = appPalette().primary,
                        modifier = Modifier
                            .clip(RoundedCornerShape(AppRadius.Pill))
                            .background(appPalette().primary.copy(alpha = 0.12f))
                            .padding(horizontal = AppSpacing.Xs, vertical = 1.dp),
                    )
                }
            }
            val caps = buildList {
                if (model.supportsVision) add("识图")
                if (model.supportsTools) add("工具")
                if (model.supportsReasoning) add("推理")
            }
            if (caps.isNotEmpty() || !model.caption.isNullOrBlank()) {
                Spacer(Modifier.padding(1.dp))
                Text(
                    (listOfNotNull(model.caption) + caps).joinToString(" · "),
                    style = MaterialTheme.typography.labelSmall,
                    color = appPalette().labelTertiary,
                )
            }
        }
        // 选中圆点 + 对勾。
        Box(
            Modifier
                .size(AppSizing.IconM)
                .scale(checkScale)
                .clip(CircleShape)
                .background(if (selected) appPalette().primary else appPalette().surface),
            contentAlignment = Alignment.Center,
        ) {
            if (selected) {
                Icon(Icons.Rounded.Check, contentDescription = "已选", tint = appPalette().onPrimary, modifier = Modifier.size(AppSizing.IconXs))
            }
        }
    }
}
