package org.minime.core.compiler

import org.minime.core.api.CompileErrorCode
import org.minime.core.api.ThemeCompileException
import org.minime.core.fallback.FallbackTheme
import org.minime.core.tokens.ThemeSnapshot

/**
 * 主题加载器（G1/R11 降级路径的入口）。
 *
 * 三条降级路径（R1 验收逐条覆盖）：
 * 1. **包不存在**（空输入 / 资源缺失）→ `PACK_NOT_FOUND` → fallback 快照；
 * 2. **包损坏**（非合法 JSON / sets 缺失）→ `PACK_CORRUPT` / `SCHEMA_INVALID` → fallback 快照；
 * 3. **编译异常**（任一 §5.2 校验行不通过 / 引擎内部错误）→ 相应 errorCode → fallback 快照。
 *
 * 降级永远有交付物（[LoadResult.degraded] = true 且 snapshot 非空）——
 * 快照损坏/编译异常/包不存在都不允许把 App 置于无主题状态。
 */
class ThemeLoader(
    private val compiler: ThemeCompiler = ThemeCompiler(),
) {
    /**
     * 加载并编译主题包 JSON。
     *
     * @param json 主题包 JSON 文本（null / 空白 = 包不存在，走降级路径 1）
     * @param activeModifiers 运行时激活的修饰符（调用方按系统上下文决定，互斥单选由调用方保证）
     */
    fun load(json: String?, activeModifiers: List<String> = emptyList()): LoadResult {
        if (json.isNullOrBlank()) {
            return degraded(CompileErrorCode.PACK_NOT_FOUND, "主题包不存在（输入为空）")
        }
        return try {
            val result = compiler.compileJson(json, activeModifiers)
            LoadResult(
                snapshot = result.snapshot,
                warnings = result.warnings,
                degraded = false,
                error = null,
            )
        } catch (e: ThemeCompileException) {
            degraded(e.code, e.message ?: "主题包编译失败", e)
        } catch (e: Exception) {
            // 编译器未预期异常（引擎 bug）——仍降级，不崩溃（G1）
            degraded(CompileErrorCode.ENGINE_INTERNAL, "引擎内部错误: ${e.message}", e)
        }
    }

    /** 加载内置默认主题包（classpath: /default-theme.json，light 上下文） */
    fun loadBundledDefault(activeModifiers: List<String> = emptyList()): LoadResult {
        val json = ThemeLoader::class.java.getResourceAsStream(BUNDLED_RESOURCE)?.use { stream ->
            stream.bufferedReader(Charsets.UTF_8).readText()
        }
        if (json.isNullOrEmpty()) {
            return degraded(CompileErrorCode.PACK_NOT_FOUND, "内置默认主题包资源缺失: $BUNDLED_RESOURCE")
        }
        return load(json, activeModifiers)
    }

    private fun degraded(
        code: CompileErrorCode,
        message: String,
        cause: Throwable? = null,
    ): LoadResult = LoadResult(
        snapshot = fallbackSnapshot(),
        warnings = emptyList(),
        degraded = true,
        error = ThemeCompileException(code, message, cause),
    )

    private fun fallbackSnapshot(): ThemeSnapshot = FallbackTheme.snapshot

    companion object {
        private const val BUNDLED_RESOURCE = "/default-theme.json"
    }
}
