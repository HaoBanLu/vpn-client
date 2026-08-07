package com.vpn.member.vpn

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class ConnectProbePolicyTest {
    @Test
    fun busDegraded_detected() {
        assertTrue(ConnectProbePolicy.isBusDataplaneDegraded("degraded"))
        assertTrue(ConnectProbePolicy.isBusDataplaneDegraded("DEGRADED"))
        assertFalse(ConnectProbePolicy.isBusDataplaneDegraded("ok"))
        assertFalse(ConnectProbePolicy.isBusDataplaneDegraded(null))
    }

    @Test
    fun mergeProbeStatus_busDegradedWinsOverOk() {
        val merged =
            ConnectProbePolicy.mergeProbeStatus(
                measured = ProbeStatus.OK,
                busProbeStatus = "degraded",
                connectionState = ConnectionState.CONNECTED,
            )
        assertEquals(ProbeStatus.DEGRADED, merged)
    }

    @Test
    fun mergeProbeStatus_okWhenBusClear() {
        val merged =
            ConnectProbePolicy.mergeProbeStatus(
                measured = ProbeStatus.OK,
                busProbeStatus = "ok",
                connectionState = ConnectionState.CONNECTED,
            )
        assertEquals(ProbeStatus.OK, merged)
    }

    @Test
    fun mergeProbeStatus_trafficTrustClearsBusDegraded() {
        val threshold = ConnectProbePolicy.TRAFFIC_TRUST_THRESHOLD_BYTES
        val merged =
            ConnectProbePolicy.mergeProbeStatus(
                measured = ProbeStatus.OK,
                busProbeStatus = "degraded",
                connectionState = ConnectionState.CONNECTED,
                sessionTrafficBytes = threshold,
            )
        assertEquals(ProbeStatus.OK, merged)
    }

    @Test
    fun mergeProbeStatus_trafficTrustClearsFailedProbe() {
        val threshold = ConnectProbePolicy.TRAFFIC_TRUST_THRESHOLD_BYTES
        val merged =
            ConnectProbePolicy.mergeProbeStatus(
                measured = ProbeStatus.FAILED,
                busProbeStatus = "degraded",
                connectionState = ConnectionState.CONNECTED,
                sessionTrafficBytes = threshold,
            )
        assertEquals(ProbeStatus.OK, merged)
    }

    @Test
    fun shouldTrustSessionTraffic_atThreshold() {
        val half = ConnectProbePolicy.TRAFFIC_TRUST_THRESHOLD_BYTES / 2
        assertFalse(ConnectProbePolicy.shouldTrustSessionTraffic(half, half - 1))
        assertTrue(ConnectProbePolicy.shouldTrustSessionTraffic(half, half))
    }
}
