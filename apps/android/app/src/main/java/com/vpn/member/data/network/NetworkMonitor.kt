package com.vpn.member.data.network

import android.content.Context
import android.net.ConnectivityManager
import android.net.Network
import android.net.NetworkCapabilities

/**
 * 监听**物理网**从不可用恢复为可用，用于刷新 dashboard / VPN 自愈。
 * 忽略 VPN 网络，避免 TUN 建立时误触 networkRestored。
 *
 * 与 [VpnNetworkEvents.transportChanged]（WiFi↔蜂窝 DNS 变化）一并驱动
 * [VpnReconnectSupervisor] 的同一套完整重连 /（关自动重连时）自愈。
 */
object NetworkMonitor {
    private val _networkRestored =
        kotlinx.coroutines.flow.MutableSharedFlow<Unit>(extraBufferCapacity = 1)
    val networkRestored: kotlinx.coroutines.flow.SharedFlow<Unit> = _networkRestored

    @Volatile
    private var started = false

    fun start(context: Context) {
        if (started) return
        started = true
        val appContext = context.applicationContext
        val cm = appContext.getSystemService(ConnectivityManager::class.java) ?: return
        var hadValidatedPhysical = hasValidatedPhysicalInternet(cm)

        val request =
            android.net.NetworkRequest.Builder()
                .addCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET)
                .addCapability(NetworkCapabilities.NET_CAPABILITY_NOT_VPN)
                .build()

        cm.registerNetworkCallback(
            request,
            object : ConnectivityManager.NetworkCallback() {
                override fun onLost(network: Network) {
                    hadValidatedPhysical = hasValidatedPhysicalInternet(cm)
                }

                override fun onCapabilitiesChanged(
                    network: Network,
                    caps: NetworkCapabilities,
                ) {
                    if (caps.hasTransport(NetworkCapabilities.TRANSPORT_VPN)) return
                    val validated =
                        caps.hasCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET) &&
                            caps.hasCapability(NetworkCapabilities.NET_CAPABILITY_VALIDATED)
                    if (!hadValidatedPhysical && validated) {
                        _networkRestored.tryEmit(Unit)
                    }
                    hadValidatedPhysical = hasValidatedPhysicalInternet(cm)
                }

                override fun onAvailable(network: Network) {
                    val caps = cm.getNetworkCapabilities(network) ?: return
                    if (caps.hasTransport(NetworkCapabilities.TRANSPORT_VPN)) return
                    val validated =
                        caps.hasCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET) &&
                            caps.hasCapability(NetworkCapabilities.NET_CAPABILITY_VALIDATED)
                    if (!hadValidatedPhysical && validated) {
                        _networkRestored.tryEmit(Unit)
                    }
                    hadValidatedPhysical = hasValidatedPhysicalInternet(cm)
                }
            },
        )
    }

    /** 是否存在已校验的物理上网能力（忽略 VPN）。没网时不应因探活失败拆隧道。 */
    fun hasValidatedPhysicalInternet(context: Context): Boolean {
        val cm = context.getSystemService(ConnectivityManager::class.java) ?: return false
        return hasValidatedPhysicalInternet(cm)
    }

    fun hasValidatedPhysicalInternet(cm: ConnectivityManager): Boolean =
        cm.allNetworks.any { network ->
            val caps = cm.getNetworkCapabilities(network) ?: return@any false
            !caps.hasTransport(NetworkCapabilities.TRANSPORT_VPN) &&
                caps.hasCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET) &&
                caps.hasCapability(NetworkCapabilities.NET_CAPABILITY_VALIDATED)
        }
}
