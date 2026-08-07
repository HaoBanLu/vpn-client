package com.vpn.member.vpn

import android.content.Context
import android.net.ConnectivityManager
import android.net.NetworkCapabilities
import android.net.TrafficStats
import android.os.Build
import android.util.Log
import com.github.kr328.clash.core.Clash
import kotlinx.coroutines.async
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.withTimeoutOrNull
import java.net.HttpURLConnection
import java.net.InetSocketAddress
import java.net.NetworkInterface
import java.net.Proxy
import java.net.URL

/**
 * 连接后校验 TUN 数据面（商业轻门禁 B）。
 * 全流量：「已保护」必须系统 VPN Network 真实出网成功；mixed-port / TUN 字节不得单独放行。
 * 见 docs/product/连接可信-系统VPN硬门禁产品需求.md
 */
object TunDataPlaneVerifier {
    private const val TAG = "TunDataPlaneVerifier"
    private const val SETTLE_MS = 300L
    private const val VPN_NETWORK_SETTLE_MS = 600L
    private const val VPN_NETWORK_RETRY = 3
    private const val VPN_NETWORK_RETRY_DELAY_MS = 700L
    private const val LOGCAT_WAIT_MS = 8_000L
    /** 流量增量低于此阈值视为计数噪声（Mihomo 打包值未解码时的误判）。 */
    private const val MIN_TRAFFIC_DELTA_BYTES = 512L
    private const val MIN_TUN_DOWNLOAD_BYTES = 64L

    suspend fun verifyOrThrow(
        context: Context,
        stack: String,
        splitDomesticDirect: Boolean = false,
        domesticReturn: Boolean = false,
    ) {
        delay(SETTLE_MS)
        val trafficBefore = readTotalBytes()
        val tunRxBefore = readTunInterfaceRxBytes()
        VpnDiag.logTraffic("before_dataplane")

        coroutineScope {
            // 尽早订阅 logcat，避免 mixed-port 探测期间产生的 TUN 日志被漏掉。
            val tunTcpDeferred = async { awaitTunTcpPassive(LOGCAT_WAIT_MS) }

            val domesticOk = probeDomesticViaMixedPort()
            val overseasOk = MihomoLocalProbe.isOverseasReachable(timeoutMs = 4_000)
            // 给系统登记 VPN Network 一点时间，再做同源真实出网（带重试）。
            delay(VPN_NETWORK_SETTLE_MS)
            val vpnNetworkOk =
                probeViaVpnNetworkWithRetry(
                    context.applicationContext,
                    domesticReturn = domesticReturn,
                )
            val tunTcpSeen = tunTcpDeferred.await()

            val trafficAfter = readTotalBytes()
            val tunRxAfter = readTunInterfaceRxBytes()
            VpnDiag.logTraffic("after_dataplane")
            val trafficDelta = (trafficAfter - trafficBefore).coerceAtLeast(0L)
            val trafficGrew = trafficDelta >= MIN_TRAFFIC_DELTA_BYTES
            val tunRxDelta = (tunRxAfter - tunRxBefore).coerceAtLeast(0L)
            val tunDownloadGrew = tunRxDelta >= MIN_TUN_DOWNLOAD_BYTES

            val passed =
                evaluateDataplanePass(
                    splitDomesticDirect = splitDomesticDirect,
                    domesticReturn = domesticReturn,
                    domesticOk = domesticOk,
                    overseasOk = overseasOk,
                    tunTcpSeen = tunTcpSeen,
                    trafficGrew = trafficGrew,
                    tunDownloadGrew = tunDownloadGrew,
                    vpnNetworkOk = vpnNetworkOk,
                )

            VpnDiag.step(
                "dataplane_check",
                extras =
                    mapOf(
                        "stack" to stack,
                        "split" to splitDomesticDirect.toString(),
                        "domestic_mixed" to domesticOk.toString(),
                        "overseas_mixed" to overseasOk.toString(),
                        "domestic_return" to domesticReturn.toString(),
                        "vpn_network_ok" to vpnNetworkOk.toString(),
                        "tun_tcp_log" to tunTcpSeen.toString(),
                        "traffic_grew" to trafficGrew.toString(),
                        "traffic_delta" to trafficDelta.toString(),
                        "tun_download_grew" to tunDownloadGrew.toString(),
                        "tun_rx_delta" to tunRxDelta.toString(),
                    ),
            )

            if (passed) {
                return@coroutineScope
            }

            if (splitDomesticDirect) {
                VpnDiag.warn("dataplane", "split 模式数据面校验放宽通过")
                return@coroutineScope
            }

            Log.w(
                TAG,
                "dataplane failed stack=$stack domestic=$domesticOk overseas=$overseasOk " +
                    "vpnNetwork=$vpnNetworkOk tunTcp=$tunTcpSeen tunDownloadGrew=$tunDownloadGrew trafficDelta=$trafficDelta",
            )
            error("android: tunnel dataplane inactive (stack=$stack)")
        }
    }

