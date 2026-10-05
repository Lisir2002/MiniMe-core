package com.mini.me_core.feature.browser.presentation

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Fingerprint
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.mini.me_core.core.theme.components.AppListItem
import com.mini.me_core.feature.browser.domain.fingerprint.FingerprintGenerator
import com.mini.me_core.feature.browser.domain.fingerprint.FingerprintManager
import com.mini.me_core.feature.browser.domain.fingerprint.FingerprintProfile
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import javax.inject.Inject

/**
 * 指纹配置管理页面 ViewModel。
 */
@HiltViewModel
class FingerprintManagerViewModel @Inject constructor(
    private val fingerprintManager: FingerprintManager,
) : ViewModel() {

    private val _profiles = MutableStateFlow<List<FingerprintProfile>>(emptyList())
    val profiles: StateFlow<List<FingerprintProfile>> = _profiles.asStateFlow()

    private val _currentId = MutableStateFlow<String?>(null)
    val currentId: StateFlow<String?> = _currentId.asStateFlow()

    private val _message = MutableStateFlow<String?>(null)
    val message: StateFlow<String?> = _message.asStateFlow()

    init {
        refresh()
    }

    fun refresh() {
        _profiles.value = fingerprintManager.getAll().sortedByDescending { it.score }
        _currentId.value = fingerprintManager.getCurrent()?.id
    }

    fun switchTo(id: String) {
        viewModelScope.launch {
            val profile = fingerprintManager.switchTo(id)
            if (profile != null) {
                _message.value = "已切换到「${profile.name}」"
                refresh()
            }
        }
    }

    fun createRandom(template: String, name: String) {
        viewModelScope.launch {
            val tpl = when (template) {
                "region_us" -> FingerprintGenerator.Template.REGION_US
                "region_cn" -> FingerprintGenerator.Template.REGION_CN
                "region_jp" -> FingerprintGenerator.Template.REGION_JP
                "region_eu" -> FingerprintGenerator.Template.REGION_EU
                "high_end" -> FingerprintGenerator.Template.HIGH_END
                "mobile" -> FingerprintGenerator.Template.MOBILE
                else -> FingerprintGenerator.Template.RANDOM
            }
            val profile = fingerprintManager.createRandom(template = tpl, name = name)
            _message.value = "已创建「${profile.name}」"
            refresh()
        }
    }

    fun delete(id: String) {
        viewModelScope.launch {
            if (fingerprintManager.delete(id)) {
                _message.value = "已删除"
                refresh()
            } else {
                _message.value = "删除失败（默认真实指纹不可删除）"
            }
        }
    }

    fun validate(id: String) {
        viewModelScope.launch {
            val result = fingerprintManager.validate(id)
            _message.value = if (result?.valid == true) "一致性校验通过" else "一致性校验未通过"
        }
    }

    fun clearMessage() {
        _message.value = null
    }
}

