package com.mini.me_core.core.util

import java.io.File

/**
 * 日志文件命名与目录解析的**单一真源**（审计 M / F2 / L1）。
 *
 * 背景：`FileLogger` / `AILogger` 曾各自复制一份「目录解析 + 滚动 + 清理」，跨模块的
 * logviewer-app 又硬编码重写了一遍规则。同一规则三份实现，任何一处漂移都会让
 * 「日志看不见 / 清不掉 / 日期筛不到」。这里把**命名约定**与**目录解析策略**收敛到一处，
 * 供两套日志实现（及未来的诊断产物）共用。
 *
 * 说明：真正的**文件替换/删除 IO** 仍留在各 Logger 内（它们对线程与缩进有各自约束），
 * 本对象只负责「怎么命名、去哪找、怎么按日期识别」这类纯规则，便于单测与跨模块复用。
 */
object LogFiles {

    /** 应用日志文件名前缀（`log-<date>[.<n>].txt`）。 */
    const val APP_LOG_PREFIX = "log-"

    /** AI 会话日志文件名前缀（`session-<id>[.<n>].log`）。 */
    const val AI_LOG_PREFIX = "session-"

    /**
     * 从应用日志文件名解析出**逻辑日期**（`yyyy-MM-dd`），兼容滚动件。
     *
     * 修复 L1：过去 UI 用 `name.removePrefix("log-").removeSuffix(".txt")` 直接取后缀，
     * 于是 `log-2026-09-29.1.txt` 解出 `"2026-09-29.1"`，与筛选用的 `"2026-09-29"`
     * 永不相等 —— 一旦启用日期筛选，**所有滚动文件被静默丢弃**。
     *
     * 本方法用正则只取日期段，`log-2026-09-29.txt` 与 `log-2026-09-29.1.txt`
     * 都解析为 `2026-09-29`。
     *
     * @return 解析成功返回 `yyyy-MM-dd`；非应用日志文件返回 null。
     */
    fun parseAppLogDate(fileName: String): String? =
        APP_LOG_DATE_PATTERN.find(fileName)?.groupValues?.getOrNull(1)

    /** 同 [parseAppLogDate]，但接收 File。 */
    fun parseAppLogDate(file: File): String? = parseAppLogDate(file.name)

    /**
     * 按 `(日期, 滚动序号)` 复合键排序（审计 L4）：同一天的活动文件排在其滚动件之前，
     * 且滚动件按序号升序。过去的 `sortedBy { it.name }` 会让 `.1` 因字符序小于 `.txt`
     * 而排在当天活动文件**之前**，查看时日期分组交错。
     */
    fun sortAppLogFiles(files: List<File>): List<File> =
        files.sortedWith(compareBy({ parseAppLogDate(it.name) ?: "" }, { rotationIndex(it.name) }))

    /**
     * 解析滚动序号：活动文件为 0，`log-<date>.N.txt` 为 N。
     * 用于排序与"是否同一逻辑日"的判断。
     */
    fun rotationIndex(fileName: String): Int =
        APP_LOG_ROTATION_PATTERN.find(fileName)?.groupValues?.getOrNull(1)?.toIntOrNull() ?: 0

    private val APP_LOG_DATE_PATTERN = Regex("""^log-(\d{4}-\d{2}-\d{2})(?:\.\d+)?\.txt$""")
    private val APP_LOG_ROTATION_PATTERN = Regex("""^log-\d{4}-\d{2}-\d{2}\.(\d+)\.txt$""")
}
