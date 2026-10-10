package com.mini.me_core.feature.proxy.domain

import com.mini.me_core.core.util.FileLogger
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.yaml.snakeyaml.Yaml
import java.io.File

/**
 * 拆分产物：Clash 配置合成 / 危险键剥离 / 回环绑定。
 *
 * 逻辑原样搬迁自 [ClashProxyManager]，安全加固一字不动：
 *  - 远程订阅 / 任何外部 YAML 均不可信，必须走 [ClashProxyManager.newSafeYaml]（SafeConstructor，
 *    禁用任意 Java 类型标签）；
 *  - 源配置里被固定覆盖块接管的顶层危险键（[ClashProxyManager.OVERRIDDEN_KEYS]）在解析成
 *    Map 后剥离，再 dump 回 YAML（嵌套块式代理节点不受损）；
 *  - 固定块绑定 127.0.0.1（[ClashProxyManager.CONTROLLER_HOST]），allow-lan false；
 *  - DNS 防泄露固定块（fake-ip + 国内 DoH 主 / 海外 DoH fallback）无条件覆盖。
 */
internal class ClashConfigSynthesizer(
    private val ports: ClashProxyPorts,
    private val secretProvider: () -> String,
    private val configDir: () -> File,
) {
    private val tag = "ClashProxyManager"

    /**
     * 合成 mihomo 配置：固定覆盖块头 + 清洗后的源配置体。
     * 用真实 YAML 解析而非逐行正则清洗；源不是 YAML 映射时退化为仅 DIRECT 兜底的空配置并告警。
     */
    @Suppress("TooGenericExceptionCaught") // 行为保持搬迁：外部订阅可能抛任意 YAML/IO 异常，必须兜底走 DIRECT 分支；cause 经 e.message 记入 FileLogger
    fun synthesizeConfig(sourceYaml: String): String {
        val clean = LinkedHashMap<Any?, Any?>()
        try {
            // 外部订阅不可信：用 SafeConstructor 解析，禁止任意类型标签。
            val root = ClashProxyManager.newSafeYaml().load<Any?>(sourceYaml)
            if (root is Map<*, *>) {
                root.forEach { (k, v) ->
                    val key = k?.toString() ?: return@forEach
                    if (key !in ClashProxyManager.OVERRIDDEN_KEYS) clean[key] = v
                }
            } else {
                FileLogger.w(
                    tag,
                    "订阅源不是 YAML 映射（实际为 ${root?.javaClass?.simpleName ?: "null"}），仅生成 DIRECT 兜底配置"
                )
            }
        } catch (e: Exception) {
            FileLogger.w(tag, "订阅源 YAML 解析失败（${e.message}），仅生成 DIRECT 兜底配置")
        }
        if (clean["rules"] == null) clean["rules"] = listOf("MATCH,DIRECT")
        // DNS 防泄露（）：无论订阅自带什么 dns 配置，一律覆盖为 fake-ip + 国内 DoH 主 / 海外 DoH
        // fallback。fake-ip 模式下命中代理规则的域名由远端代理解析，本机不再发出明文 DNS 查询；
        // fallback-filter 按 geoip=CN 分流：国内域名走国内 DoH，海外域名走 Cloudflare DoH，
        // 避免「订阅商 DNS 被劫持 / 本机 DNS 泄露真实访问目标」。
        clean["dns"] = buildFixedDnsConfig()
        val body = Yaml().dump(clean)
        return buildString {
            appendLine("mixed-port: ${ports.mixed}")
            appendLine("allow-lan: false")
            appendLine("mode: rule")
            // info 而非 silent：内核启动期 FATAL/错误必须落到 mihomo.log，否则秒退原因完全不可见。
            appendLine("log-level: info")
            appendLine("external-controller: ${ClashProxyManager.CONTROLLER_HOST}:${ports.controller}")
            appendLine("secret: \"${secretProvider()}\"")
            appendLine()
            append(body.trimEnd('\n'))
            appendLine()
        }
    }

    /**
     * 固定 DNS 配置（ 防泄露）。
     *
     * - enhanced-mode=fake-ip：命中代理规则的域名直接返回 198.18.0.0/16 假地址，真实域名由远端代理
     *   出口解析，本机不发明文 DNS；
     * - default-nameserver：纯 IP 明文 DNS，仅用于引导 DoH 域名本身的解析（bootstrap），
     *   用国内可达的阿里/腾讯 DoH 前置解析；
     * - nameserver（主）：国内 DoH（阿里 223.5.5.5 / 腾讯 1.12.12.12），国内域名低延迟；
     * - fallback（备）：海外 DoH（Cloudflare 1.1.1.1 / Google 8.8.8.8），国内主 DNS 被污染时兜底；
     * - fallback-filter.geoip-code=CN：仅当解析结果地理归属不在 CN 时采用 fallback 答案，
     *   实现「国内域名国内解、海外域名海外解」的分流，避免 DNS 泄露。
     * - ipv6=false：关闭 IPv6 DNS，避免 IPv6 隧道绕过代理造成泄露。
     */
    private fun buildFixedDnsConfig(): Map<String, Any?> = linkedMapOf(
        "enable" to true,
        "ipv6" to false,
        "enhanced-mode" to "fake-ip",
        "fake-ip-range" to "198.18.0.1/16",
        "default-nameserver" to listOf("223.5.5.5", "119.29.29.29"),
        "nameserver" to listOf(
            "https://223.5.5.5/dns-query",
            "https://1.12.12.12/dns-query",
        ),
        "fallback" to listOf(
            "https://1.1.1.1/dns-query",
            "https://8.8.8.8/dns-query",
        ),
        "fallback-filter" to linkedMapOf(
            "geoip" to true,
            "geoip-code" to "CN",
            "ipcidr" to listOf("240.0.0.0/4"),
        ),
        "fake-ip-filter" to listOf(
            "*.lan",
            "*.local",
            "+.internal",
            "localhost.ptlogin2.qq.com",
        ),
    )

    /**
     * 用 SnakeYAML 解析 Clash 配置，统计内联节点 / 分组 / proxy-provider。
     * 修复「解析 OK 但节点 0」：真实 YAML 解析后块式/流式/provider 一并对齐 Clash。
     */
    @Suppress("TooGenericExceptionCaught") // 行为保持搬迁：预检对任意解析异常都返回 ok=false 并带 error 文本，不能因未列异常类型而崩溃
    fun parseClashConfig(yaml: String): ClashConfigSummary {
        return try {
            // 外部配置不可信：SafeConstructor 禁止 !! 任意类型标签。
            val root = ClashProxyManager.newSafeYaml().load<Any?>(yaml)
            if (root !is Map<*, *>) {
                return ClashConfigSummary(ok = false, error = "不是合法的 YAML 映射")
            }
            val proxies = root["proxies"] as? List<*> ?: emptyList<Any?>()
            val groups = root["proxy-groups"] as? List<*> ?: emptyList<Any?>()
            val providers = root["proxy-providers"] as? Map<*, *> ?: emptyMap<Any?, Any?>()
            val validProxyTypes = setOf(
                "ss", "shadowsocks", "vmess", "trojan", "ssr", "shadowsocksr",
                "http", "https", "socks5", "hysteria2", "hy2", "vless",
                "wireguard", "wg", "tuic", "tuic5", "snell", "brook",
                "naive", "naiveproxy", "ssh", "direct", "reject", "relay",
                "dns", "mtproto", "anytls", "ech"
            )
            val nodes = proxies.mapNotNull { item ->
                if (item !is Map<*, *>) return@mapNotNull null
                val name = item["name"]?.toString()?.takeIf { it.isNotBlank() }
                    ?: return@mapNotNull null
                val type = item["type"]?.toString()?.lowercase() ?: "unknown"
                // 过滤非节点条目：必须是已知代理类型，且有 server 字段
                if (type !in validProxyTypes) return@mapNotNull null
                val server = item["server"]?.toString()?.takeIf { it.isNotBlank() }
                    ?: return@mapNotNull null
                ProxyNodeInfo(
                    name = name,
                    type = type,
                    server = server,
                    port = (item["port"] as? Number)?.toInt()
                        ?: item["port"]?.toString()?.toIntOrNull() ?: 0,
                )
            }
            val groupNames = groups.mapNotNull { (it as? Map<*, *>)?.get("name")?.toString() }
            ClashConfigSummary(ok = true, nodes = nodes, groups = groupNames, providerCount = providers.size)
        } catch (e: Exception) {
            ClashConfigSummary(ok = false, error = e.message ?: "YAML 解析失败")
        }
    }

    /** 把合成配置落盘到 filesDir/minime/proxy/config.yaml。 */
    suspend fun writeConfigFile(configYaml: String) = withContext(Dispatchers.IO) {
        runCatching {
            val dir = configDir().apply { mkdirs() }
            val cfg = File(dir, ClashProxyManager.CONFIG_FILE)
            cfg.writeText(configYaml, Charsets.UTF_8)
            FileLogger.i(tag, "proxy config 已写入 ${cfg.absolutePath}")
        }.onFailure { FileLogger.w(tag, "写 proxy config 失败: ${it.message}") }
    }

    /** 落盘 config.yaml 是否能解析为 YAML 映射（mihomo 加载前的最后一道闸，防「配损坏 → 秒退」）。 */
    fun validateConfigFile(f: File): Boolean = runCatching {
        val root = ClashProxyManager.newSafeYaml().load<Any?>(f.readText(Charsets.UTF_8))
        root is Map<*, *>
    }.getOrDefault(false)
}
