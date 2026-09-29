package com.mini.me_core.datalayer.migration

/**
 * 迁移引擎**拒绝继续**的致命信号（审计 H5）。
 *
 * 背景：过去 `MigrationEngine.preOpen` 用 `error(...)` 抛 `IllegalStateException`
 * 表达「版本回退，拒绝打开以防数据损坏」，而调用方 `DataLayerModule` 用 `runCatching` 把它
 * 和「快照失败」这类非致命异常一起吞掉，最后照常用低版本 schema 打开高版本库——
 * 致命信号被降级成一个 warn 日志。
 *
 * 现在区分两类失败，调用方据此决定「终止」还是「带日志继续」：
 *  - **[MigrationRejectedException]**：继续 = 数据损坏或静默丢失，**必须上抛终止**；
 *  - 其它异常（IO / 快照失败等）：记 error 后可按降级策略继续。
 *
 * 继承 [IllegalStateException] 是为了兼容既有的 `catch (IllegalStateException)` 调用点，
 * 使其不会因为类型变化而意外被当作"未知异常"处理。
 */
class MigrationRejectedException(
    message: String,
    val reason: RejectReason,
    cause: Throwable? = null,
) : IllegalStateException(message, cause) {

    /** 拒绝原因，便于调用方分类与埋点。 */
    enum class RejectReason {
        /** 版本回退：库版本高于代码 schema 版本，用低版本 schema 打开必然损坏数据。 */
        VERSION_DOWNGRADE,

        /**
         * 库不可读，且**未能把现场安全保存**（复制到 backup 目录失败）。
         * 此时隔离 + 重建 = 静默清空用户数据，必须终止而不是继续启动。
         */
        UNREADABLE_NO_SAFETY_COPY,
    }
}
