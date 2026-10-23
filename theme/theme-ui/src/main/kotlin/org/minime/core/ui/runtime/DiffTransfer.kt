package org.minime.core.ui.runtime

import org.minime.core.schema.BooleanValue
import org.minime.core.schema.ColorValue
import org.minime.core.schema.CubicBezierValue
import org.minime.core.schema.DimensionValue
import org.minime.core.schema.DurationValue
import org.minime.core.schema.GlassValue
import org.minime.core.schema.NumberValue
import org.minime.core.schema.ShadowValue
import org.minime.core.schema.SpringValue
import org.minime.core.schema.StringValue
import org.minime.core.schema.TokenValue
import org.minime.core.schema.TypographyValue

/**
 * diff 主题包传输层类型（R4.3，设计文档 §7/§8）。
 *
 * 边界：本文件只放**纯数据 + 展示摘要**，编排在 [ThemeManager] 的
 * export/preview/import 三方法——ThemeManager 持有 overlays 与基础包状态，
 * 是唯一有资格填充这些类型的组件。
 */

/**
 * 基础包指纹比对三态（设计 §6.3）。
 *
 * - MATCH：文件声明的基础包指纹 = 当前基础包指纹（同包导出再导入的正常态）；
 * - MISMATCH：基础包不同（可能是别的包的 diff）——**警告级**，不阻断导入，
 *   对话框以 stateWarning banner 呈现「不存在的路径将报错」；
 * - UNKNOWN：文件未声明指纹（形态 B 纯 DTCG）或当前无基础包可比——无从判定。
 */
enum class FingerprintState { MATCH, MISMATCH, UNKNOWN }

/**
 * diff 包预览结果（[ThemeManager.previewDiff] 返回；纯解析、零改态）。
 *
 * 对话框消费约定（设计 §8.3）：
 * - [Failed] = 坏包（格式层拒绝）：以错误 banner 呈现 code/detail，**无「应用」钮**；
 * - [Ok]：呈现 N 条 / 分类 chips（byCategory 由 UI 按工坊口径从 [Ok.overlays] 计算）/
 *   指纹 banner / 样本；[Ok.vocabularyMisses] 与 [Ok.typeMisses] 非空时同样禁「应用」
 *   （这两类路径在覆写编译期必被 [org.minime.core.compiler.OverlayApplier] 拒绝）。
 */
sealed interface DiffPreviewResult {

    data class Ok(
        /** 全量覆写表（UI 按工坊六分类口径分组计数；应用时整表替换当前 overlays） */
        val overlays: Map<String, TokenValue>,
        val total: Int,
        val fingerprint: FingerprintState,
        val basePackId: String?,
        val basePackVersion: String?,
        val exportedAt: String?,
        /** 前 8 条「path = 值摘要」（对话框样本列表，mono 字体渲染） */
        val sample: List<String>,
        /** 不在标准词汇表（173 条）的路径——应用必失败（SCHEMA_INVALID），对话框须呈现 */
        val vocabularyMisses: List<String>,
        /** 值类型与词汇表声明类型不符的路径——应用必失败（TYPE_MISMATCH），对话框须呈现 */
        val typeMisses: List<String>,
    ) : DiffPreviewResult

    data class Failed(val code: String, val message: String) : DiffPreviewResult
}

/**
 * diff 包导入结果（[ThemeManager.importDiff] 返回，设计 §7）。
 *
 * - [Applied]：解析层已接受并进入覆写编译流程（后台 + last-wins + 失败回滚，
 *   与 R4.1 覆写同一管线）；编译失败经 `ThemeStatus.Failed` 暴露且 overlays 回滚；
 * - [Rejected]：格式层拒绝（坏包）——调用方零改态，对话框以错误态呈现。
 */
sealed interface ImportResult {
    data class Applied(val count: Int) : ImportResult
    data class Rejected(val code: String, val message: String) : ImportResult
}

/**
 * 令牌值摘要（对话框样本行用）：紧凑、确定性的单行表示。
 *
 * 展示层辅助——不参与任何序列化/校验路径（那是 [org.minime.core.schema.DiffSerializer]
 * 的职责）；放传输层而非 Composable 内，保证 ThemeManager 预览可测且两端口径一致。
 */
internal fun summarizeTokenValue(value: TokenValue): String = when (value) {
    is ColorValue -> value.hex
    is DimensionValue -> "${fmtTokenFloat(value.value)}${value.unit}"
    is DurationValue -> "${fmtTokenFloat(value.ms)}ms"
    is NumberValue -> fmtTokenFloat(value.value)
    is BooleanValue -> value.value.toString()
    is StringValue -> value.value
    is TypographyValue -> buildString {
        append(fmtTokenFloat(value.fontSize.value)).append("sp")
        value.lineHeight?.let { append('/').append(fmtTokenFloat(it.value)).append("sp") }
        value.fontWeight?.let { append(' ').append(it) }
        value.fontFamily?.let { append(' ').append(it) }
    }
    is SpringValue -> "spring(${fmtTokenFloat(value.stiffness)}, ${fmtTokenFloat(value.damping)}, ${fmtTokenFloat(value.initialVelocity)})"
    is GlassValue -> "glass(blur=${fmtTokenFloat(value.blur.value)}${value.blur.unit}, sat=${fmtTokenFloat(value.saturation)}, tint=${value.tint.hex}, a=${fmtTokenFloat(value.tintAlpha)}, hairline=${value.hairline})"
    is ShadowValue -> "shadow(${value.color.hex})"
    is CubicBezierValue -> "cubicBezier(${fmtTokenFloat(value.x1)}, ${fmtTokenFloat(value.y1)}, ${fmtTokenFloat(value.x2)}, ${fmtTokenFloat(value.y2)})"
}

/**
 * 摘要浮点紧凑格式（去尾零，最多两位小数）——与工坊滑杆读数 `wsFmt` 同一展示约定
 * （分处两个模块，各自 module-internal；引擎侧不消费本格式，无真源冲突）。
 */
internal fun fmtTokenFloat(v: Float): String {
    val r = Math.round(v * 100f) / 100f
    return if (r == r.toInt().toFloat()) r.toInt().toString() else r.toString()
}