/**
 * 指纹配置管理页面。
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun FingerprintManagerScreen(
    onBack: () -> Unit,
    viewModel: FingerprintManagerViewModel = hiltViewModel(),
) {
    val profiles by viewModel.profiles.collectAsState()
    val currentId by viewModel.currentId.collectAsState()
    val message by viewModel.message.collectAsState()

    var showCreateDialog by remember { mutableStateOf(false) }
    var deleteTarget by remember { mutableStateOf<FingerprintProfile?>(null) }

    // 消息自动清除
    message?.let {
        androidx.compose.runtime.LaunchedEffect(it) {
            kotlinx.coroutines.delay(2500)
            viewModel.clearMessage()
        }
    }

    Box(modifier = Modifier.fillMaxSize()) {
        if (profiles.isEmpty()) {
            Box(
                modifier = Modifier.fillMaxSize(),
                contentAlignment = Alignment.Center,
            ) {
                Text("暂无指纹配置", color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        } else {
            LazyColumn(
                modifier = Modifier.fillMaxSize(),
                contentPadding = androidx.compose.foundation.layout.PaddingValues(16.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                items(profiles, key = { it.id }) { profile ->
                        FingerprintCard(
                            profile = profile,
                            isCurrent = profile.id == currentId,
                            onSwitch = { viewModel.switchTo(profile.id) },
                            onDelete = { deleteTarget = profile },
                            onValidate = { viewModel.validate(profile.id) },
                        )
                    }
                }
            }

            // 消息提示
            message?.let { msg ->
                Box(
                    modifier = Modifier
                        .align(Alignment.BottomCenter)
                        .padding(bottom = 80.dp)
                        .clip(RoundedCornerShape(12.dp))
                        .background(MaterialTheme.colorScheme.primary)
                        .padding(horizontal = 20.dp, vertical = 10.dp),
                ) {
                    Text(msg, color = Color.White, fontSize = 14.sp)
                }
            }

            // 新建配置浮动按钮
            FloatingActionButton(
                onClick = { showCreateDialog = true },
                containerColor = MaterialTheme.colorScheme.primary,
                modifier = Modifier
                    .align(Alignment.BottomEnd)
                    .padding(16.dp),
            ) {
                Icon(Icons.Default.Add, contentDescription = "新建配置", tint = Color.White)
            }
        }

    // 新建配置对话框
    if (showCreateDialog) {
        CreateFingerprintDialog(
            onDismiss = { showCreateDialog = false },
            onCreate = { template, name ->
                viewModel.createRandom(template, name)
                showCreateDialog = false
            },
        )
    }

    // 删除确认对话框
    deleteTarget?.let { profile ->
        AlertDialog(
            onDismissRequest = { deleteTarget = null },
            title = { Text("删除配置") },
            text = { Text("确定删除「${profile.name}」？此操作不可撤销。") },
            confirmButton = {
                TextButton(onClick = {
                    viewModel.delete(profile.id)
                    deleteTarget = null
                }) {
                    Text("删除", color = MaterialTheme.colorScheme.error)
                }
            },
            dismissButton = {
                TextButton(onClick = { deleteTarget = null }) {
                    Text("取消")
                }
            },
        )
    }
}

/**
 * 指纹配置卡片。
 */
@Composable
private fun FingerprintCard(
    profile: FingerprintProfile,
    isCurrent: Boolean,
    onSwitch: () -> Unit,
    onDelete: () -> Unit,
    onValidate: () -> Unit,
) {
    val statusColor = when (profile.status) {
        "active" -> Color(0xFF22C55E)
        "cooling" -> Color(0xFFF59E0B)
        else -> Color(0xFF9CA3AF)
    }
    val statusText = when (profile.status) {
        "active" -> "可用"
        "cooling" -> "冷却中"
        else -> "已停用"
    }

    Card(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(enabled = !isCurrent) { onSwitch() },
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(
            containerColor = if (isCurrent)
                MaterialTheme.colorScheme.primaryContainer
            else
                MaterialTheme.colorScheme.surface,
        ),
        border = if (isCurrent)
            androidx.compose.foundation.BorderStroke(2.dp, MaterialTheme.colorScheme.primary)
        else null,
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            // 标题行
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Icon(
                    Icons.Default.Fingerprint,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.size(24.dp),
                )
                Spacer(modifier = Modifier.width(8.dp))
                Text(
                    profile.name,
                    fontWeight = FontWeight.Bold,
                    fontSize = 16.sp,
                    modifier = Modifier.weight(1f),
                )
                if (isCurrent) {
                    Box(
                        modifier = Modifier
                            .clip(CircleShape)
                            .background(MaterialTheme.colorScheme.primary)
                            .padding(horizontal = 10.dp, vertical = 4.dp),
                    ) {
                        Text("当前", color = Color.White, fontSize = 12.sp, fontWeight = FontWeight.Medium)
                    }
                }
            }

            Spacer(modifier = Modifier.height(8.dp))

            // 信息行
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                InfoChip("${profile.browser.replaceFirstChar { it.uppercase() }} ${profile.browserVersion.substringBefore(".")}")
                InfoChip(profile.region)
                InfoChip(if (profile.score > 0) "评分 ${profile.score.toInt()}" else "未校验")
                Box(
                    modifier = Modifier
                        .clip(CircleShape)
                        .background(statusColor.copy(alpha = 0.15f))
                        .padding(horizontal = 8.dp, vertical = 2.dp),
                ) {
                    Text(statusText, color = statusColor, fontSize = 12.sp)
                }
            }

            Spacer(modifier = Modifier.height(12.dp))

            // 操作按钮行
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.End,
            ) {
                if (!isCurrent) {
                    TextButton(onClick = onSwitch) {
                        Text("切换", color = MaterialTheme.colorScheme.primary)
                    }
                }
                TextButton(onClick = onValidate) {
                    Icon(Icons.Default.Refresh, contentDescription = null, modifier = Modifier.size(16.dp))
                    Spacer(modifier = Modifier.width(4.dp))
                    Text("校验", fontSize = 13.sp)
                }
                if (profile.id != FingerprintProfile.DEFAULT_ID) {
                    TextButton(onClick = onDelete) {
                        Icon(Icons.Default.Delete, contentDescription = null, modifier = Modifier.size(16.dp), tint = MaterialTheme.colorScheme.error)
                        Spacer(modifier = Modifier.width(4.dp))
                        Text("删除", color = MaterialTheme.colorScheme.error, fontSize = 13.sp)
                    }
                }
            }
        }
    }
}

