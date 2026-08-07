package com.vpn.member.vpn

import org.junit.After
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.util.TimeZone

class OverseasLocaleHintTest {
    private var originalTz: TimeZone? = null

    @After
    fun restoreTimezone() {
        originalTz?.let { TimeZone.setDefault(it) }
    }

    @Test
    fun yangonIsOverseas() {
        assertTrue(OverseasLocaleHint.isOverseasTimezone("Asia/Yangon"))
    }

    @Test
    fun shanghaiIsNotOverseas() {
        assertFalse(OverseasLocaleHint.isOverseasTimezone("Asia/Shanghai"))
    }

    @Test
    fun defaultTimezone_usesSystemDefault() {
        originalTz = TimeZone.getDefault()
        TimeZone.setDefault(TimeZone.getTimeZone("Asia/Yangon"))
        assertTrue(OverseasLocaleHint.isOverseasTimezone())
    }
}
