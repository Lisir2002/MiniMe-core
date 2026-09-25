package com.mini.me_core.feature.agent.presentation.component.markdown

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

/**
 * MiniMe 表格组件（F2.1）。
 * - 表头 primaryContainer 背景，onPrimaryContainer 粗体；
 * - 单元格内边距 8dp 水平 / 6dp 垂直；边框 1dp outlineVariant；
 * - 隔行变色：奇数行 surface 30% 透明度；超宽表格横向滚动。
 */
@Composable
fun MiniMeTable(
    header: List<List<MdInline>>,
    rows: List<List<List<MdInline>>>,
    onOpenUrl: ((String) -> Unit)?,
    modifier: Modifier = Modifier,
) {
    val colors = MaterialTheme.colorScheme
    if (header.isEmpty()) return
    Column(
        modifier = modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(8.dp))
            .border(BorderStroke(1.dp, colors.outlineVariant), RoundedCornerShape(8.dp)),
    ) {
        Row(
            Modifier
                .fillMaxWidth()
                .horizontalScroll(rememberScrollState())
        ) {
            Column(Modifier.widthIn(min = (header.size * 120).dp)) {
                // 表头
                Row(Modifier.background(colors.primaryContainer)) {
                    header.forEach { cell ->
                        Box(
                            Modifier
                                .widthIn(min = 110.dp)
                                .border(BorderStroke(0.5.dp, colors.outlineVariant.copy(alpha = 0.5f)))
                                .padding(horizontal = 8.dp, vertical = 6.dp)
                        ) {
                            LinkableInlines(
                                inlines = cell,
                                baseStyle = MaterialTheme.typography.bodyMedium.copy(
                                    color = colors.onPrimaryContainer,
                                    fontWeight = FontWeight.Bold,
                                    fontSize = 13.sp,
                                    lineHeight = 18.sp,
                                ),
                                onOpenUrl = onOpenUrl,
                            )
                        }
                    }
                }
                // 数据行（隔行变色）
                rows.forEachIndexed { rIdx, row ->
                    Row(
                        Modifier.background(
                            if (rIdx % 2 == 1) colors.surface.copy(alpha = 0.3f) else Color.Transparent
                        )
                    ) {
                        for (c in header.indices) {
                            val cell = row.getOrElse(c) { emptyList() }
                            Box(
                                Modifier
                                    .widthIn(min = 110.dp)
                                    .border(BorderStroke(0.5.dp, colors.outlineVariant.copy(alpha = 0.5f)))
                                    .padding(horizontal = 8.dp, vertical = 6.dp)
                            ) {
                                LinkableInlines(
                                    inlines = cell,
                                    baseStyle = MaterialTheme.typography.bodyMedium.copy(
                                        color = colors.onSurface,
                                        fontSize = 13.sp,
                                        lineHeight = 18.sp,
                                    ),
                                    onOpenUrl = onOpenUrl,
                                )
                            }
                        }
                    }
                }
            }
        }
    }
}
