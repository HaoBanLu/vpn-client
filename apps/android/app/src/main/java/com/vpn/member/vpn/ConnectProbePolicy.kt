package com.vpn.member.vpn

/** 连接后探测与 VpnConnectionBus 数据面状态合并策略。 */
object ConnectProbePolicy {
    /** 会话累计流量超过此值时，以真实转发为准，不再因探针 URL 失败标 degraded。 */
    const val TRAFFIC_TRUST_THRESHOLD_BYTES = 5L * 1024 * 1024

    fun shouldTrustSessionTraffic(downloadBytes: Long, uploadBytes: Long): Boolean =
        (downloadBytes.coerceAtLeast(0L) + uploadBytes.coerceAtLeast(0L)) >= TRAFFIC_TRUST_THRESHOLD_BYTES

    fun isBusDataplaneDegraded(busProbeStatus: String?): Boolean =
        busProbeStatus?.equals(ProbeStatus.DEGRADED.name, ignoreCase = true) == true

    /**
     * mixed-port 探测可能通过而 TUN 未转发；bus 已标 degraded 时不得覆盖为 OK。
     * 但若会话已有实质流量，说明隧道在真实转发，忽略探针误报。
     */
    fun mergeProbeStatus(
        measured: ProbeStatus,
        busProbeStatus: String?,
        connectionState: ConnectionState,
        sessionTrafficBytes: Long = 0L,
    ): ProbeStatus {
        if (connectionState != ConnectionState.CONNECTED) return measured
        if (shouldTrustSessionTraffic(sessionTrafficBytes, 0L)) {
            return when (measured) {
                ProbeStatus.FAILED, ProbeStatus.DEGRADED -> ProbeStatus.OK
                else -> measured
            }
        }
        if (isBusDataplaneDegraded(busProbeStatus)) return ProbeStatus.DEGRADED
        return measured
    }
}
