package com.vpn.member.vpn

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class ClashConfigSanitizerTest {
    @Test
    fun stripRemoteGeoRules_removesGeositeAndGeoip() {
        val yaml =
            """
            rules:
              - IP-CIDR,127.0.0.0/8,DIRECT,no-resolve
              - GEOSITE,cn,DIRECT
              - GEOIP,CN,DIRECT,no-resolve
              - MATCH,Proxy
            """.trimIndent()

        val got = ClashConfigSanitizer.stripRemoteGeoRules(yaml)

        assertFalse(got.contains("GEOSITE"))
        assertFalse(got.contains("GEOIP"))
        assertTrue(got.contains("IP-CIDR,127.0.0.0/8"))
        assertTrue(got.contains("MATCH,Proxy"))
    }

    @Test
    fun stripGeoipDnsFallback_disablesGeoipAndRemovesCode() {
        val yaml =
            """
            dns:
              fallback-filter:
                geoip: true
                geoip-code: CN
                ipcidr:
                  - 240.0.0.0/4
            """.trimIndent()

        val got = ClashConfigSanitizer.stripGeoipDnsFallback(yaml)

        assertTrue(got.contains("geoip: false"))
        assertFalse(got.contains("geoip: true"))
        assertFalse(got.contains("geoip-code"))
    }

    @Test
    fun stripRemoteRuleProviders_removesHttpProvidersAndRuleSet() {
        val yaml =
            """
            rule-providers:
              reject:
                type: http
                behavior: domain
                url: "https://cdn.jsdelivr.net/gh/Loyalsoldier/clash-rules@release/reject.txt"
                path: ./ruleset/reject.yaml
                interval: 86400
            rules:
              - RULE-SET,reject,REJECT
              - MATCH,GLOBAL
            """.trimIndent()

        val got = ClashConfigSanitizer.stripRemoteRuleProviders(yaml)

        assertFalse(got.contains("rule-providers:"))
        assertFalse(got.contains("RULE-SET"))
        assertFalse(got.contains("jsdelivr"))
        assertTrue(got.contains("MATCH,GLOBAL"))
    }

    @Test
    fun localizeRuleProviders_switchesHttpToFile() {
        val yaml =
            """
            rule-providers:
              reject:
                type: http
                behavior: domain
                url: "https://cdn.jsdelivr.net/gh/Loyalsoldier/clash-rules@release/reject.txt"
                path: ./ruleset/reject.yaml
                interval: 86400
            """.trimIndent()

        val got = ClashConfigSanitizer.localizeRuleProviders(yaml)

        assertTrue(got.contains("type: file"))
        assertTrue(got.contains("path: ./providers/ruleset/reject.yaml"))
        assertFalse(got.contains("type: http"))
        assertFalse(got.contains("interval:"))
    }

    @Test
    fun preferOverseasFriendlyDns_removesChinaNameservers() {
        val yaml =
            """
            dns:
              nameserver:
                - https://doh.pub/dns-query
                - https://dns.alidns.com/dns-query
                - 1.1.1.1
              fallback:
                - https://1.1.1.1/dns-query
            """.trimIndent()

        val got = ClashConfigSanitizer.preferOverseasFriendlyDns(yaml)

        assertFalse(got.contains("doh.pub"))
        assertFalse(got.contains("alidns"))
        assertTrue(got.contains("1.1.1.1"))
    }

    @Test
    fun ensureSnifferForFullTunnel_appendsSnifferBlock() {
        val yaml = "rules:\n  - MATCH,GLOBAL\n"
        val got = ClashConfigSanitizer.ensureSnifferForFullTunnel(yaml)
        assertTrue(got.contains("sniffer:"))
        assertTrue(got.contains("override-destination: true"))
    }

    @Test
    fun ensureAndroidTunHints_appendsClashForAndroidBlock() {
        val yaml = "mode: rule\n"
        val got = ClashConfigSanitizer.ensureAndroidTunHints(yaml)
        assertTrue(got.contains("clash-for-android:"))
        assertTrue(got.contains("append-system-dns: false"))
    }

    @Test
    fun hardenDomesticReturnDnsForAbroad_replacesChinaBootstrapDns() {
        val yaml =
            """
            app-profile: domestic_return
            dns:
              default-nameserver:
                - 223.5.5.5
                - 114.114.114.114
              nameserver:
                - https://doh.pub/dns-query
                - https://dns.alidns.com/dns-query
              fallback:
                - https://1.1.1.1/dns-query
            """.trimIndent()

        val got = ClashConfigSanitizer.hardenDomesticReturnDnsForAbroad(yaml)

        assertFalse(got.contains("doh.pub"))
        assertFalse(got.contains("223.5.5.5"))
        assertTrue(got.contains("1.1.1.1"))
        assertTrue(got.contains("8.8.8.8"))
        assertTrue(got.contains("https://1.1.1.1/dns-query"))
    }
}
