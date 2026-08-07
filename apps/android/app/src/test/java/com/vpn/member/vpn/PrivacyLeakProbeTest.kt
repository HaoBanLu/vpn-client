package com.vpn.member.vpn

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class PrivacyLeakProbeTest {
    @Test
    fun passedWhenExitDiffersFromBaselineAndDnsOk() {
        val result =
            PrivacyLeakProbe.evaluate(
                exitIp = "203.0.113.1",
                baselineIp = "198.51.100.2",
                ipv6LocalActive = false,
                dnsReachable = true,
                ipv6ProtectionEnabled = true,
            )
        assertTrue(result.passed)
        assertTrue(result.exitIpLooksProtected)
    }

    @Test
    fun failedWhenExitMatchesBaseline() {
        val result =
            PrivacyLeakProbe.evaluate(
                exitIp = "198.51.100.2",
                baselineIp = "198.51.100.2",
                ipv6LocalActive = false,
                dnsReachable = true,
                ipv6ProtectionEnabled = true,
            )
        assertFalse(result.passed)
        assertFalse(result.exitIpLooksProtected)
    }

    @Test
    fun failedWhenIpv6LocalActiveAndProtectionOn() {
        val result =
            PrivacyLeakProbe.evaluate(
                exitIp = "203.0.113.1",
                baselineIp = "198.51.100.2",
                ipv6LocalActive = true,
                dnsReachable = true,
                ipv6ProtectionEnabled = true,
            )
        assertFalse(result.passed)
    }

    @Test
    fun ignoresIpv6LocalWhenProtectionOff() {
        val result =
            PrivacyLeakProbe.evaluate(
                exitIp = "203.0.113.1",
                baselineIp = "198.51.100.2",
                ipv6LocalActive = true,
                dnsReachable = true,
                ipv6ProtectionEnabled = false,
            )
        assertTrue(result.passed)
    }

    @Test
    fun failedWhenDnsUnreachable() {
        val result =
            PrivacyLeakProbe.evaluate(
                exitIp = "203.0.113.1",
                baselineIp = "198.51.100.2",
                ipv6LocalActive = false,
                dnsReachable = false,
                ipv6ProtectionEnabled = true,
            )
        assertFalse(result.passed)
    }
}
