package com.vpn.member.vpn

import java.net.HttpURLConnection
import java.net.InetSocketAddress
import java.net.Proxy
import java.net.URL

/**
 * 经 Mihomo mixed-port（127.0.0.1:7890）做健康探测。
 * mixed-port 只证明内核/节点通；「其它 App 能否上网」须另走 [TunDataPlaneVerifier] 的系统 VPN 探测。
 */
object MihomoLocalProbe {
    const val MIXED_PORT = 7890

    private val overseasUrls =
        listOf(
            "https://www.gstatic.com/generate_204",
            "https://cp.cloudflare.com/generate_204",
        )

    /** 回国加速：探测国内站可达性（抖音、头条等经国内出口）。 */
    private val domesticUrls =
        listOf(
            "https://www.baidu.com",
            "https://www.qq.com",
            "https://www.douyin.com",
        )

    fun isDomesticReachable(timeoutMs: Int = 8_000): Boolean = measureDomesticLatency(timeoutMs) != null

    fun measureDomesticLatency(timeoutMs: Int = 8_000): Int? {
        val proxy = Proxy(Proxy.Type.HTTP, InetSocketAddress("127.0.0.1", MIXED_PORT))
        for (url in domesticUrls) {
            val latency = runCatching { httpHeadLatency(proxy, url, timeoutMs) }.getOrNull()
            if (latency != null) return latency
        }
        return null
    }

    fun measureOverseasLatency(timeoutMs: Int = 8_000): Int? {
        val proxy = Proxy(Proxy.Type.HTTP, InetSocketAddress("127.0.0.1", MIXED_PORT))
        for (url in overseasUrls) {
            val latency = runCatching { httpHeadLatency(proxy, url, timeoutMs) }.getOrNull()
            if (latency != null) return latency
        }
        return null
    }

    fun isOverseasReachable(timeoutMs: Int = 8_000): Boolean = measureOverseasLatency(timeoutMs) != null

    private fun httpHeadLatency(proxy: Proxy, urlString: String, timeoutMs: Int): Int? {
        // 部分节点/中间盒对 HEAD 不友好；generate_204 类 URL 优先 GET。
        val methods = if (urlString.contains("generate_204")) listOf("GET", "HEAD") else listOf("HEAD", "GET")
        for (method in methods) {
            val startedAt = System.currentTimeMillis()
            val conn = URL(urlString).openConnection(proxy) as HttpURLConnection
            val latency =
                try {
                    conn.requestMethod = method
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
            if (latency != null) return latency
        }
        return null
    }
}
