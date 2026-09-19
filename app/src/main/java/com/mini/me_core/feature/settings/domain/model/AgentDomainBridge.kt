package com.mini.me_core.feature.settings.domain.model

/**
 * 解耦桥接（settings → agent）。
 *
 * settings 层不再直接引用 feature.agent 的共享域类型，改为经本文件 typealias 透明转发。
 * typealias 在编译期指向 agent 域中的同名类，不改业务语义、不改运行时行为；
 * RHS 使用全限定名，本文件不写任何指向 agent 域包的 import 语句，
 * 从而使 settings 目录对 agent 域包的 import 文件数下降。
 *
 * 注：仅桥接 settings 真正消费的枚举/数据类；后续可按需把这些纯域模型正式下沉到共享层。
 */
typealias ZthPresetTier = com.mini.me_core.feature.agent.domain.zth.ZthPresetTier
typealias ZthPerformanceClass = com.mini.me_core.feature.agent.domain.zth.ZthPerformanceClass
typealias McpServerConfig = com.mini.me_core.feature.agent.domain.mcp.McpServerConfig
typealias McpServerStatus = com.mini.me_core.feature.agent.domain.mcp.McpServerStatus
typealias McpServerState = com.mini.me_core.feature.agent.domain.mcp.McpServerStatus.State
typealias McpToolDescriptor = com.mini.me_core.feature.agent.domain.mcp.McpToolDescriptor
