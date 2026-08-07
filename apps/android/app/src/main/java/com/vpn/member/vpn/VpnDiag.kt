package com.vpn.member.vpn

import android.util.Log
import com.github.kr328.clash.core.Clash
import com.github.kr328.clash.core.model.ProxySort
import com.vpn.member.debug.AppDebugLogger

/** VPN / Mihomo 关键路径诊断日志（logcat tag: VpnDiag）。 */
object VpnDiag {
    private const val TAG = "VpnDiag"

    fun step(phase: String, detail: String? = null, extras: Map<String, String> = emptyMap()) {
        val ctx = buildMap {
            put("phase", phase)
            detail?.let { put("detail", it) }
            putAll(extras)
        }
        runCatching { Log.i(TAG, format(ctx)) }
        AppDebugLogger.info(category = "vpn_diag", message = phase, context = ctx)
    }

    fun warn(phase: String, detail: String, extras: Map<String, String> = emptyMap()) {
        val ctx = buildMap {
            put("phase", phase)
            put("detail", detail)
            putAll(extras)
        }
        runCatching { Log.w(TAG, format(ctx)) }
        AppDebugLogger.warn(category = "vpn_diag", message = phase, context = ctx)
    }

    fun logTraffic(label: String) {
        runCatching {
            val now = Clash.queryTrafficNow()
            val total = Clash.queryTrafficTotal()
            step(
                "traffic",
                label,
                mapOf(
                    "now_up" to MihomoTrafficCodec.unpackUpload(now).toString(),
                    "now_down" to MihomoTrafficCodec.unpackDownload(now).toString(),
                    "total_up" to MihomoTrafficCodec.unpackUpload(total).toString(),
                    "total_down" to MihomoTrafficCodec.unpackDownload(total).toString(),
                ),
            )
        }.onFailure {
            warn("traffic", it.message ?: "query failed")
        }
    }

    fun logTunnelState(label: String) {
        runCatching {
            val state = Clash.queryTunnelState()
            step(
                "tunnel_state",
                label,
                mapOf("mode" to state.mode.name),
            )
        }.onFailure {
            warn("tunnel_state", it.message ?: "query failed")
        }
    }

    fun logProxyGroup(name: String) {
        runCatching {
            val group = Clash.queryGroup(name, ProxySort.Default)
            step(
                "proxy_group",
                name,
                mapOf(
                    "type" to group.type,
                    "now" to group.now,
                    "proxies" to group.proxies.take(8).joinToString(",") { it.name },
                ),
            )
        }.onFailure {
            warn("proxy_group", "$name: ${it.message}")
        }
    }

    private fun format(ctx: Map<String, String>): String =
        ctx.entries.joinToString(" ") { "${it.key}=${it.value}" }
}
