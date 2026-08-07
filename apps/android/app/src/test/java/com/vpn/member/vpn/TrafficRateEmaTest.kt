package com.vpn.member.vpn

import org.junit.Assert.assertEquals
import org.junit.Test

class TrafficRateEmaTest {
    @Test
    fun smooth_usesInstantOnFirstSample() {
        assertEquals(1024L, TrafficRateEma.smooth(previous = 0L, instant = 1024L))
    }

    @Test
    fun smooth_blendsTowardInstant() {
        val smoothed = TrafficRateEma.smooth(previous = 1000L, instant = 2000L, alpha = 0.5)
        assertEquals(1500L, smoothed)
    }

    @Test
    fun smooth_clampsNegativeInstantToZero() {
        assertEquals(5L, TrafficRateEma.smooth(previous = 10L, instant = -5L, alpha = 0.5))
    }
}
