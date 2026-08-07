package com.vpn.member.vpn.mihomo

import org.junit.Assert.assertEquals
import org.junit.Test

class MihomoDnsFilterTest {
    @Test
    fun rejectsTunPortalDns() {
        assertEquals(false, MihomoDnsFilter.isUsableUpstream("172.19.0.2"))
        assertEquals(false, MihomoDnsFilter.isUsableUpstream("198.18.0.1"))
    }

    @Test
    fun acceptsPublicDns() {
        assertEquals(true, MihomoDnsFilter.isUsableUpstream("223.5.5.5"))
        assertEquals(true, MihomoDnsFilter.isUsableUpstream("8.8.8.8"))
    }

    @Test
    fun rejectsLinkLocalAndZoneIdDns() {
        assertEquals(false, MihomoDnsFilter.isUsableUpstream("fe80::1"))
        assertEquals(false, MihomoDnsFilter.isUsableUpstream("fe80::1%wlan0"))
        assertEquals(false, MihomoDnsFilter.isUsableUpstream("240c::6666%wlan0"))
    }

    @Test
    fun formatDnsEndpointKeepsHostOnly() {
        assertEquals("223.5.5.5", MihomoDnsFilter.formatDnsEndpoint("223.5.5.5"))
        assertEquals("8.8.8.8", MihomoDnsFilter.formatDnsEndpoint("8.8.8.8", 5353))
    }
}
