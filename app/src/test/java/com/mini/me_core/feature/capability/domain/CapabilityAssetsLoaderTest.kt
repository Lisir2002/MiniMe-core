package com.mini.me_core.feature.capability.domain

import com.mini.me_core.feature.agent.domain.execution.mcp.McpException
import com.mini.me_core.feature.agent.domain.execution.mcp.McpManager
import com.mini.me_core.feature.agent.domain.execution.mcp.McpPromptDescriptor
import com.mini.me_core.feature.agent.domain.execution.mcp.McpResourceDescriptor
import com.mini.me_core.feature.agent.domain.execution.mcp.OptionalCapability
import io.mockk.coEvery
import io.mockk.mockk
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * G4：[CapabilityAssetsLoader] 的状态机。
 *
 * 要钉死的核心不变量是**「空」的三种原因在到达 UI 时仍然可区分**：
 * [OptionalCapability.Unsupported] → [AssetListState.Unsupported]、
 * [OptionalCapability.Failed] → [AssetListState.Failed]。二者一旦在某层被压平成
 * 「空列表」，用户看到的就只有一句「暂无」，既不知道该不该点重试，
 * 也不知道该去查网络还是骂 server。这正是 G4 存在的全部理由。
 *
 * 第二条：单条正文失败必须落到 [AssetBodyState.Failed] 并把 server 原文透出。
 * `prompts/get` 最常见的失败是缺必填参数，那段报错是用户唯一能据此改输入的线索。
 */
class CapabilityAssetsLoaderTest {

    private val manager: McpManager = mockk(relaxed = true)

    // ── 列表五态 ────────────────────────────────────────────────────

    @Test
    fun `未加载时状态为 Idle`() {
        val loader = CapabilityAssetsLoader(manager)
        assertTrue(loader.promptsOf("s").value is AssetListState.Idle)
        assertTrue(loader.resourcesOf("s").value is AssetListState.Idle)
    }

    @Test
    fun `枚举成功转为 Ready 并保住条目`() = runBlocking {
        coEvery { manager.listServerPromptsDetailed("s") } returns
            OptionalCapability.Listed(listOf(prompt("review")))
        val loader = CapabilityAssetsLoader(manager)

        loader.loadPrompts("s")

        val state = loader.promptsOf("s").value
        assertTrue(state is AssetListState.Ready)
        assertEquals(1, (state as AssetListState.Ready).items.size)
        assertEquals("review", state.items[0].name)
    }

    @Test
    fun `server 不支持 prompts 时转为 Unsupported 而非空 Ready`() = runBlocking {
        // 区分点：Unsupported 意味着「这 server 没这功能」，UI 不该给重试按钮。
        coEvery { manager.listServerPromptsDetailed("s") } returns OptionalCapability.Unsupported
        val loader = CapabilityAssetsLoader(manager)

        loader.loadPrompts("s")

        assertTrue(loader.promptsOf("s").value is AssetListState.Unsupported)
    }

    @Test
    fun `枚举失败转为 Failed 并保留原因`() = runBlocking {
        coEvery { manager.listServerPromptsDetailed("s") } returns OptionalCapability.Failed("连接被重置")
        val loader = CapabilityAssetsLoader(manager)

        loader.loadPrompts("s")

        val state = loader.promptsOf("s").value
        assertTrue(state is AssetListState.Failed)
        assertEquals("连接被重置", (state as AssetListState.Failed).reason)
    }

    @Test
    fun `管理器抛异常时转为 Failed 而非向外传播`() = runBlocking {
        // 加载器处在 UI 与协议层之间，此处任何一个未捕获异常都会直接崩掉详情页。
        coEvery { manager.listServerPromptsDetailed("s") } throws McpException(message = "boom")
        val loader = CapabilityAssetsLoader(manager)

        loader.loadPrompts("s")

        val state = loader.promptsOf("s").value
        assertTrue(state is AssetListState.Failed)
        assertEquals("boom", (state as AssetListState.Failed).reason)
    }

    @Test
    fun `resources 的三态映射与 prompts 完全一致`() = runBlocking {
        coEvery { manager.listServerResourcesDetailed("s") } returns OptionalCapability.Unsupported
        val loader = CapabilityAssetsLoader(manager)

        loader.loadResources("s")

        assertTrue(loader.resourcesOf("s").value is AssetListState.Unsupported)
    }

    // ── Loading 去重与 force ────────────────────────────────────────

    @Test
    fun `Loading 中重复调用不再发第二次请求`() = runBlocking {
        val gate = CompletableDeferred<OptionalCapability<McpPromptDescriptor>>()
        var calls = 0
        coEvery { manager.listServerPromptsDetailed("s") } coAnswers {
            calls += 1
            gate.await()
        }
        val loader = CapabilityAssetsLoader(manager)
        val first = launch(start = kotlinx.coroutines.CoroutineStart.UNDISPATCHED) { loader.loadPrompts("s") }

        loader.loadPrompts("s")

        assertEquals("第二次调用应被 Loading 去重挡下", 1, calls)
        gate.complete(OptionalCapability.Listed(emptyList()))
        first.join()
    }

