package com.vpn.member.vpn

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * 数据面 soft-fail 策略：有实质流量则信任隧道；不再因探针误报主动断隧道。
 */
class DataplaneRecoveryGuardTest {
    @Test
    fun belowTrafficTrust_shouldNotSkipDisconnect() {
        val bytes = ConnectProbePolicy.TRAFFIC_TRUST_THRESHOLD_BYTES - 1
        assertFalse(ConnectProbePolicy.shouldTrustSessionTraffic(bytes, 0))
        assertFalse(ConnectProbePolicy.shouldTrustSessionTraffic(0, bytes))
    }

    @Test
    fun atTrafficTrust_shouldSkipDisconnect() {
        val half = ConnectProbePolicy.TRAFFIC_TRUST_THRESHOLD_BYTES / 2
        assertTrue(ConnectProbePolicy.shouldTrustSessionTraffic(half, half))
        assertTrue(
            ConnectProbePolicy.shouldTrustSessionTraffic(
                ConnectProbePolicy.TRAFFIC_TRUST_THRESHOLD_BYTES,
                0,
            ),
        )
    }

    @Test
    fun degraded_doesNotForceDisconnectTunnel() {
        assertFalse(VpnAutoReconnectPolicy.DATAPLANE_FORCE_DISCONNECT_ON_DEGRADED)
    }
}
