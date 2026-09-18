package com.mini.me_core.feature.agent.presentation.component

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.rememberScrollState
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.mini.me_core.R
import com.mini.me_core.feature.agent.domain.skill.Skill
import com.mini.me_core.feature.agent.domain.skill.SkillScope
import com.mini.me_core.feature.agent.presentation.SkillPickerViewModel
import com.mini.me_core.newui.designsystem.component.AppBottomSheetList
import com.mini.me_core.newui.designsystem.component.AppMenuRow
import com.mini.me_core.newui.designsystem.component.AppSearchBar
import com.mini.me_core.newui.designsystem.component.AppSectionHeader
import com.mini.me_core.newui.designsystem.component.AppSwitch
import com.mini.me_core.newui.designsystem.theme.appPalette
import com.mini.me_core.newui.designsystem.token.generated.AppSpacing

/**
 * 技能选择底部弹窗（newui 层）：列出全部技能，可搜索、可全局启用/禁用。
 *
 * 布局：
 * - 顶部 [AppBottomSheetList] 容器（newui 骨架，不手写 Material3）；
 * - 先放 [AppSearchBar]，按名称 / 描述 / 标签过滤；
 * - 下方按「已启用 / 未启用」两组 [AppSectionHeader] 分组，已启用在前；
 * - 每行 [AppMenuRow]：副标题为 `作用域 · 类型 · 描述`，尾随槽放 [AppSwitch] 开关。
 *
 * 颜色 / 尺寸全部走 newui token（[appPalette] / [AppSpacing]），不出现裸色值或硬编码 dp。
 */
@Composable
fun SkillPickerSheet(
    viewModel: SkillPickerViewModel,
    onDismiss: () -> Unit,
) {
    val query by viewModel.searchQuery.collectAsStateWithLifecycle()
    val skills by viewModel.filteredSkills.collectAsStateWithLifecycle()

    AppBottomSheetList(onDismiss = onDismiss, title = stringResource(R.string.skill_picker_title)) {
        AppSearchBar(
            value = query,
            onValueChange = viewModel::setQuery,
            modifier = Modifier.fillMaxWidth(),
            placeholder = stringResource(R.string.skill_picker_search_hint),
            onClear = { viewModel.setQuery("") },
        )

        Spacer(Modifier.height(AppSpacing.Md))

        if (skills.isEmpty()) {
            Text(
                text = stringResource(R.string.skill_picker_empty),
                style = MaterialTheme.typography.bodyMedium,
                color = appPalette().labelSecondary,
                modifier = Modifier.fillMaxWidth(),
            )
        } else {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(AppSpacing.Xs),
            ) {
                val enabled = skills.filter { it.enabled }
                val disabled = skills.filterNot { it.enabled }

                if (enabled.isNotEmpty()) {
                    AppSectionHeader(title = stringResource(R.string.skill_picker_section_enabled))
                    enabled.forEach { skill ->
                        SkillPickerRow(
                            skill = skill,
                            onToggle = { viewModel.setSkillEnabled(skill.id, it) },
                        )
                    }
                }

                if (disabled.isNotEmpty()) {
                    Spacer(Modifier.height(AppSpacing.Sm))
                    AppSectionHeader(title = stringResource(R.string.skill_picker_section_disabled))
                    disabled.forEach { skill ->
                        SkillPickerRow(
                            skill = skill,
                            onToggle = { viewModel.setSkillEnabled(skill.id, it) },
                        )
                    }
                }
            }
        }
    }
}

/** 单个技能行：名称 + 副标题（作用域 · 类型 · 描述），尾随开关。 */
@Composable
private fun SkillPickerRow(
    skill: Skill,
    onToggle: (Boolean) -> Unit,
) {
    val scopeLabel = when (skill.scope) {
        SkillScope.GLOBAL -> stringResource(R.string.skill_scope_global)
        SkillScope.AGENT -> stringResource(R.string.skill_scope_agent)
        SkillScope.CONVERSATION -> stringResource(R.string.skill_scope_conversation)
    }
    AppMenuRow(
        title = skill.name,
        subtitle = "$scopeLabel · ${skill.type.name} · ${skill.description}",
        trailing = {
            AppSwitch(
                checked = skill.enabled,
                onCheckedChange = onToggle,
            )
        },
    )
}
