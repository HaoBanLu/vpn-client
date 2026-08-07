package com.vpn.member.vpn

import org.junit.Assert.assertEquals
import org.junit.Test

class ProbeResultTest {
    @Test
    fun toStatus_okWhenTunnelLatencyUnderSlowThreshold() {
        val result =
            ProbeResult(
                basicOk = true,
                overseasOk = true,
                slow = false,
                latencyMs = ConnectivityProbe.SLOW_LATENCY_MS - 1,
            )
        assertEquals(ProbeStatus.OK, result.toStatus())
    }

    @Test
    fun toStatus_slowWhenTunnelLatencyAtOrAboveThreshold() {
        val atThreshold =
            ProbeResult(
                basicOk = true,
                overseasOk = true,
                slow = true,
                latencyMs = ConnectivityProbe.SLOW_LATENCY_MS,
            )
        assertEquals(ProbeStatus.SLOW, atThreshold.toStatus())

        val above =
            ProbeResult(
                basicOk = true,
                overseasOk = true,
                slow = true,
                latencyMs = 900,
            )
        assertEquals(ProbeStatus.SLOW, above.toStatus())
    }

    @Test
    fun toStatus_limitedOverseasWhenBasicOkButTunnelUnreachable() {
        val result =
            ProbeResult(
                basicOk = true,
                overseasOk = false,
                slow = false,
                latencyMs = null,
            )
        assertEquals(ProbeStatus.LIMITED_OVERSEAS, result.toStatus())
    }

    @Test
    fun toStatus_failedWhenBasicUnreachable() {
        val result =
            ProbeResult(
                basicOk = false,
                overseasOk = false,
                slow = false,
                latencyMs = null,
            )
        assertEquals(ProbeStatus.FAILED, result.toStatus())
    }
}
