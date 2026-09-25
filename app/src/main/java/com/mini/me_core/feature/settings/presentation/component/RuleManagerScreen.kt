package com.mini.me_core.feature.settings.presentation.component

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.mini.me_core.R
import com.mini.me_core.core.theme.Spacing

/** P3：分层规则管理页（只读列表 + 启用/禁用内存覆盖）。 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun RuleManagerScreen(
    projectRoot: String,
    onBack: () -> Unit,
    viewModel: RuleManagerViewModel = hiltViewModel()
) {
    val rules by viewModel.rules.collectAsStateWithLifecycle()
    val disabledNames by viewModel.disabledNames.collectAsStateWithLifecycle()

    LaunchedEffect(projectRoot) { viewModel.load(projectRoot) }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(stringResource(R.string.norm_flow_rule_manager_title)) },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Rounded.ArrowBack, contentDescription = stringResource(R.string.norm_flow_asset_back))
                    }
                }
            )
        }
    ) { padding ->
        if (rules.isEmpty()) {
            Column(
                modifier = Modifier.padding(padding).fillMaxSize(),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.Center
            ) {
                Text(
                    text = stringResource(R.string.norm_flow_rule_empty),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        } else {
            LazyColumn(
                modifier = Modifier.padding(padding).fillMaxSize(),
                contentPadding = androidx.compose.foundation.layout.PaddingValues(vertical = Spacing.sm)
            ) {
                items(rules, key = { it.name }) { rule ->
                    val disabled = rule.name in disabledNames
                    Card(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = Spacing.lg, vertical = 2.dp),
                        colors = CardDefaults.cardColors(
                            containerColor = if (disabled) MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.3f)
                            else MaterialTheme.colorScheme.surface
                        ),
                        elevation = CardDefaults.cardElevation(defaultElevation = 0.dp)
                    ) {
                        Row(
                            modifier = Modifier.padding(Spacing.md),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Column(modifier = Modifier.weight(1f)) {
                                Text(
                                    text = rule.name,
                                    style = MaterialTheme.typography.titleSmall
                                )
                                Text(
                                    text = "${layerLabel(rule.layer.name)} · P${rule.priority}",
                                    style = MaterialTheme.typography.labelSmall,
                                    color = MaterialTheme.colorScheme.primary
                                )
                                Spacer(Modifier.height(2.dp))
                                Text(
                                    text = rule.summary,
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                    maxLines = 2
                                )
                            }
                            Switch(
                                checked = !disabled,
                                onCheckedChange = { enabled -> viewModel.toggleRule(rule.name, enabled) }
                            )
                        }
                    }
                }
                item { Spacer(Modifier.height(Spacing.xl)) }
            }
        }
    }
}

@Composable
private fun layerLabel(layerName: String): String = when (layerName) {
    "GLOBAL" -> stringResource(R.string.norm_flow_rule_layer_global)
    "PROJECT" -> stringResource(R.string.norm_flow_rule_layer_project)
    "WORKSPACE" -> stringResource(R.string.norm_flow_rule_layer_workspace)
    "MODULE" -> stringResource(R.string.norm_flow_rule_layer_module)
    else -> layerName
}
