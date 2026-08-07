package com.vpn.member.vpn.mihomo

import android.net.ConnectivityManager
import android.net.Network
import android.net.NetworkCapabilities
import android.os.Build

/** 过滤 TUN/无效地址，避免 Mihomo 把 172.19.0.2 当作上游 DNS 形成环路。 */
object MihomoDnsFilter {
    private val TUN_DNS_PREFIXES = listOf("172.19.", "172.18.", "198.18.")

    fun isUsableUpstream(addr: String): Boolean {
        val trimmed = addr.trim()
        if (trimmed.isBlank() || trimmed == "0.0.0.0" || trimmed == "::") return false
        if (TUN_DNS_PREFIXES.any { trimmed.startsWith(it) }) return false
        // 链路本地 / 带 zone 的地址（如 fe80::1%wlan0）写入 Mihomo 易导致 DNS 异常。
        if (trimmed.startsWith("fe80:", ignoreCase = true) || trimmed.contains('%')) return false
        return true
    }

    fun formatDnsEndpoint(
        host: String,
        port: Int = 53,
    ): String = host.trim()

    /** 按传输类型优先级选取物理网（WiFi > 以太网 > 蜂窝）。 */
    fun findBestPhysicalNetwork(cm: ConnectivityManager): Network? =
        cm.allNetworks
            .mapNotNull { network ->
                val caps = cm.getNetworkCapabilities(network) ?: return@mapNotNull null
                if (caps.hasTransport(NetworkCapabilities.TRANSPORT_VPN)) return@mapNotNull null
                if (!caps.hasCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET)) return@mapNotNull null
                network to transportPriority(caps)
            }.minByOrNull { it.second }
            ?.first

    fun findPhysicalNetwork(cm: ConnectivityManager): Network? = findBestPhysicalNetwork(cm)

    fun extractDnsServers(
        cm: ConnectivityManager,
        network: Network?,
    ): List<String> {
        if (network == null) return emptyList()
        return cm.getLinkProperties(network)
            ?.dnsServers
            ?.mapNotNull { it.hostAddress?.trim()?.takeIf { addr -> isUsableUpstream(addr) } }
            .orEmpty()
            .distinct()
    }

    private fun transportPriority(caps: NetworkCapabilities): Int =
        when {
            caps.hasTransport(NetworkCapabilities.TRANSPORT_WIFI) -> 0
            caps.hasTransport(NetworkCapabilities.TRANSPORT_ETHERNET) -> 1
            Build.VERSION.SDK_INT >= Build.VERSION_CODES.S &&
                caps.hasTransport(NetworkCapabilities.TRANSPORT_USB) -> 2
            caps.hasTransport(NetworkCapabilities.TRANSPORT_BLUETOOTH) -> 3
            caps.hasTransport(NetworkCapabilities.TRANSPORT_CELLULAR) -> 4
            else -> 20
        }
}
