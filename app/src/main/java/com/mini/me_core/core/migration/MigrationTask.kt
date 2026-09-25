package com.mini.me_core.core.migration

import android.content.Context

/**
 * 启动期数据迁移任务抽象（通用迁移框架）。
 *
 * 背景：历史上「品牌迁移（DeepCore-Code → MiniMe-core）」是一段写死在启动入口里的特例逻辑。
 * 现把它抽象为可扩展的任务接口——任何「首次升级后需要跑一次」的一次性数据搬迁/修复，
 * 都实现本接口并注册进 [MigrationRunner]，由编排器在启动早期统一按序执行。
 *
 * 设计纪律：
 *  - 每个任务有全局唯一 [id]，[MigrationRunner] 据此持久化「已完成」标记；
 *  - 任务在数据库打开**之前**执行（涉及文件改名/目录搬迁，必须先于任何 DB 访问），
 *    故只依赖 [Context]，不依赖 KVStore / Hilt 注入的 Repository；
 *  - 任务必须幂等、静默、失败不阻塞启动：内部用 runCatching 包裹，绝不向外抛异常；
 *  - 已完成任务下次启动零开销（Runner 查到标记直接跳过，只打一条 Verbose）。
 */
interface MigrationTask {

    /** 全局唯一标识（如 "brand_deepcode_to_minime"），作为完成标记的 key。 */
    val id: String

    /** 人类可读的任务名（如 "品牌迁移 DeepCore→MiniMe"），用于 Info 日志。 */
    val title: String

    /**
     * 执行迁移。必须幂等：重复执行不应破坏已有数据。
     * 实现内部应自行 runCatching 包裹 IO，任何失败降级为日志，绝不向上抛。
     *
     * @return true = 本次实际执行了搬迁/修复；false = 检测到无需迁移（已完成/全新安装）。
     *         Runner 据此决定是否输出 Info 详情（实际迁移才打 Info，否则静默）。
     */
    fun execute(context: Context): Boolean
}
