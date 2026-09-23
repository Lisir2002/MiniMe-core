package com.mini.logs.ui.logs

import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material3.Checkbox
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.mini.me_core.core.theme.components.AppButton
import com.mini.me_core.core.theme.components.AppButtonSize
import com.mini.me_core.core.theme.components.AppButtonVariant
import com.mini.me_core.core.theme.components.AppChip
import com.mini.me_core.core.theme.components.AppChipColor
import com.mini.me_core.core.theme.components.AppChipVariant
import com.mini.me_core.core.theme.components.AppSectionHeader
import com.mini.me_core.core.theme.tokens.LocalAppTheme
import com.mini.me_core.core.theme.tokens.PrimitiveSpacing

/**
 * 高级筛选 BottomSheet：Tag 筛选 + 时间范围 + 排除规则。
 */
@OptIn(ExperimentalMaterial3Api::class, ExperimentalFoundationApi::class)
@Composable
fun FilterSheet(
    tagCounts: List<Pair<String, Int>>,
    selectedTags: Set<String>,
    excludedTags: Set<String>,
    onToggleTag: (String) -> Unit,
    onToggleExcludeTag: (String) -> Unit,
    onClearTags: () -> Unit,
    onReset: () -> Unit,
    onDismiss: () -> Unit,
) {
    val colors = LocalAppTheme.current.colors
    var tagSearchQuery by remember { mutableStateOf("") }

    val filteredTags = remember(tagCounts, tagSearchQuery) {
        if (tagSearchQuery.isBlank()) tagCounts
        else tagCounts.filter { it.first.contains(tagSearchQuery, ignoreCase = true) }
    }

    ModalBottomSheet(onDismissRequest = onDismiss) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = PrimitiveSpacing.Lg)
                .verticalScroll(rememberScrollState())
                .padding(bottom = PrimitiveSpacing.Xxl),
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(
                    text = "高级筛选",
                    fontWeight = FontWeight.Bold,
                    fontSize = 18.sp,
                    color = colors.textPrimary,
                )
                Row {
                    TextButton(onClick = onReset) { Text("重置") }
                    TextButton(onClick = onDismiss) { Text("完成") }
                }
            }

            Spacer(Modifier.height(PrimitiveSpacing.Md))

            // Tag 搜索
            OutlinedTextField(
                value = tagSearchQuery,
                onValueChange = { tagSearchQuery = it },
                modifier = Modifier.fillMaxWidth(),
                placeholder = { Text("搜索 Tag...") },
                singleLine = true,
                colors = OutlinedTextFieldDefaults.colors(
                    focusedBorderColor = colors.brandPrimary,
                    unfocusedBorderColor = colors.borderDefault,
                    focusedContainerColor = colors.surfaceCard,
                    unfocusedContainerColor = colors.surfaceCard,
                ),
            )

            Spacer(Modifier.height(PrimitiveSpacing.Sm))

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                AppSectionHeader(
                    title = "Tag 筛选",
                    subtitle = "长按 Tag 可设为排除",
                    modifier = Modifier.weight(1f),
                )
                if (selectedTags.isNotEmpty() || excludedTags.isNotEmpty()) {
                    TextButton(onClick = onClearTags) { Text("清除") }
                }
            }

            // Tag 列表
            filteredTags.forEach { (tag, count) ->
                val isSelected = tag in selectedTags
                val isExcluded = tag in excludedTags
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .combinedClickable(
                            onClick = { onToggleTag(tag) },
                            onLongClick = { onToggleExcludeTag(tag) },
                        )
                        .padding(vertical = PrimitiveSpacing.Xs, horizontal = PrimitiveSpacing.Xs),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Checkbox(
                        checked = isSelected,
                        onCheckedChange = { onToggleTag(tag) },
                    )
                    Spacer(Modifier.width(PrimitiveSpacing.Sm))
                    Text(
                        text = tag,
                        color = when {
                            isExcluded -> colors.error
                            isSelected -> colors.brandPrimary
                            else -> colors.textPrimary
                        },
                        fontSize = 13.sp,
                        fontWeight = if (isSelected || isExcluded) FontWeight.Bold else FontWeight.Normal,
                        modifier = Modifier.weight(1f),
                    )
                    if (isExcluded) {
                        Text(
                            text = "排除",
                            color = colors.error,
                            fontSize = 10.sp,
                            modifier = Modifier.background(
                                colors.errorContainer,
                                androidx.compose.foundation.shape.RoundedCornerShape(3.dp),
                            ).padding(horizontal = 4.dp, vertical = 1.dp),
                        )
                        Spacer(Modifier.width(PrimitiveSpacing.Xs))
                    }
                    Text(
                        text = "($count)",
                        color = colors.textTertiary,
                        fontSize = 11.sp,
                    )
                }
            }

            Spacer(Modifier.height(PrimitiveSpacing.Lg))
            HorizontalDivider(color = colors.borderMuted)

            // 已排除 Tag 显示
            if (excludedTags.isNotEmpty()) {
                Spacer(Modifier.height(PrimitiveSpacing.Md))
                AppSectionHeader(title = "已排除的 Tag")
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(PrimitiveSpacing.Xs),
                ) {
                    excludedTags.forEach { tag ->
                        AppChip(
                            text = "$tag ×",
                            variant = AppChipVariant.Filled,
                            chipColor = AppChipColor.Error,
                            modifier = Modifier.clickable { onToggleExcludeTag(tag) },
                        )
                    }
                }
            }

            Spacer(Modifier.height(PrimitiveSpacing.Lg))
            AppButton(
                text = "应用筛选",
                onClick = onDismiss,
                modifier = Modifier.fillMaxWidth(),
            )
        }
    }
}
