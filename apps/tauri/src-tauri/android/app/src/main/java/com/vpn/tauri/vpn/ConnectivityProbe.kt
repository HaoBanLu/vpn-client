package com.vpn.tauri.vpn

import android.net.Network
import android.net.NetworkCapabilities
import com.vpn.tauri.vpn.NetworkServices
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.withContext
import java.net.HttpURLConnection
import java.net.URL

/** 与原生 Android 一致：split 下国内走系统网，海外经 VPN。 */
object ConnectivityProbe {
    private const val DEFAULT_TIMEOUT_MS = 8_000
    private const val VPN_NETWORK_WAIT_MS = 5_000L
    private const val VPN_NETWORK_POLL_MS = 200L
    private const val TUN_SETTLE_MS = 500L

    const val SLOW_LATENCY_MS = 1_500

    private val basicUrls =
        listOf(
            "https://www.baidu.com",
            "https://www.qq.com",
        )

    private val overseasUrls =
        listOf(
            "https://www.gstatic.com/generate_204",
            "https://cp.cloudflare.com/generate_204",
        )

    suspend fun probe(
        timeoutMs: Int = DEFAULT_TIMEOUT_MS,
        splitDomesticDirect: Boolean = false,
    ): ProbeResult =
        withContext(Dispatchers.IO) {
            val startedAt = System.currentTimeMillis()
            val stageTimeout = (timeoutMs / 2).coerceAtLeast(4_000)

            val vpnNetwork = waitForVpnNetwork()
            val basicNetwork =
                if (splitDomesticDirect) {
                    findDefaultNetwork() ?: vpnNetwork
                } else {
                    vpnNetwork
                }
            if (basicNetwork == null && vpnNetwork == null) {
                return@withContext ProbeResult(basicOk = false, overseasOk = false, slow = false)
            }

            delay(TUN_SETTLE_MS)

            val basicOk =
                basicNetwork?.let { probeAny(it, basicUrls, stageTimeout) } ?: false
            if (!basicOk) {
                return@withContext ProbeResult(
                    basicOk = false,
                    overseasOk = false,
                    slow = false,
                    latencyMs = (System.currentTimeMillis() - startedAt).toInt(),
                )
            }
            delay(300)
            val overseasOk =
                vpnNetwork?.let { probeAny(it, overseasUrls, stageTimeout) } ?: false
            val latencyMs = (System.currentTimeMillis() - startedAt).toInt()
            ProbeResult(
                basicOk = true,
                overseasOk = overseasOk,
                slow = overseasOk && latencyMs >= SLOW_LATENCY_MS,
                latencyMs = latencyMs,
            )
        }

    suspend fun waitForVpnNetwork(
        timeoutMs: Long = VPN_NETWORK_WAIT_MS,
        pollMs: Long = VPN_NETWORK_POLL_MS,
    ): Network? {
        val deadline = System.currentTimeMillis() + timeoutMs
        while (System.currentTimeMillis() < deadline) {
            findVpnNetwork()?.let { return it }
            delay(pollMs)
        }
        return findVpnNetwork()
    }

    fun findVpnNetwork(): Network? {
        val cm = NetworkServices.connectivity
        return cm.allNetworks.firstOrNull { network ->
            cm.getNetworkCapabilities(network)
                ?.hasTransport(NetworkCapabilities.TRANSPORT_VPN) == true
        }
    }

    fun findDefaultNetwork(): Network? = NetworkServices.connectivity.activeNetwork

    private fun probeAny(network: Network, urls: List<String>, timeoutMs: Int): Boolean {
        for (url in urls) {
            if (runCatching { httpOk(network, url, timeoutMs) }.getOrDefault(false)) {
                return true
            }
        }
        return false
    }

    private fun httpOk(network: Network, urlString: String, timeoutMs: Int): Boolean {
        val conn = network.openConnection(URL(urlString)) as HttpURLConnection
        return try {
            conn.requestMethod = "GET"
            conn.connectTimeout = timeoutMs
            conn.readTimeout = timeoutMs
            conn.instanceFollowRedirects = true
            conn.connect()
            val code = conn.responseCode
            code in 200..399 || code == 204
        } finally {
            conn.disconnect()
        }
    }
}
