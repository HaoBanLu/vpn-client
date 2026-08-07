package com.vpn.member.vpn

/** 断线/网络恢复自动重连退避策略。 */
object VpnAutoReconnectPolicy {
    const val MAX_ATTEMPTS = 3
    const val CONNECT_TIMEOUT_MS = 30_000L
    const val PERIODIC_HEALTH_PROBE_MS = 120_000L
    /** 探测质量较差时缩短间隔，便于尽快恢复。 */
    const val DEGRADED_HEALTH_PROBE_MS = 60_000L
    /**
     * 历史：数据面 degraded 后强制断开等待时长。
     * 现行：任意探针 soft-degraded 不再按本常量定时断开（dataplaneDegradedDisconnectMs 语义保留对照）。
     * TUN 数据面 inactive 由 VpnTunnelService.disconnectDataplaneInactive 立即断开。
     */
    const val DATAPLANE_DEGRADED_DISCONNECT_MS = 90_000L
    /** 已废弃：勿再用于「degraded 一律断开」；inactive 走 disconnectDataplaneInactive。 */
    const val DATAPLANE_FORCE_DISCONNECT_ON_DEGRADED = false

    private val BACKOFF_MS = longArrayOf(3_000L, 6_000L, 10_000L)

    fun backoffDelayMs(attemptIndex: Int): Long =
        BACKOFF_MS.getOrElse(attemptIndex.coerceAtLeast(0)) { BACKOFF_MS.last() }
}