    @Test
    fun `force 可以打断卡住的 Loading`() = runBlocking {
        // force 存在的理由：上一轮请求若持续挂起，非强制调用会被永久挡在门外，
        // 「重试」按钮点了没反应，是这个 bug 唯一能被注意到的现象。
        val gate = CompletableDeferred<OptionalCapability<McpPromptDescriptor>>()
        var calls = 0
        coEvery { manager.listServerPromptsDetailed("s") } coAnswers {
            calls += 1
            if (calls == 1) gate.await() else OptionalCapability.Listed(listOf(prompt("review")))
        }
        val loader = CapabilityAssetsLoader(manager)
        val stuck = launch(start = kotlinx.coroutines.CoroutineStart.UNDISPATCHED) { loader.loadPrompts("s") }
        assertTrue(loader.promptsOf("s").value is AssetListState.Loading)

        launch(start = kotlinx.coroutines.CoroutineStart.UNDISPATCHED) { loader.loadPrompts("s", force = true) }

        assertTrue(loader.promptsOf("s").value is AssetListState.Ready)
        assertEquals(2, calls)
        gate.complete(OptionalCapability.Listed(emptyList()))
        stuck.join()
    }

    // ── 分桶：按 server 与资产类型隔离 ───────────────────────────────

    @Test
    fun `同名 prompt 与 resource 的正文互不覆盖`() = runBlocking {
        // bodyKey 里带 kind 就是为此：只有 ref 作键的话，一个名为 x 的 prompt
        // 与 uri 为 x 的 resource 会共用同一个正文状态。
        coEvery { manager.getServerPrompt("s", "x", any()) } returns "prompt 正文"
        coEvery { manager.readServerResource("s", "x") } returns "resource 正文"
        val loader = CapabilityAssetsLoader(manager)

        loader.fetchBody(AssetKind.PROMPT, "s", "x")
        loader.fetchBody(AssetKind.RESOURCE, "s", "x")

        assertEquals(
            AssetBodyState.Ready("prompt 正文"),
            loader.bodyOf(AssetKind.PROMPT, "s", "x").value
        )
        assertEquals(
            AssetBodyState.Ready("resource 正文"),
            loader.bodyOf(AssetKind.RESOURCE, "s", "x").value
        )
    }

    @Test
    fun `不同 server 的列表状态彼此隔离`() = runBlocking {
        coEvery { manager.listServerPromptsDetailed("a") } returns OptionalCapability.Unsupported
        coEvery { manager.listServerPromptsDetailed("b") } returns
            OptionalCapability.Listed(listOf(prompt("p")))
        val loader = CapabilityAssetsLoader(manager)

        loader.loadPrompts("a")
        loader.loadPrompts("b")

        assertTrue(loader.promptsOf("a").value is AssetListState.Unsupported)
        assertTrue(loader.promptsOf("b").value is AssetListState.Ready)
    }

    // ── 正文状态 ────────────────────────────────────────────────────

    @Test
    fun `取 prompt 正文成功时带上参数并转 Ready`() = runBlocking {
        coEvery { manager.getServerPrompt("s", "review", mapOf("lang" to "zh")) } returns "请用中文评审"
        val loader = CapabilityAssetsLoader(manager)

        loader.fetchBody(AssetKind.PROMPT, "s", "review", mapOf("lang" to "zh"))

        assertEquals(
            AssetBodyState.Ready("请用中文评审"),
            loader.bodyOf(AssetKind.PROMPT, "s", "review").value
        )
    }

    @Test
    fun `缺必填参数时透出 server 原文而非吞掉`() = runBlocking {
        // 这条是最容易被写坏的一处：若改成「失败就回到 Idle」，用户只会以为没点上，
        // 于是反复点同一处，而真实的失败原因始终不可见。
        val message = "缺少必填参数: lang"
        coEvery { manager.getServerPrompt("s", "review", any()) } throws McpException(message = message)
        val loader = CapabilityAssetsLoader(manager)

        loader.fetchBody(AssetKind.PROMPT, "s", "review")

        assertEquals(
            AssetBodyState.Failed(message),
            loader.bodyOf(AssetKind.PROMPT, "s", "review").value
        )
    }

    @Test
    fun `正文为空串时是 Ready 而不是失败`() = runBlocking {
        // 空正文是合法结果；把它渲染成失败，用户会以为出问题了。
        coEvery { manager.readServerResource("s", "minime://skill/a") } returns ""
        val loader = CapabilityAssetsLoader(manager)

        loader.fetchBody(AssetKind.RESOURCE, "s", "minime://skill/a")

        assertEquals(
            AssetBodyState.Ready(""),
            loader.bodyOf(AssetKind.RESOURCE, "s", "minime://skill/a").value
        )
    }

    private fun prompt(name: String) = McpPromptDescriptor(name = name)

    private fun resource(uri: String) = McpResourceDescriptor(uri = uri)
}
