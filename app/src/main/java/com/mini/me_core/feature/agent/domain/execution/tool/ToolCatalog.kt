package com.mini.me_core.feature.agent.domain.execution.tool

import javax.inject.Inject
import javax.inject.Singleton

/**
 * B2 工具目录检索器（Deferred Loading 的「查菜单」组件）。
 *
 * 背景：DEFERRED 工具不进每轮上下文，模型通过 `tool_catalog` 元工具按关键词检索发现。
 * 检索为轻量 BM25 变体（词项权重求和 + 稳定排序），无倒排索引构建成本：
 * 每次检索对当前 [ToolRegistry] 全量工具（40+ 个）现取现算，微秒级，
 * 且天然反映 MCP 工具的动态注册/注销（不做缓存，免失效逻辑）。
 *
 * 分词策略（中英混合）：
 * - 拉丁词：snake_case / camelCase 边界切分并小写，同时保留完整小写形式
 *   （"readFile" 产出 read / file / readfile，三种查询形态都可命中）；
 * - CJK：无词边界，按二元组（bigram）切分（"读取文件" → 读取/取文/文件/件内），
 *   单字不退化为 bigram（噪声大），直接以单字入索引。
 *
 * 权重：工具名 3 > 参数名 2 > 别名 3 > 描述 1。命中数求和，同分按工具名升序（确定性）。
 */
@Singleton
class ToolCatalog @Inject constructor() {

    /**
     * 检索与 [query] 相关的工具。
     *
     * @param tools 候选工具清单（调用方传入当前 registry 全量，含 MCP 动态工具）
     * @param query 检索关键词（中文/英文/混合均可；空或纯停用字符返回空列表）
     * @param limit 返回上限（默认 [DEFAULT_LIMIT]）
     * @return 按相关度降序的工具列表（无命中返回空列表）
     */
    fun search(
        tools: List<AgentTool>,
        query: String,
        limit: Int = DEFAULT_LIMIT
    ): List<AgentTool> {
        val queryTokens = tokenize(query).toSet()
        if (queryTokens.isEmpty() || tools.isEmpty()) return emptyList()
        return tools
            .map { tool -> tool to scoreOf(tool, queryTokens) }
            .filter { (_, score) -> score > 0 }
            .sortedWith(
                compareByDescending<Pair<AgentTool, Int>> { it.second }.thenBy { it.first.name }
            )
            .take(limit.coerceAtLeast(0))
            .map { it.first }
    }

    /** 单工具打分：查询词项在 name(3)/alias(3)/param(2)/description(1) 的权重命中求和。 */
    private fun scoreOf(tool: AgentTool, queryTokens: Set<String>): Int {
        var score = 0
        val nameTokens = tokenize(tool.name).toHashSet()
        val aliasTokens = tool.aliases.flatMap { tokenize(it) }.toHashSet()
        val paramTokens = tool.parameters.keys.flatMap { tokenize(it) }.toHashSet()
        val descTokens = tokenize(tool.description).toHashSet()
        for (t in queryTokens) {
            if (t in nameTokens) score += NAME_WEIGHT
            if (t in aliasTokens) score += ALIAS_WEIGHT
            if (t in paramTokens) score += PARAM_WEIGHT
            if (t in descTokens) score += DESC_WEIGHT
        }
        return score
    }

    /**
     * 中英混合分词：
     * - 拉丁字母/数字序列：camelCase 与 snake_case 边界切分小写 + 完整小写形式；
     * - CJK 连续段：二元组（bigram），单字段原样入列；
     * - 其余字符（空格/标点）为分隔符。
     */
    internal fun tokenize(text: String): List<String> {
        val tokens = mutableListOf<String>()
        val latin = StringBuilder()
        val cjk = StringBuilder()

        fun flushLatin() {
            if (latin.isEmpty()) return
            val raw = latin.toString()
            latin.clear()
            // camelCase 边界切分（readFile → read/file），下划线已在分隔符处断开
            raw.split(CAMEL_BOUNDARY).filter { it.isNotBlank() }.forEach { tokens.add(it.lowercase()) }
            tokens.add(raw.lowercase())
        }

        fun flushCjk() {
            if (cjk.isEmpty()) return
            val raw = cjk.toString()
            cjk.clear()
            if (raw.length == 1) {
                tokens.add(raw)
            } else {
                for (i in 0..raw.length - 2) tokens.add(raw.substring(i, i + 2))
            }
        }

        for (ch in text) {
            when {
                ch.isLetterOrDigit() && !isCjk(ch) -> latin.append(ch)
                isCjk(ch) -> {
                    flushLatin()
                    cjk.append(ch)
                }
                else -> {
                    flushLatin()
                    flushCjk()
                }
            }
        }
        flushLatin()
        flushCjk()
        return tokens
    }

    private fun isCjk(ch: Char): Boolean = ch.code in 0x2E80..0x9FFF || ch.code in 0xF900..0xFAFF

    companion object {
        /** 目录默认返回条数：5 条足够模型决策，又不至于把 deferred 全量倒回上下文。 */
        const val DEFAULT_LIMIT = 5

        private const val NAME_WEIGHT = 3
        private const val ALIAS_WEIGHT = 3
        private const val PARAM_WEIGHT = 2
        private const val DESC_WEIGHT = 1

        /** camelCase 边界：小写/数字后紧跟大写（readFile → read|File；parseURL2JSON → parse|URL2|JSON）。 */
        private val CAMEL_BOUNDARY = Regex("(?<=[a-z0-9])(?=[A-Z])")
    }
}
