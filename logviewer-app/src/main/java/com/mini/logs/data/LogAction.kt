package com.mini.logs.data

import com.mini.me_core.core.util.LogLevel

/**
 * 日志长按菜单操作定义。
 *
 * 用 sealed class 替代原来的 9 个回调参数，新增功能只需加子类，
 * 不改 LogActionMenu 签名，可扩展、可测试。
 *
 * 分组：复制 / 过滤 / 搜索 / 标记 / 分享与导航
 */
sealed class LogAction {

    // ── 复制 ──

    /** 复制原始行（时间戳+等级+Tag+消息）。 */
    data object CopyRaw : LogAction()

    /** 仅复制消息内容（去掉前缀）。 */
    data object CopyMessage : LogAction()

    /** 复制指定字段。 */
    data class CopyAs(val field: CopyField) : LogAction() {
        enum class CopyField { TIMESTAMP, TAG, LEVEL, STACKTRACE }
    }

    // ── 过滤 ──

    /** 只显示此 Tag。 */
    data class FilterTagOnly(val tag: String) : LogAction()

    /** 隐藏此 Tag。 */
    data class FilterTagHide(val tag: String) : LogAction()

    /** 只显示此等级。 */
    data class FilterLevelOnly(val level: LogLevel) : LogAction()

    /** 过滤相似消息（用消息前 N 字符模糊匹配）。 */
    data class FilterSimilar(val message: String) : LogAction()

    // ── 搜索 ──

    /** 用指定文本搜索。 */
    data class SearchWith(val query: String) : LogAction()

    // ── 标记 ──

    /** 设置高亮颜色，-1 表示清除。 */
    data class Highlight(val colorIndex: Int) : LogAction()

    /** 添加/编辑书签，note=null 表示弹出输入框。 */
    data class Bookmark(val note: String?) : LogAction()

    /** 清除当前行的所有标记（高亮+书签）。 */
    data object ClearMarks : LogAction()

    // ── 分享与导航 ──

    /** 分享此行。 */
    data object ShareLine : LogAction()

    /** 分享上下文（当前行±5行）。 */
    data object ShareContext : LogAction()

    /** 查看前后上下文（高亮当前行±N行）。 */
    data object ViewContext : LogAction()

    /** 跳转到指定时间。 */
    data class JumpToTime(val time: String) : LogAction()
}
