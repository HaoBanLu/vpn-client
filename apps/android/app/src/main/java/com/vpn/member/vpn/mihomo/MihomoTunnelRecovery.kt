package com.vpn.member.vpn.mihomo

import android.content.Context
import android.util.Log
import com.github.kr328.clash.core.Clash
import com.vpn.member.debug.AppDebugLogger

/**
 * 亮屏 / 网络恢复时轻量自愈：恢复内核、同步物理网 DNS、触发代理健康检查。
 * 对齐 CMFA SuspendModule 亮屏分支 + NetworkObserve 的 DNS 刷新。
 */
object MihomoTunnelRecovery {
    private const val TAG = "MihomoTunnelRecovery"

    fun heal(context: Context, reason: String = "unknown") {
        val appContext = context.applicationContext
        runCatching {
            Clash.suspendCore(false)
            MihomoEnvironment.refreshPhysicalDns(appContext)
            Clash.healthCheckAll()
        }.onSuccess {
            Log.i(TAG, "tunnel heal ok reason=$reason")
            AppDebugLogger.info(
                category = "mihomo",
                message = "隧道自愈完成",
                context = mapOf("reason" to reason),
            )
        }.onFailure { e ->
            Log.w(TAG, "tunnel heal failed reason=$reason", e)
            AppDebugLogger.warn(
                category = "mihomo",
                message = "隧道自愈失败",
                context = mapOf("reason" to reason, "error" to (e.message ?: "unknown")),
            )
        }
    }

    fun onLowMemory() {
        runCatching { Clash.forceGc() }
    }
}
