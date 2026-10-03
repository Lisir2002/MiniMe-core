package com.mini.me_core.feature.editor.snippets.ui

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.mini.me_core.R
import com.mini.me_core.feature.editor.snippets.model.Snippet

/**
 * 新增/编辑自定义片段的对话框。
 *
 * @param existing 为 null 表示新增；否则编辑该片段
 */
@Composable
fun SnippetEditDialog(
    existing: Snippet?,
    onDismiss: () -> Unit,
    onSave: (name: String, prefix: String, language: String, description: String, body: List<String>) -> Unit,
) {
    var name by remember { mutableStateOf(existing?.name ?: "") }
    var prefix by remember { mutableStateOf(existing?.prefix ?: "") }
    var language by remember { mutableStateOf(existing?.language ?: "") }
    var description by remember { mutableStateOf(existing?.description ?: "") }
    var bodyText by remember { mutableStateOf(existing?.body?.joinToString("\n") ?: "") }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(if (existing == null) stringResource(R.string.snippet_add) else stringResource(R.string.snippet_edit)) },
        text = {
            Column {
                OutlinedTextField(
                    value = name,
                    onValueChange = { name = it },
                    label = { Text(stringResource(R.string.snippet_name)) },
                    singleLine = true,
                    modifier = Modifier.padding(bottom = 8.dp),
                )
                Row {
                    OutlinedTextField(
                        value = prefix,
                        onValueChange = { prefix = it },
                        label = { Text(stringResource(R.string.snippet_prefix)) },
                        singleLine = true,
                        modifier = Modifier
                            .weight(1f)
                            .padding(end = 4.dp),
                    )
                    OutlinedTextField(
                        value = language,
                        onValueChange = { language = it },
                        label = { Text(stringResource(R.string.snippet_language)) },
                        singleLine = true,
                        modifier = Modifier.weight(1f),
                    )
                }
                OutlinedTextField(
                    value = description,
                    onValueChange = { description = it },
                    label = { Text(stringResource(R.string.snippet_description)) },
                    singleLine = true,
                    modifier = Modifier.padding(top = 8.dp),
                )
                OutlinedTextField(
                    value = bodyText,
                    onValueChange = { bodyText = it },
                    label = { Text(stringResource(R.string.snippet_body)) },
                    supportingText = { Text(stringResource(R.string.snippet_body_hint)) },
                    minLines = 4,
                    modifier = Modifier.padding(top = 8.dp),
                )
            }
        },
        confirmButton = {
            TextButton(onClick = {
                onSave(name, prefix, language, description, bodyText.split("\n"))
            }) {
                Text(stringResource(R.string.snippet_save))
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text(stringResource(R.string.snippet_cancel))
            }
        },
    )
}
