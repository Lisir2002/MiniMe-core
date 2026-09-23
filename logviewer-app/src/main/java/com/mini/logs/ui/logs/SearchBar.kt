package com.mini.logs.ui.logs

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material.icons.rounded.ArrowDownward
import androidx.compose.material.icons.rounded.ArrowUpward
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.unit.dp
import com.mini.me_core.core.theme.tokens.LocalAppTheme
import com.mini.me_core.core.theme.tokens.PrimitiveSpacing

/**
 * 搜索模式顶栏：替换正常顶栏。
 * 包含返回按钮、搜索输入框、清除按钮、匹配计数、上一处/下一处。
 */
@Composable
fun SearchTopBar(
    query: String,
    matchIndex: Int,
    matchCount: Int,
    onQueryChange: (String) -> Unit,
    onExitSearch: () -> Unit,
    onClearQuery: () -> Unit,
    onPrevMatch: () -> Unit,
    onNextMatch: () -> Unit,
) {
    val colors = LocalAppTheme.current.colors

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = PrimitiveSpacing.SmPlus, vertical = PrimitiveSpacing.Xs),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        IconButton(onClick = onExitSearch) {
            Icon(
                imageVector = Icons.AutoMirrored.Rounded.ArrowBack,
                contentDescription = "退出搜索",
                tint = colors.textSecondary,
            )
        }

        OutlinedTextField(
            value = query,
            onValueChange = onQueryChange,
            modifier = Modifier.weight(1f),
            placeholder = { Text("搜索日志...") },
            singleLine = true,
            keyboardOptions = KeyboardOptions(imeAction = ImeAction.Search),
            colors = OutlinedTextFieldDefaults.colors(
                focusedBorderColor = colors.brandPrimary,
                unfocusedBorderColor = colors.borderDefault,
                focusedContainerColor = colors.surfaceCard,
                unfocusedContainerColor = colors.surfaceCard,
            ),
            trailingIcon = {
                if (query.isNotEmpty()) {
                    IconButton(onClick = onClearQuery) {
                        Icon(
                            imageVector = Icons.Rounded.Close,
                            contentDescription = "清除",
                            tint = colors.textTertiary,
                        )
                    }
                }
            },
        )

        if (matchCount > 0) {
            Spacer(Modifier.width(PrimitiveSpacing.Xs))
            Text(
                text = "${matchIndex + 1}/$matchCount",
                color = colors.textSecondary,
                fontSize = 12.dp.let { androidx.compose.ui.unit.TextUnit(it.value, androidx.compose.ui.unit.TextUnitType.Sp) },
            )
        }

        IconButton(onClick = onPrevMatch, enabled = matchCount > 0) {
            Icon(
                imageVector = Icons.Rounded.ArrowUpward,
                contentDescription = "上一处",
                tint = if (matchCount > 0) colors.textSecondary else colors.textDisabled,
            )
        }
        IconButton(onClick = onNextMatch, enabled = matchCount > 0) {
            Icon(
                imageVector = Icons.Rounded.ArrowDownward,
                contentDescription = "下一处",
                tint = if (matchCount > 0) colors.textSecondary else colors.textDisabled,
            )
        }
    }
}
