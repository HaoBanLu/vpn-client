package com.vpn.member.vpn

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class ClashDirectBypassPatcherTest {
    @Test
    fun inject_insertsBeforeLastMatchRule() {
        val yaml =
            """
            proxies:
              - name: node
            rules:
              - IP-CIDR,127.0.0.0/8,DIRECT,no-resolve
              - MATCH,Proxy
            """.trimIndent()
        val rules =
            listOf(
                DirectBypassRule(
                    id = "1",
                    type = DirectBypassRuleType.DOMAIN_SUFFIX,
                    value = "example.com",
                ),
            )
        val patched = ClashDirectBypassPatcher.inject(yaml, rules)
        val lines = patched.lines()
        val matchIndex = lines.indexOfFirst { it.trim().startsWith("- MATCH,") }
        val injectedIndex = lines.indexOfFirst { it.contains("DOMAIN-SUFFIX,example.com,DIRECT") }
        assertTrue(injectedIndex >= 0)
        assertTrue(matchIndex > injectedIndex)
    }

    @Test
    fun inject_appendsRulesSectionWhenMissing() {
        val yaml =
            """
            proxies:
              - name: node
            """.trimIndent()
        val rules =
            listOf(
                DirectBypassRule(
                    id = "1",
                    type = DirectBypassRuleType.IP_CIDR,
                    value = "10.0.0.0/8",
                ),
            )
        val patched = ClashDirectBypassPatcher.inject(yaml, rules)
        assertTrue(patched.contains("rules:"))
        assertTrue(patched.contains("IP-CIDR,10.0.0.0/8,DIRECT,no-resolve"))
    }

    @Test
    fun inject_skipsWhenNoEnabledRules() {
        val yaml = "proxies:\n  - name: node\nrules:\n  - MATCH,Proxy\n"
        val patched = ClashDirectBypassPatcher.inject(yaml, emptyList())
        assertFalse(patched.contains("DOMAIN-SUFFIX"))
    }
}
