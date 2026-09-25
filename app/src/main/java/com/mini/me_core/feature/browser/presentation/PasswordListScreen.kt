package com.mini.me_core.feature.browser.presentation

import android.widget.Toast
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.ContentCopy
import androidx.compose.material.icons.rounded.Delete
import androidx.compose.material.icons.rounded.Lock
import androidx.compose.material.icons.rounded.Public
import androidx.compose.material.icons.rounded.Visibility
import androidx.compose.material.icons.rounded.VisibilityOff
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.mini.me_core.R
import com.mini.me_core.core.theme.Spacing
import com.mini.me_core.feature.browser.domain.PasswordStrength
import com.mini.me_core.feature.browser.domain.SavedPassword

/**
 * F4.3 密码管理面板：进入需解锁（主密码 / 生物识别），解锁后展示已保存密码列表。
 * 支持搜索、查看/隐藏、复制、自动填充、删除与密码强度指示。
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun PasswordListScreen(
    passwords: List<SavedPassword>,
    locked: Boolean,
    onUnlock: () -> Unit,
    onFill: (SavedPassword) -> Unit,
    onDelete: (String) -> Unit,
    onDismiss: () -> Unit
) {
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = false)
    val clipboard = LocalClipboardManager.current
    val context = LocalContext.current
    var query by remember { mutableStateOf("") }
    var revealHost by remember { mutableStateOf<String?>(null) }
    var deleteHost by remember { mutableStateOf<String?>(null) }

    ModalBottomSheet(onDismissRequest = onDismiss, sheetState = sheetState) {
        Column(modifier = Modifier.padding(bottom = Spacing.lg)) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = Spacing.lg),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    stringResource(R.string.browser_pwm_title),
                    style = MaterialTheme.typography.titleLarge,
                    modifier = Modifier.weight(1f)
                )
                if (!locked) {
                    TextButton(onClick = onDismiss) { Text(stringResource(R.string.common_close)) }
                }
            }
            Spacer(Modifier.height(Spacing.sm))

            if (locked) {
                // 锁定态：显示解锁引导
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(Spacing.xl),
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    Icon(
                        Icons.Rounded.Lock,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.size(40.dp)
                    )
                    Spacer(Modifier.height(Spacing.md))
                    Text(
                        stringResource(R.string.browser_pwm_locked_hint),
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Spacer(Modifier.height(Spacing.md))
                    TextButton(onClick = onUnlock) {
                        Text(stringResource(R.string.browser_pwm_unlock))
                    }
                }
            } else {
                OutlinedTextField(
                    value = query,
                    onValueChange = { query = it },
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = Spacing.lg),
                    singleLine = true,
                    placeholder = { Text(stringResource(R.string.browser_pwm_search_hint)) },
                    shape = RoundedCornerShape(12.dp),
                    textStyle = MaterialTheme.typography.bodyMedium
                )
                Spacer(Modifier.height(Spacing.sm))
                val filtered = remember(passwords, query) {
                    if (query.isBlank()) passwords
                    else passwords.filter {
                        it.host.contains(query, ignoreCase = true) || it.username.contains(query, ignoreCase = true)
                    }
                }
                if (filtered.isEmpty()) {
                    Text(
                        stringResource(R.string.browser_pwm_empty),
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.padding(Spacing.lg)
                    )
                } else {
                    LazyColumn(modifier = Modifier.heightIn(max = 460.dp)) {
                        items(filtered, key = { it.host }) { sp ->
                            val revealed = revealHost == sp.host
                            Column(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(horizontal = Spacing.lg, vertical = Spacing.sm)
                            ) {
                                Row(verticalAlignment = Alignment.CenterVertically) {
                                    Icon(
                                        Icons.Rounded.Public,
                                        contentDescription = null,
                                        tint = MaterialTheme.colorScheme.onSurfaceVariant,
                                        modifier = Modifier.size(18.dp)
                                    )
                                    Spacer(Modifier.width(Spacing.sm))
                                    Column(modifier = Modifier.weight(1f)) {
                                        Text(
                                            text = sp.host,
                                            style = MaterialTheme.typography.bodyMedium,
                                            maxLines = 1,
                                            overflow = TextOverflow.Ellipsis
                                        )
                                        Text(
                                            text = sp.username,
                                            style = MaterialTheme.typography.bodySmall,
                                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                                            maxLines = 1,
                                            overflow = TextOverflow.Ellipsis
                                        )
                                    }
                                    IconButton(onClick = { revealHost = if (revealed) null else sp.host }) {
                                        Icon(
                                            if (revealed) Icons.Rounded.VisibilityOff else Icons.Rounded.Visibility,
                                            contentDescription = null,
                                            tint = MaterialTheme.colorScheme.onSurfaceVariant,
                                            modifier = Modifier.size(18.dp)
                                        )
                                    }
                                }
                                Spacer(Modifier.height(Spacing.xs))
                                Row(verticalAlignment = Alignment.CenterVertically) {
                                    Text(
                                        text = if (revealed) sp.password else "• • • • • •",
                                        style = MaterialTheme.typography.bodySmall,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                                        modifier = Modifier.weight(1f)
                                    )
                                    StrengthBar(sp.strength)
                                    Spacer(Modifier.width(Spacing.sm))
                                    TextButton(onClick = {
                                        clipboard.setText(AnnotatedString(sp.password))
                                        Toast.makeText(context, context.getString(R.string.browser_pwm_copied), Toast.LENGTH_SHORT).show()
                                    }) {
                                        Icon(Icons.Rounded.ContentCopy, contentDescription = stringResource(R.string.browser_pwm_copy), modifier = Modifier.size(16.dp), tint = MaterialTheme.colorScheme.onSurfaceVariant)
                                    }
                                    TextButton(onClick = { onFill(sp) }) {
                                        Text(stringResource(R.string.browser_pwm_fill), style = MaterialTheme.typography.bodySmall)
                                    }
                                    IconButton(onClick = { deleteHost = sp.host }) {
                                        Icon(Icons.Rounded.Delete, contentDescription = stringResource(R.string.browser_pwm_delete), tint = MaterialTheme.colorScheme.error, modifier = Modifier.size(18.dp))
                                    }
                                }
                                HorizontalDivider(modifier = Modifier.padding(top = Spacing.sm))
                            }
                        }
                    }
                }
            }
        }
    }

    if (deleteHost != null) {
        AlertDialog(
            onDismissRequest = { deleteHost = null },
            title = { Text(stringResource(R.string.browser_pwm_delete)) },
            text = { Text(stringResource(R.string.browser_pwm_delete_confirm, deleteHost!!)) },
            confirmButton = {
                TextButton(onClick = { onDelete(deleteHost!!); deleteHost = null }) {
                    Text(stringResource(R.string.workspace_confirm))
                }
            },
            dismissButton = {
                TextButton(onClick = { deleteHost = null }) {
                    Text(stringResource(R.string.common_cancel))
                }
            }
        )
    }
}

/** 密码强度指示条：3 段，按等级点亮。 */
@Composable
private fun StrengthBar(strength: PasswordStrength) {
    val filled = when (strength) {
        PasswordStrength.WEAK -> 1
        PasswordStrength.MEDIUM -> 2
        PasswordStrength.STRONG -> 3
    }
    val color = when (strength) {
        PasswordStrength.WEAK -> MaterialTheme.colorScheme.error
        PasswordStrength.MEDIUM -> MaterialTheme.colorScheme.tertiary
        PasswordStrength.STRONG -> MaterialTheme.colorScheme.primary
    }
    Row(horizontalArrangement = Arrangement.spacedBy(2.dp)) {
        repeat(3) { i ->
            Box(
                modifier = Modifier
                    .size(width = 14.dp, height = 3.dp)
                    .background(if (i < filled) color else MaterialTheme.colorScheme.surfaceVariant, CircleShape)
            )
        }
    }
}
