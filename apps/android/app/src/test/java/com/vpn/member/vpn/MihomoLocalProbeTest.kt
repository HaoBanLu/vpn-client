package com.vpn.member.vpn

import org.junit.Assert.assertEquals
import org.junit.Test

class MihomoLocalProbeTest {
    @Test
    fun mixedPort_matchesClashDefault() {
        assertEquals(7890, MihomoLocalProbe.MIXED_PORT)
    }
}
