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
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Dashboard
import androidx.compose.material.icons.rounded.Memory
import androidx.compose.material.icons.rounded.Storage
import androidx.compose.material.icons.rounded.Terminal
import androidx.compose.material3.Icon
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.unit.dp
import com.mini.me_core.feature.terminal.domain.ResourceSnapshot

/**
 * F3.4 资源监控卡片：2x2 网格 CPU/内存/存储/进程。
 */
@Composable
fun ResourceMonitorCard(
    snapshot: ResourceSnapshot,
    modifier: Modifier = Modifier,
) {
    Column(
        modifier = modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp)
            .clip(RoundedCornerShape(12.dp))
            .background(MaterialTheme.colorScheme.surfaceVariant)
            .padding(12.dp)
    ) {
        Text("资源监控", style = MaterialTheme.typography.titleSmall,
            color = MaterialTheme.colorScheme.onSurface)
        Spacer(Modifier.height(8.dp))
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            MetricCell(
                modifier = Modifier.weight(1f),
                icon = Icons.Rounded.Dashboard,
                label = "CPU",
                value = "%.0f%%".format(snapshot.cpuPercent),
                progress = snapshot.cpuPercent / 100f
            )
            MetricCell(
                modifier = Modifier.weight(1f),
                icon = Icons.Rounded.Memory,
                label = "内存",
                value = "${snapshot.memUsedMb}/${snapshot.memTotalMb}MB",
                progress = if (snapshot.memTotalMb > 0)
                    snapshot.memUsedMb.toFloat() / snapshot.memTotalMb else 0f
            )
        }
        Spacer(Modifier.height(8.dp))
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            MetricCell(
                modifier = Modifier.weight(1f),
                icon = Icons.Rounded.Storage,
                label = "存储",
                value = "%.0f%%".format(snapshot.storageUsedPercent),
                progress = snapshot.storageUsedPercent / 100f
            )
            MetricCell(
                modifier = Modifier.weight(1f),
                icon = Icons.Rounded.Terminal,
                label = "进程",
                value = "${snapshot.processCount}",
                progress = 0f
            )
        }
    }
}

@Composable
private fun MetricCell(
    icon: ImageVector,
    label: String,
    value: String,
    progress: Float,
    modifier: Modifier = Modifier,
) {
    Column(
        modifier = modifier
            .clip(RoundedCornerShape(8.dp))
            .background(MaterialTheme.colorScheme.surface)
            .padding(10.dp)
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Icon(icon, null, tint = MaterialTheme.colorScheme.primary,
                modifier = Modifier.size(16.dp))
            Spacer(Modifier.width(6.dp))
            Text(label, style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
        Spacer(Modifier.height(4.dp))
        Text(value, style = MaterialTheme.typography.titleSmall,
            color = MaterialTheme.colorScheme.onSurface)
        if (progress > 0f) {
            Spacer(Modifier.height(4.dp))
            LinearProgressIndicator(
                progress = { progress.coerceIn(0f, 1f) },
                modifier = Modifier.fillMaxWidth().height(4.dp)
            )
        }
    }
}
