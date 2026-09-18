package com.mini.me_core.feature.agent.presentation

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.mini.me_core.core.util.FileLogger
import com.mini.me_core.feature.agent.domain.skill.Skill
import com.mini.me_core.feature.agent.domain.skill.SkillStateRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import javax.inject.Inject

/**
 * 技能选择底部弹窗 ViewModel（newui 层）。
 *
 * 职责：
 * - 合并 [SkillStateRepository.skillsFlow] 与搜索关键字，产出 [filteredSkills]；
 * - 过滤按 name / description / tags 大小写不敏感匹配；
 * - 结果整体排序：已启用在前、组内按名称升序，UI 据此渲染「已启用 / 未启用」两组；
 * - 开关写回仓库（[SkillStateRepository.setEnabled] 即时生效并持久化）。
 */
@HiltViewModel
class SkillPickerViewModel @Inject constructor(
    private val skillStateRepository: SkillStateRepository,
) : ViewModel() {

    private companion object {
        const val TAG = "SkillPickerViewModel"
        const val STOP_TIMEOUT_MS = 5_000L
    }

    /** 搜索关键字（可空串）。 */
    private val _searchQuery = MutableStateFlow("")
    val searchQuery: StateFlow<String> = _searchQuery.asStateFlow()

    /** 过滤并按「已启用在前、组内按名称」排序后的技能列表。 */
    val filteredSkills: StateFlow<List<Skill>> =
        combine(skillStateRepository.skillsFlow, _searchQuery) { skills, query ->
            val q = query.trim().lowercase()
            val filtered = if (q.isEmpty()) {
                skills
            } else {
                skills.filter { skill ->
                    skill.name.lowercase().contains(q) ||
                        skill.description.lowercase().contains(q) ||
                        skill.tags.any { tag -> tag.lowercase().contains(q) }
                }
            }
            // 已启用在前，两组内部均按名称升序
            filtered.sortedWith(
                compareByDescending<Skill> { it.enabled }.thenBy { it.name.lowercase() }
            )
        }.stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(STOP_TIMEOUT_MS),
            initialValue = emptyList(),
        )

    /** 更新搜索关键字。 */
    fun setQuery(query: String) {
        _searchQuery.value = query
    }

    /** 开关技能启用状态（写回仓库，即时生效）。 */
    fun setSkillEnabled(id: String, enabled: Boolean) {
        viewModelScope.launch {
            runCatching { skillStateRepository.setEnabled(id, enabled) }
                .onFailure { FileLogger.e(TAG, "更新技能启用状态失败: $id", it) }
        }
    }
}
