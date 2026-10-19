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
import androidx.compose.material.icons.rounded.Dns
import androidx.compose.material.icons.rounded.Extension
import androidx.compose.material.icons.rounded.KeyboardArrowDown
import androidx.compose.material.icons.rounded.KeyboardArrowUp
import androidx.compose.material.icons.rounded.Memory
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
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
import com.mini.me_core.feature.agent.domain.execution.mcp.ServerKind
import com.mini.me_core.feature.agent.domain.execution.tool.ToolExposure
import com.mini.me_core.feature.agent.domain.execution.tool.ToolPermissionPolicy
import com.mini.me_core.feature.capability.domain.CapabilityGroupCard
import com.mini.me_core.feature.capability.domain.CapabilitySkillGroupCard
import com.mini.me_core.feature.capability.domain.CapabilityToolCard
import com.mini.me_core.feature.capability.presentation.UnifiedCapabilityViewModel
import com.mini.me_core.feature.settings.presentation.SkillsViewModel
import com.mini.me_core.feature.settings.presentation.component.SkillsScreen

/**
 * F2 统一能力页：内置能力分组、远端 MCP server 与技能在**同一个列表**里管理。
 *
 * 这三者在协议层已统一为 MCP（内置三分组对应三台进程内 MCP server，见 F1），
 * 因此 UI 侧不必再分「工具页 / MCP 页 / 技能页」三处——一张列表、同一套卡片渲染。
 *
 * H1 两级层级：本页是**入口列表**——每张卡片标明接入形态（本地内置 / 远端接入），
 * 点卡片进入该 server 的能力中心（工具 / 提示词 / 资源，见
 * [CapabilityGroupDetailScreen]）。原 D4 置于顶部的「Agent 概览」状态卡已迁到
 * 独立的 Agent 设置页（`SettingsSection.Agent`），本页回归列表单一职责。
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
    // F4：技能分组默认折叠——技能是用户维度资产，不该在「看能力清单」时占掉半屏。
    var skillGroupExpanded by rememberSaveable { mutableStateOf(false) }

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
            // D4 原「Agent 概览」状态卡已移出：Agent 改为独立设置页（SettingsSection.Agent），
            // 本页回归「server 列表」单一职责——入口只回答「有哪些能力、点什么进去」。
            builtinSection(builtin, onOpenGroupDetail)
            remoteSection(remote, onOpenGroupDetail)
            skillSection(
                card = overview.skillGroup,
                expanded = skillGroupExpanded,
                onToggleExpanded = { skillGroupExpanded = !skillGroupExpanded },
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
 * F4：技能区。
 *
 * 形态从 F2 的「内嵌完整 `SkillsScreen`」改为**与其它分组同构的折叠卡片**：
 * 折叠时只占一张卡（名称 + 计数 + MCP 映射徽章），点开才展开完整管理界面。
 *
 * 为什么不彻底退化成「跳转入口」：技能管理有搜索、三类筛选、导入导出、删除确认，
 * 做成跳转会让用户在「能力页 → 技能页」之间来回跳，比合并前更绕——那就违背了
 * 页面合并的初衷。折叠卡片是折中：默认不占屏，需要时**原地**展开。
 *
 * 徽章显式写出 `PROMPT → prompts` / `SCRIPT → resources` / `MCP → 别名`，
 * 让「技能为什么没出现在 prompts 里」在 UI 上可自解释，而不是靠文档。
 */
private fun LazyListScope.skillSection(
    card: CapabilitySkillGroupCard,
    expanded: Boolean,
    onToggleExpanded: () -> Unit,
    onOpenSkillDetail: (String) -> Unit,
    onEditSkill: (String) -> Unit
) {
    item(key = "h_skill") {
        SectionHeader(stringResource(R.string.capability_group_section_skill))
    }
    item(key = "skill_card") {
        // 卡片壳在这里而非 SkillGroupCard 内：让 SkillGroupCard 保持纯内容布局，
        // 与 [GroupCard] 的「壳 + 内容」拆分方式一致。
        Card(
            modifier = Modifier.fillMaxWidth(),
            shape = RoundedCornerShape(Radius.lg),
            colors = CardDefaults.cardColors(
                containerColor = MaterialTheme.colorScheme.surface
            )
        ) {
            Column(modifier = Modifier.padding(Spacing.lg)) {
                SkillGroupCard(
                    card = card,
                    expanded = expanded,
                    onToggleExpanded = onToggleExpanded,
                    onOpenSkillDetail = onOpenSkillDetail,
                    onEditSkill = onEditSkill
                )
            }
        }
    }
}

