package com.mini.me_core.feature.agent.presentation

import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/**
 * 输入框草稿状态（ 从 AIAgentViewModel 提取，行为保持搬迁）。
 *
 * 原审计计划称「ComposerViewModel」，但它不是 @HiltViewModel（铁律#4 禁止新增 Hilt VM），
 * 仅为普通 internal 协作者，故命名 [ComposerState]，只拥有输入框文本草稿的读写。
 * 发送动作（enqueueAgentRequest）仍由 AIAgentViewModel 负责，本类不触碰。
 */
internal class ComposerState {

    private val _inputDraft = MutableStateFlow("")
    val inputDraft: StateFlow<String> = _inputDraft.asStateFlow()

    fun updateInputDraft(text: String) { _inputDraft.value = text }

    fun clearInputDraft() { _inputDraft.value = "" }
}
