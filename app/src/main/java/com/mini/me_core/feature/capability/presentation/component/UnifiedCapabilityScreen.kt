// Compose 约定：@Composable 函数用大驼峰命名（与仓库既有页面一致，见 config/detekt/baseline.xml 中
// CapabilityCenterScreen / McpEditorDialog 等既有条目）。此处按文件级豁免 detekt 的
// FunctionNaming，而不是逐条塞进 baseline —— baseline 只会静默膨胀，且看不出豁免理由。
@file:Suppress("FunctionNaming")

package com.mini.me_core.feature.capability.presentation.component


import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListScope
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material.icons.automirrored.rounded.KeyboardArrowRight
import androidx.compose.material.icons.rounded.Bolt
import androidx.compose.material.icons.rounded.Dns
import androidx.compose.material.icons.rounded.Extension
import androidx.compose.material.icons.rounded.Inventory2
import androidx.compose.material.icons.rounded.Lock
import androidx.compose.material.icons.rounded.Memory
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.mini.me_core.R
import com.mini.me_core.core.theme.Radius
import com.mini.me_core.core.theme.Spacing
import com.mini.me_core.core.theme.components.AppEmptyState
import com.mini.me_core.core.theme.components.AppTopAppBar
import com.mini.me_core.core.ui.rememberPersistentLazyListState
import com.mini.me_core.feature.agent.domain.execution.mcp.McpServerStatus
import com.mini.me_core.feature.agent.domain.execution.tool.ToolExposure
import com.mini.me_core.feature.agent.domain.execution.tool.ToolPermissionPolicy
import com.mini.me_core.feature.capability.domain.CapabilityGroupCard
import com.mini.me_core.feature.capability.domain.CapabilityStatusCard
import com.mini.me_core.feature.capability.domain.CapabilityToolCard
import com.mini.me_core.feature.capability.presentation.UnifiedCapabilityViewModel
import com.mini.me_core.feature.settings.data.repository.ExecutionMode
import com.mini.me_core.feature.settings.presentation.SkillsViewModel
import com.mini.me_core.feature.settings.presentation.component.SkillsScreen

/**
 * F2 统一能力页：内置能力分组、远端 MCP server 与技能在**同一个列表**里管理。
 *
 * 这三者在协议层已统一为 MCP（内置三分组对应三台进程内 MCP server，见 F1），
 * 因此 UI 侧不必再分「工具页 / MCP 页 / 技能页」三处——一张列表、同一套卡片渲染。
 *
 * 灰度开关 `unified_capability_page_enabled` 关闭时不走本组件，
 * 由 [CapabilityCenterScreen] 保持旧三Tab 形态。
 */
@Composable
fun UnifiedCapabilityScreen(
    onNavigateBack: () -> Unit,
    onOpenGroupDetail: (String) -> Unit,
    viewModel: UnifiedCapabilityViewModel = hiltViewModel(),
    onOpenSkillDetail: (String) -> Unit = {},
    onEditSkill: (String) -> Unit = {}
) {
    val overview by viewModel.overview.collectAsStateWithLifecycle()
    val listState = rememberPersistentLazyListState("unified_capability")

    Scaffold(
        containerColor = MaterialTheme.colorScheme.background,
        contentWindowInsets = WindowInsets(0, 0, 0, 0),
        topBar = {
            AppTopAppBar(
                title = stringResource(R.string.capability_center_title),
                onNavigateBack = onNavigateBack,
                navigationIcon = Icons.AutoMirrored.Rounded.ArrowBack,
                navigationContentDescription = stringResource(R.string.common_back),
                actions = {
                    // 无障碍标签用真实语义文案，不拿subtitle 充数（读屏会念出「内置能力、MCP
                    // 服务与技能」，与图标无关）。此处图标纯装饰，故标签给空。
                    Icon(
                        imageVector = Icons.Rounded.Extension,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.padding(end = Spacing.lg)
                    )
                }
            )
        }
    ) { padding ->
        val builtin = overview.groups.filter { it.isBuiltin }
        val remote = overview.groups.filterNot { it.isBuiltin }
        LazyColumn(
            state = listState,
            modifier = Modifier
                .fillMaxSize()
                .padding(padding),
            contentPadding = PaddingValues(
                start = Spacing.lg,
                end = Spacing.lg,
                bottom = Spacing.xxl
            ),
            verticalArrangement = Arrangement.spacedBy(Spacing.sm)
        ) {
            // D4：原「Agent」Tab 的五字段升格为顶部全局状态卡。
            overview.statusCard?.let { card ->
                item(key = "status_card") { CapabilityStatusCardItem(card) }
            }
            builtinSection(builtin, onOpenGroupDetail)
            remoteSection(remote, onOpenGroupDetail)
            skillSection(
                skillCount = overview.skills.size,
                onOpenSkillDetail = onOpenSkillDetail,
                onEditSkill = onEditSkill
            )
        }
    }
}