/**
 * 技能分组卡：折叠态是分组摘要，展开态原地内嵌完整技能管理界面。
 *
 * 卡片壳与 [GroupCard] 同形（同圆角、同内边距、同箭头语义），使技能在视觉上
 * 与内置分组/远端 server 读起来是同一种东西——这是「统一管理」的形式要求。
 */
@Composable
private fun SkillGroupCard(
    card: CapabilitySkillGroupCard,
    expanded: Boolean,
    onToggleExpanded: () -> Unit,
    onOpenSkillDetail: (String) -> Unit,
    onEditSkill: (String) -> Unit
) {
    Column {
        SkillGroupCardHeader(card = card, expanded = expanded, onToggle = onToggleExpanded)
        Spacer(Modifier.height(Spacing.sm))
        SkillMcpMappingRow(card)
        if (!card.exposedViaMcp) {
            Spacer(Modifier.height(Spacing.xs))
            Text(
                text = stringResource(R.string.capability_skill_no_mcp_exposure),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
        if (expanded) {
            Spacer(Modifier.height(Spacing.sm))
            SkillGroupBody(
                onOpenSkillDetail = onOpenSkillDetail,
                onEditSkill = onEditSkill
            )
        }
    }
}

/** 技能分组卡的标题行：名称 + 计数 + 「管理」提示 + 折叠箭头（整行可点）。 */
@Composable
private fun SkillGroupCardHeader(
    card: CapabilitySkillGroupCard,
    expanded: Boolean,
    onToggle: () -> Unit
) {
    val colors = MaterialTheme.colorScheme
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onToggle),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Icon(
            imageVector = Icons.Rounded.Extension,
            contentDescription = null,
            tint = colors.primary,
            modifier = Modifier.size(20.dp)
        )
        Spacer(Modifier.width(Spacing.sm))
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = stringResource(R.string.capability_skill_group_name),
                style = MaterialTheme.typography.titleSmall,
                color = colors.onSurface
            )
            Text(
                text = stringResource(R.string.capability_group_tool_count, card.total),
                style = MaterialTheme.typography.bodySmall,
                color = colors.onSurfaceVariant
            )
        }
        Pill(text = stringResource(R.string.capability_skill_group_manage), tint = colors.primary)
        Spacer(Modifier.width(Spacing.xs))
        Icon(
            imageVector = if (expanded) Icons.Rounded.KeyboardArrowUp else Icons.Rounded.KeyboardArrowDown,
            contentDescription = stringResource(
                if (expanded) {
                    R.string.capability_skill_group_collapse
                } else {
                    R.string.capability_skill_group_expand
                }
            ),
            tint = colors.onSurfaceVariant
        )
    }
}

/**
 * 技能 → MCP 的映射徽章行。
 *
 * 把 `prompts N` / `resources N` / `别名 N` 直接写在卡片上，是为了让「技能为什么没
 * 出现在 prompts列表里」在 UI 上自解释——MCP 型技能是远端工具的别名，天然不被暴露。
 */
@Composable
private fun SkillMcpMappingRow(card: CapabilitySkillGroupCard) {
    val colors = MaterialTheme.colorScheme
    Row(horizontalArrangement = Arrangement.spacedBy(Spacing.xs)) {
        Pill(
            text = stringResource(R.string.capability_skill_map_prompts) + " " + card.promptCount,
            tint = colors.tertiary
        )
        Pill(
            text = stringResource(R.string.capability_skill_map_resources) + " " + card.scriptCount,
            tint = colors.tertiary
        )
        if (card.mcpAliasCount > 0) {
            Pill(
                text = stringResource(R.string.capability_skill_map_alias) + " " + card.mcpAliasCount,
                tint = colors.onSurfaceVariant
            )
        }
    }
}

