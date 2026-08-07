package com.vpn.member.vpn

import android.util.Log
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.net.HttpURLConnection
import java.net.Inet6Address
import java.net.NetworkInterface
import java.net.URL

/** 轻量隐私泄露自检（IP / IPv6 本地栈 / DNS 可达性）。 */
object PrivacyLeakProbe {
    private const val TAG = "PrivacyLeakProbe"

    data class Result(
        val exitIp: String? = null,
        val exitIpLooksProtected: Boolean = false,
        val ipv6LocalActive: Boolean = false,
        val dnsReachable: Boolean = false,
        val passed: Boolean = false,
    )

    suspend fun run(
        baselineIp: String? = null,
        ipv6ProtectionEnabled: Boolean = true,
    ): Result =
        withContext(Dispatchers.IO) {
            val exitIp = runCatching { ExitIpProbe.probeViaVpn() }.getOrNull()?.ip
            val ipv6Local = if (ipv6ProtectionEnabled) detectLocalIpv6() else false
            val dnsOk = runCatching { probeDns() }.getOrDefault(false)
            evaluate(
                exitIp = exitIp,
                baselineIp = baselineIp,
                ipv6LocalActive = ipv6Local,
                dnsReachable = dnsOk,
                ipv6ProtectionEnabled = ipv6ProtectionEnabled,
            )
        }

    /** 纯逻辑评估，便于单测（网络探测在 [run] 中完成）。 */
    fun evaluate(
        exitIp: String?,
        baselineIp: String?,
        ipv6LocalActive: Boolean,
        dnsReachable: Boolean,
        ipv6ProtectionEnabled: Boolean,
    ): Result {
        val ipv6Risk = ipv6ProtectionEnabled && ipv6LocalActive
        val protected =
            !exitIp.isNullOrBlank() &&
                (baselineIp.isNullOrBlank() || !exitIp.equals(baselineIp, ignoreCase = true))
        val passed = protected && !ipv6Risk && dnsReachable
        return Result(
            exitIp = exitIp,
            exitIpLooksProtected = protected,
            ipv6LocalActive = ipv6LocalActive,
            dnsReachable = dnsReachable,
            passed = passed,
        )
    }

    private fun detectLocalIpv6(): Boolean {
        return runCatching {
            NetworkInterface.getNetworkInterfaces().toList().any { nic ->
                nic.isUp && !nic.isLoopback &&
                    nic.inetAddresses.toList().any { addr ->
                        addr is Inet6Address && !addr.isLoopbackAddress && !addr.isLinkLocalAddress
                    }
            }
        }.getOrDefault(false)
    }

    private fun probeDns(): Boolean {
        return runCatching {
            val conn = URL("https://cloudflare-dns.com/dns-query?name=example.com&type=A").openConnection() as HttpURLConnection
            conn.connectTimeout = 5_000
            conn.readTimeout = 5_000
            conn.requestMethod = "GET"
            conn.setRequestProperty("Accept", "application/dns-json")
            val code = conn.responseCode
            conn.disconnect()
            code in 200..299
        }.getOrElse {
            Log.w(TAG, "dns probe failed", it)
            false
        }
    }
}