/**
 * 信息标签。
 */
@Composable
private fun InfoChip(text: String) {
    Box(
        modifier = Modifier
            .clip(RoundedCornerShape(6.dp))
            .background(MaterialTheme.colorScheme.surfaceVariant)
            .padding(horizontal = 8.dp, vertical = 3.dp),
    ) {
        Text(text, fontSize = 12.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
    }
}

/**
 * 新建指纹配置对话框。
 */
@Composable
private fun CreateFingerprintDialog(
    onDismiss: () -> Unit,
    onCreate: (template: String, name: String) -> Unit,
) {
    val templates = listOf(
        "region_us" to "美国 Chrome",
        "region_cn" to "中国 Chrome",
        "region_jp" to "日本 Chrome",
        "region_eu" to "欧洲 Chrome",
        "high_end" to "高端配置",
        "mobile" to "移动端",
    )
    var selectedTemplate by remember { mutableStateOf("region_us") }
    var name by remember { mutableStateOf("") }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("新建指纹配置") },
        text = {
            Column {
                Text("选择模板：", fontSize = 14.sp, fontWeight = FontWeight.Medium)
                Spacer(modifier = Modifier.height(8.dp))
                templates.forEach { (key, label) ->
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable { selectedTemplate = key }
                            .padding(vertical = 6.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Box(
                            modifier = Modifier
                                .size(18.dp)
                                .clip(CircleShape)
                                .border(
                                    2.dp,
                                    if (selectedTemplate == key) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.outline,
                                    CircleShape,
                                ),
                            contentAlignment = Alignment.Center,
                        ) {
                            if (selectedTemplate == key) {
                                Box(
                                    modifier = Modifier
                                        .size(10.dp)
                                        .clip(CircleShape)
                                        .background(MaterialTheme.colorScheme.primary),
                                )
                            }
                        }
                        Spacer(modifier = Modifier.width(10.dp))
                        Text(label, fontSize = 14.sp)
                    }
                }
                Spacer(modifier = Modifier.height(12.dp))
                Text("配置名称（可选）：", fontSize = 14.sp, fontWeight = FontWeight.Medium)
                Spacer(modifier = Modifier.height(6.dp))
                androidx.compose.material3.OutlinedTextField(
                    value = name,
                    onValueChange = { name = it },
                    modifier = Modifier.fillMaxWidth(),
                    placeholder = { Text("留空则自动生成") },
                    singleLine = true,
                )
            }
        },
        confirmButton = {
            TextButton(onClick = {
                val finalName = name.ifBlank { "${templates.first { it.first == selectedTemplate }.second} ${System.currentTimeMillis() % 10000}" }
                onCreate(selectedTemplate, finalName)
            }) {
                Text("创建")
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text("取消")
            }
        },
    )
}