/**
 * 技能分组体（展开态）：内嵌完整技能管理界面。
 *
 * 保留 [SkillsScreen] 原样而非重写卡片——技能管理有筛选/搜索/导入导出/删除确认，
 * 重写一遍等于制造第二套技能 UI 实现，后续必然与设置页那份分叉。
 */
@Composable
private fun SkillGroupBody(
    onOpenSkillDetail: (String) -> Unit,
    onEditSkill: (String) -> Unit
) {
    val skillsViewModel: SkillsViewModel = hiltViewModel()
    SkillsScreen(
        viewModel = skillsViewModel,
        onOpenSkillDetail = onOpenSkillDetail,
        onEditSkill = onEditSkill
    )
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
            GroupBadgeRow(group)
        }
    }
}

/**
 * 分组卡片的徽章行（G2 / H1）。
 *
 * 抽出成独立函数有两个理由：一是 [GroupCard] 加入形态/禁用两枚徽章后已超detekt
 * `LongMethod` 阈值；二是徽章的**显示条件**本身是一条不变量——集中在一处，
 * 才不会被后续改动零散地改成「一律都印」。
 *
 * H1：**每张卡片都必须印接入形态**（本地内置 / 远端接入）——用户要求「每张列表卡片
 * 写好是本地内置还是远端接入」。内置分组的形态恒为本地内置，印出来不是废话：
 * 没有它，用户无法从卡片上区分「这台是进程内的还是连出去的」。
 * 传输类型（HTTP / STDIO）只对远端有意义，作为形态之后的第二枚补充徽章。
 */
@Composable
private fun GroupBadgeRow(group: CapabilityGroupCard) {
    val colors = MaterialTheme.colorScheme
    Row(horizontalArrangement = Arrangement.spacedBy(Spacing.xs)) {
        Pill(
            text = if (group.isBuiltin) {
                stringResource(R.string.capability_group_form_builtin)
            } else {
                stringResource(R.string.capability_group_form_remote)
            },
            tint = if (group.isBuiltin) colors.primary else colors.tertiary
        )
        if (!group.isBuiltin) {
            Pill(
                text = if (group.remotelyExposed) {
                    stringResource(R.string.capability_group_exposed)
                } else {
                    stringResource(R.string.capability_group_internal_only)
                },
                tint = if (group.remotelyExposed) colors.tertiary else colors.onSurfaceVariant
            )
            Pill(
                text = stringResource(R.string.capability_group_kind, kindLabel(group.kind)),
                tint = colors.onSurfaceVariant
            )
        }
        if (!group.enabled) {
            Pill(
                text = stringResource(R.string.capability_group_disabled),
                tint = colors.error
            )
        }
        Pill(
            text = stringResource(R.string.capability_agent_tool_count) + " " + group.tools.size,
            tint = colors.onSurfaceVariant
        )
    }
}

/**
 * server 形态的本地化标签（G2）。
 *
 * 在 UI 层做这次映射、而不是直接印枚举名，是为了让枚举新增取值时**编译期就暴露**
 * 缺字符串（`when` 需穷尽），不会静默印出 `REMOTE_STDIO` 这种开发者词汇给用户。
 */
@Composable
private fun kindLabel(kind: ServerKind): String = when (kind) {
    ServerKind.BUILTIN -> stringResource(R.string.capability_kind_builtin)
    ServerKind.REMOTE_HTTP -> stringResource(R.string.capability_kind_http)
    ServerKind.REMOTE_STDIO -> stringResource(R.string.capability_kind_stdio)
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

/**
 * 通用小胶囊徽章。
 *
 * G4 起不再是本文件私有：分组详情页的 prompts / resources 页要用同一枚视觉令牌，
 * 复制一份过去等于给「徽章长什么样」开了第二个真源。放在这里被两边共用，
 * 比各自 private 一份更省事也更不容易跑偏。
 */
@Composable
fun Pill(text: String, tint: Color) {
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
