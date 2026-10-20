package org.minime.core.compiler

import org.minime.core.api.ImportWarning
import org.minime.core.api.ThemeCompileException
import org.minime.core.tokens.ThemeSnapshot

/**
 * 编译结果（[ThemeCompiler.compile] 产出）。
 *
 * [warnings] 为 §5.2 警告行（WCAG 对比度 / 玻璃包络）——不阻断导入，由设置页明示。
 */
data class CompileResult(
    val snapshot: ThemeSnapshot,
    val warnings: List<ImportWarning>,
)

/**
 * 加载结果（[ThemeLoader] 产出，G1/R11 降级路径的交付契约）。
 *
 * [degraded] = true 表示已降级到内置 fallback 快照，[error] 携带降级原因（供上报）；
 * 降级永远有交付物——包不存在 / 包损坏 / 编译异常都不允许把 App 置于无主题状态。
 */
data class LoadResult(
    val snapshot: ThemeSnapshot,
    val warnings: List<ImportWarning>,
    val degraded: Boolean,
    val error: ThemeCompileException?,
)
