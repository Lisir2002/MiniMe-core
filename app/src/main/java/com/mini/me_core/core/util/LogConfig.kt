package com.mini.me_core.core.util

/**
 * 日志层集中配置。
 *
 * 把原本散落在 [FileLogger] / [AILogger] 中的常量（文件上限、缓冲大小、目录名、格式版本等）
 * 收敛到一处，便于统一调参与运行时覆盖。所有字段均为 `var`，可在 `init` 之前（或运行时）
 * 按需覆盖；[reset] 恢复内置默认值。
 *
 * 设计原则：
 * - 默认值与历史行为保持一致（FileLogger 单文件 5MB、AILogger 20MB、保留 7 天）；
 * - 纯 Kotlin object，无 Android 依赖，可在单元测试中直接覆盖/重置。
 */
object LogConfig {

    // ── 文件大小上限（字节） ──
    /** FileLogger 单个按天日志文件上限，超过后滚动为 .1 / .2 ... （默认 5MB）。 */
    var maxFileBytes: Long = DEFAULT_MAX_FILE_BYTES

    /** AILogger 单个会话日志文件上限，超过后滚动为 .1 / .2 ... （默认 20MB）。 */
    var maxAiFileBytes: Long = DEFAULT_MAX_AI_FILE_BYTES

    // ── 清理策略 ──
    /** 日志保留天数：早于该天数的文件在 init / 权限切换时删除（默认 7 天）。 */
    var maxAgeDays: Int = DEFAULT_MAX_AGE_DAYS

    // ── 内存缓冲 / 批量 flush ──
    /** 内存缓冲累积到该字节数时触发一次批量落盘（默认 64KB）。 */
    var bufferSizeBytes: Int = DEFAULT_BUFFER_SIZE_BYTES

    /** 定时 flush 间隔：即使缓冲未达阈值，也按该周期把缓冲落盘（默认 500ms）。 */
    var flushIntervalMs: Long = DEFAULT_FLUSH_INTERVAL_MS

    // ── 目录命名 ──
    /** 公共外部存储根目录名（Documents/<publicRootDir>/ 与 Download/<publicRootDir>/）。 */
    var publicRootDir: String = DEFAULT_PUBLIC_ROOT_DIR

    /** FileLogger 在公共目录下的子目录名。 */
    var logSubdir: String = DEFAULT_LOG_SUBDIR

    /** AILogger 在公共目录下的子目录名。 */
    var aiLogSubdir: String = DEFAULT_AI_LOG_SUBDIR

    // ── 格式 ──
    /** 日志格式版本号，写入新文件的格式头 `# MiniMe Log Format v<formatVersion>`。 */
    var formatVersion: String = DEFAULT_FORMAT_VERSION

    // ── 开关 ──
    /** 是否在写入前对消息 / 堆栈执行 [LogSanitizer] 敏感信息脱敏（默认开启）。 */
    var enableSanitizer: Boolean = true

    // ── 默认值（私有常量，供 reset 恢复） ──
    const val DEFAULT_MAX_FILE_BYTES: Long = 5L * 1024 * 1024          // 5MB
    const val DEFAULT_MAX_AI_FILE_BYTES: Long = 20L * 1024 * 1024      // 20MB
    const val DEFAULT_MAX_AGE_DAYS: Int = 7
    const val DEFAULT_BUFFER_SIZE_BYTES: Int = 64 * 1024               // 64KB
    const val DEFAULT_FLUSH_INTERVAL_MS: Long = 500L
    const val DEFAULT_PUBLIC_ROOT_DIR: String = "MiniMe-core"
    const val DEFAULT_LOG_SUBDIR: String = "logs"
    const val DEFAULT_AI_LOG_SUBDIR: String = "ai-logs"
    const val DEFAULT_FORMAT_VERSION: String = "1"

    /** 恢复所有字段到内置默认值。单元测试在 @Before / @After 中调用以隔离用例。 */
    fun reset() {
        maxFileBytes = DEFAULT_MAX_FILE_BYTES
        maxAiFileBytes = DEFAULT_MAX_AI_FILE_BYTES
        maxAgeDays = DEFAULT_MAX_AGE_DAYS
        bufferSizeBytes = DEFAULT_BUFFER_SIZE_BYTES
        flushIntervalMs = DEFAULT_FLUSH_INTERVAL_MS
        publicRootDir = DEFAULT_PUBLIC_ROOT_DIR
        logSubdir = DEFAULT_LOG_SUBDIR
        aiLogSubdir = DEFAULT_AI_LOG_SUBDIR
        formatVersion = DEFAULT_FORMAT_VERSION
        enableSanitizer = true
    }
}
