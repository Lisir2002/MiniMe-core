package com.mini.me_core.feature.agent.domain.container

/**
 * 容器侧 mcp.json 变更门控（纯逻辑，可单测）。
 *
 * 规则：检测到相对已确认基线的变更时返回「待确认」——宿主 MCP 侧不得自动热加载，
 * 必须等宿主 UI 显式确认（[approve] 推进基线）后才放行。
 */
object McpConfigChangeGate {

    data class Snapshot(val canonicalPath: String, val mtime: Long)

    /** 首次见到配置文件时直接采纳为基线（无变更）。 */
    fun shouldFlagPending(baseline: Snapshot?, currentPath: String, currentMtime: Long): Boolean {
        if (baseline == null) return false
        if (baseline.canonicalPath != currentPath) return false
        return baseline.mtime != currentMtime
    }
}
