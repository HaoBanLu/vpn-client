package com.vpn.kuayun.vpn

import android.content.Context
import android.content.Intent
import android.net.VpnService
import androidx.activity.result.ActivityResultLauncher
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch

class VpnController(context: Context) {
    private val appContext = context.applicationContext
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)

    val status: StateFlow<VpnConnectionStatus> = VpnConnectionBus.status

    fun prepare(launcher: ActivityResultLauncher<Intent>) {
        VpnService.prepare(appContext)?.let { launcher.launch(it) }
    }

    fun connect(configJson: String, nodeName: String? = null) {
        scope.launch {
            val intent =
                Intent(appContext, VpnTunnelService::class.java).apply {
                    action = VpnTunnelService.ACTION_CONNECT
                    putExtra(VpnTunnelService.EXTRA_CONFIG, configJson)
                    putExtra(VpnTunnelService.EXTRA_NODE_NAME, nodeName)
                }
            appContext.startForegroundService(intent)
        }
    }

    fun reconnect(configJson: String, nodeName: String? = null) {
        scope.launch {
            val intent =
                Intent(appContext, VpnTunnelService::class.java).apply {
                    action = VpnTunnelService.ACTION_RECONNECT
                    putExtra(VpnTunnelService.EXTRA_CONFIG, configJson)
                    putExtra(VpnTunnelService.EXTRA_NODE_NAME, nodeName)
                }
            appContext.startForegroundService(intent)
        }
    }

    fun disconnect() {
        scope.launch {
            val intent =
                Intent(appContext, VpnTunnelService::class.java).apply {
                    action = VpnTunnelService.ACTION_DISCONNECT
                }
            appContext.startService(intent)
        }
    }
}
