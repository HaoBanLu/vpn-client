package com.vpn.member.vpn.mihomo

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.os.Build
import android.os.PowerManager
import android.util.Log
import com.github.kr328.clash.core.Clash
import com.vpn.member.vpn.VpnConnectionBus

/**
 * 息屏时按需挂起 Mihomo；**VPN 已连接时不挂起**，避免用户感知为莫名掉线。
 * 亮屏仍触发自愈。
 */
class MihomoSuspendObserver(private val context: Context) {
    private var receiver: BroadcastReceiver? = null

    fun start() {
        if (receiver != null) return
        val filter =
            IntentFilter().apply {
                addAction(Intent.ACTION_SCREEN_ON)
                addAction(Intent.ACTION_SCREEN_OFF)
            }
        val observer =
            object : BroadcastReceiver() {
                override fun onReceive(
                    ctx: Context,
                    intent: Intent,
                ) {
                    when (intent.action) {
                        Intent.ACTION_SCREEN_OFF -> applyScreenOffPolicy()
                        Intent.ACTION_SCREEN_ON -> {
                            MihomoTunnelRecovery.heal(ctx, reason = "screen_on")
                            Log.d(TAG, "Clash resumed (screen on)")
                        }
                    }
                }
            }
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            context.registerReceiver(observer, filter, Context.RECEIVER_NOT_EXPORTED)
        } else {
            context.registerReceiver(observer, filter)
        }
        receiver = observer

        val interactive = context.getSystemService(PowerManager::class.java)?.isInteractive ?: true
        if (!interactive) {
            applyScreenOffPolicy()
        }
    }

    fun stop() {
        receiver?.let { runCatching { context.unregisterReceiver(it) } }
        receiver = null
        Clash.suspendCore(false)
    }

    private fun applyScreenOffPolicy() {
        val state = VpnConnectionBus.status.value.state
        if (MihomoSuspendPolicy.shouldSuspendCore(screenOff = true, state = state)) {
            Clash.suspendCore(true)
            Log.d(TAG, "Clash suspended (screen off, state=$state)")
        } else {
            // 保持内核运行，避免已连接时息屏即「假掉线」
            Clash.suspendCore(false)
            Log.d(TAG, "Clash keep running on screen off (state=$state)")
        }
    }

    companion object {
        private const val TAG = "MihomoSuspendObserver"
    }
}
