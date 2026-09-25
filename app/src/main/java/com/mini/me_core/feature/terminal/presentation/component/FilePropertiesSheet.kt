package com.mini.me_core.feature.terminal.presentation.component

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.unit.dp
import com.mini.me_core.R
import com.mini.me_core.feature.terminal.domain.ContainerFileEntry
import com.mini.me_core.feature.terminal.domain.ContainerFileType

/**
 * P1 模块6：文件属性 BottomSheet。
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun FilePropertiesSheet(
    entry: ContainerFileEntry,
    type: ContainerFileType,
    childCount: Int?,
    onDismiss: () -> Unit,
    onCopyPath: () -> Unit,
) {
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    ModalBottomSheet(onDismissRequest = onDismiss, sheetState = sheetState) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 20.dp, vertical = 8.dp)
        ) {
            Box(
                modifier = Modifier
                    .size(56.dp)
                    .clip(RoundedCornerShape(14.dp))
                    .background(fileTypeBg(type))
                    .align(Alignment.CenterHorizontally),
                contentAlignment = Alignment.Center
            ) {
                Icon(fileTypeIcon(type), null, tint = fileTypeFg(type),
                    modifier = Modifier.size(30.dp))
            }
            Spacer(Modifier.height(12.dp))
            Text(entry.name, style = MaterialTheme.typography.titleMedium,
                color = MaterialTheme.colorScheme.onSurface,
                modifier = Modifier.align(Alignment.CenterHorizontally))
            Spacer(Modifier.height(16.dp))

            PropRow(stringResource(R.string.fm_type), typeLabel(type))
            if (entry.isDir && childCount != null) {
                PropRow(stringResource(R.string.fm_contains_items, childCount), "")
            }
            PropRow(stringResource(R.string.fm_size),
                if (entry.isDir) "-" else "${formatSize(entry.sizeBytes)} (${entry.sizeBytes} B)")
            PropRow(stringResource(R.string.fm_location), entry.path, mono = true)
            PropRow(stringResource(R.string.fm_modified), formatSmartTime(entry.modifiedAt))
            PropRow(stringResource(R.string.fm_permissions), entry.permissions, mono = true)
            PropRow(stringResource(R.string.fm_owner), entry.owner)

            Spacer(Modifier.height(8.dp))
            TextButton(onClick = onCopyPath, modifier = Modifier.align(Alignment.End)) {
                Text(stringResource(R.string.fm_copy_path))
            }
            Spacer(Modifier.height(8.dp))
        }
    }
}

@Composable
private fun PropRow(label: String, value: String, mono: Boolean = false) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 6.dp),
        verticalAlignment = Alignment.Top
    ) {
        Text(label, style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.width(96.dp))
        Text(
            value,
            style = MaterialTheme.typography.bodyMedium.copy(
                fontFamily = if (mono) FontFamily.Monospace else FontFamily.Default
            ),
            color = MaterialTheme.colorScheme.onSurface
        )
    }
}

@Composable
private fun typeLabel(type: ContainerFileType): String = when (type) {
    ContainerFileType.FOLDER -> stringResource(R.string.fm_type_folder)
    ContainerFileType.TEXT -> stringResource(R.string.fm_type_text)
    ContainerFileType.IMAGE -> stringResource(R.string.fm_type_image)
    ContainerFileType.SCRIPT -> stringResource(R.string.fm_type_script)
    ContainerFileType.ARCHIVE -> stringResource(R.string.fm_type_archive)
    ContainerFileType.CONFIG -> stringResource(R.string.fm_type_config)
    ContainerFileType.EXECUTABLE -> stringResource(R.string.fm_type_executable)
    ContainerFileType.AUDIO -> stringResource(R.string.fm_type_audio)
    ContainerFileType.OTHER -> stringResource(R.string.fm_type_other)
}
