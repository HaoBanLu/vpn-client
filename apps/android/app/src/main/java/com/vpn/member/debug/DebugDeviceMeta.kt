package com.vpn.member.debug

import android.content.Context
import android.net.ConnectivityManager
import android.net.NetworkCapabilities
import android.os.Build
import com.vpn.member.BuildConfig
import com.vpn.member.data.local.AppPreferences
import com.vpn.member.data.device.DeviceInfoProvider
import com.vpn.member.vpn.AppRouteMode
import com.vpn.member.vpn.ConnectionScenario
import com.vpn.member.vpn.OverseasLocaleHint
import com.vpn.member.vpn.TunStackMode
import com.vpn.member.vpn.VpnConnectionBus
import com.vpn.member.vpn.VpnSessionStore
import java.util.Locale
import java.util.TimeZone

/** 诊断日志批次附带的设备 / 运行环境快照（不含 token）。 */
object DebugDeviceMeta {
    fun build(context: Context): Map<String, String> {
        val appContext = context.applicationContext
        val device = DeviceInfoProvider.get(appContext)
        val prefs = AppPreferences(appContext)
        val bus = VpnConnectionBus.status.value
        val snapshot = VpnSessionStore(appContext).readSnapshot()
        val domesticReturnFull =
            snapshot != null &&
                ConnectionScenario.isDomesticReturnProfile(snapshot.profile) &&
                !AppRouteMode.isDomesticDirectEnabled(snapshot.routeMode)
        val tunStackEffective =
            TunStackMode.resolveForSession(
                prefs.getTunStackMode(),
                domesticReturnFull = domesticReturnFull,
            )
        return linkedMapOf(
            "app_version" to device.appVersion,
            "app_version_code" to BuildConfig.VERSION_CODE.toString(),
            "device_brand" to device.deviceBrand,
            "device_model" to device.deviceModel,
            "device_name" to device.deviceName,
            "os_name" to device.osName,
            "os_version" to device.osVersion,
            "os_sdk" to Build.VERSION.SDK_INT.toString(),
            "client_platform" to device.clientPlatform,
            "tun_stack" to tunStackEffective,
            "tun_stack_pref" to (prefs.getTunStackMode()?.trim().orEmpty().ifBlank { "default" }),
            "connection_profile" to (snapshot?.profile?.trim().orEmpty().ifBlank { "-" }),
            "overseas_timezone" to OverseasLocaleHint.isOverseasTimezone().toString(),
            "locale" to Locale.getDefault().toLanguageTag(),
            "timezone" to TimeZone.getDefault().id,
            "network_type" to currentNetworkLabel(appContext),
            "vpn_state" to bus.state.name.lowercase(),
            "vpn_probe_status" to (bus.probeStatus ?: ""),
            "vpn_connected_node" to (bus.connectedNode ?: ""),
        ).filterValues { it.isNotBlank() }
    }

    private fun currentNetworkLabel(context: Context): String {
        val cm = context.getSystemService(ConnectivityManager::class.java) ?: return "unknown"
        val network = cm.activeNetwork ?: return "offline"
        val caps = cm.getNetworkCapabilities(network) ?: return "unknown"
        if (caps.hasTransport(NetworkCapabilities.TRANSPORT_VPN)) {
            return "vpn"
        }
        return when {
            caps.hasTransport(NetworkCapabilities.TRANSPORT_WIFI) -> "wifi"
            caps.hasTransport(NetworkCapabilities.TRANSPORT_CELLULAR) -> "cellular"
            caps.hasTransport(NetworkCapabilities.TRANSPORT_ETHERNET) -> "ethernet"
            else -> "other"
        }
    }
}
