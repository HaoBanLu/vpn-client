package com.vpn.member.vpn

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.net.VpnService
import android.util.Log

/** 开机后按用户设置尝试恢复 VPN（P3-3）。 */
class VpnBootReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent?) {
        if (intent?.action != Intent.ACTION_BOOT_COMPLETED) return
        val appContext = context.applicationContext
        val store = VpnSessionStore(appContext)
        if (!store.isBootAutoConnectEnabled()) return
        if (!store.isAutoReconnectEnabled()) return
        if (!VpnAuthGate.isLoggedIn(appContext)) return
        val snapshot = store.readSnapshot() ?: return
        if (!snapshot.wasUserConnected) return
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
