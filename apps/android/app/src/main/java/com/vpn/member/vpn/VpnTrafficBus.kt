package com.vpn.member.vpn

import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

data class VpnTrafficSnapshot(
    val stats: VpnSessionStats,
    val rates: VpnTrafficRates,
    val source: TrafficStatsSource,
) {
    companion object {
        val EMPTY =
            VpnTrafficSnapshot(
                stats = VpnSessionStats(0L, 0L, 0L),
                rates = VpnTrafficRates(0L, 0L),
                source = TrafficStatsSource.NONE,
            )
    }
}

enum class TrafficStatsSource {
    NONE,
    MIHOMO,
    SYSTEM,
}

/** 连接页与通知栏共用的流量快照（由 [VpnTunnelService] 统一采样发布）。 */
object VpnTrafficBus {
    private val _snapshot = MutableStateFlow(VpnTrafficSnapshot.EMPTY)
    val snapshot: StateFlow<VpnTrafficSnapshot> = _snapshot.asStateFlow()

    fun publish(snapshot: VpnTrafficSnapshot) {
        _snapshot.value = snapshot
    }

    fun clear() {
        _snapshot.value = VpnTrafficSnapshot.EMPTY
    }
}
