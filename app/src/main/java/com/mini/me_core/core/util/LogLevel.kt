package com.mini.me_core.core.util

/**
 * 日志等级，由低到高。[NONE] 用作阈值时关闭一切输出（没有任何等级 ≥ NONE）。
 *
 * 顺序即严重程度：阈值 [FileLogger.minLevel] 之下的日志（logcat 与落盘）都会被丢弃。
 *
 * [FATAL] 位于 [ERROR] 与 [NONE] 之间，用于「进程即将崩溃/已崩溃」这种最高严重等级的兜底记录
 * （见 [FileLogger.flushSync] 与 MiniMeCore 的崩溃处理器）。
 */
enum class LogLevel {
    VERBOSE, DEBUG, INFO, WARN, ERROR, FATAL, NONE
}
