package org.minime.core.api

/**
 * 引擎门面常量与公开契约类型（错误码 / 异常 / 导入警告）。
 *
 * 版本线（分批实施计划 §1）三条独立，互不换算：
 * 1. **软件发版版本**（git tag / Release）——项目四段式规范 `x.x.x.x(-rcN)`，D 段 +1；**由维护者确认后才打 tag**；
 * 2. **引擎版本** [VERSION]——**二段式 `主.次`，步长 +0.1，逢九进一**（`1.0 → 1.1 … 1.9 → 2.0`）；
 * 3. **主题包版本**（`manifest.version`）——主题作者自定。
 *
 * 引擎版本只用于主题包 `manifest.engineVersion` 的兼容区间判定，**不是软件发版版本**。
 *
 * 本包为契约叶包（零内部依赖）——schema/tokens/fallback/compiler 全部向上引用它；
 * 编译/加载的结果类型（CompileResult/LoadResult）与入口（ThemeLoader）在 compiler 包（§4.2 导入器）。
 */
object MiniMeEngine {
    /** 引擎版本（二段式，逢九进一）。R1 = 1.0，R2 = 1.1 … R10 = 1.9，转正 = 2.0 */
    const val VERSION: String = "1.1"

    /** 引擎兼容区间（供主题包 manifest.engineVersion 参照） */
    const val COMPAT_RANGE: String = ">=1.0 <2.0"
}

/** 导入/编译失败原因（§5.2 准入校验 + R11 降级路径） */
enum class CompileErrorCode {
    /** 主题包不存在（空输入/文件缺失）——G1 降级路径 1 */
    PACK_NOT_FOUND,
    /** 主题包损坏（非合法 JSON）——G1 降级路径 2 */
    PACK_CORRUPT,
    /** manifest/resolutionOrder/扩展组/schema 声明等结构性错误（§5.2 第 1 行） */
    SCHEMA_INVALID,
    /** manifest.engineVersion 兼容区间不含当前引擎版本 */
    ENGINE_VERSION_INCOMPATIBLE,
    /** 未知 $type（不在 §6.1.3 白名单） */
    TYPE_UNKNOWN,
    /** 值形态与类型不符 / 词汇表令牌类型不符 / 别名目标类型不符 */
    TYPE_MISMATCH,
    /** 别名断链（目标不存在） */
    ALIAS_BROKEN,
    /** 别名循环引用 */
    ALIAS_CYCLE,
    /** 别名语法非法（非 {token.path} 形态） */
    ALIAS_SYNTAX,
    /** 修饰符相关违规：未知修饰符 / 修饰符集含别名（§5.1 规则 3） */
    MODIFIER_INVALID,
    /** 体积超限（tokens > 512KB，§5.2） */
    SIZE_LIMIT_EXCEEDED,
    /** 玻璃表面消费令牌非 opaque（§6.8.5-2） */
    GLASS_OPAQUE_VIOLATION,
    /** 快照构建失败（引擎内部错误） */
    SNAPSHOT_BUILD_FAILED,
    /** 引擎内部未预期异常（含编译器 bug）——G1 降级路径 3 */
    ENGINE_INTERNAL,
}

class ThemeCompileException(
    val code: CompileErrorCode,
    message: String,
    cause: Throwable? = null,
) : Exception(message, cause)

/** 导入警告（不阻断导入，§5.2：设置页明示） */
enum class ImportWarningCode {
    /** text×bg / accent×surface 对比度低于阈值（§5.2 WCAG 行） */
    WCAG_TEXT_CONTRAST,
    /** 玻璃等效底色包络对比度低于阈值（§6.8.5-3） */
    WCAG_GLASS_ENVELOPE,
}

data class ImportWarning(
    val code: ImportWarningCode,
    val message: String,
    val foreground: String? = null,
    val background: String? = null,
)
