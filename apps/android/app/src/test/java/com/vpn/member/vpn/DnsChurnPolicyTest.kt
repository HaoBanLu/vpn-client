package com.vpn.member.vpn

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class DnsChurnPolicyTest {
    @Test
    fun dnsReasons_useLongerDebounce() {
        assertTrue(DnsChurnPolicy.isDnsOnlyReason("dns_changed"))
        assertTrue(DnsChurnPolicy.isDnsOnlyReason("transport_dns_changed"))
        assertEquals(DnsChurnPolicy.DNS_DEBOUNCE_MS, DnsChurnPolicy.reconnectDebounceMs("dns_changed"))
        assertEquals(DnsChurnPolicy.DNS_DEBOUNCE_MS, DnsChurnPolicy.reconnectDebounceMs("transport_dns_changed"))
    }

    @Test
    fun nonDns_usesStandardDebounce() {
        assertFalse(DnsChurnPolicy.isDnsOnlyReason("network_available"))
        assertEquals(
            NetworkRestorePolicy.RECONNECT_DEBOUNCE_MS,
            DnsChurnPolicy.reconnectDebounceMs("transport_network_available"),
        )
    }
}
