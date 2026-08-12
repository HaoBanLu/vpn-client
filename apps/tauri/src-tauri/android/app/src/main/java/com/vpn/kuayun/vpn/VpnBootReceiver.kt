package com.vpn.kuayun.vpn

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.net.VpnService
import android.util.Log

/** 开机后按用户设置尝试恢复 VPN（需已授权 VPN + 上次仍为连接意图）。 */
class VpnBootReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent?) {
        if (intent?.action != Intent.ACTION_BOOT_COMPLETED) return
        val appContext = context.applicationContext
        if (!StabilityPrefs.isBootAutoConnectEnabled(appContext)) return
        if (!StabilityPrefs.wasUserConnected(appContext)) return
        val config = StabilityPrefs.lastConfig(appContext)
        if (config.isBlank()) {
            Log.i(TAG, "boot auto-connect skipped: no saved config")
            return
        }
        if (VpnService.prepare(appContext) != null) {
            Log.i(TAG, "boot auto-connect skipped: vpn permission not granted")
            return
        }
        val serviceIntent =
            Intent(appContext, VpnTunnelService::class.java).apply {
                action = VpnTunnelService.ACTION_RESTORE
            }
        runCatching {
            appContext.startForegroundService(serviceIntent)
        }.onFailure { err ->
            Log.w(TAG, "boot auto-connect skipped: startForegroundService failed", err)
        }
    }

    companion object {
        private const val TAG = "VpnBootReceiver"
    }
}
