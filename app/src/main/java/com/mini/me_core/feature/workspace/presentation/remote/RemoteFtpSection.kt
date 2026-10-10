package com.mini.me_core.feature.workspace.presentation.remote
import com.mini.me_core.core.theme.tokens.LocalCornerRadius
import com.mini.me_core.core.theme.tokens.PrimitiveSpacing

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.mini.me_core.R
import com.mini.me_core.core.theme.components.AppButton
import com.mini.me_core.core.theme.components.AppButtonSize
import com.mini.me_core.core.theme.components.AppButtonVariant
import com.mini.me_core.core.theme.components.AppCard
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.ContentCopy
import androidx.compose.material.icons.rounded.Dns
import androidx.compose.material.icons.rounded.Info
import androidx.compose.material.icons.rounded.Settings
import androidx.compose.material.icons.rounded.Visibility
import androidx.compose.material.icons.rounded.VisibilityOff

@Composable
fun WiFiFtpServerSection(viewModel: RemoteServerViewModel) {
    val isRunning by viewModel.ftpServerManager.isRunning.collectAsStateWithLifecycle()
    val serverUrl by viewModel.ftpServerManager.serverUrl.collectAsStateWithLifecycle()
    val port by viewModel.ftpServerManager.port.collectAsStateWithLifecycle()
    val username by viewModel.ftpServerManager.username.collectAsStateWithLifecycle()
    val password by viewModel.ftpServerManager.password.collectAsStateWithLifecycle()
    val isAnonymous by viewModel.ftpServerManager.isAnonymous.collectAsStateWithLifecycle()
    val autoStart by viewModel.ftpServerManager.autoStart.collectAsStateWithLifecycle()
    val isLanExposed by viewModel.ftpServerManager.isLanExposed.collectAsStateWithLifecycle()
    val errorMessage by viewModel.ftpServerManager.errorMessage.collectAsStateWithLifecycle()

    // 安全确认弹窗状态
    val pendingHostKey by viewModel.pendingHostKey.collectAsStateWithLifecycle()
    val pendingCleartextFtp by viewModel.pendingCleartextFtp.collectAsStateWithLifecycle()
    var showLanRiskDialog by remember { mutableStateOf(false) }

    var editPort by remember(port) { mutableStateOf(port.toString()) }
    var editUsername by remember(username) { mutableStateOf(username) }
    var editPassword by remember(password) { mutableStateOf(password) }
    var editPasswordVisible by remember { mutableStateOf(false) }
    var editAnonymous by remember(isAnonymous) { mutableStateOf(isAnonymous) }
    var editAutoStart by remember(autoStart) { mutableStateOf(autoStart) }
    val context = LocalContext.current
    val clipboard = LocalClipboardManager.current

    // ── SSH 首连主机指纹确认框（fail-closed：不决策则连接一直挂起/拒绝） ──
    pendingHostKey?.let { req ->
        AlertDialog(
            onDismissRequest = { viewModel.cancelPendingHostKey() },
            title = { Text("确认主机身份") },
            text = {
                Column {
                    Text("首次连接 ${req.hostKey}。请核对服务器指纹是否与管理员提供的一致：")
                    Spacer(modifier = Modifier.height(PrimitiveSpacing.Sm))
                    Text(
                        req.fingerprintSha256,
                        fontFamily = androidx.compose.ui.text.font.FontFamily.Monospace,
                        style = MaterialTheme.typography.bodySmall
                    )
                }
            },
            confirmButton = {
                Row {
                    TextButton(onClick = { viewModel.resolvePendingHostKey(com.mini.me_core.core.security.HostKeyTrustDecision.TRUST_ONCE) }) {
                        Text("本次信任")
                    }
                    TextButton(onClick = { viewModel.resolvePendingHostKey(com.mini.me_core.core.security.HostKeyTrustDecision.TRUST_ALWAYS) }) {
                        Text("永久信任")
                    }
                    TextButton(onClick = { viewModel.cancelPendingHostKey() }) {
                        Text("拒绝", color = MaterialTheme.colorScheme.error)
                    }
                }
            }
        )
    }

    // ── 明文 FTP 风险确认框 ──
    pendingCleartextFtp?.let { host ->
        AlertDialog(
            onDismissRequest = { viewModel.resolveCleartextFtp(false) },
            title = { Text("明文 FTP 风险") },
            text = {
                Text("连接 $host 使用明文 FTP：账号口令与文件在网络中明文传输，同网段可被嗅探。" +
                    "建议改用 SFTP。是否仍要明文连接该主机？（确认后仅对该主机生效）")
            },
            confirmButton = {
                Row {
                    TextButton(onClick = { viewModel.resolveCleartextFtp(false) }) {
                        Text("取消", color = MaterialTheme.colorScheme.error)
                    }
                    TextButton(onClick = { viewModel.resolveCleartextFtp(true) }) {
                        Text("确认并连接")
                    }
                }
            }
        )
    }

    // ── 开启局域网访问的明文风险二次确认框 ──
    if (showLanRiskDialog) {
        AlertDialog(
            onDismissRequest = { showLanRiskDialog = false },
            title = { Text("开启局域网访问？") },
            text = {
                Text("开启后 FTP 将对同网段所有设备开放，且为明文传输：账号口令与文件内容可被同网段嗅探。" +
                    "请确认当前网络可信（如家/办公室 Wi-Fi，非公共网络）。")
            },
            confirmButton = {
                Row {
                    TextButton(onClick = { showLanRiskDialog = false }) { Text("取消") }
                    TextButton(onClick = {
                        showLanRiskDialog = false
                        viewModel.setFtpLanExposed(true)
                    }) { Text("确认开启", color = MaterialTheme.colorScheme.error) }
                }
            }
        )
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(PrimitiveSpacing.Lg),
        verticalArrangement = Arrangement.spacedBy(PrimitiveSpacing.Md)
    ) {
        // ── 状态卡片：开关 + 运行状态 + 服务器地址（可复制） ──
        AppCard {
            Column(modifier = Modifier.padding(PrimitiveSpacing.Lg)) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(
                            Icons.Rounded.Dns,
                            contentDescription = null,
                            tint = if (isRunning) MaterialTheme.colorScheme.tertiary else MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.size(24.dp)
                        )
                        Spacer(modifier = Modifier.width(PrimitiveSpacing.Md))
                        Column {
                            Text(
                                text = "FTP",
                                style = MaterialTheme.typography.titleMedium,
                                color = MaterialTheme.colorScheme.onSurface
                            )
                            Text(
                                text = if (isRunning) stringResource(R.string.ftp_running, serverUrl)
                                else stringResource(R.string.ftp_not_running),
                                style = MaterialTheme.typography.bodySmall,
                                color = if (isRunning) MaterialTheme.colorScheme.tertiary else MaterialTheme.colorScheme.onSurfaceVariant,
                                modifier = Modifier.padding(top = 2.dp)
                            )
                        }
                    }
                    Switch(
                        checked = isRunning,
                        onCheckedChange = { viewModel.toggleFtpServer() }
                    )
                }

                if (isRunning) {
                    Spacer(modifier = Modifier.height(PrimitiveSpacing.Sm))
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                text = stringResource(R.string.remote_ftp_server_url),
                                style = MaterialTheme.typography.labelMedium,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                            Text(
                                text = serverUrl,
                                style = MaterialTheme.typography.bodyMedium,
                                color = MaterialTheme.colorScheme.onSurface,
                                fontWeight = FontWeight.Medium
                            )
                        }
                        IconButton(onClick = {
                            clipboard.setText(AnnotatedString(serverUrl))
                            android.widget.Toast.makeText(context, context.getString(R.string.remote_ftp_url_copied), android.widget.Toast.LENGTH_SHORT).show()
                        }) {
                            Icon(
                                Icons.Rounded.ContentCopy,
                                contentDescription = stringResource(R.string.remote_ftp_copy_url),
                                tint = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }
                }

                errorMessage?.let { error ->
                    Spacer(modifier = Modifier.height(PrimitiveSpacing.Sm))
                    Text(
                        text = context.getString(R.string.ftp_error, error),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.error
                    )
                }
            }
        }

        // ── 配置卡片 ──
        AppCard {
            Column(
                modifier = Modifier.padding(PrimitiveSpacing.Lg),
                verticalArrangement = Arrangement.spacedBy(PrimitiveSpacing.Md)
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(
                        Icons.Rounded.Settings,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.size(20.dp)
                    )
                    Spacer(modifier = Modifier.width(PrimitiveSpacing.Md))
                    Text(
                        text = stringResource(R.string.ftp_config_title),
                        style = MaterialTheme.typography.titleMedium,
                        color = MaterialTheme.colorScheme.onSurface
                    )
                }

                OutlinedTextField(
                    value = editPort,
                    onValueChange = { editPort = it.filter { ch -> ch.isDigit() } },
                    label = { Text(stringResource(R.string.ftp_listen_port)) },
                    modifier = Modifier.fillMaxWidth(),
                    singleLine = true
                )

                OutlinedTextField(
                    value = editUsername,
                    onValueChange = { editUsername = it },
                    label = { Text(stringResource(R.string.ftp_login_username)) },
                    modifier = Modifier.fillMaxWidth(),
                    singleLine = true,
                    enabled = !editAnonymous
                )

                OutlinedTextField(
                    value = editPassword,
                    onValueChange = { editPassword = it },
                    label = { Text(stringResource(R.string.ftp_login_password)) },
                    modifier = Modifier.fillMaxWidth(),
                    singleLine = true,
                    enabled = !editAnonymous,
                    visualTransformation = if (editPasswordVisible) VisualTransformation.None else PasswordVisualTransformation(),
                    trailingIcon = {
                        val image = if (editPasswordVisible) Icons.Rounded.Visibility else Icons.Rounded.VisibilityOff
                        IconButton(onClick = { editPasswordVisible = !editPasswordVisible }) {
                            Icon(image, contentDescription = stringResource(R.string.remote_toggle_password), tint = MaterialTheme.colorScheme.onSurfaceVariant)
                        }
                    }
                )
                // 查看口令入口：点击眼睛图标即可显示；系统已自动为未设置弱口令的账号生成强随机口令。
                if (!editAnonymous) {
                    Text(
                        text = "建议：使用强口令（至少 8 位、非纯数字）。未设置时系统已自动生成随机强口令，点击右侧眼睛图标可查看。",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column(modifier = Modifier.weight(1f)) {
                        Text(text = stringResource(R.string.ftp_allow_anonymous), style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurface)
                        Text(text = stringResource(R.string.ftp_anonymous_desc), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                    Switch(checked = editAnonymous, onCheckedChange = { editAnonymous = it })
                }

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column(modifier = Modifier.weight(1f)) {
                        Text(text = stringResource(R.string.ftp_auto_start), style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurface)
                        Text(text = stringResource(R.string.ftp_auto_start_desc), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                    Switch(checked = editAutoStart, onCheckedChange = { editAutoStart = it })
                }

                // 局域网访问开关：默认关（仅回环）。打开时必须经风险二次确认。
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column(modifier = Modifier.weight(1f)) {
                        Text(text = "局域网访问（明文暴露）", style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurface)
                        Text(
                            text = if (isLanExposed) "已对同网段开放（明文）" else "默认仅本机回环可访问",
                            style = MaterialTheme.typography.bodySmall,
                            color = if (isLanExposed) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                    Switch(
                        checked = isLanExposed,
                        onCheckedChange = { wantOn ->
                            if (wantOn) showLanRiskDialog = true else viewModel.setFtpLanExposed(false)
                        }
                    )
                }

                AppButton(
                    text = stringResource(R.string.ftp_save_config),
                    onClick = {
                        val p = editPort.toIntOrNull() ?: 2121
                        viewModel.saveFtpServerConfig(p, editUsername, editPassword, editAnonymous, editAutoStart)
                        android.widget.Toast.makeText(context, context.getString(R.string.ftp_config_saved), android.widget.Toast.LENGTH_SHORT).show()
                    },
                    modifier = Modifier.align(Alignment.End),
                    size = AppButtonSize.Medium,
                )
            }
        }

        // ── 使用说明卡片 ──
        AppCard {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(PrimitiveSpacing.Lg),
                verticalAlignment = Alignment.Top
            ) {
                Icon(
                    Icons.Rounded.Info,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.size(20.dp).padding(top = 2.dp)
                )
                Spacer(modifier = Modifier.width(PrimitiveSpacing.Md))
                Column {
                    Text(
                        text = stringResource(R.string.ftp_usage_title),
                        style = MaterialTheme.typography.titleMedium,
                        color = MaterialTheme.colorScheme.onSurface
                    )
                    Text(
                        text = stringResource(R.string.ftp_usage_desc),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.padding(top = 4.dp)
                    )
                }
            }
        }
    }
}