/** 内置能力分组区（内置三分组）。 */
private fun LazyListScope.builtinSection(
    groups: List<CapabilityGroupCard>,
    onOpenGroupDetail: (String) -> Unit
) {
    if (groups.isEmpty()) return
    item(key = "h_builtin") {
        SectionHeader(stringResource(R.string.capability_group_section_builtin))
    }
    items(groups, key = { "b_${it.id}" }) { group ->
        GroupCard(group = group, onClick = { onOpenGroupDetail(group.id) })
    }
}

/** MCP 服务区。远端一个都没有时给空态——不能只留一个标题，否则像是加载失败。 */
private fun LazyListScope.remoteSection(
    groups: List<CapabilityGroupCard>,
    onOpenGroupDetail: (String) -> Unit
) {
    item(key = "h_remote") {
        SectionHeader(stringResource(R.string.capability_group_section_remote))
    }
    if (groups.isEmpty()) {
        item(key = "remote_empty") {
            AppEmptyState(
                icon = Icons.Rounded.Dns,
                title = stringResource(R.string.capability_remote_empty),
                subtitle = stringResource(R.string.capability_remote_empty_subtitle),
                modifier = Modifier.fillMaxWidth()
            )
        }
    } else {
        items(groups, key = { "r_${it.id}" }) { group ->
            GroupCard(group = group, onClick = { onOpenGroupDetail(group.id) })
        }
    }
}

/**
 * 技能区。
 *
 * F4 前技能尚未经 MCP 暴露（`SkillPromptAdapter` / `SkillResourceAdapter` 未落地），
 * 故此处暂作独立分组；F4 完成后技能在协议层与工具同源，届时可与其它分组共用卡片渲染。
 */
private fun LazyListScope.skillSection(
    skillCount: Int,
    onOpenSkillDetail: (String) -> Unit,
    onEditSkill: (String) -> Unit
) {
    item(key = "h_skill") {
        SectionHeader(stringResource(R.string.capability_group_section_skill))
    }
    item(key = "skills_body") {
        SkillGroupBody(
            count = skillCount,
            onOpenSkillDetail = onOpenSkillDetail,
            onEditSkill = onEditSkill
        )
    }
}

// ──────────────────────────────────────────────
// 顶部状态卡（D4）
// ──────────────────────────────────────────────

/**
 * Agent 概览状态卡：执行模式 · Provider · 权限规则 N · 工具 43 · 技能 N。
 *
 * 由旧「Agent」Tab 升格而来。原先它独占一个 Tab 却只放这几行字，平白多占一屏；
 * 升格到列表顶部后它成了**全局上下文条**——看任何分组前先知道当前运行环境与能力规模。
 */
