package org.minime.core.ui.runtime

import org.minime.core.api.CompileErrorCode

/**
 * 主题运行时状态（设置页与调试面板消费，§10.8 调试面板「主题」行的数据源）。
 */
sealed interface ThemeStatus {

    /** 尚未初始化（冷启动前） */
    data object Idle : ThemeStatus

    /** 正在后台编译（设置页显示进度，§8.2「导入不卡 UI」） */
    data class Loading(val packId: String, val modifiers: List<String>) : ThemeStatus

    /** 已生效 */
    data class Ready(
        val packId: String,
        val version: String,
        val modifiers: List<String>,
        val warnings: Int,
        val degraded: Boolean,
    ) : ThemeStatus

    /**
     * 编译/加载失败——**绝不无主题**（G1）。
     *
     * 两个信息缺一不可：
     * - [packId] / [code]：谁失败了、为什么（设置页据此提示「你选的主题包已损坏」）；
     * - [fellBackTo]：实际生效的是哪个包（内置包 / [ThemeManager.FALLBACK_ID]），
     *   冷启动降级链命中时非 null——**有主题**与**可诊断**必须同时满足。
     */
    data class Failed(
        val packId: String,
        val modifiers: List<String>,
        val code: CompileErrorCode,
        val message: String,
        val fellBackTo: String? = null,
    ) : ThemeStatus
}
