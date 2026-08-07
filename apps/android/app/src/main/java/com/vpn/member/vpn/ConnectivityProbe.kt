package com.vpn.member.vpn

import android.net.Network
import android.net.NetworkCapabilities
import com.vpn.member.vpn.NetworkServices
import com.vpn.member.vpn.mihomo.MihomoDnsFilter
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.withContext
import java.net.HttpURLConnection
import java.net.URL

/**
 * 连接后网络探测。
 * - **健康检查**：基础站点 + 经 VPN 的海外可达性（与延迟展示分离）。
 * - **隧道延迟**：经 VPN [Network] 对 urltest 同款 URL 发单次 HTTP HEAD，与 Clash/Mihomo 一致。
 * - split 分流：国内站点走系统直连做基础检查；隧道延迟仍经 VPN。
 */
object ConnectivityProbe {
    private const val DEFAULT_TIMEOUT_MS = 8_000
    private const val VPN_NETWORK_WAIT_MS = 5_000L
    private const val VPN_NETWORK_POLL_MS = 200L
    /** 隧道建立后稍等再测健康，不计入展示的隧道延迟。 */
    private const val TUN_SETTLE_MS = 500L
    const val DEFAULT_PROBE_ATTEMPTS = 3
    private const val DEFAULT_PROBE_RETRY_DELAY_MS = 2_000L

    /** 与后端 subscription urltest / clash_health_url 默认一致。 */
    private val tunnelProbeUrls =
        listOf(
            "https://www.gstatic.com/generate_204",
            "https://cp.cloudflare.com/generate_204",
        )

    private val basicUrls =
        listOf(
            "https://www.baidu.com",
            "https://www.qq.com",
        )

    suspend fun probe(
        timeoutMs: Int = DEFAULT_TIMEOUT_MS,
        splitDomesticDirect: Boolean = false,
        domesticReturn: Boolean = false,
    ): ProbeResult =
        withContext(Dispatchers.IO) {
            // 健康检查走 mixed-port（快）；系统路径是否真通由 TunDataPlaneVerifier 硬门禁负责。
            val vpnUp = findVpnNetwork() != null
            if (!vpnUp) {
                return@withContext ProbeResult(
                    basicOk = false,
                    overseasOk = false,
                    slow = false,
                    failureCause = ProbeFailureCause.VPN_NOT_UP,
                )
            }

            delay(TUN_SETTLE_MS)

            val basicOk =
                when {
                    splitDomesticDirect -> {
                        val physical = findPhysicalNetwork()
                        when {
                            physical != null -> probeBasicOnNetwork(physical, timeoutMs)
                            else -> probeBasicOnNetworkDirect(timeoutMs)
                        }
                    }
                    domesticReturn -> MihomoLocalProbe.isDomesticReachable(timeoutMs)
                    else -> MihomoLocalProbe.isOverseasReachable(timeoutMs)
                }
            if (!basicOk) {
                return@withContext ProbeResult(
                    basicOk = false,
                    overseasOk = false,
                    slow = false,
                    latencyMs = null,
                    failureCause =
                        if (splitDomesticDirect) {
                            ProbeFailureCause.PHYSICAL_OFFLINE
                        } else {
                            ProbeFailureCause.PROXY_UNREACHABLE
                        },
                )
            }

            val tunnelLatencyMs =
                when {
                    domesticReturn -> MihomoLocalProbe.measureDomesticLatency(timeoutMs)
                    else -> MihomoLocalProbe.measureOverseasLatency(timeoutMs)
                }
            val overseasOk = if (domesticReturn) basicOk else tunnelLatencyMs != null
            val slow = tunnelLatencyMs != null && tunnelLatencyMs >= SLOW_LATENCY_MS
            ProbeResult(
                basicOk = true,
                overseasOk = overseasOk,
                slow = slow,
                latencyMs = tunnelLatencyMs,
            )
        }

    /** 隧道延迟偏高阈值（与 Clash url-test 展示习惯接近，非流水线总耗时）。 */
    const val SLOW_LATENCY_MS = 300

    /** 连接后探测：失败时重试，减少偶发入口抖动导致的误判。 */
    suspend fun probeWithRetry(
        timeoutMs: Int = DEFAULT_TIMEOUT_MS,
        splitDomesticDirect: Boolean = false,
        domesticReturn: Boolean = false,
        maxAttempts: Int = DEFAULT_PROBE_ATTEMPTS,
        retryDelayMs: Long = DEFAULT_PROBE_RETRY_DELAY_MS,
    ): ProbeResult {
        var last = ProbeResult(basicOk = false, overseasOk = false, slow = false)
        repeat(maxAttempts.coerceAtLeast(1)) { attempt ->
            last = probe(timeoutMs = timeoutMs, splitDomesticDirect = splitDomesticDirect, domesticReturn = domesticReturn)
            if (last.basicOk) return last
            if (attempt < maxAttempts - 1) {
                delay(retryDelayMs)
            }
        }
        return last
    }

    /** 在国内物理网上探测基础连通性（split 模式用）。 */
    fun probeBasicOnNetwork(network: Network, timeoutMs: Int = DEFAULT_TIMEOUT_MS): Boolean =
        probeAny(network, basicUrls, timeoutMs)

    fun probeBasicOnNetworkDirect(timeoutMs: Int = DEFAULT_TIMEOUT_MS): Boolean =
        basicUrls.any { url -> runCatching { httpOkDirect(url, timeoutMs) }.getOrDefault(false) }

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

    /** 非 VPN 的物理网络（split 模式国内直连探测用）。 */
    fun findPhysicalNetwork(): Network? {
        val cm = NetworkServices.connectivity
        return MihomoDnsFilter.findPhysicalNetwork(cm)
    }

    /** @deprecated 活跃网络在 VPN 建立后常为 TUN，请用 [findPhysicalNetwork]。 */
    fun findDefaultNetwork(): Network? = NetworkServices.connectivity.activeNetwork

    /** 经 VPN 对测速 URL 发单次 HEAD，返回 RTT（毫秒）。 */
    internal fun measureTunnelLatency(
        network: Network,
        urls: List<String>,
        timeoutMs: Int,
    ): Int? {
        for (url in urls) {
            val latency =
                runCatching { httpHeadLatency(network, url, timeoutMs) }.getOrNull()
            if (latency != null) return latency
        }
        return null
    }

    private fun probeAny(network: Network, urls: List<String>, timeoutMs: Int): Boolean {
        for (url in urls) {
            if (runCatching { httpOk(network, url, timeoutMs) }.getOrDefault(false)) {
                return true
            }
        }
        return false
    }

    private fun httpHeadLatency(network: Network, urlString: String, timeoutMs: Int): Int? {
        val startedAt = System.currentTimeMillis()
        val conn = network.openConnection(URL(urlString)) as HttpURLConnection
        return try {
            conn.requestMethod = "HEAD"
            conn.connectTimeout = timeoutMs
            conn.readTimeout = timeoutMs
            conn.instanceFollowRedirects = true
            conn.connect()
            val code = conn.responseCode
            if (code in 200..399 || code == 204) {
                (System.currentTimeMillis() - startedAt).toInt()
            } else {
                null
            }
        } finally {
            conn.disconnect()
        }
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

    private fun httpOkDirect(urlString: String, timeoutMs: Int): Boolean {
        val conn = URL(urlString).openConnection() as HttpURLConnection
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
