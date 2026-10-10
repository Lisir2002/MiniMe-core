package com.mini.me_core.feature.proxy.domain

import com.mini.me_core.core.util.FileLogger
import com.mini.me_core.feature.proxy.data.ProxySettingsRepository
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request

/**
 * P2-16 拆分产物：远程订阅下载 / 更新。
 *
 * 逻辑原样搬迁自 [ClashProxyManager]：
 *  - 订阅抓取伪装成 clash.meta UA（订阅商 nginx 按 UA 白名单放行，否则回 406 HTML）；
 *  - 手动/自动刷新：拉远端 YAML → 更新 updatedAt → 若该 profile 正是活跃且内核在跑，热重载新配置；
 *  - 周期扫描 autoUpdate 订阅，到期逐个刷新（失败隔离）。
 */
internal class ClashSubscriptionService(
    private val okHttp: OkHttpClient,
    private val repository: ProxySettingsRepository,
    private val synthesizer: ClashConfigSynthesizer,
    private val controlClient: ClashControlClient,
    private val ensureSecretLoaded: suspend () -> Unit,
    private val isKernelAlive: () -> Boolean,
    private val isEnabledCache: () -> Boolean,
) {
    private val tag = "ClashProxyManager"

    /** 订阅 URL 全文抓取（拉取远端订阅 YAML）。失败返回 null。 */
    suspend fun fetchSubscriptionYaml(url: String): String? = withContext(Dispatchers.IO) {
        runCatching {
            val req = Request.Builder().url(url)
                .header("User-Agent", ClashProxyManager.SUBSCRIPTION_USER_AGENT)
                .get().build()
            okHttp.newCall(req).execute().use { resp ->
                if (!resp.isSuccessful || resp.body == null) return@use null
                resp.body?.string()
            }
        }.getOrNull()
    }

    /** 取出某 profile 的订阅型入口 URL：非订阅型 / 无秘密均返回 null（对应原早 return false）。 */
    private suspend fun findSubscriptionEntry(id: String): String? {
        val sub = repository.subscriptionsFlow.first().firstOrNull { it.id == id }
        if (sub != null && sub.kind == ProxySubscription.KIND_SUBSCRIPTION) {
            return repository.revealSecret(id)?.trim()
        }
        return null
    }

    /** 若该订阅正是当前活跃且内核在跑，热重载最新配置（不杀进程）。 */
    private suspend fun hotReloadIfActive(id: String, fresh: String) {
        val activeId = repository.activeProfileIdFlow.first()
        if (activeId == id && isEnabledCache() && isKernelAlive()) {
            val config = synthesizer.synthesizeConfig(fresh)
            synthesizer.writeConfigFile(config)
            controlClient.reloadConfig(config)
            FileLogger.i(tag, "活跃订阅 $id 已热重载最新配置")
        }
    }

    /**
     * P1-7：手动/自动刷新某个订阅型 profile。
     *
     * 拉取远端最新 YAML → 更新 updatedAt（URL 不变故 cipher 不换）→ 若该 profile 正是当前活跃且内核
     * 在跑，热重载新配置（不杀进程）。manual 型 / 拉取失败返回 false，不影响其他 profile。
     */
    suspend fun refreshSubscriptionNow(id: String): Boolean {
        ensureSecretLoaded()
        val url = findSubscriptionEntry(id)
        if (url != null) {
            val fresh = fetchSubscriptionYaml(url)
            if (fresh != null) {
                repository.refreshSubscription(id, url)
                hotReloadIfActive(id, fresh)
                FileLogger.i(tag, "订阅 $id 更新成功（updatedAt=${System.currentTimeMillis()}）")
                return true
            }
            FileLogger.w(tag, "刷新订阅 $id 失败：拉取 YAML 返回空")
        }
        return false
    }

    /** P1-7：周期检查所有 autoUpdate 订阅型 profile 是否到期，逐个刷新（失败隔离，不影响其他）。 */
    suspend fun autoUpdateDueSubscriptions() {
        val now = System.currentTimeMillis()
        val subs = runCatching { repository.subscriptionsFlow.first() }.getOrDefault(emptyList())
        subs.filter { it.kind == ProxySubscription.KIND_SUBSCRIPTION && it.autoUpdate }.forEach { sub ->
            val elapsed = if (sub.updatedAt == 0L) Long.MAX_VALUE else now - sub.updatedAt
            val due = elapsed >= sub.updateIntervalHours * 3600_000L
            if (due) {
                runCatching { refreshSubscriptionNow(sub.id) }
                    .onFailure { FileLogger.w(tag, "自动更新订阅 ${sub.id} 失败: ${it.message}") }
            }
        }
    }

    /**
     * 重建核心：config.yaml 缺失时从活跃 profile 重新合成并落盘；任一步不可用返回 false。
     * 调用方负责异常隔离（对应原 try/catch(Throwable)）。
     */
    private suspend fun rebuildFromActiveSource(): Boolean {
        val activeId = repository.activeProfileIdFlow.first()
        if (activeId != null) {
            val revealed = repository.revealSecret(activeId)
            if (revealed != null) {
                val trimmed = revealed.trim()
                // 订阅型按 trimmed 拉远端；手动型原样传 revealed（保持原行为，不做额外 trim）。
                val source = if (trimmed.startsWith("http://") || trimmed.startsWith("https://")) {
                    fetchSubscriptionYaml(trimmed)
                } else {
                    revealed
                }
                if (source != null && source.isNotBlank()) {
                    synthesizer.writeConfigFile(synthesizer.synthesizeConfig(source))
                    return true
                }
            }
        }
        return false
    }

    /**
     * 启动时自动恢复用：config.yaml 缺失时，从活跃 profile 重新合成并落盘（尽力而为）。
     * 订阅型需重新拉远端 YAML，网络不可达时返回 false，自动恢复跳过。
     * 用 runCatching 等价隔离任意异常（含网络/IO/解析），不暴露给启动协程。
     */
    suspend fun rebuildConfigFromActiveProfile(): Boolean {
        ensureSecretLoaded()
        return runCatching { rebuildFromActiveSource() }
            .onFailure { FileLogger.w(tag, "启动重建 config.yaml 失败: ${it.message}") }
            .getOrDefault(false)
    }
}
