package com.vpn.member.vpn

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class TunnelWatchdogPolicyTest {
    @Test
    fun streakIncrementsOnlyWhenOnlineAndFailing() {
        assertEquals(0, TunnelWatchdogPolicy.nextFailStreak(vpnNetworkOk = true, previousStreak = 2, physicalOnline = true))
        assertEquals(0, TunnelWatchdogPolicy.nextFailStreak(vpnNetworkOk = false, previousStreak = 2, physicalOnline = false))
        assertEquals(3, TunnelWatchdogPolicy.nextFailStreak(vpnNetworkOk = false, previousStreak = 2, physicalOnline = true))
    }

    @Test
    fun reconnectThreshold() {
        assertFalse(TunnelWatchdogPolicy.shouldRequestReconnect(1))
        assertTrue(TunnelWatchdogPolicy.shouldRequestReconnect(2))
    }
}
