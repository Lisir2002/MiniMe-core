package org.minime.core.schema

/**
 * 合并前的原始令牌（未解析别名）。
 * 标量令牌 = literal 或 alias 二选一；复合令牌 = fields（逐字段 literal/alias）
 * 或整体 alias（引用另一条同型复合令牌）。
 */
data class RawToken(
    val path: String,
    val declaredType: TokenType,
    val literal: TokenValue? = null,
    val alias: String? = null,
    val fields: Map<String, RawField>? = null,
)

/** 复合型字段（§5.1：复合型字段允许别名，与顶层别名同一解析时序） */
data class RawField(
    val literal: TokenValue? = null,
    val alias: String? = null,
)

/** 主题包 manifest（§5.1） */
data class Manifest(
    val name: String,
    val version: String,
    val author: String?,
    val engineVersion: String,
    val description: String?,
    val preview: String?,
)

/** 一个 set（公共集或修饰符集）：扁平路径 → 原始令牌 */
data class SetModel(
    val name: String,
    val tokens: Map<String, RawToken>,
)

/** resolutionOrder 条目（set 与 modifier 二选一） */
data class ResolutionEntry(
    val set: String?,
    val modifier: String?,
)

/** `$extensions.minime.<group>` 令牌组（编辑器/终端/图表等开放词汇，§3.7/§5.1） */
data class ExtensionGroup(
    val name: String,
    val tokens: Map<String, RawToken>,
)

/** `$extensions.minime.glass` 配置：声明为玻璃表面消费的文本/图标令牌（§6.8.5-2） */
data class GlassConfig(
    val consumers: List<String> = emptyList(),
)

/** `$extensions.minime.schema` 声明（引擎自定义型字段清单，须与 CompositeSchemas 一致） */
data class SchemaDeclarations(
    val spring: List<String>? = null,
    val glass: List<String>? = null,
)

/**
 * 主题包模型（MVP：单 JSON）。
 * 规范边界：version/$value/$type/$description/$extensions/别名 {path} 严守 DTCG Format；
 * sets + resolutionOrder 的合并语义是 MiniMe Resolver 稳定子集（§5.1 三规则）。
 */
data class ThemePack(
    val dcgVersion: String,
    val manifest: Manifest,
    val sets: Map<String, SetModel>,
    val resolutionOrder: List<ResolutionEntry>,
    val extensions: Map<String, ExtensionGroup>,
    val glassConfig: GlassConfig = GlassConfig(),
    val schemaDeclarations: SchemaDeclarations = SchemaDeclarations(),
)
