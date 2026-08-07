package com.vpn.member.vpn.mihomo

import android.content.Context
import android.net.ConnectivityManager
import android.net.LinkProperties
import android.net.Network
import android.net.NetworkCapabilities
import android.net.NetworkRequest
import android.os.Build
import android.util.Log
import com.github.kr328.clash.core.Clash
import com.vpn.member.debug.AppDebugLogger
import com.vpn.member.vpn.VpnNetworkEvents
import java.util.concurrent.ConcurrentHashMap

/**
 * 物理网监听与 DNS 同步（对齐 CMFA NetworkObserveModule）。
 * WiFi 优先于蜂窝；onLosing 宽限期内仍视为可用。
 */
class MihomoNetworkObserver(context: Context) {
    private val appContext = context.applicationContext
    private val connectivityManager = appContext.getSystemService(ConnectivityManager::class.java)
    private var callback: ConnectivityManager.NetworkCallback? = null

    private data class NetworkInfo(
        @Volatile var losingDeadlineMs: Long = 0L,
        @Volatile var dnsList: List<String> = emptyList(),
    ) {
        fun isAvailable(): Boolean = losingDeadlineMs < System.currentTimeMillis()
    }

    private val networkInfos = ConcurrentHashMap<Network, NetworkInfo>()

    @Volatile
    private var currentDnsList = emptyList<String>()

    fun start() {
        val cm = connectivityManager ?: return
        seedKnownNetworks(cm)
        publishBestDns(cm)

        val request =
            NetworkRequest.Builder()
                .addCapability(NetworkCapabilities.NET_CAPABILITY_NOT_VPN)
                .addCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET)
                .apply {
                    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
                        addCapability(NetworkCapabilities.NET_CAPABILITY_FOREGROUND)
                    }
                    addCapability(NetworkCapabilities.NET_CAPABILITY_NOT_RESTRICTED)
                }.build()

        val networkCallback =
            object : ConnectivityManager.NetworkCallback() {
                override fun onAvailable(network: Network) {
                    Log.i(TAG, "onAvailable network=$network")
                    networkInfos[network] = NetworkInfo()
                    publishBestDns(cm)
                    VpnNetworkEvents.notifyTransportChanged("network_available")
                }

                override fun onLosing(
                    network: Network,
                    maxMsToLive: Int,
                ) {
                    Log.i(TAG, "onLosing network=$network maxMs=$maxMsToLive")
                    networkInfos[network]?.losingDeadlineMs = System.currentTimeMillis() + maxMsToLive
                    publishBestDns(cm)
                }

                override fun onLost(network: Network) {
                    Log.i(TAG, "onLost network=$network")
                    networkInfos.remove(network)
                    publishBestDns(cm)
                    // WiFi 掉线但蜂窝仍在时 VALIDATED 可能从未变 false，必须主动通知切网
                    VpnNetworkEvents.notifyTransportChanged("network_lost")
                }

                override fun onLinkPropertiesChanged(
                    network: Network,
                    linkProperties: LinkProperties,
                ) {
                    val dns =
                        linkProperties.dnsServers
                            .mapNotNull { it.hostAddress?.trim() }
                            .filter { MihomoDnsFilter.isUsableUpstream(it) }
                            .map { MihomoDnsFilter.formatDnsEndpoint(it) }
                    networkInfos[network]?.dnsList = dns
                    publishBestDns(cm)
                }
            }
        callback = networkCallback
        cm.registerNetworkCallback(request, networkCallback)
    }

    fun stop() {
        val cm = connectivityManager ?: return
        callback?.let { runCatching { cm.unregisterNetworkCallback(it) } }
        callback = null
        networkInfos.clear()
        currentDnsList = emptyList()
        runCatching { Clash.notifyDnsChanged(emptyList()) }
    }

    fun refreshDns() {
        val cm = connectivityManager ?: return
        seedKnownNetworks(cm)
        publishBestDns(cm)
    }

    companion object {
        private const val TAG = "MihomoNetworkObserver"

        fun publishPhysicalDns(cm: ConnectivityManager) {
            val network = MihomoDnsFilter.findBestPhysicalNetwork(cm) ?: return
            val dnsList =
                MihomoDnsFilter.extractDnsServers(cm, network)
                    .map { MihomoDnsFilter.formatDnsEndpoint(it) }
            if (dnsList.isEmpty()) return
            Clash.notifyDnsChanged(dnsList)
            Log.i(TAG, "publishPhysicalDns $dnsList")
        }

        private fun transportPriority(caps: NetworkCapabilities?): Int {
            if (caps == null) return 100
            return when {
                caps.hasTransport(NetworkCapabilities.TRANSPORT_VPN) -> 90
                caps.hasTransport(NetworkCapabilities.TRANSPORT_WIFI) -> 0
                caps.hasTransport(NetworkCapabilities.TRANSPORT_ETHERNET) -> 1
                Build.VERSION.SDK_INT >= Build.VERSION_CODES.S &&
                    caps.hasTransport(NetworkCapabilities.TRANSPORT_USB) -> 2
                caps.hasTransport(NetworkCapabilities.TRANSPORT_BLUETOOTH) -> 3
                caps.hasTransport(NetworkCapabilities.TRANSPORT_CELLULAR) -> 4
                else -> 20
            }
        }
    }

    private fun seedKnownNetworks(cm: ConnectivityManager) {
        cm.allNetworks.forEach { network ->
            val caps = cm.getNetworkCapabilities(network) ?: return@forEach
            if (caps.hasTransport(NetworkCapabilities.TRANSPORT_VPN)) return@forEach
            if (!caps.hasCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET)) return@forEach
            val info = networkInfos.getOrPut(network) { NetworkInfo() }
            info.dnsList = MihomoDnsFilter.extractDnsServers(cm, network).map { MihomoDnsFilter.formatDnsEndpoint(it) }
        }
    }

    private fun publishBestDns(cm: ConnectivityManager) {
        val dnsList =
            networkInfos.asSequence()
                .filter { (_, info) -> info.isAvailable() }
                .minByOrNull { (network, info) ->
                    val caps = cm.getNetworkCapabilities(network)
                    transportPriority(caps) + if (info.dnsList.isEmpty()) 5 else 0
                }?.value?.dnsList
                ?: MihomoDnsFilter.extractDnsServers(cm, MihomoDnsFilter.findBestPhysicalNetwork(cm))
                    .map { MihomoDnsFilter.formatDnsEndpoint(it) }

        if (dnsList.isEmpty()) return
        if (dnsList == currentDnsList) return

        currentDnsList = dnsList
        Clash.notifyDnsChanged(dnsList)
        Log.i(TAG, "notifyDnsChanged $dnsList")
        VpnNetworkEvents.notifyTransportChanged("dns_changed")
        AppDebugLogger.info(
            category = "mihomo",
            message = "已同步物理网 DNS",
            context = mapOf("servers" to dnsList.joinToString(",")),
        )
    }
}