@Composable
private fun CapabilityStatusCardItem(card: CapabilityStatusCard) {
    val colors = MaterialTheme.colorScheme
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(Radius.lg),
        colors = CardDefaults.cardColors(containerColor = colors.surfaceVariant.copy(alpha = 0.45f))
    ) {
        Column(modifier = Modifier.padding(Spacing.lg)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = stringResource(R.string.capability_status_card_title),
                    style = MaterialTheme.typography.labelMedium,
                    color = colors.onSurfaceVariant
                )
                Text(
                    text = executionModeText(card.executionMode),
                    style = MaterialTheme.typography.labelMedium,
                    fontWeight = FontWeight.Bold,
                    color = colors.primary
                )
            }
            Spacer(Modifier.height(Spacing.md))
            Text(
                text = card.activeProviderName
                    ?: stringResource(R.string.capability_agent_no_provider),
                style = MaterialTheme.typography.titleSmall,
                color = colors.onSurface
            )
            card.activeProviderModel?.let { model ->
                Text(
                    text = model,
                    style = MaterialTheme.typography.bodySmall,
                    color = colors.onSurfaceVariant
                )
            }
            Spacer(Modifier.height(Spacing.md))
            Row(horizontalArrangement = Arrangement.spacedBy(Spacing.lg)) {
                MiniStat(Icons.Rounded.Lock, card.permissionRuleCount.toString())
                MiniStat(Icons.Rounded.Inventory2, card.toolCount.toString())
                MiniStat(Icons.Rounded.Bolt, card.skillCount.toString())
            }
        }
    }
}

@Composable
private fun MiniStat(icon: androidx.compose.ui.graphics.vector.ImageVector, value: String) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        Icon(
            imageVector = icon,
            contentDescription = null,
            tint = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.size(14.dp)
        )
        Spacer(Modifier.width(Spacing.xs))
        Text(
            text = value,
            style = MaterialTheme.typography.labelLarge,
            color = MaterialTheme.colorScheme.onSurface
        )
    }
}

@Composable
private fun executionModeText(mode: ExecutionMode): String = when (mode) {
    ExecutionMode.LOCAL_PROOT -> stringResource(R.string.capability_agent_execution_local)
    ExecutionMode.REMOTE_SSH -> stringResource(R.string.capability_agent_execution_remote)
}

// ──────────────────────────────────────────────
// 分组卡片（内置与远端共用）
// ──────────────────────────────────────────────

/**
 * 一个能力分组的卡片。
 *
 * **内置分组与远端 server 用同一个 Composable**：二者在协议层同构（都是提供一组 MCP 工具
 * 的 server），差别只在标题、图标与是否有连接状态。若分成两套组件，合并就名存实亡。
 */
@Composable
private fun GroupCard(group: CapabilityGroupCard, onClick: () -> Unit) {
    val colors = MaterialTheme.colorScheme
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick),
        shape = RoundedCornerShape(Radius.lg),
        colors = CardDefaults.cardColors(containerColor = colors.surface)
    ) {
        Column(modifier = Modifier.padding(Spacing.lg)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(
                    imageVector = if (group.isBuiltin) Icons.Rounded.Memory else Icons.Rounded.Dns,
                    contentDescription = null,
                    tint = colors.primary,
                    modifier = Modifier.size(20.dp)
                )
                Spacer(Modifier.width(Spacing.sm))
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = group.displayName,
                        style = MaterialTheme.typography.titleSmall,
                        color = colors.onSurface
                    )
                    Text(
                        text = stringResource(R.string.capability_group_tool_count, group.tools.size),
                        style = MaterialTheme.typography.bodySmall,
                        color = colors.onSurfaceVariant
                    )
                }
                group.status?.let { StatusPill(it) }
                Spacer(Modifier.width(Spacing.xs))
                Icon(
                    imageVector = Icons.AutoMirrored.Rounded.KeyboardArrowRight,
                    contentDescription = stringResource(R.string.mcp_details),
                    tint = colors.onSurfaceVariant
                )
            }
            Spacer(Modifier.height(Spacing.sm))
            Row(horizontalArrangement = Arrangement.spacedBy(Spacing.xs)) {
                if (!group.isBuiltin) {
                    Pill(
                        text = if (group.remotelyExposed) {
                            stringResource(R.string.capability_group_exposed)
                        } else {
                            stringResource(R.string.capability_group_internal_only)
                        },
                        tint = if (group.remotelyExposed) colors.tertiary else colors.onSurfaceVariant
                    )
                }
                Pill(
                    text = stringResource(R.string.capability_agent_tool_count) + " " + group.tools.size,
                    tint = colors.onSurfaceVariant
                )
            }
        }
    }
}

/**
 * 连接状态胶囊。内置分组无连接态（进程内 server 恒可用），故只在远端 server 上渲染。
 */