    /**
     * 全流量（出海/回国）必须 [vpnNetworkOk]；mixed / TUN 计数仅诊断，不得单独放行。
     * 分流仍放宽：国内或海外或 TUN 活跃之一即可。
     * [domesticReturn] / [trafficGrew] 保留入参供调用方与诊断对齐；成功判定不再依赖二者。
     */
    @Suppress("UNUSED_PARAMETER")
    internal fun evaluateDataplanePass(
        splitDomesticDirect: Boolean,
        domesticReturn: Boolean,
        domesticOk: Boolean,
        overseasOk: Boolean,
        tunTcpSeen: Boolean,
        trafficGrew: Boolean,
        tunDownloadGrew: Boolean = false,
        vpnNetworkOk: Boolean = false,
    ): Boolean {
        val tunActive = tunTcpSeen || tunDownloadGrew || vpnNetworkOk
        return when {
            splitDomesticDirect -> domesticOk || overseasOk || tunActive
            // 出海与回国全流量：同一硬门禁（系统 VPN 真通）；探测 URL 在 probeViaVpnNetwork 按场景区分。
            else -> vpnNetworkOk
        }
    }

    private fun readTotalBytes(): Long =
        runCatching {
            MihomoTrafficCodec.totalBytes(Clash.queryTrafficTotal())
        }.getOrDefault(0L)

