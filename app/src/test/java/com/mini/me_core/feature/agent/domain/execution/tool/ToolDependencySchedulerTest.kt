package com.mini.me_core.feature.agent.domain.execution.tool

import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonPrimitive
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * L4 依赖感知调度器单测 + C2 组合验收场景。
 *
 * 组合场景对应批次 C 验收标准「一批中含失败写 + 后续依赖写被标记 skipped」：
 * 用真实 [ToolDependencyScheduler.buildSchedule] 的依赖图驱动
 * [ToolBatchPolicy.failedDependency] 跳过判定（workflow runBatchCall 的同款逻辑）。
 */
class ToolDependencySchedulerTest {

    private class FakeTool(
        override val name: String,
        override val capabilities: Set<ToolCapability> = emptySet(),
        override val dependsOn: Set<String> = emptySet()
    ) : AgentTool() {
        override val description: String = "fake"
        override val parameters: Map<String, ToolParameter> = emptyMap()
        override suspend fun execute(args: Map<String, JsonElement>): ToolResult {
            return ToolResult.Success(JsonPrimitive("ok"))
        }
    }

    private fun call(id: String, name: String) = ToolCall(id = id, name = name, arguments = emptyMap())

    private val scheduler = ToolDependencyScheduler()

    @Test
    fun buildSchedule_noDeps_singleLayerEmptyDependencyMap() {
        val registry = ToolRegistry().apply {
            register("readFile", FakeTool("readFile", setOf(ToolCapability.READ_WORKSPACE)))
            register("search", FakeTool("search", setOf(ToolCapability.READ_WORKSPACE)))
        }
        val calls = listOf(call("c1", "readFile"), call("c2", "search"))

        val plan = scheduler.buildSchedule(calls, registry)

        assertFalse(plan.hasCycle)
        assertEquals(1, plan.layers.size)
        assertEquals(2, plan.layers.first().size)
        // C2：依赖图对外暴露（partial failure 跳过判定的数据源）
        assertEquals(setOf("c1", "c2"), plan.dependencies.keys)
        assertTrue(plan.dependencies.values.all { it.isEmpty() })
    }

    @Test
    fun buildSchedule_depEdge_twoLayersAndDependencyExposed() {
        val registry = ToolRegistry().apply {
            register("writeFile", FakeTool("writeFile", setOf(ToolCapability.WRITE_WORKSPACE)))
            register("editFile", FakeTool("editFile", setOf(ToolCapability.WRITE_WORKSPACE), dependsOn = setOf("writeFile")))
        }
        val w = call("w1", "writeFile")
        val e = call("e1", "editFile")

        val plan = scheduler.buildSchedule(listOf(e, w), registry)

        assertFalse(plan.hasCycle)
        assertEquals(2, plan.layers.size)
        // 依赖方在更晚层：writeFile 先于 editFile
        assertEquals(listOf("writeFile"), plan.layers[0].map { it.name })
        assertEquals(listOf("editFile"), plan.layers[1].map { it.name })
        // 依赖图暴露：editFile 依赖 writeFile 调用
        assertEquals(setOf("w1"), plan.dependencies["e1"])
        assertTrue(plan.dependencies["w1"]!!.isEmpty())
    }

    @Test
    fun buildSchedule_cycle_detected() {
        val registry = ToolRegistry().apply {
            register("a", FakeTool("a", dependsOn = setOf("b")))
            register("b", FakeTool("b", dependsOn = setOf("a")))
        }

        val plan = scheduler.buildSchedule(listOf(call("x", "a"), call("y", "b")), registry)

        assertTrue(plan.hasCycle)
        assertTrue(plan.cycleMessage!!.contains("循环依赖"))
    }

    @Test
    fun buildSchedule_emptyCalls_emptyPlan() {
        val plan = scheduler.buildSchedule(emptyList(), ToolRegistry())
        assertFalse(plan.hasCycle)
        assertTrue(plan.layers.isEmpty())
        assertTrue(plan.dependencies.isEmpty())
    }

    /**
     * C2 组合验收：一批中含失败写（writeFile 失败）+ 后续依赖写（editFile 依赖 writeFile）
     * → 依赖调用判定为失败，editFile 被标记跳过（DEPENDENCY_FAILED），不盲跑。
     */
    @Test
    fun partialFailure_failedWrite_dependentEditSkipped() {
        val registry = ToolRegistry().apply {
            register("writeFile", FakeTool("writeFile", setOf(ToolCapability.WRITE_WORKSPACE)))
            register("editFile", FakeTool("editFile", setOf(ToolCapability.WRITE_WORKSPACE), dependsOn = setOf("writeFile")))
        }
        val w = call("w1", "writeFile")
        val e = call("e1", "editFile")
        val plan = scheduler.buildSchedule(listOf(w, e), registry)

        // 模拟执行结果：writeFile 失败、editFile 尚未执行
        val failedIds = setOf(w.id)
        val isFailed: (String) -> Boolean = { it in failedIds }

        // workflow runBatchCall 的同款判定：写失败 → 依赖写跳过
        val failedDepForEdit = ToolBatchPolicy.failedDependency(e, plan.dependencies, isFailed)
        assertEquals(w.id, failedDepForEdit)
        // 写层含写操作能力 → 串行（保顺序，依赖才有意义）
        assertTrue(ToolBatchPolicy.layerRequiresSerialization(plan.layers[0], registry))
        // 无失败的对照组：writeFile 成功时 editFile 不跳过
        assertNull(ToolBatchPolicy.failedDependency(e, plan.dependencies) { false })
    }
}
