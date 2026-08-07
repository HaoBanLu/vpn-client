package com.vpn.member.vpn

import android.app.ActivityManager
import android.content.Context
import android.net.ConnectivityManager
import android.net.NetworkCapabilities
import android.net.VpnService

/**
 * 冷启动时对齐 UI 与系统 VPN / 前台服务状态（P1-3）。
 */
object VpnTunnelStateSync {
    fun isVpnTransportActive(context: Context): Boolean {
        val cm = context.getSystemService(ConnectivityManager::class.java) ?: return false
        return cm.allNetworks.any { network ->
            val caps = cm.getNetworkCapabilities(network) ?: return@any false
            caps.hasTransport(NetworkCapabilities.TRANSPORT_VPN)
        }
    }

    fun isServiceRunning(context: Context): Boolean {
        val manager = context.getSystemService(ActivityManager::class.java) ?: return false
        @Suppress("DEPRECATION")
        return manager.getRunningServices(Int.MAX_VALUE).any {
            it.service.className == VpnTunnelService::class.java.name
        }
    }

    fun hasVpnPermission(context: Context): Boolean = VpnService.prepare(context) == null

    /**
     * @return 建议同步到 VpnConnectionBus 的状态；null 表示无需变更。
     */
    fun reconcileBusState(context: Context): ConnectionState? {
        val busState = VpnConnectionBus.status.value.state
        val serviceUp = isServiceRunning(context)
        val vpnUp = isVpnTransportActive(context)
        return reconcileBusState(
            busState = busState,
            serviceUp = serviceUp,
            vpnUp = vpnUp,
            tunnelRunning = VpnTunnelService.isTunnelRunning,
        )
    }

    /**
     * 纯逻辑分支，供单测覆盖；生产路径见 [reconcileBusState]。
     */
    internal fun reconcileBusState(
        busState: ConnectionState,
        serviceUp: Boolean,
        vpnUp: Boolean,
        tunnelRunning: Boolean,
    ): ConnectionState? =
        when {
            // Kill Switch 保持着 Service + TUN，但实际不是「已连接」，不做误判
            serviceUp && vpnUp && busState == ConnectionState.DISCONNECTED && tunnelRunning ->
                ConnectionState.CONNECTED
            !serviceUp && !vpnUp && busState == ConnectionState.CONNECTED ->
                ConnectionState.DISCONNECTED
            else -> null
        }
}