    /** 读 TUN 网卡累计下行（仅数据面流量，不含 App 自身 mixed-port 探测）。 */
    private fun readTunInterfaceRxBytes(): Long {
        val iface = findVpnInterfaceName() ?: return 0L
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.S) return 0L
        return TrafficStats.getRxBytes(iface).coerceAtLeast(0L)
    }

    private fun findVpnInterfaceName(): String? =
        runCatching {
            NetworkInterface.getNetworkInterfaces()
                .toList()
                .firstOrNull { networkInterface ->
                    networkInterface.isUp &&
                        !networkInterface.isLoopback &&
                        networkInterface.isPointToPoint &&
                        (networkInterface.name.startsWith("tun") || networkInterface.name.startsWith("ppp"))
                }?.name
        }.getOrNull()

    private fun probeDomesticViaMixedPort(timeoutMs: Int = 4_000): Boolean {
        val proxy = Proxy(Proxy.Type.HTTP, InetSocketAddress("127.0.0.1", MihomoLocalProbe.MIXED_PORT))
        val urls = listOf("https://www.qq.com", "https://cp.cloudflare.com/generate_204")
        for (url in urls) {
            val ok =
                runCatching {
                    val conn = URL(url).openConnection(proxy) as HttpURLConnection
                    try {
                        conn.requestMethod = "HEAD"
                        conn.connectTimeout = timeoutMs
                        conn.readTimeout = timeoutMs
                        conn.instanceFollowRedirects = true
                        conn.connect()
                        conn.responseCode in 200..399
                    } finally {
                        conn.disconnect()
                    }
                }.getOrDefault(false)
            if (ok) {
                VpnDiag.step("domestic_mixed_ok", url)
                return true
            }
        }
        return false
    }

    /**
     * 切网/断网恢复后专用：只测系统 VPN Network 是否真通（与硬门禁同源）。
     * 禁止用 mixed-port 结果代替本方法。
     *
     * @param requireHttp 为 true 时**不接受**仅 `NET_CAPABILITY_VALIDATED`
     *（切网后 VALIDATED 常为陈旧真值，曾导致 luban7733 假恢复）。
     */
    suspend fun probeVpnNetworkOk(
        context: Context,
        domesticReturn: Boolean,
        attempts: Int = PostHealRecoveryPolicy.POST_HEAL_PROBE_ATTEMPTS,
        timeoutMs: Int = PostHealRecoveryPolicy.POST_HEAL_PROBE_TIMEOUT_MS,
        retryDelayMs: Long = PostHealRecoveryPolicy.POST_HEAL_RETRY_DELAY_MS,
        settleMs: Long = VPN_NETWORK_SETTLE_MS,
        requireHttp: Boolean = true,
    ): Boolean {
        delay(settleMs.coerceAtLeast(0L))
        val maxAttempts = attempts.coerceAtLeast(1)
        repeat(maxAttempts) { attempt ->
            if (
                probeViaVpnNetworkOnce(
                    context.applicationContext,
                    domesticReturn,
                    timeoutMs,
                    acceptValidated = !requireHttp,
                )
            ) {
                return true
            }
            if (attempt < maxAttempts - 1) {
                delay(retryDelayMs)
            }
        }
        VpnDiag.warn(
            "vpn_network_ok",
            "post_heal_all_attempts_failed",
            mapOf("attempts" to maxAttempts.toString()),
        )
        return false
    }

    /** 系统 VPN Network 真实出网：重试 + GET（部分机型/CDN 对 HEAD 不友好）。 */
    private suspend fun probeViaVpnNetworkWithRetry(
        context: Context,
        domesticReturn: Boolean,
    ): Boolean {
        repeat(VPN_NETWORK_RETRY) { attempt ->
            if (
                probeViaVpnNetworkOnce(
                    context,
                    domesticReturn,
                    timeoutMs = 6_000,
                    acceptValidated = true,
                )
            ) {
                return true
            }
            if (attempt < VPN_NETWORK_RETRY - 1) {
                delay(VPN_NETWORK_RETRY_DELAY_MS)
            }
        }
        VpnDiag.warn("vpn_network_ok", "all_attempts_failed", mapOf("attempts" to VPN_NETWORK_RETRY.toString()))
        return false
    }

    /** 通过系统 VPN Network 发起探测（与浏览器同路；跨云自身不再强制 disallow）。 */
    private fun probeViaVpnNetworkOnce(
        context: Context,
        domesticReturn: Boolean,
        timeoutMs: Int = 6_000,
        acceptValidated: Boolean = true,
    ): Boolean {
        val connectivityManager =
            context.getSystemService(Context.CONNECTIVITY_SERVICE) as? ConnectivityManager
                ?: return false
        val vpnNetwork =
            findVpnNetwork(connectivityManager) ?: run {
                VpnDiag.warn("vpn_network_ok", "no_vpn_network")
                return false
            }

        val caps = connectivityManager.getNetworkCapabilities(vpnNetwork)
        val validated = caps?.hasCapability(NetworkCapabilities.NET_CAPABILITY_VALIDATED) == true
        VpnDiag.step(
            "vpn_network_caps",
            extras =
                mapOf(
                    "validated" to validated.toString(),
                    "accept_validated" to acceptValidated.toString(),
                    "internet" to
                        (caps?.hasCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET) == true).toString(),
                ),
        )
        // 首连硬门禁可认 VALIDATED；切网后探测必须走 HTTP，避免陈旧 VALIDATED 假恢复。
        if (acceptValidated && validated) {
            VpnDiag.step("vpn_network_ok", "validated")
            return true
        }

        // 回国须探国内站，禁止仅用 gstatic（会误杀缅甸→国内落地）。
        val urls =
            if (domesticReturn) {
                listOf(
                    "https://www.qq.com",
                    "http://connectivitycheck.platform.hicloud.com/generate_204",
                    "https://connectivitycheck.platform.hicloud.com/generate_204",
                )
            } else {
                listOf(
                    "http://www.gstatic.com/generate_204",
                    "https://www.gstatic.com/generate_204",
                    "http://cp.cloudflare.com/generate_204",
                    "https://cp.cloudflare.com/generate_204",
                )
            }
        for (url in urls) {
            val result =
                runCatching {
                    val conn = vpnNetwork.openConnection(URL(url)) as HttpURLConnection
                    try {
                        conn.instanceFollowRedirects = true
                        conn.connectTimeout = timeoutMs
                        conn.readTimeout = timeoutMs
                        conn.requestMethod = "GET"
                        conn.setRequestProperty("Connection", "close")
                        conn.connect()
                        val code = conn.responseCode
                        // 读一小段响应，避免部分实现未真正完成请求
                        runCatching { conn.inputStream.use { it.read(ByteArray(64)) } }
                        code in 200..399 || code == 204
                    } finally {
                        conn.disconnect()
                    }
                }
            if (result.getOrDefault(false)) {
                VpnDiag.step("vpn_network_ok", url)
                return true
            }
            val err = result.exceptionOrNull()?.message?.take(120)
            if (!err.isNullOrBlank()) {
                Log.w(TAG, "vpn network probe fail url=$url err=$err")
                VpnDiag.warn("vpn_network_probe", err, mapOf("url" to url))
            }
        }
        return false
    }

    private fun findVpnNetwork(connectivityManager: ConnectivityManager): android.net.Network? {
        fun isVpn(network: android.net.Network): Boolean {
            val caps = connectivityManager.getNetworkCapabilities(network) ?: return false
            return caps.hasTransport(NetworkCapabilities.TRANSPORT_VPN)
        }
        connectivityManager.activeNetwork?.takeIf(::isVpn)?.let { return it }
        return connectivityManager.allNetworks.firstOrNull(::isVpn)
    }

    private suspend fun awaitTunTcpPassive(timeoutMs: Long = LOGCAT_WAIT_MS): Boolean =
        coroutineScope {
            val channel = runCatching { Clash.subscribeLogcat() }.getOrNull() ?: return@coroutineScope false
            withTimeoutOrNull(timeoutMs) {
                for (msg in channel) {
                    val line = msg.message
                    if (line.contains("[TCP]") && line.contains("172.19.0.1")) {
                        VpnDiag.step("tun_tcp_seen_passive", line.take(220))
                        return@withTimeoutOrNull true
                    }
                }
                false
            } ?: false
        }
}
