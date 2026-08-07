package com.vpn.member.vpn

import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Test
import java.util.TimeZone

class TunStackModeTest {
    private var originalTz: TimeZone? = null

    @After
    fun restoreTimezone() {
        originalTz?.let { TimeZone.setDefault(it) }
    }

    private fun withTimezone(id: String, block: () -> Unit) {
        originalTz = TimeZone.getDefault()
        TimeZone.setDefault(TimeZone.getTimeZone(id))
        try {
            block()
        } finally {
            TimeZone.setDefault(originalTz)
            originalTz = null
        }
    }

    @Test
    fun defaultsToSystemForOverseasWeakNetwork() {
        assertEquals(TunStackMode.SYSTEM, TunStackMode.resolve(null))
        assertEquals(TunStackMode.SYSTEM, TunStackMode.resolve(""))
        assertEquals(TunStackMode.SYSTEM, TunStackMode.resolve("unknown"))
    }

    @Test
    fun acceptsKnownStacks() {
        assertEquals(TunStackMode.GVISOR, TunStackMode.resolve("gvisor"))
        assertEquals(TunStackMode.MIXED, TunStackMode.resolve("MIXED"))
        assertEquals(TunStackMode.SYSTEM, TunStackMode.resolve("system"))
    }

    @Test
    fun resolveForSession_domesticReturnFull_defaultsGvisor() {
        assertEquals(TunStackMode.GVISOR, TunStackMode.resolveForSession(null, domesticReturnFull = true))
        assertEquals(TunStackMode.GVISOR, TunStackMode.resolveForSession("", domesticReturnFull = true))
    }

    @Test
    fun resolveForSession_overseas_defaultsGvisor() {
        assertEquals(TunStackMode.GVISOR, TunStackMode.resolveForSession(null, domesticReturnFull = false))
    }

    @Test
    fun resolveForSession_chinaDomesticReturn_respectsUserSystem() {
        withTimezone("Asia/Shanghai") {
            assertEquals(TunStackMode.SYSTEM, TunStackMode.resolveForSession("system", domesticReturnFull = true))
        }
    }

    @Test
    fun resolveForSession_abroadDomesticReturn_forcesGvisor() {
        withTimezone("Asia/Yangon") {
            assertEquals(TunStackMode.GVISOR, TunStackMode.resolveForSession("system", domesticReturnFull = true))
            assertEquals(TunStackMode.GVISOR, TunStackMode.resolveForSession("gvisor", domesticReturnFull = true))
        }
    }
}
