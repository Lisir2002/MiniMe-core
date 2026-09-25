package com.mini.me_core.feature.terminal.presentation.component

import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
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
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Add
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material3.AssistChip
import androidx.compose.material3.Button
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExposedDropdownMenuBox
import androidx.compose.material3.ExposedDropdownMenuAnchorType
import androidx.compose.material3.ExposedDropdownMenuDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.SegmentedButton
import androidx.compose.material3.SegmentedButtonDefaults
import androidx.compose.material3.SingleChoiceSegmentedButtonRow
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.mini.me_core.R
import com.mini.me_core.core.theme.Spacing
import com.mini.me_core.core.theme.components.AppBottomSheet
import com.mini.me_core.core.theme.components.AppTextField
import com.mini.me_core.feature.agent.domain.container.ContainerProfile
import com.mini.me_core.feature.agent.domain.container.RootfsSource
import com.mini.me_core.feature.settings.data.repository.ExecutionMode
import com.mini.me_core.feature.workspace.domain.model.RemoteConnection
import com.mini.me_core.feature.workspace.domain.model.RemoteProtocol
import androidx.compose.material3.rememberModalBottomSheetState

/**
 * 添加/编辑镜像的 ModalBottomSheet。
 *
 * 顶部 SegmentedButton 切换本地镜像 / 远程 SSH。
 * 本地镜像分支：名称、shell 路径、额外绑定（chip 形式）、额外参数、选 tar.gz 文件。
 * 远程 SSH 分支：名称、下拉选工作区已配置的 SFTP 通道、远程工作区路径。
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ProfileEditSheet(
    initial: ContainerProfile?,
    remoteConnections: List<RemoteConnection>,
    onDismiss: () -> Unit,
    onConfirm: (ContainerProfile) -> Unit
) {
    val context = LocalContext.current
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    // SFTP 通道才适合 SSH exec（FTP/LOCAL 不走 sshj）
    val sshConnections = remoteConnections.filter { it.protocol == RemoteProtocol.SFTP }

    var mode by remember { mutableStateOf(initial?.mode ?: ExecutionMode.LOCAL_PROOT) }
    var name by remember { mutableStateOf(initial?.name ?: "") }
    // 本地镜像字段
    var shellPath by remember { mutableStateOf(initial?.shellPath ?: "/bin/sh") }
    var bindings by remember { mutableStateOf(initial?.extraBindings ?: emptyList()) }
    var newBinding by remember { mutableStateOf("") }
    var argsText by remember { mutableStateOf(initial?.extraArgs?.joinToString(" ") ?: "") }
    val initialUri = (initial?.rootfsSource as? RootfsSource.LocalFile)?.uri
    var pickedUri by remember { mutableStateOf(initialUri) }
    var pickedFileName by remember { mutableStateOf<String?>(null) }
    var pickedFileSize by remember { mutableStateOf<Long?>(null) }
    val pickLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.OpenDocument()
    ) { uri ->
        if (uri != null) {
            pickedUri = uri.toString()
            // 查询文件名和大小
            runCatching {
                context.contentResolver.query(uri, null, null, null, null)?.use { cursor ->
                    val nameIdx = cursor.getColumnIndex(android.provider.OpenableColumns.DISPLAY_NAME)
                    val sizeIdx = cursor.getColumnIndex(android.provider.OpenableColumns.SIZE)
                    if (cursor.moveToFirst()) {
                        pickedFileName = if (nameIdx >= 0) cursor.getString(nameIdx) else null
                        pickedFileSize = if (sizeIdx >= 0) cursor.getLong(sizeIdx) else null
                    }
                }
            }
        }
    }
    // 远程 SSH 字段
    val initialSsh = (initial?.rootfsSource as? RootfsSource.RemoteSsh)
    var selectedConnId by remember { mutableStateOf(initialSsh?.connectionId ?: sshConnections.firstOrNull()?.id ?: "") }
    var remotePath by remember { mutableStateOf(initialSsh?.remoteWorkspacePath ?: "") }
    var connExpanded by remember { mutableStateOf(false) }

    AppBottomSheet(
        onDismiss = onDismiss,
        sheetState = sheetState,
        containerColor = MaterialTheme.colorScheme.surface
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .verticalScroll(rememberScrollState())
                .padding(horizontal = Spacing.lg)
                .padding(bottom = Spacing.xl),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Text(
                text = if (initial == null) stringResource(R.string.container_add_image)
                else stringResource(R.string.container_edit_image),
                style = MaterialTheme.typography.titleMedium,
                color = MaterialTheme.colorScheme.onSurface
            )

            SingleChoiceSegmentedButtonRow(modifier = Modifier.fillMaxWidth()) {
                SegmentedButton(
                    selected = mode == ExecutionMode.LOCAL_PROOT,
                    onClick = { mode = ExecutionMode.LOCAL_PROOT },
                    shape = SegmentedButtonDefaults.itemShape(index = 0, count = 2)
                ) { Text(stringResource(R.string.container_local_image)) }
                SegmentedButton(
                    selected = mode == ExecutionMode.REMOTE_SSH,
                    onClick = { mode = ExecutionMode.REMOTE_SSH },
                    shape = SegmentedButtonDefaults.itemShape(index = 1, count = 2)
                ) { Text(stringResource(R.string.container_remote_ssh)) }
            }

            AppTextField(
                value = name,
                onValueChange = { name = it },
                label = { Text(stringResource(R.string.common_name)) },
                singleLine = true,
                modifier = Modifier.fillMaxWidth()
            )

            if (mode == ExecutionMode.LOCAL_PROOT) {
                AppTextField(
                    value = shellPath,
                    onValueChange = { shellPath = it },
                    label = { Text(stringResource(R.string.container_shell_path)) },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )

                // 额外绑定目录：chip 列表 + 输入框添加
                Text(
                    text = stringResource(R.string.tc_bindings_hint),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                // 已有绑定 chips
                if (bindings.isNotEmpty()) {
                    Row(
                        horizontalArrangement = Arrangement.spacedBy(6.dp),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        bindings.forEach { binding ->
                            Row(
                                modifier = Modifier
                                    .clip(RoundedCornerShape(16.dp))
                                    .background(MaterialTheme.colorScheme.surfaceVariant)
                                    .padding(horizontal = 12.dp, vertical = 6.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Text(binding, fontSize = 12.sp)
                                Spacer(modifier = Modifier.width(4.dp))
                                Icon(
                                    Icons.Rounded.Close,
                                    contentDescription = null,
                                    modifier = Modifier
                                        .size(14.dp)
                                        .clickable { bindings = bindings.filterNot { it == binding } }
                                )
                            }
                        }
                    }
                }
                // 新绑定输入行
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    AppTextField(
                        value = newBinding,
                        onValueChange = { newBinding = it },
                        label = { Text(stringResource(R.string.tc_add_binding)) },
                        singleLine = true,
                        modifier = Modifier.weight(1f)
                    )
                    IconButton(
                        onClick = {
                            if (newBinding.isNotBlank()) {
                                bindings = bindings + newBinding.trim()
                                newBinding = ""
                            }
                        }
                    ) {
                        Icon(Icons.Rounded.Add, contentDescription = null)
                    }
                }

                AppTextField(
                    value = argsText,
                    onValueChange = { argsText = it },
                    label = { Text(stringResource(R.string.container_extra_proot_args)) },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )

                // 选择 tar.gz 文件
                Spacer(modifier = Modifier.size(Spacing.xs))
                TextButton(
                    onClick = { pickLauncher.launch(arrayOf("*/*")) }
                ) {
                    Text(
                        when {
                            pickedFileName != null -> {
                                val sizeMb = pickedFileSize?.let { "%.1f MB".format(it / (1024.0 * 1024.0)) } ?: ""
                                "$pickedFileName $sizeMb".trim()
                            }
                            pickedUri != null -> stringResource(R.string.container_file_selected)
                            else -> stringResource(R.string.container_select_image_file)
                        }
                    )
                }
            } else {
                if (sshConnections.isEmpty()) {
                    Text(
                        text = stringResource(R.string.container_no_sftp_channel),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.error
                    )
                } else {
                    ExposedDropdownMenuBox(
                        expanded = connExpanded,
                        onExpandedChange = { connExpanded = !connExpanded }
                    ) {
                        val selectedName = sshConnections.firstOrNull { it.id == selectedConnId }?.name
                            ?: stringResource(R.string.container_select_ssh_channel)
                        AppTextField(
                            value = selectedName,
                            onValueChange = {},
                            readOnly = true,
                            label = { Text(stringResource(R.string.container_ssh_channel)) },
                            trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded = connExpanded) },
                            modifier = Modifier
                                .fillMaxWidth()
                                .menuAnchor(ExposedDropdownMenuAnchorType.PrimaryNotEditable)
                        )
                        ExposedDropdownMenu(
                            expanded = connExpanded,
                            onDismissRequest = { connExpanded = false }
                        ) {
                            sshConnections.forEach { conn ->
                                DropdownMenuItem(
                                    text = { Text("${conn.name} (${conn.host}:${conn.port})") },
                                    onClick = {
                                        selectedConnId = conn.id
                                        if (remotePath.isBlank()) {
                                            remotePath = "/home/${conn.username}/workspace"
                                        }
                                        connExpanded = false
                                    }
                                )
                            }
                        }
                    }
                    AppTextField(
                        value = remotePath,
                        onValueChange = { remotePath = it },
                        label = { Text(stringResource(R.string.container_remote_workspace_path)) },
                        placeholder = { Text("/home/user/workspace") },
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth()
                    )
                    Text(
                        text = stringResource(R.string.container_remote_workspace_desc),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }

            Spacer(modifier = Modifier.size(Spacing.xs))
            // 全宽确认按钮
            Button(
                onClick = {
                    val profile = buildProfile(
                        mode = mode,
                        name = name,
                        shellPath = shellPath,
                        bindings = bindings,
                        argsText = argsText,
                        pickedUri = pickedUri,
                        selectedConnId = selectedConnId,
                        remotePath = remotePath
                    )
                    if (profile != null) onConfirm(profile)
                },
                enabled = canConfirm(mode, pickedUri, selectedConnId, sshConnections),
                modifier = Modifier.fillMaxWidth()
            ) {
                Text(if (initial == null) stringResource(R.string.common_add) else stringResource(R.string.common_save))
            }
        }
    }
}

