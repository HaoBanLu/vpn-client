package com.vpn.member.vpn

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class VpnAutoReconnectPolicyTest {
    @Test
    fun backoffIncreasesWithAttempts() {
        assertEquals(3_000L, VpnAutoReconnectPolicy.backoffDelayMs(0))
        assertEquals(6_000L, VpnAutoReconnectPolicy.backoffDelayMs(1))
        assertEquals(10_000L, VpnAutoReconnectPolicy.backoffDelayMs(2))
        assertEquals(10_000L, VpnAutoReconnectPolicy.backoffDelayMs(99))
    }

    @Test
    fun healthProbeIntervals() {
        assertEquals(120_000L, VpnAutoReconnectPolicy.PERIODIC_HEALTH_PROBE_MS)
        assertEquals(60_000L, VpnAutoReconnectPolicy.DEGRADED_HEALTH_PROBE_MS)
        assertEquals(90_000L, VpnAutoReconnectPolicy.DATAPLANE_DEGRADED_DISCONNECT_MS)
    }
}

class NodeFailoverMonitorTest {
    @Test
    fun autoFailoverDisabledEvenAfterThreeFailures() {
        NodeFailoverMonitor.reset()
        assertFalse(NodeFailoverMonitor.AUTO_FAILOVER_ENABLED)
        assertFalse(NodeFailoverMonitor.shouldFailover())
        NodeFailoverMonitor.recordFailure()
        NodeFailoverMonitor.recordFailure()
        NodeFailoverMonitor.recordFailure()
        // 默认关闭自动切节点：连续失败只记状态，不触发 failover。
        assertFalse(NodeFailoverMonitor.shouldFailover())
        assertEquals(3, NodeFailoverMonitor.consecutiveFailsForTest())
        NodeFailoverMonitor.recordSuccess()
        assertEquals(0, NodeFailoverMonitor.consecutiveFailsForTest())
        assertFalse(NodeFailoverMonitor.shouldFailover())
    }
}
