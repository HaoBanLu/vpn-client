package com.vpn.member.vpn

import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File

/** 验证规则直连经 Sanitizer 写入 config.yaml 的完整链路。 */
class DirectConnectIntegrationTest {
    private val sampleYaml =
        """
        proxies:
          - name: test-node
            type: vmess
            server: 1.2.3.4
            port: 443
            uuid: 00000000-0000-0000-0000-000000000001
            alterId: 0
            cipher: auto
        proxy-groups:
          - name: Proxy
            type: select
            proxies:
              - test-node
              - DIRECT
        rules:
          - IP-CIDR,127.0.0.0/8,DIRECT,no-resolve
          - MATCH,Proxy
        """.trimIndent()

    @Test
    fun sanitizer_writesDirectBypassRulesBeforeMatch() {
        val dir = createTempDir(prefix = "clash_cfg_test_")
        val rules =
            listOf(
                DirectBypassRule(
                    id = "1",
                    type = DirectBypassRuleType.DOMAIN_SUFFIX,
                    value = "bank.example.com",
                ),
                DirectBypassRule(
                    id = "2",
                    type = DirectBypassRuleType.IP_CIDR,
                    value = "203.0.113.0/24",
                    enabled = false,
                ),
            )

        ClashConfigSanitizer.prepareConfigDirectory(
            configYaml = sampleYaml,
            configDir = dir,
            geoReady = true,
            rulesetsReady = false,
            directBypassRules = rules,
        )

        val written = File(dir, "config.yaml").readText()
        assertTrue(written.contains("DOMAIN-SUFFIX,bank.example.com,DIRECT"))
        assertTrue(written.contains("IP-CIDR,203.0.113.0/24").not())
        val matchIndex = written.indexOf("- MATCH,Proxy")
        val ruleIndex = written.indexOf("DOMAIN-SUFFIX,bank.example.com,DIRECT")
        assertTrue(matchIndex > ruleIndex)
    }

    @Test
    fun appDirectConnectStore_excludesDisabledBypassRulesFromClash() {
        val enabledOnly =
            DirectBypassRuleStore.dedupeForClash(
                listOf(
                    DirectBypassRule("1", DirectBypassRuleType.DOMAIN, "a.com", enabled = false),
                    DirectBypassRule("2", DirectBypassRuleType.DOMAIN, "b.com", enabled = true),
                ),
            )
        assertTrue(enabledOnly.size == 1)
        assertTrue(enabledOnly.first().value == "b.com")
    }
}
