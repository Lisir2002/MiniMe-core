package com.mini.me_core.core.util

/**
 * 日志「行头 / 附属行」聚合的**单一真源**（审计 L2）。
 *
 * 背景：把扁平日志行聚合成「行头 + 堆栈附属行」的逻辑曾在三处各自实现——
 * 应用内 `LogLineItem.buildLogEntries`、`SettingsViewModel` 的过滤/统计、
 * 以及独立模块 logviewer-app 的 `LogRepository.parseLinesToEntries`。
 * 三份实现漂移会让"同一条日志在不同界面显示不同"，这里抽成纯 Kotlin、可单测的共享模型，
 * 各 UI 层只做渲染映射。
 *
 * 规则：
 *  - 跳过空白行与格式头块（`# ...`）；
 *  - 能被 [LogLineParser] 解析的行 → 新 [Entry] 行头；
 *  - 紧随其后的不可解析行 → 归入当前 Entry 的 [Entry.stack]；
 *  - 任何行头之前出现的孤立不可解析行 → [Loose]。
 */
object LogEntryClassifier {

    /** 一条结构化日志（行头 + 其后的堆栈附属行）。 */
    data class Entry(
        val headerRaw: String,
        val parsed: ParsedLogLine,
        val stack: List<String>,
    )

    /** 游离附属行（没有任何行头的孤立行）。 */
    data class Loose(val line: String)

    /** 分类结果项。 */
    sealed interface Item {
        data class Head(val entry: Entry) : Item
        data class Stray(val loose: Loose) : Item
    }

    /** 把扁平日志行聚合成有序的分类项列表。 */
    fun classify(lines: List<String>): List<Item> {
        val items = mutableListOf<Item>()
        var headerRaw: String? = null
        var parsed: ParsedLogLine? = null
        var stack = mutableListOf<String>()

        fun flush() {
            val h = headerRaw
            val p = parsed
            if (h != null && p != null) items.add(Item.Head(Entry(h, p, stack.toList())))
        }

        for (line in lines) {
            if (line.isBlank()) continue
            if (line.trimStart().startsWith("#")) continue
            val p = LogLineParser.parse(line)
            if (p != null) {
                flush()
                headerRaw = line
                parsed = p
                stack = mutableListOf()
            } else {
                if (headerRaw == null) items.add(Item.Stray(Loose(line))) else stack.add(line)
            }
        }
        flush()
        return items
    }
}
