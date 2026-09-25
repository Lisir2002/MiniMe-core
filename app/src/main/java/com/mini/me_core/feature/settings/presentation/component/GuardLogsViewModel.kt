package com.mini.me_core.feature.settings.presentation.component

import androidx.lifecycle.ViewModel
import com.mini.me_core.feature.agent.domain.guard.GuardLogEntry
import com.mini.me_core.feature.agent.domain.guard.GuardLogRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.StateFlow
import javax.inject.Inject

/** P2：护栏日志 ViewModel，从 GuardLogRepository 读取最近拦截记录。 */
@HiltViewModel
class GuardLogsViewModel @Inject constructor(
    private val guardLogRepository: GuardLogRepository
) : ViewModel() {
    val logs: StateFlow<List<GuardLogEntry>> = guardLogRepository.logsFlow
}
