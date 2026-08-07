package com.vpn.member.vpn

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class ConnectTimingTrackerTest {
    @Test
    fun recordsClickToTunDuration() {
        ConnectTimingTracker.reset()
        ConnectTimingTracker.markConnectClick()
        Thread.sleep(15)
        ConnectTimingTracker.markConfigDispatched()
        ConnectTimingTracker.markTunReady()
        val ms = ConnectTimingTracker.lastClickToTunMsForTest()
        requireNotNull(ms)
        assertTrue(ms >= 15)
    }

    @Test
    fun kpiTargetIsFiveSeconds() {
        assertEquals(5_000L, ConnectTimingTracker.KPI_TARGET_MS)
    }
}
