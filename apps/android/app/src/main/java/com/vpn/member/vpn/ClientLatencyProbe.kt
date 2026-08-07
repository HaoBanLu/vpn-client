package com.vpn.member.vpn

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.net.InetSocketAddress
import java.net.Socket

/** 客户端本地 TCP 探测订阅入口延迟（节点页「入口延迟」；未经 VPN，与连接页「隧道延迟」不同）。 */
object ClientLatencyProbe {
    suspend fun probeTcp(host: String, port: Int, timeoutMs: Int = 5000): Int? =
        withContext(Dispatchers.IO) {
            runCatching {
                val start = System.currentTimeMillis()
                Socket().use { socket ->
                    socket.connect(InetSocketAddress(host, port), timeoutMs)
                }
                (System.currentTimeMillis() - start).toInt()
            }.getOrNull()
        }

    fun parseEndpoint(endpoint: String?): Pair<String, Int>? {
        val raw = endpoint?.trim().orEmpty()
        if (raw.isEmpty()) return null
        val idx = raw.lastIndexOf(':')
        if (idx <= 0 || idx >= raw.lastIndex) return null
        val host = raw.substring(0, idx).trim()
        val port = raw.substring(idx + 1).trim().toIntOrNull() ?: return null
        if (host.isEmpty() || port <= 0) return null
        return host to port
    }
}
