package com.mini.me_core.feature.terminal.presentation.component

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Archive
import androidx.compose.material.icons.rounded.AudioFile
import androidx.compose.material.icons.rounded.Check
import androidx.compose.material.icons.rounded.EditNote
import androidx.compose.material.icons.rounded.Folder
import androidx.compose.material.icons.rounded.InsertDriveFile
import androidx.compose.material.icons.rounded.InsertPhoto
import androidx.compose.material.icons.rounded.Launch
import androidx.compose.material.icons.rounded.Terminal
import androidx.compose.material.icons.rounded.Code
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.mini.me_core.R
import com.mini.me_core.feature.terminal.domain.ContainerFileEntry
import com.mini.me_core.feature.terminal.domain.ContainerFileType
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/** 文件类型 → 图标。 */
internal fun fileTypeIcon(type: ContainerFileType): ImageVector = when (type) {
    ContainerFileType.FOLDER -> Icons.Rounded.Folder
    ContainerFileType.IMAGE -> Icons.Rounded.InsertPhoto
    ContainerFileType.SCRIPT -> Icons.Rounded.Terminal
    ContainerFileType.ARCHIVE -> Icons.Rounded.Archive
    ContainerFileType.CONFIG -> Icons.Rounded.Code
    ContainerFileType.EXECUTABLE -> Icons.Rounded.Launch
    ContainerFileType.AUDIO -> Icons.Rounded.AudioFile
    ContainerFileType.TEXT -> Icons.Rounded.EditNote
    ContainerFileType.OTHER -> Icons.Rounded.InsertDriveFile
}

/** 文件类型 → 图标底色（全部走 MaterialTheme，无硬编码色值）。 */
@Composable
internal fun fileTypeBg(type: ContainerFileType): Color = when (type) {
    ContainerFileType.FOLDER -> MaterialTheme.colorScheme.primaryContainer
    ContainerFileType.IMAGE -> MaterialTheme.colorScheme.tertiaryContainer
    ContainerFileType.SCRIPT -> MaterialTheme.colorScheme.secondaryContainer
    ContainerFileType.ARCHIVE -> MaterialTheme.colorScheme.primaryContainer
    ContainerFileType.CONFIG -> MaterialTheme.colorScheme.surfaceVariant
    ContainerFileType.EXECUTABLE -> MaterialTheme.colorScheme.errorContainer
    ContainerFileType.AUDIO -> MaterialTheme.colorScheme.tertiaryContainer
    ContainerFileType.TEXT -> MaterialTheme.colorScheme.surfaceVariant
    ContainerFileType.OTHER -> MaterialTheme.colorScheme.surfaceVariant
}

/** 文件类型 → 图标前景色。 */
@Composable
internal fun fileTypeFg(type: ContainerFileType): Color = when (type) {
    ContainerFileType.FOLDER -> MaterialTheme.colorScheme.onPrimaryContainer
    ContainerFileType.IMAGE -> MaterialTheme.colorScheme.onTertiaryContainer
    ContainerFileType.SCRIPT -> MaterialTheme.colorScheme.onSecondaryContainer
    ContainerFileType.ARCHIVE -> MaterialTheme.colorScheme.onPrimaryContainer
    ContainerFileType.CONFIG -> MaterialTheme.colorScheme.onSurfaceVariant
    ContainerFileType.EXECUTABLE -> MaterialTheme.colorScheme.onErrorContainer
    ContainerFileType.AUDIO -> MaterialTheme.colorScheme.onTertiaryContainer
    ContainerFileType.TEXT -> MaterialTheme.colorScheme.onSurfaceVariant
    ContainerFileType.OTHER -> MaterialTheme.colorScheme.onSurfaceVariant
}

internal fun formatSize(bytes: Long): String = when {
    bytes >= 1024 * 1024 -> "%.1f MB".format(bytes / (1024.0 * 1024.0))
    bytes >= 1024 -> "%.1f KB".format(bytes / 1024.0)
    bytes > 0 -> "$bytes B"
    else -> "-"
}

/** 智能时间格式：今天 HH:mm，今年 MM-dd，往年 yyyy-MM-dd。 */
internal fun formatSmartTime(epochSec: Long): String {
    if (epochSec <= 0) return "-"
    val ms = epochSec * 1000
    val now = Date()
    val target = Date(ms)
    val sdfYMD = SimpleDateFormat("yyyy-MM-dd", Locale.getDefault())
    val sdfMD = SimpleDateFormat("MM-dd", Locale.getDefault())
    val sdfHM = SimpleDateFormat("HH:mm", Locale.getDefault())
    return when {
        sdfYMD.format(now) == sdfYMD.format(target) -> sdfHM.format(target)
        target.year == now.year -> sdfMD.format(target)
        else -> sdfYMD.format(target)
    }
}

@Composable
internal fun FileRow(
    entry: ContainerFileEntry,
    type: ContainerFileType,
    childCount: Int?,
    selected: Boolean,
    onClick: () -> Unit,
    onLongClick: () -> Unit,
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(10.dp))
            .background(
                if (selected) MaterialTheme.colorScheme.primaryContainer
                else MaterialTheme.colorScheme.surface
            )
            .clickable(onClick = onClick)
            .padding(horizontal = 12.dp, vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Box(
            modifier = Modifier
                .size(40.dp)
                .clip(RoundedCornerShape(10.dp))
                .background(fileTypeBg(type)),
            contentAlignment = Alignment.Center
        ) {
            Icon(
                fileTypeIcon(type), null,
                tint = fileTypeFg(type),
                modifier = Modifier.size(22.dp)
            )
        }
        Spacer(Modifier.width(12.dp))
        Column(modifier = Modifier.weight(1f)) {
            Text(
                entry.name,
                style = MaterialTheme.typography.bodyLarge,
                color = MaterialTheme.colorScheme.onSurface,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
            val subtitle = when {
                entry.isDir -> childCount?.let { "$it ${stringResource(R.string.fm_items_suffix)}" } ?: "-"
                else -> "${formatSize(entry.sizeBytes)} · ${formatSmartTime(entry.modifiedAt)}"
            }
            Text(
                subtitle,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
        if (selected) {
            Icon(
                Icons.Rounded.Check, null,
                tint = MaterialTheme.colorScheme.primary,
                modifier = Modifier.size(20.dp)
            )
        }
    }
}
