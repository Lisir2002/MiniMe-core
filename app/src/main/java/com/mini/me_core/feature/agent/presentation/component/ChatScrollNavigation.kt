package com.mini.me_core.feature.agent.presentation.component

/**
 * 「跳到上一条用户消息」浮动按钮的纯计算内核。
 *
 * 与渲染层解耦，供 AIChatPanel 消费与纯 JVM 单测直接验证。
 * 消息列表以任务组（[com.mini.me_core.feature.agent.presentation.TaskGroup]）为
 * LazyColumn item，故「用户消息位置」以组索引近似：组内用户片段通常位于组首，
 * 且任务组标题即取第一条用户消息摘要，跳到组索引处用户即可看到自己的发言上下文。
 */

/**
 * 当前视口顶部之上最近的一个用户消息所在组索引。
 *
 * 语义（无状态，完全由视口位置决定，可预测）：
 *  - 视口顶在组 N 时，目标取「严格在 N 之上」的最后一个用户消息组；
 *  - 因此当视口顶本身就落在某个用户消息组上时，再点击天然跳到上一条
 *    （不会重复跳回当前正在看的这一条）；
 *  - 返回 null = 上方没有用户消息（视口已在最顶的用户消息处）——
 *    此时「向上」按钮应隐藏消失。
 *
 * @param userMessageGroupIndices 含用户消息片段的组索引升序列表
 * @param topGroupIndex 当前视口顶部落入的组索引（调用方已钳制到合法范围）
 * @return 目标组索引；null 表示上方无用户消息
 */
internal fun previousUserMessageGroupIndex(
    userMessageGroupIndices: List<Int>,
    topGroupIndex: Int
): Int? = userMessageGroupIndices.lastOrNull { it < topGroupIndex }