/** 据表单状态构造 ContainerProfile；校验不通过返回 null。 */
private fun buildProfile(
    mode: ExecutionMode,
    name: String,
    shellPath: String,
    bindings: List<String>,
    argsText: String,
    pickedUri: String?,
    selectedConnId: String,
    remotePath: String
): ContainerProfile? {
    return when (mode) {
        ExecutionMode.LOCAL_PROOT -> {
            if (pickedUri == null) return null
            val args = argsText.split(' ').map { it.trim() }.filter { it.isNotEmpty() }
            ContainerProfile(
                id = "", // 由调用方覆写
                name = name,
                rootfsSource = RootfsSource.LocalFile(pickedUri),
                shellPath = shellPath.ifBlank { null },
                extraBindings = bindings,
                extraArgs = args,
                isBuiltin = false,
                mode = ExecutionMode.LOCAL_PROOT
            )
        }

        ExecutionMode.REMOTE_SSH -> {
            if (selectedConnId.isBlank()) return null
            ContainerProfile(
                id = "", // 由调用方覆写
                name = name,
                rootfsSource = RootfsSource.RemoteSsh(selectedConnId, remotePath),
                shellPath = null,
                isBuiltin = false,
                mode = ExecutionMode.REMOTE_SSH
            )
        }
    }
}

/** 保存按钮可用条件：本地镜像需选了文件，远程 SSH 需选了通道。 */
private fun canConfirm(
    mode: ExecutionMode,
    pickedUri: String?,
    selectedConnId: String,
    sshConnections: List<RemoteConnection>
): Boolean = when (mode) {
    ExecutionMode.LOCAL_PROOT -> pickedUri != null
    ExecutionMode.REMOTE_SSH -> sshConnections.isNotEmpty() && selectedConnId.isNotBlank()
}
