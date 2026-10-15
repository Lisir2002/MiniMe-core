package com.mini.me_core.feature.agent.domain.core.provider

import android.content.Context
import android.net.ConnectivityManager
import android.net.NetworkCapabilities
import com.mini.me_core.core.util.FileLogger
import dagger.hilt.android.qualifiers.ApplicationContext
import javax.inject.Inject
import javax.inject.Singleton

private const val TAG = "NetworkStatusChecker"

/**
 * LLM 调用前的网络可达性预检（P1 离线预检）。
 *
 * 背景：系统无网络时直接打 LLM 必然失败，且失败形态是「发了一部分才被 reset」，
 * 用户侧只看到一句 "Software caused connection abort"，既慢又难懂。
 * 在 CallLlm 入口先判一次网络，无网则直接以 OFFLINE 归类结束回合，
 * UI 展示友好文案（见 strings llm_error_offline），不发起无效请求。
 *
 * 判定策略（宁漏勿误）：
 * - 无任何 transport（飞行模式 / 无 WiFi 无数据）→ 离线；
 * - 有 transport + INTERNET 能力 → 在线。
 * - [NetworkCapabilities.NET_CAPABILITY_VALIDATED] **不参与硬判**：国内设备
 *   Google 校验服务器不可达时该能力常为 false，但网络实际可用；误判离线会
 *   直接阻断用户请求，代价远大于漏判。
 * - 系统服务异常 / 读取失败 → 兜底按**在线**处理（放行，由既有重试链路兜底）。
 */
@Singleton
class NetworkStatusChecker @Inject constructor(
    @ApplicationContext private val context: Context,
) {
    /** 当前是否有可用网络；任何异常都按在线兜底（不阻断请求）。 */
    fun isOnline(): Boolean = runCatching {
        val cm = context.getSystemService(Context.CONNECTIVITY_SERVICE) as? ConnectivityManager
            ?: return true
        val caps = cm.getNetworkCapabilities(cm.activeNetwork) ?: return false
        isOnlineFromCaps(
            hasInternet = caps.hasCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET),
            wifi = caps.hasTransport(NetworkCapabilities.TRANSPORT_WIFI),
            cellular = caps.hasTransport(NetworkCapabilities.TRANSPORT_CELLULAR),
            ethernet = caps.hasTransport(NetworkCapabilities.TRANSPORT_ETHERNET),
            vpn = caps.hasTransport(NetworkCapabilities.TRANSPORT_VPN),
        )
    }.getOrDefault(true)

    /** 供日志/排查：当前网络状态的一句话摘要（不抛异常）。 */
    fun describe(): String = runCatching {
        val cm = context.getSystemService(Context.CONNECTIVITY_SERVICE) as? ConnectivityManager
            ?: return "no-connectivity-service"
        val network = cm.activeNetwork ?: return "no-active-network"
        val caps = cm.getNetworkCapabilities(network) ?: return "no-capabilities"
        buildString {
            append("transports=")
            append(
                listOfNotNull(
                    "wifi".takeIf { caps.hasTransport(NetworkCapabilities.TRANSPORT_WIFI) },
                    "cellular".takeIf { caps.hasTransport(NetworkCapabilities.TRANSPORT_CELLULAR) },
                    "ethernet".takeIf { caps.hasTransport(NetworkCapabilities.TRANSPORT_ETHERNET) },
                    "vpn".takeIf { caps.hasTransport(NetworkCapabilities.TRANSPORT_VPN) },
                ).joinToString("+").ifEmpty { "none" }
            )
            append(" internet=").append(caps.hasCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET))
            append(" validated=").append(caps.hasCapability(NetworkCapabilities.NET_CAPABILITY_VALIDATED))
            append(" online=").append(
                isOnlineFromCaps(
                    hasInternet = caps.hasCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET),
                    wifi = caps.hasTransport(NetworkCapabilities.TRANSPORT_WIFI),
                    cellular = caps.hasTransport(NetworkCapabilities.TRANSPORT_CELLULAR),
                    ethernet = caps.hasTransport(NetworkCapabilities.TRANSPORT_ETHERNET),
                    vpn = caps.hasTransport(NetworkCapabilities.TRANSPORT_VPN),
                )
            )
        }
    }.getOrElse { e ->
        FileLogger.w(TAG, "网络状态摘要读取失败", e)
        "unknown"
    }
}

/**
 * 纯判定函数（无 Android 依赖，供单元测试，对齐 [RetryPolicy] 顶层函数惯例）：
 * 有 INTERNET 能力且至少一种 transport（WiFi / 蜂窝 / 以太网 / VPN）才判在线。
 *
 * VALIDATED 能力故意不参与：国内设备 Google 校验不可达时它常为 false 但网络实际可用，
 * 误判离线会直接阻断用户请求，代价远大于漏判（漏判由既有重试链路兜底）。
 */
internal fun isOnlineFromCaps(
    hasInternet: Boolean,
    wifi: Boolean,
    cellular: Boolean,
    ethernet: Boolean,
    vpn: Boolean,
): Boolean = hasInternet && (wifi || cellular || ethernet || vpn)
