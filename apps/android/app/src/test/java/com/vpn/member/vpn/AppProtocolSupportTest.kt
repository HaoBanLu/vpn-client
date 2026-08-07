package com.vpn.member.vpn

import com.vpn.member.data.api.NodeItem
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Assert.assertEquals
import org.junit.Test

/** 经中转：自研 App 支持全部 sing-box 族；OpenVPN/WireGuard 仍不可连。 */
class AppProtocolSupportTest {
    private fun node(
        protocol: String,
        accessMode: String? = "direct",
        tlsMode: String? = null,
        status: String = "online",
    ) = NodeItem(
        id = 1L,
        name = "test",
        region = "cn",
        status = status,
        protocol = protocol,
        access_mode = accessMode,
        tls_mode = tlsMode,
    )

    @Test
    fun relaySingBoxProtocolsAreConnectable() {
        for (protocol in listOf("vless", "vmess", "trojan", "shadowsocks", "ss", "hysteria2", "hy2")) {
            val n = node(protocol, accessMode = "relay", tlsMode = "tls")
            assertTrue(protocol, AppProtocolSupport.isAppConnectable(n))
            assertTrue(protocol, AppProtocolSupport.isRelayCompatible(n))
            assertNull(protocol, AppProtocolSupport.unsupportedReason(n))
        }
    }

    @Test
    fun relayVlessWithoutRealityStillConnectable() {
        val n = node("vless", accessMode = "relay", tlsMode = "tls")
        assertTrue(AppProtocolSupport.isAppConnectable(n))
    }

    @Test
    fun relayOpenVpnAndWireGuardNotConnectable() {
        for (protocol in listOf("openvpn", "wireguard")) {
            val n = node(protocol, accessMode = "relay")
            assertFalse(protocol, AppProtocolSupport.isAppConnectable(n))
            assertFalse(protocol, AppProtocolSupport.isRelayCompatible(n))
            val reason = AppProtocolSupport.unsupportedReason(n)
            assertTrue(protocol, reason != null && reason.contains("官方客户端"))
        }
    }

    @Test
    fun directOpenVpnNotConnectable() {
        val n = node("openvpn", accessMode = "direct")
        assertFalse(AppProtocolSupport.isAppConnectable(n))
        assertEquals(
            "需使用 OpenVPN 官方客户端，自研 App 不支持",
            AppProtocolSupport.unsupportedReason(n),
        )
    }

    @Test
    fun directTrojanConnectable() {
        assertTrue(AppProtocolSupport.isAppConnectable(node("trojan", accessMode = "direct")))
    }

    @Test
    fun normalizeAliases() {
        assertEquals("hysteria2", AppProtocolSupport.normalizeProtocol("hy2"))
        assertEquals("shadowsocks", AppProtocolSupport.normalizeProtocol("ss"))
        assertEquals("vmess", AppProtocolSupport.normalizeProtocol(""))
    }
}
