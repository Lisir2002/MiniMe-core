package com.mini.me_core.feature.agent.domain.execution.workflow

import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonObject

/**
 * 循环级 guard：连续「相同工具 + 相同参数」调用的重复提醒追踪器。
 *
 * 对齐 DSH repeat-tool-reminder：阈值 3/5/8。当模型对同一工具连续传入完全相同的参数
 * 达到阈值时，生成 advisory 提醒（不阻塞、不改写、决策留给模型），由 workflow 在下一轮
 * CallLlm 时注入系统提示词末尾。
 *
 * 线程安全：[record] 可能在并行 awaitAll 的多协程下被并发调用，[takeAdvisory] 由
 * 非挂起的 PromptSource.build 同步取走。所有可变状态统一由对象锁保护，保证 streak 计数
 * 与 pendingAdvisory 不丢、不重、不竞态。
 */
class LoopGuardTracker(
    /** 连续相同调用提醒阈值序列（升序）。 */
    private val thresholds: List<Int> = listOf(3, 5, 8)
) {
    /** 当前连续追踪的工具名。 */
    private var currentTool: String? = null

    /** 当前连续追踪的参数签名（用于「相同参数」判定）。 */
    private var currentArgsSignature: String? = null

    /** 当前连续相同调用的次数。 */
    private var count: Int = 0

    /** 当前 streak 内已提醒过的阈值级，避免同一级重复提醒。 */
    private val announced = mutableSetOf<Int>()

    /** 尚未被 [takeAdvisory] 消费的提醒。 */
    private var pendingAdvisory: String? = null

    /** 已累积的连续相同调用次数（供可观测/单测断言）。 */
    val currentCount: Int get() = synchronized(this) { count }

    /** 当前被连续追踪的工具名（供可观测/单测断言）。 */
    val currentToolName: String? get() = synchronized(this) { currentTool }

    /**
     * 记录一次已执行（或已批准执行）的工具调用。工具名与参数签名均与上次相同则 streak +1；
     * 否则重置 streak。命中未提醒的阈值级时生成 pending 提醒，由 [takeAdvisory] 取走。
     */
    fun record(toolName: String, args: Map<String, JsonElement>) = synchronized(this) {
        val signature = argsSignature(args)
        if (toolName == currentTool && signature == currentArgsSignature) {
            count += 1
        } else {
            currentTool = toolName
            currentArgsSignature = signature
            count = 1
            announced.clear()
        }
        val hit = thresholds.firstOrNull { it == count && announced.add(it) }
        if (hit != null) {
            pendingAdvisory = buildAdvisory(toolName, hit)
        }
    }

    /** 取走待注入的提醒（一次性消费）；无未消费提醒时返回 null。 */
    fun takeAdvisory(): String? = synchronized(this) {
        val advisory = pendingAdvisory
        pendingAdvisory = null
        advisory
    }

    /** 规范化参数签名：稳定键序 JSON 序列化 + 长度上限（大参数如文件内容只取前段参与判定）。 */
    private fun argsSignature(args: Map<String, JsonElement>): String =
        Json.encodeToString(JsonObject(args)).take(MAX_SIGNATURE_LENGTH)

    private fun buildAdvisory(toolName: String, level: Int): String =
        "【系统提醒】你已连续 $level 次调用「$toolName」工具并传入完全相同的参数。" +
            "如果前面的调用没有解决问题，请不要再盲目重复相同操作：先读取更多上下文定位根因，" +
            "或改用其他工具/策略，或直接向用户说明并确认，避免无意义的重复。"

    private companion object {
        /** 参数签名长度上限：仅用于相同性判定，前段足以区分不同调用。 */
        const val MAX_SIGNATURE_LENGTH = 512
    }
}
