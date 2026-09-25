package com.mini.me_core.feature.terminal.presentation.component

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Bolt
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.unit.dp
import com.mini.me_core.feature.terminal.domain.QuickCommand
import com.mini.me_core.feature.terminal.domain.QuickCommandRepository

/**
 * F3.3 快捷命令面板：底部滑出 50% 屏高，搜索框 + 分类 Tab + 2 列网格命令卡片。
 *
 * 点击命令直接回调 [onRun]（输入终端并执行），长按回调 [onCopy]（仅填入输入框）。
 */
@Composable
fun QuickCommandPanel(
    repo: QuickCommandRepository,
    recent: List<QuickCommand>,
    custom: List<QuickCommand>,
    onRun: (QuickCommand) -> Unit,
    onCopy: (QuickCommand) -> Unit,
    onDismiss: () -> Unit,
) {
    var query by remember { mutableStateOf("") }
    var category by remember { mutableStateOf("all") }

    fun filter(list: List<QuickCommand>): List<QuickCommand> = list.filter {
        (category == "all" || it.category == category) &&
            (query.isBlank() || it.name.contains(query, true) || it.command.contains(query, true))
    }

    val all = repo.builtin + custom
    val shown = filter(all)

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .background(MaterialTheme.colorScheme.surface)
            .padding(12.dp)
    ) {
        Text(
            "快捷命令",
            style = MaterialTheme.typography.titleMedium,
            color = MaterialTheme.colorScheme.onSurface
        )
        Spacer(Modifier.height(8.dp))
        OutlinedTextField(
            value = query, onValueChange = { query = it },
            singleLine = true,
            label = { Text("搜索命令") },
            modifier = Modifier.fillMaxWidth()
        )
        Spacer(Modifier.height(8.dp))
        LazyRow(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
            item {
                FilterChip(selected = category == "all", onClick = { category = "all" },
                    label = { Text("全部") })
            }
            items(repo.categories) { c ->
                FilterChip(selected = category == c, onClick = { category = c },
                    label = { Text(c) })
            }
        }
        if (recent.isNotEmpty() && query.isBlank() && category == "all") {
            Spacer(Modifier.height(8.dp))
            Text("最近使用", style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant)
            LazyRow(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                items(recent) { cmd ->
                    FilterChip(selected = false, onClick = { onRun(cmd) }, label = { Text(cmd.name) })
                }
            }
        }
        Spacer(Modifier.height(8.dp))
        LazyVerticalGrid(
            columns = GridCells.Fixed(2),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
            modifier = Modifier.fillMaxWidth().height(260.dp)
        ) {
            items(shown) { cmd ->
                CommandCard(cmd = cmd, onClick = { onRun(cmd) }, onLongClick = { onCopy(cmd) })
            }
        }
    }
}

@Composable
private fun CommandCard(cmd: QuickCommand, onClick: () -> Unit, onLongClick: () -> Unit) {
    Row(
        modifier = Modifier
            .clip(RoundedCornerShape(10.dp))
            .background(MaterialTheme.colorScheme.surfaceVariant)
            .clickable(onClick = onClick)
            .padding(12.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Icon(
            Icons.Rounded.Bolt, null,
            tint = MaterialTheme.colorScheme.primary,
            modifier = Modifier.size(20.dp)
        )
        Spacer(Modifier.width(8.dp))
        Column {
            Text(cmd.name, style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurface)
            Text(cmd.command, style = MaterialTheme.typography.bodySmall,
                fontFamily = FontFamily.Monospace,
                color = MaterialTheme.colorScheme.onSurfaceVariant, maxLines = 1)
        }
    }
}