@Composable
private fun StatusPill(status: McpServerStatus) {
    val colors = MaterialTheme.colorScheme
    val (text, tint) = when (status.state) {
        McpServerStatus.State.CONNECTED ->
            stringResource(R.string.mcp_connected) to colors.tertiary
        McpServerStatus.State.CONNECTING ->
            stringResource(R.string.mcp_connecting) to colors.primary
        McpServerStatus.State.FAILED ->
            stringResource(R.string.mcp_connection_failed) to colors.error
        McpServerStatus.State.DISABLED ->
            stringResource(R.string.mcp_disabled) to colors.onSurfaceVariant
    }
    Pill(
        text = if (status.state == McpServerStatus.State.CONNECTED) {
            "$text ${status.toolCount}/${status.totalToolCount}"
        } else {
            text
        },
        tint = tint
    )
}

@Composable
private fun Pill(text: String, tint: Color) {
    Box(
        modifier = Modifier
            .background(tint.copy(alpha = 0.12f), RoundedCornerShape(Radius.sm))
            .padding(horizontal = Spacing.sm, vertical = 2.dp)
    ) {
        Text(
            text = text,
            style = MaterialTheme.typography.labelSmall,
            color = tint
        )
    }
}

// ──────────────────────────────────────────────
// 工具卡片与徽章
// ──────────────────────────────────────────────

/**
 * 工具卡片上的三枚分色徽章。
 *
 * 三枚徽章回答三个不同问题，不可互相替代：
 * - **常驻/ 按需**：批次 B 的加载分级——每轮是否占用模型的上下文预算。
 * - **需审批**：执行前是否要打断用户。判定只依据本地 [ToolPermissionPolicy]，
 *   **不采信 MCP `annotations`**——按 MCP 规范那是不可信提示且 `destructiveHint`
 *   默认为 true，据此放行等于把外部声明当事实。
 */
@Composable
fun ToolBadgeRow(tool: CapabilityToolCard) {
    Row(horizontalArrangement = Arrangement.spacedBy(Spacing.xs)) {
        Pill(
            text = if (tool.exposure == ToolExposure.CORE) {
                stringResource(R.string.capability_badge_core)
            } else {
                stringResource(R.string.capability_badge_deferred)
            },
            tint = if (tool.exposure == ToolExposure.CORE) {
                MaterialTheme.colorScheme.primary
            } else {
                MaterialTheme.colorScheme.onSurfaceVariant
            }
        )
        if (tool.requiresApproval || tool.permissionPolicy == ToolPermissionPolicy.ASK) {
            Pill(text = stringResource(R.string.capability_badge_ask), tint = MaterialTheme.colorScheme.error)
        }
        tool.builtinGroup?.let { group ->
            Pill(
                text = group.serverName,
                tint = MaterialTheme.colorScheme.tertiary
            )
        }
    }
}

@Composable
private fun SectionHeader(title: String) {
    Text(
        text = title,
        style = MaterialTheme.typography.labelLarge,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
        modifier = Modifier.padding(top = Spacing.md, bottom = Spacing.xs)
    )
}

/**
 * 技能分组体。
 *
 * F4 之前技能尚未经 MCP 暴露（`SkillPromptAdapter` / `SkillResourceAdapter` 未落地），
 * 故此处**内嵌完整的 `SkillsScreen`** 而非一个跳转入口：技能管理有筛选、搜索、导入导出、
 * 删除确认等交互，做成入口跳转会让用户在「能力页→技能页」之间来回跳，比合并前更绕。
 *
 * F4 落地后，PROMPT 型 Skill 映射为 MCP prompts、SCRIPT 型映射为 resources，
 * 技能在协议层与工具同源，届时可与其它分组共用同一套卡片渲染，本函数退化为分组壳。
 */
@Composable
private fun SkillGroupBody(
    count: Int,
    onOpenSkillDetail: (String) -> Unit,
    onEditSkill: (String) -> Unit
) {
    val skillsViewModel: SkillsViewModel = hiltViewModel()
    Column {
        Text(
            text = stringResource(R.string.capability_group_tool_count, count),
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.padding(vertical = Spacing.xs)
        )
        SkillsScreen(
            viewModel = skillsViewModel,
            onOpenSkillDetail = onOpenSkillDetail,
            onEditSkill = onEditSkill
        )
    }
}
