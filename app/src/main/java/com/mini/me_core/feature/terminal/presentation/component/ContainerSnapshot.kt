package com.mini.me_core.feature.terminal.presentation.component

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Add
import androidx.compose.material.icons.rounded.Delete
import androidx.compose.material.icons.rounded.Restore
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.unit.dp
import com.mini.me_core.feature.terminal.domain.ContainerSnapshot
import com.mini.me_core.feature.terminal.domain.ContainerSnapshotManager
import kotlinx.coroutines.launch
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/**
 * F3.8 容器快照管理：创建/列表/恢复/删除。
 */
@Composable
fun ContainerSnapshotScreen(
    manager: ContainerSnapshotManager,
    modifier: Modifier = Modifier,
) {
    var snapshots by remember { mutableStateOf(manager.list()) }
    var showCreate by remember { mutableStateOf(false) }
    var confirmRestore by remember { mutableStateOf<ContainerSnapshot?>(null) }
    val scope = rememberCoroutineScope()

    Column(modifier = modifier.fillMaxWidth()) {
        Row(
            modifier = Modifier.fillMaxWidth().padding(16.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text("容器快照", style = MaterialTheme.typography.titleMedium,
                color = MaterialTheme.colorScheme.onSurface)
            OutlinedButton(onClick = { showCreate = true }) {
                Icon(Icons.Rounded.Add, null, modifier = Modifier.size(16.dp))
                Spacer(Modifier.width(4.dp)); Text("新建快照")
            }
        }
        LazyColumn(modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp)) {
            items(snapshots) { snap ->
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(8.dp))
                        .background(MaterialTheme.colorScheme.surfaceVariant)
                        .padding(12.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column(modifier = Modifier.weight(1f)) {
                        Text(snap.name, style = MaterialTheme.typography.bodyLarge,
                            color = MaterialTheme.colorScheme.onSurface)
                        Text(
                            "%.1f MB · ".format(snap.sizeBytes / 1024.0 / 1024.0) +
                                SimpleDateFormat("yyyy-MM-dd HH:mm", Locale.getDefault())
                                    .format(Date(snap.createdAt)),
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                    IconButton(onClick = { confirmRestore = snap }) {
                        Icon(Icons.Rounded.Restore, "恢复",
                            tint = MaterialTheme.colorScheme.primary)
                    }
                    IconButton(onClick = {
                        scope.launch { manager.delete(snap); snapshots = manager.list() }
                    }) {
                        Icon(Icons.Rounded.Delete, "删除",
                            tint = MaterialTheme.colorScheme.error)
                    }
                }
            }
        }
    }

    if (showCreate) {
        var name by remember { mutableStateOf("") }
        AlertDialog(
            onDismissRequest = { showCreate = false },
            title = { Text("新建快照") },
            text = {
                OutlinedTextField(
                    value = name, onValueChange = { name = it },
                    singleLine = true, label = { Text("快照名称") },
                    modifier = Modifier.fillMaxWidth()
                )
            },
            confirmButton = {
                TextButton(onClick = {
                    showCreate = false
                    scope.launch {
                        manager.create(name, "")
                        snapshots = manager.list()
                    }
                }) { Text("创建") }
            },
            dismissButton = { TextButton(onClick = { showCreate = false }) { Text("取消") } }
        )
    }

    confirmRestore?.let { snap ->
        AlertDialog(
            onDismissRequest = { confirmRestore = null },
            title = { Text("恢复快照？") },
            text = { Text("当前容器将被快照「${snap.name}」覆盖，此操作不可撤销。") },
            confirmButton = {
                TextButton(onClick = {
                    val target = snap
                    confirmRestore = null
                    scope.launch {
                        manager.restore(target)
                        snapshots = manager.list()
                    }
                }) { Text("恢复", color = MaterialTheme.colorScheme.error) }
            },
            dismissButton = { TextButton(onClick = { confirmRestore = null }) { Text("取消") } }
        )
    }
}
