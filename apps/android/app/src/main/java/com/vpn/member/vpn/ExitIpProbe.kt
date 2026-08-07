package com.vpn.member.vpn

import com.google.gson.Gson
import com.google.gson.annotations.SerializedName
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.net.HttpURLConnection
import java.net.InetSocketAddress
import java.net.Proxy
import java.net.URL

/** VPN 隧道建立后，经 Mihomo mixed-port 探测公网出口 IP 与归属地。 */
data class ExitIpInfo(
    val ip: String,
    val country: String? = null,
    val region: String? = null,
    val city: String? = null,
)

/** 自研 API 探测所需鉴权与 baseUrl（通常为 BuildConfig.API_BASE_URL）。 */
data class ExitIpProbeContext(
    val apiBaseUrl: String,
    val authToken: String?,
)

object ExitIpProbe {
    private const val TIMEOUT_MS = 8_000
    private const val USER_AGENT = "KuayunVPN-Android/ExitIpProbe"
    private val gson = Gson()

    suspend fun probeViaVpn(context: ExitIpProbeContext? = null): ExitIpInfo? =
        withContext(Dispatchers.IO) {
            if (ConnectivityProbe.findVpnNetwork() == null) return@withContext null
            val proxy =
                Proxy(Proxy.Type.HTTP, InetSocketAddress("127.0.0.1", MihomoLocalProbe.MIXED_PORT))
            providers(context).firstNotNullOfOrNull { provider ->
                runCatching { provider.probe(proxy) }.getOrNull()
            }
        }

    private fun providers(context: ExitIpProbeContext?): List<ExitIpProvider> {
        val list = mutableListOf<ExitIpProvider>()
        val token = context?.authToken?.takeIf { it.isNotBlank() }
        val base = context?.apiBaseUrl?.trim()?.trimEnd('/')
        if (token != null && !base.isNullOrBlank()) {
            list += OwnApiProvider("$base/client/exit-ip", token)
        }
        list += IpApiComProvider()
        list += IpSbProvider()
        list += CloudflareTraceProvider()
        list += IpifyProvider()
        return list
    }

    internal fun parseOwnApiResponse(body: String): ExitIpInfo? {
        val root = gson.fromJson(body, OwnApiEnvelope::class.java) ?: return null
        if (root.code != 0) return null
        val data = root.data ?: return null
        return parseExitIpFields(
            ip = data.ip,
            country = data.country,
            region = data.region,
            city = data.city,
        )
    }

    internal fun parseIpApiComResponse(body: String): ExitIpInfo? {
        val json = gson.fromJson(body, IpApiComPayload::class.java) ?: return null
        if (json.status != "success") return null
        return parseExitIpFields(
            ip = json.query,
            country = json.country,
            region = json.regionName,
            city = json.city,
        )
    }

    internal fun parseIpSbResponse(body: String): ExitIpInfo? {
        val json = gson.fromJson(body, IpSbPayload::class.java) ?: return null
        return parseExitIpFields(
            ip = json.ip,
            country = json.country,
            region = json.region,
            city = json.city,
        )
    }

    internal fun parseCloudflareTrace(body: String): ExitIpInfo? {
        val ip =
            body.lineSequence()
                .map { it.trim() }
                .firstOrNull { it.startsWith("ip=") }
                ?.substringAfter("=")
                ?.trim()
                ?: return null
        return parseExitIpFields(ip = ip)
    }

    internal fun parseIpifyResponse(body: String): ExitIpInfo? {
        val json = gson.fromJson(body, IpifyPayload::class.java) ?: return null
        return parseExitIpFields(ip = json.ip)
    }

    private fun parseExitIpFields(
        ip: String?,
        country: String? = null,
        region: String? = null,
        city: String? = null,
    ): ExitIpInfo? {
        val normalizedIp = ip?.trim().orEmpty()
        if (normalizedIp.isBlank()) return null
        return ExitIpInfo(
            ip = normalizedIp,
            country = country?.trim()?.takeIf { it.isNotBlank() },
            region = region?.trim()?.takeIf { it.isNotBlank() },
            city = city?.trim()?.takeIf { it.isNotBlank() },
        )
    }

    private fun fetchViaProxy(
        url: String,
        proxy: Proxy,
        headers: Map<String, String> = emptyMap(),
    ): String? {
        val conn = URL(url).openConnection(proxy) as HttpURLConnection
        return try {
            conn.requestMethod = "GET"
            conn.connectTimeout = TIMEOUT_MS
            conn.readTimeout = TIMEOUT_MS
            conn.setRequestProperty("User-Agent", USER_AGENT)
            headers.forEach { (key, value) -> conn.setRequestProperty(key, value) }
            conn.connect()
            if (conn.responseCode !in 200..299) return null
            conn.inputStream.bufferedReader().readText()
        } finally {
            conn.disconnect()
        }
    }

    private interface ExitIpProvider {
        fun probe(proxy: Proxy): ExitIpInfo?
    }

    private class OwnApiProvider(
        private val url: String,
        private val authToken: String,
    ) : ExitIpProvider {
        override fun probe(proxy: Proxy): ExitIpInfo? {
            val body =
                fetchViaProxy(
                    url = url,
                    proxy = proxy,
                    headers = mapOf("Authorization" to "Bearer $authToken"),
                ) ?: return null
            return parseOwnApiResponse(body)
        }
    }

    private class IpApiComProvider : ExitIpProvider {
        override fun probe(proxy: Proxy): ExitIpInfo? {
            val body =
                fetchViaProxy(
                    url = "https://ip-api.com/json/?fields=status,query,country,regionName,city",
                    proxy = proxy,
                ) ?: return null
            return parseIpApiComResponse(body)
        }
    }

    private class IpSbProvider : ExitIpProvider {
        override fun probe(proxy: Proxy): ExitIpInfo? {
            val body =
                fetchViaProxy(
                    url = "https://api.ip.sb/geoip",
                    proxy = proxy,
                ) ?: return null
            return parseIpSbResponse(body)
        }
    }

    private class CloudflareTraceProvider : ExitIpProvider {
        override fun probe(proxy: Proxy): ExitIpInfo? {
            val body =
                fetchViaProxy(
                    url = "https://www.cloudflare.com/cdn-cgi/trace",
                    proxy = proxy,
                ) ?: return null
            return parseCloudflareTrace(body)
        }
    }

    private class IpifyProvider : ExitIpProvider {
        override fun probe(proxy: Proxy): ExitIpInfo? {
            val body =
                fetchViaProxy(
                    url = "https://api.ipify.org?format=json",
                    proxy = proxy,
                ) ?: return null
            return parseIpifyResponse(body)
        }
    }

    private data class OwnApiEnvelope(
        val code: Int = -1,
        val data: OwnApiPayload? = null,
    )

    private data class OwnApiPayload(
        val ip: String? = null,
        val country: String? = null,
        val region: String? = null,
        val city: String? = null,
    )

    private data class IpApiComPayload(
        val status: String? = null,
        val query: String? = null,
        val country: String? = null,
        @SerializedName("regionName") val regionName: String? = null,
        val city: String? = null,
    )

    private data class IpSbPayload(
        val ip: String? = null,
        val country: String? = null,
        val region: String? = null,
        val city: String? = null,
    )

    private data class IpifyPayload(val ip: String? = null)
}
