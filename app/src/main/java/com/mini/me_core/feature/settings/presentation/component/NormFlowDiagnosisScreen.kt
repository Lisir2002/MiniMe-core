package com.mini.me_core.feature.settings.presentation.component

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material.icons.rounded.ContentCopy
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.mini.me_core.R
import com.mini.me_core.core.theme.Spacing

/** P2：注入诊断面板——显示最近一次 step 注入的源级诊断与完整内容。 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun NormFlowDiagnosisScreen(
    onBack: () -> Unit,
    viewModel: NormFlowDiagnosisViewModel = hiltViewModel()
) {
    val diagnosisList by viewModel.diagnosisList.collectAsStateWithLifecycle()
    val injectionContent by viewModel.injectionContent.collectAsStateWithLifecycle()
    val budgetUsed by viewModel.budgetUsed.collectAsStateWithLifecycle()
    val clipboard = LocalClipboardManager.current

    LaunchedEffect(Unit) { viewModel.refresh() }
    val content = injectionContent

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(stringResource(R.string.norm_flow_diagnosis)) },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Rounded.ArrowBack, contentDescription = stringResource(R.string.norm_flow_asset_back))
                    }
                },
                actions = {
                    if (!content.isNullOrBlank()) {
                        IconButton(onClick = {
                            clipboard.setText(AnnotatedString(content))
                        }) {
                            Icon(Icons.Rounded.ContentCopy, contentDescription = stringResource(R.string.norm_flow_diagnosis_copy))
                        }
                    }
                }
            )
        }
    ) { padding ->
        LazyColumn(
            modifier = Modifier
                .padding(padding)
                .fillMaxSize()
        ) {
            // 预算使用率
            item {
                Card(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = Spacing.lg, vertical = Spacing.sm),
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.4f)),
                    elevation = CardDefaults.cardElevation(defaultElevation = 0.dp)
                ) {
                    Column(modifier = Modifier.padding(Spacing.lg)) {
                        Text(
                            text = stringResource(R.string.norm_flow_diagnosis_budget),
                            style = MaterialTheme.typography.labelMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                        Spacer(Modifier.height(4.dp))
                        Text(
                            text = "$budgetUsed / 800 chars",
                            style = MaterialTheme.typography.titleMedium
                        )
                    }
                }
            }

            // 注入源列表
            item {
                Text(
                    text = stringResource(R.string.norm_flow_diagnosis_sources),
                    style = MaterialTheme.typography.labelLarge,
                    modifier = Modifier.padding(start = Spacing.lg, top = Spacing.md, bottom = Spacing.sm),
                    color = MaterialTheme.colorScheme.primary
                )
            }
            items(diagnosisList.size) { index ->
                val d = diagnosisList[index]
                Card(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = Spacing.lg, vertical = 2.dp),
                    colors = CardDefaults.cardColors(
                        containerColor = if (d.kept) MaterialTheme.colorScheme.surface
                        else MaterialTheme.colorScheme.errorContainer.copy(alpha = 0.3f)
                    ),
                    elevation = CardDefaults.cardElevation(defaultElevation = 0.dp)
                ) {
                    Row(
                        modifier = Modifier.padding(Spacing.md),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Column(modifier = Modifier.weight(1f)) {
                            Text(text = d.name, style = MaterialTheme.typography.titleSmall)
                            Text(
                                text = "${d.importance} · ${if (d.kept) stringResource(R.string.norm_flow_diagnosis_kept) else stringResource(R.string.norm_flow_diagnosis_trimmed)} · ${d.reason}",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                        Text(
                            text = "${d.chars}",
                            style = MaterialTheme.typography.labelMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
            }

            // 注入内容全文
            item {
                Text(
                    text = stringResource(R.string.norm_flow_diagnosis_content),
                    style = MaterialTheme.typography.labelLarge,
                    modifier = Modifier.padding(start = Spacing.lg, top = Spacing.md, bottom = Spacing.sm),
                    color = MaterialTheme.colorScheme.primary
                )
                Card(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = Spacing.lg, vertical = Spacing.sm),
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.3f)),
                    elevation = CardDefaults.cardElevation(defaultElevation = 0.dp)
                ) {
                    Text(
                        text = content ?: stringResource(R.string.norm_flow_asset_empty),
                        style = MaterialTheme.typography.bodySmall,
                        modifier = Modifier.padding(Spacing.lg)
                    )
                }
            }
        }
    }
}
