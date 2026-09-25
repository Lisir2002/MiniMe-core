package com.mini.me_core.feature.settings.presentation.component

import androidx.compose.foundation.clickable
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
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import com.mini.me_core.core.theme.components.AppEmptyState
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.mini.me_core.R
import com.mini.me_core.core.theme.components.AppSegmentedControl
import com.mini.me_core.feature.agent.domain.prompt.AgentAsset
import com.mini.me_core.feature.agent.domain.sop.SopAsset
import com.mini.me_core.core.theme.Spacing

/** P1：规范查看器（只读）：静态规则 / SOP 两个 Tab，点击项查看全文。 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun NormFlowAssetViewerScreen(
    initialTab: Int,
    onBack: () -> Unit,
    viewModel: NormFlowAssetViewModel = hiltViewModel()
) {
    val staticRules by viewModel.staticRules.collectAsStateWithLifecycle()
    val sopList by viewModel.sopList.collectAsStateWithLifecycle()

    var selectedTab by remember { mutableStateOf(initialTab) }
    var selectedStaticRule by remember { mutableStateOf<AgentAsset?>(null) }
    var selectedSop by remember { mutableStateOf<SopAsset?>(null) }

    LaunchedEffect(Unit) { viewModel.load() }

    // 全文查看模式
    selectedStaticRule?.let { asset ->
        AssetDetailView(
            title = asset.name,
            meta = "${stringResource(R.string.norm_flow_asset_word_count)}: ${asset.body.length}",
            body = asset.body,
            onBack = { selectedStaticRule = null }
        )
        return
    }
    selectedSop?.let { sop ->
        AssetDetailView(
            title = sop.name,
            meta = "${stringResource(R.string.norm_flow_asset_steps)}: ${sop.body.lines().count { it.isNotBlank() }}",
            body = sop.body,
            onBack = { selectedSop = null }
        )
        return
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(stringResource(R.string.norm_flow_asset_viewer)) },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Rounded.ArrowBack, contentDescription = stringResource(R.string.norm_flow_asset_back))
                    }
                }
            )
        }
    ) { padding ->
        Column(modifier = Modifier.padding(padding)) {
            AppSegmentedControl(
                tabs = listOf(
                    stringResource(R.string.norm_flow_asset_static_rules),
                    stringResource(R.string.norm_flow_asset_sop)
                ),
                selectedIndex = selectedTab,
                onSelect = { selectedTab = it }
            )
            if (selectedTab == 0) {
                if (staticRules.isEmpty()) {
                    EmptyState()
                } else {
                    LazyColumn(modifier = Modifier.fillMaxSize()) {
                        items(staticRules, key = { it.name }) { asset ->
                            AssetRow(
                                title = asset.name,
                                subtitle = asset.description,
                                meta = "${asset.body.length} ${stringResource(R.string.norm_flow_asset_word_count)}",
                                onClick = { selectedStaticRule = asset }
                            )
                        }
                    }
                }
            } else {
                if (sopList.isEmpty()) {
                    EmptyState()
                } else {
                    LazyColumn(modifier = Modifier.fillMaxSize()) {
                        items(sopList, key = { it.name }) { sop ->
                            AssetRow(
                                title = sop.name,
                                subtitle = sop.whenToUse,
                                meta = "${sop.body.lines().count { it.isNotBlank() }} ${stringResource(R.string.norm_flow_asset_steps)}",
                                onClick = { selectedSop = sop }
                            )
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun AssetRow(title: String, subtitle: String, meta: String, onClick: () -> Unit) {
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = Spacing.lg, vertical = Spacing.xs)
            .clickable { onClick() },
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.4f)),
        elevation = CardDefaults.cardElevation(defaultElevation = 0.dp)
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(Spacing.lg),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Text(text = title, style = MaterialTheme.typography.titleMedium)
                if (subtitle.isNotBlank()) {
                    Text(text = subtitle, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
                Spacer(Modifier.height(2.dp))
                Text(text = meta, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        }
    }
}

@Composable
private fun AssetDetailView(title: String, meta: String, body: String, onBack: () -> Unit) {
    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(title) },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Rounded.ArrowBack, contentDescription = stringResource(R.string.norm_flow_asset_back))
                    }
                }
            )
        }
    ) { padding ->
        Column(
            modifier = Modifier
                .padding(padding)
                .fillMaxSize()
                .verticalScroll(rememberScrollState())
                .padding(Spacing.lg)
        ) {
            Text(text = meta, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            Spacer(Modifier.height(Spacing.md))
            Text(text = body, style = MaterialTheme.typography.bodyMedium)
        }
    }
}

@Composable
private fun EmptyState() {
    AppEmptyState(
        title = stringResource(R.string.norm_flow_asset_empty),
    )
}
