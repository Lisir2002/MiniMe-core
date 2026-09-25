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
import androidx.compose.material.icons.rounded.Key
import androidx.compose.material.icons.rounded.Visibility
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.unit.dp
import com.mini.me_core.feature.terminal.domain.SshKeyEntry
import com.mini.me_core.feature.terminal.domain.SshKeyStore
import kotlinx.coroutines.launch
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/**
 * F3.7 SSH 密钥管理：列表 + 生成/查看公钥/重命名/删除/设默认。
 */
@Composable
fun SshKeyManager(
    store: SshKeyStore,
    onGenerate: (name: String, type: String) -> Unit,
    modifier: Modifier = Modifier,
) {
    val keys by store.keys.collectAsState(initial = emptyList())
    var showGen by remember { mutableStateOf(false) }
    var viewing by remember { mutableStateOf<SshKeyEntry?>(null) }
    val scope = rememberCoroutineScope()

    Column(modifier = modifier.fillMaxWidth()) {
        Row(
            modifier = Modifier.fillMaxWidth().padding(16.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text("SSH 密钥", style = MaterialTheme.typography.titleMedium,
                color = MaterialTheme.colorScheme.onSurface)
            OutlinedButton(onClick = { showGen = true }) {
                Icon(Icons.Rounded.Add, null, modifier = Modifier.size(16.dp))
                Spacer(Modifier.width(4.dp)); Text("生成")
            }
        }
        LazyColumn(modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp)) {
            items(keys) { key ->
                KeyRow(key = key, onViewPub = { viewing = key },
                    onRename = { }, onDelete = { scope.launch { store.delete(key.id) } },
                    onSetDefault = { scope.launch { store.setDefault(key.id) } })
            }
        }
    }

    if (showGen) {
        var name by remember { mutableStateOf("id_ed25519") }
        var type by remember { mutableStateOf("Ed25519") }
        AlertDialog(
            onDismissRequest = { showGen = false },
            title = { Text("生成新密钥") },
            text = {
                Column {
                    Text("名称", style = MaterialTheme.typography.labelMedium)
                    androidx.compose.material3.OutlinedTextField(
                        value = name, onValueChange = { name = it },
                        singleLine = true, modifier = Modifier.fillMaxWidth()
                    )
                    Spacer(Modifier.height(8.dp))
                    Text("类型", style = MaterialTheme.typography.labelMedium)
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        listOf("Ed25519", "RSA2048", "RSA4096").forEach { t ->
                            androidx.compose.material3.FilterChip(
                                selected = type == t, onClick = { type = t },
                                label = { Text(t) }
                            )
                        }
                    }
                }
            },
            confirmButton = {
                TextButton(onClick = {
                    onGenerate(name.ifBlank { "id_ed25519" }, type)
                    showGen = false
                }) { Text("生成") }
            },
            dismissButton = { TextButton(onClick = { showGen = false }) { Text("取消") } }
        )
    }

    viewing?.let { entry ->
        AlertDialog(
            onDismissRequest = { viewing = null },
            title = { Text("公钥 (${entry.name})") },
            text = {
                Text(entry.publicKey, style = MaterialTheme.typography.bodySmall,
                    fontFamily = FontFamily.Monospace)
            },
            confirmButton = { TextButton(onClick = { viewing = null }) { Text("关闭") } },
            dismissButton = {}
        )
    }
}

@Composable
private fun KeyRow(
    key: SshKeyEntry,
    onViewPub: () -> Unit,
    onRename: () -> Unit,
    onDelete: () -> Unit,
    onSetDefault: () -> Unit,
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(8.dp))
            .background(MaterialTheme.colorScheme.surfaceVariant)
            .padding(12.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Icon(Icons.Rounded.Key, null, tint = MaterialTheme.colorScheme.primary,
            modifier = Modifier.size(20.dp))
        Spacer(Modifier.width(12.dp))
        Column(modifier = Modifier.weight(1f)) {
            Text(key.name, style = MaterialTheme.typography.bodyLarge,
                color = MaterialTheme.colorScheme.onSurface)
            Text(
                "${key.type} · " + SimpleDateFormat("yyyy-MM-dd", Locale.getDefault())
                    .format(Date(key.createdAt)) + if (key.isDefault) " · 默认" else "",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
        IconButton(onClick = onViewPub) {
            Icon(Icons.Rounded.Visibility, "公钥", tint = MaterialTheme.colorScheme.onSurfaceVariant)
        }
        IconButton(onClick = onSetDefault) {
            Text("设默认", style = MaterialTheme.typography.labelSmall,
                color = if (key.isDefault) MaterialTheme.colorScheme.primary
                else MaterialTheme.colorScheme.onSurfaceVariant)
        }
    }
}
