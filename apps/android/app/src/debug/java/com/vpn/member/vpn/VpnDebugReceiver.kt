package com.vpn.member.vpn

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.net.VpnService
import android.util.Log

/** Debug 包专用：供 adb / 模拟器脚本在应用进程内启动 VPN（shell 无法直接 start-foreground-service）。 */
class VpnDebugReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent?) {
        if (intent?.action != ACTION_DEBUG_CONNECT) return
        val appContext = context.applicationContext
        val missing = VpnService.prepare(appContext)
        if (missing != null) {
            Log.e(TAG, "VPN permission not granted; open App and approve once")
            missing.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            appContext.startActivity(missing)
            return
        }
        val routeTarget = intent.getStringExtra(EXTRA_ROUTE_TARGET)?.trim().orEmpty().ifBlank { "武汉" }
        val routeMode = intent.getStringExtra(EXTRA_ROUTE_MODE)?.trim().orEmpty().ifBlank { "split" }
        val service =
            Intent(appContext, VpnTunnelService::class.java).apply {
                action = VpnTunnelService.ACTION_CONNECT
                putExtra(VpnTunnelService.EXTRA_USE_STORED_CONFIG, true)
                putExtra(VpnTunnelService.EXTRA_ROUTE_TARGET, routeTarget)
                putExtra(VpnTunnelService.EXTRA_ROUTE_MODE, routeMode)
                putExtra(VpnTunnelService.EXTRA_NODE_NAME, routeTarget)
            }
        appContext.startForegroundService(service)
        Log.i(TAG, "debug connect started routeTarget=$routeTarget routeMode=$routeMode")
    }

    companion object {
        private const val TAG = "VpnDebugReceiver"
        const val ACTION_DEBUG_CONNECT = "com.vpn.member.DEBUG_CONNECT"
        const val EXTRA_ROUTE_TARGET = "route_target"
        const val EXTRA_ROUTE_MODE = "route_mode"
    }
}
