package com.mini.me_core.feature.agent.presentation

import com.mini.mecore.datalayer.sqldelight.agent.Agent_session as V2AgentSession
import com.mini.me_core.datalayer.repository.AgentRepository
import com.mini.me_core.feature.agent.domain.session.SessionUseCase
import com.mini.me_core.feature.agent.domain.session.checkpoint.CheckpointManager
import com.mini.me_core.feature.backup.domain.BackupManager
import com.mini.me_core.feature.settings.data.repository.DefaultModelSettingsRepository
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.every
import io.mockk.mockk
import io.mockk.slot
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Before
import org.junit.Test

/**
 * 回归：新会话默认模型绑定丢失 / 「清空上下文并切换模型」竞态。
 *
 * 场景一：createSession 旧实现在 upsertSession 之前调 updateProviderModel，
 * UPDATE 打在不存在的行上 = no-op，调用方随后插入 provider_id=null，
 * 新会话永远丢失默认模型绑定，请求时静默回退全局 active provider。
 * 场景二：F2.5「清空上下文」旧实现 setSessionProviderModel + newSession 两步走，
 * 非空会话时前者不写默认模型，新会话仍读旧默认模型，用户选的模型丢失。
 */
class ChatSessionControllerSessionBindingTest {

    private lateinit var sessionUseCase: SessionUseCase
    private lateinit var defaultModelSettings: DefaultModelSettingsRepository
    private lateinit var currentSessionId: MutableStateFlow<String?>
    private lateinit var currentWorkspace: MutableStateFlow<String>
    private val resetCalls = mutableListOf<String>()

    private fun controller() = ChatSessionController(
        scope = CoroutineScope(Dispatchers.Unconfined),
        currentSessionId = currentSessionId,
        currentWorkspace = currentWorkspace,
        sessionUseCase = sessionUseCase,
        v2Agent = mockk<AgentRepository>(relaxed = true),
        checkpointManager = mockk<CheckpointManager>(relaxed = true),
        defaultModelSettingsRepository = defaultModelSettings,
        environmentProbeController = mockk(relaxed = true),
        backupManager = mockk<BackupManager>(relaxed = true),
        onResetSessionState = { resetCalls += it },
        onCancelSessionJob = {},
        onClearSessionMaps = {}
    )

    private fun stubNewSession(id: String) {
        every { sessionUseCase.newSession(any()) } returns V2AgentSession(
            id = id, title = "新会话", mode = "BUILD", model = null, status = "active",
            created_at = 1L, updated_at = 1L, workspace_path = "", reasoning_effort = "MEDIUM",
            provider_id = null, total_input_tokens = 0L, total_output_tokens = 0L, last_input_tokens = 0L
        )
    }

    @Before
    fun setUp() {
        sessionUseCase = mockk(relaxed = true)
        defaultModelSettings = mockk(relaxed = true)
        currentSessionId = MutableStateFlow(null)
        currentWorkspace = MutableStateFlow("/ws")
        resetCalls.clear()
        stubNewSession("new-session-id")
    }

    // ── createSession：绑定必须随对象落库 ────────────────────────────────────

    @Test
    fun createSession_carriesDefaultBindingOnReturnedSession() = runBlocking {
        coEvery { defaultModelSettings.getDefaultProviderId() } returns "p-def"
        coEvery { defaultModelSettings.getDefaultModel() } returns "m-def"

        val s = controller().createSession()

        assertEquals("p-def", s.provider_id)
        assertEquals("m-def", s.model)
        // 旧实现依赖的 UPDATE 必须不再出现（打在不存在的行上 = no-op）
        coVerify(exactly = 0) { sessionUseCase.updateProviderModel(any(), any(), any()) }
    }

    @Test
    fun createSession_withoutDefault_returnsUnboundSession() = runBlocking {
        coEvery { defaultModelSettings.getDefaultProviderId() } returns ""
        coEvery { defaultModelSettings.getDefaultModel() } returns ""

        val s = controller().createSession()

        assertNull(s.provider_id)
        assertNull(s.model)
    }

    // ── newSessionWithModel：显式绑定，无竞态 ────────────────────────────────

    @Test
    fun newSessionWithModel_nonEmptyCurrentSession_createsNewBoundSession() {
        currentSessionId.value = "s-old"
        coEvery { sessionUseCase.isSessionEmpty("s-old") } returns false

        controller().newSessionWithModel("pX", "mX")

        val slot = slot<V2AgentSession>()
        coVerify(exactly = 1) { sessionUseCase.upsertSession(capture(slot)) }
        assertEquals("pX", slot.captured.provider_id)
        assertEquals("mX", slot.captured.model)
        // 绑定随对象一次落库，不再依赖 UPDATE 时序
        coVerify(exactly = 0) { sessionUseCase.updateProviderModel(any(), any(), any()) }
        // 非空会话不动默认模型设置（旧实现两步走在这里丢模型）
        coVerify(exactly = 0) { defaultModelSettings.setDefaultModel(any(), any()) }
        assertEquals("new-session-id", currentSessionId.value)
    }

    @Test
    fun newSessionWithModel_emptyCurrentSession_reusesAndBinds() {
        currentSessionId.value = "s-empty"
        coEvery { sessionUseCase.isSessionEmpty("s-empty") } returns true

        controller().newSessionWithModel("pX", "mX")

        // 空会话复用：不堆积空会话，仅改绑模型并复位 UI 态
        assertEquals(listOf("s-empty"), resetCalls)
        coVerify(exactly = 1) { sessionUseCase.updateProviderModel("s-empty", "pX", "mX") }
        coVerify(exactly = 1) { defaultModelSettings.setDefaultModel("pX", "mX") }
        coVerify(exactly = 0) { sessionUseCase.upsertSession(any()) }
        assertEquals("s-empty", currentSessionId.value)
    }

    @Test
    fun newSessionWithModel_blankWorkspace_isNoop() {
        currentWorkspace.value = ""
        currentSessionId.value = "s-old"
        coEvery { sessionUseCase.isSessionEmpty("s-old") } returns false

        controller().newSessionWithModel("pX", "mX")

        coVerify(exactly = 0) { sessionUseCase.upsertSession(any()) }
        coVerify(exactly = 0) { sessionUseCase.updateProviderModel(any(), any(), any()) }
        assertEquals("s-old", currentSessionId.value)
    }
}
