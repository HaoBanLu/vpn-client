package com.vpn.member.vpn.mihomo

import android.content.Context
import android.net.ConnectivityManager
import com.github.kr328.clash.core.Clash
import java.net.InetSocketAddress
import java.util.TimeZone

/**
 * Mihomo 运行期环境同步（对齐 CMFA：DNS / 时区 / 息屏挂起 / per-app 查询）。
 * 在 startTun 前启动，断开时停止。
 */
object MihomoEnvironment {
    private var networkObserver: MihomoNetworkObserver? = null
    private var suspendObserver: MihomoSuspendObserver? = null

    fun start(context: Context) {
        notifyTimeZone()
        notifyInstalledApps(context)
        val appContext = context.applicationContext
        val observer = MihomoNetworkObserver(appContext)
        observer.start()
        networkObserver = observer
        val suspend = MihomoSuspendObserver(appContext)
        suspend.start()
        suspendObserver = suspend
    }

    fun stop() {
        suspendObserver?.stop()
        suspendObserver = null
        networkObserver?.stop()
        networkObserver = null
    }

    /** TUN 建立后或网络恢复时刷新物理网 DNS。 */
    fun refreshPhysicalDns(context: Context) {
        networkObserver?.refreshDns()
            ?: run {
                val cm = context.applicationContext.getSystemService(ConnectivityManager::class.java) ?: return
                MihomoNetworkObserver.publishPhysicalDns(cm)
            }
    }

    fun querySocketUid(
        context: Context,
        protocol: Int,
        source: InetSocketAddress,
        target: InetSocketAddress,
    ): Int {
        val cm = context.getSystemService(ConnectivityManager::class.java) ?: return -1
        return runCatching {
            cm.getConnectionOwnerUid(protocol, source, target)
        }.getOrDefault(-1)
    }

    private fun notifyTimeZone() {
        val tz = TimeZone.getDefault()
        Clash.notifyTimeZoneChanged(tz.id, tz.rawOffset / 3600000)
    }

    private fun notifyInstalledApps(context: Context) {
        val pm = context.packageManager
        val selfUid = context.applicationInfo.uid
        val pairs =
            pm.getInstalledApplications(0)
                .mapNotNull { app ->
                    val uid = app.uid
                    if (uid == selfUid) return@mapNotNull null
                    val label = app.loadLabel(pm).toString().trim()
                    if (label.isEmpty()) return@mapNotNull null
                    uid to label
                }
        if (pairs.isNotEmpty()) {
            Clash.notifyInstalledAppsChanged(pairs)
        }
    }
}
