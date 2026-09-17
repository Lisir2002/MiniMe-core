package com.mini.me_core.newui.designsystem.component

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Check
import androidx.compose.material.icons.rounded.KeyboardArrowRight
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Text
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.mini.me_core.newui.designsystem.theme.appPalette
import com.mini.me_core.newui.designsystem.token.generated.AppRadius
import com.mini.me_core.newui.designsystem.token.generated.AppSizing
import com.mini.me_core.newui.designsystem.token.generated.AppSpacing
import com.mini.me_core.newui.designsystem.token.generated.AppStroke

/** 一个可选模型。 */
data class AppModelOption(
    val id: String,
    val name: String,
    val provider: String,
    val badge: String? = null,
    val caption: String? = null,
)

/** 模型选择底部弹层：按 provider 分组，单选高亮。 */
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
    ) {
        Column(Modifier.padding(horizontal = AppSpacing.Lg, vertical = AppSpacing.Sm)) {
            Text("选择模型", style = MaterialTheme.typography.titleMedium, color = appPalette().ink, fontWeight = FontWeight.SemiBold)
            Spacer(Modifier.padding(AppSpacing.Xs))
            providers.forEach { (provider, models) ->
                Text(
                    provider,
                    style = MaterialTheme.typography.labelSmall,
                    color = appPalette().labelTertiary,
                    modifier = Modifier.padding(top = AppSpacing.Md, bottom = AppSpacing.Xs),
                )
                models.forEach { m ->
                    val selected = m.id == selectedId
                    Row(
                        Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(AppRadius.Md))
                            .background(if (selected) appPalette().primary.copy(alpha = 0.10f) else appPalette().surface)
                            .border(
                                AppStroke.Thin,
                                if (selected) appPalette().primary.copy(alpha = 0.4f) else appPalette().separator,
                                RoundedCornerShape(AppRadius.Md),
                            )
                            .clickable { onSelect(m) }
                            .padding(horizontal = AppSpacing.Md, vertical = AppSpacing.Sm),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Column(Modifier.weight(1f)) {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Text(m.name, style = MaterialTheme.typography.bodyLarge, color = appPalette().ink, fontWeight = FontWeight.Medium)
                                if (m.badge != null) {
                                    Spacer(Modifier.width(AppSpacing.Xs))
                                    Text(
                                        m.badge,
                                        style = MaterialTheme.typography.labelSmall,
                                        color = appPalette().primary,
                                        modifier = Modifier
                                            .clip(RoundedCornerShape(AppRadius.Pill))
                                            .background(appPalette().primary.copy(alpha = 0.12f))
                                            .padding(horizontal = AppSpacing.Xs, vertical = 1.dp),
                                    )
                                }
                            }
                            if (m.caption != null) {
                                Spacer(Modifier.padding(2.dp))
                                Text(m.caption, style = MaterialTheme.typography.labelSmall, color = appPalette().labelTertiary)
                            }
                        }
                        if (selected) {
                            Icon(Icons.Rounded.Check, contentDescription = "已选", tint = appPalette().primary, modifier = Modifier.width(AppSizing.IconM))
                        } else {
                            Icon(Icons.Rounded.KeyboardArrowRight, contentDescription = null, tint = appPalette().labelTertiary)
                        }
                    }
                }
            }
            Spacer(Modifier.padding(AppSpacing.Lg))
        }
    }
}
