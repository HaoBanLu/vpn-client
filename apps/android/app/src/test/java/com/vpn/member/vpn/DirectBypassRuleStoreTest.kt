package com.vpn.member.vpn

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class DirectBypassRuleStoreTest {
    @Test
    fun validate_domainSuffix_stripsWildcardPrefix() {
        val result = DirectBypassRuleStore.validateAndNormalize(DirectBypassRuleType.DOMAIN_SUFFIX, "*.Example.COM")
        assertTrue(result.isSuccess)
        assertEquals("example.com", result.getOrNull())
    }

    @Test
    fun validate_domain_rejectsInvalid() {
        val result = DirectBypassRuleStore.validateAndNormalize(DirectBypassRuleType.DOMAIN, "not a domain!")
        assertTrue(result.isFailure)
    }

    @Test
    fun validate_ipCidr_normalizesHost() {
        val result = DirectBypassRuleStore.validateAndNormalize(DirectBypassRuleType.IP_CIDR, "192.168.1.0/24")
        assertTrue(result.isSuccess)
        assertEquals("192.168.1.0/24", result.getOrNull())
    }

    @Test
    fun validate_ipCidr_rejectsInvalidPrefix() {
        val result = DirectBypassRuleStore.validateAndNormalize(DirectBypassRuleType.IP_CIDR, "192.168.1.0/33")
        assertTrue(result.isFailure)
    }

    @Test
    fun toClashLine_ipCidrIncludesNoResolve() {
        val line =
            DirectBypassRuleStore.toClashLine(
                DirectBypassRule(
                    id = "1",
                    type = DirectBypassRuleType.IP_CIDR,
                    value = "10.0.0.0/8",
                ),
            )
        assertEquals("- IP-CIDR,10.0.0.0/8,DIRECT,no-resolve", line)
    }

    @Test
    fun jsonRoundTrip_preservesRules() {
        val rules =
            listOf(
                DirectBypassRule(
                    id = "a",
                    type = DirectBypassRuleType.DOMAIN_SUFFIX,
                    value = "example.com",
                    enabled = true,
                ),
                DirectBypassRule(
                    id = "b",
                    type = DirectBypassRuleType.IP_CIDR,
                    value = "203.0.113.0/24",
                    enabled = false,
                ),
            )
        val parsed = DirectBypassRuleStore.parseJson(DirectBypassRuleStore.toJson(rules))
        assertEquals(2, parsed.size)
        assertEquals("example.com", parsed[0].value)
        assertFalse(parsed[1].enabled)
    }

    @Test
    fun dedupeForClash_keepsFirstEnabledRule() {
        val rules =
            listOf(
                DirectBypassRule("1", DirectBypassRuleType.DOMAIN, "Example.com"),
                DirectBypassRule("2", DirectBypassRuleType.DOMAIN, "example.com"),
            )
        val deduped = DirectBypassRuleStore.dedupeForClash(rules)
        assertEquals(1, deduped.size)
        assertEquals("1", deduped.first().id)
    }
}
