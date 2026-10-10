package com.mini.me_core.feature.agent.domain.execution.permission

import kotlinx.coroutines.flow.Flow

/**
 * 工具授权规则的持久化端口。实现（全局/项目级 JSON 文件读写）位于 data 层。
 *
 * 安全要点不变：全局规则存 app 私有目录（AI 无法篡改），项目级规则存工作区内
 * （可被 git 追踪/回滚）；项目级整工具 shell ALLOW 一律不落盘，防 AI 自我提权。
 */
@Suppress("ClassOrdering") // 行为保持搬迁：端口成员顺序与原类一致，不重排
interface PermissionRulesRepository {

    /** 当前选中的项目名；无选中时为 null（此时项目级规则不可用，仅全局生效）。 */
    fun currentProjectName(): String?

    /** 当前项目名流，跟随工作区切换自动更新，供 UI 订阅。 */
    val currentProjectNameFlow: Flow<String?>

    /** 当前项目规则流：跟随工作区切换自动重新加载对应项目规则；无选中工作区时发空列表。 */
    val currentProjectRulesFlow: Flow<List<PermissionRule>>

    /** 全局规则流，供管理界面观察。 */
    val globalRulesFlow: Flow<List<PermissionRule>>

    /** 一次性读取全部全局规则（备份用）。 */
    suspend fun getGlobalRulesOnce(): List<PermissionRule>

    /** 全量替换全局规则（备份导入用），原子写文件并更新缓存。 */
    suspend fun setGlobalRules(rules: List<PermissionRule>)

    /** 指定项目的规则流，供管理界面观察。 */
    fun projectRulesFlow(projectName: String): Flow<List<PermissionRule>>

    /** 评估用：当前项目规则 + 全局规则合并（项目在前）。一次性读取快照。 */
    suspend fun loadEffectiveForCurrentProject(): List<PermissionRule>

    /** 按 scope 新增规则。PROJECT 写入当前项目；无当前项目则忽略并告警。 */
    suspend fun add(scope: PermissionScope, rule: PermissionRule)

    suspend fun removeGlobalRule(rule: PermissionRule)

    suspend fun removeProjectRule(projectName: String, rule: PermissionRule)

    /** 把一条项目规则提升为全局：项目删、全局加。 */
    suspend fun promoteToGlobal(projectName: String, rule: PermissionRule)
}
