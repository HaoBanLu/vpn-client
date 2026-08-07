package com.vpn.member.vpn

import com.vpn.member.data.api.NodeItem
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class NodeFailoverSelectorTest {
    private fun node(
        name: String,
        region: String = "sg",
        status: String = "online",
        latency: Int? = 100,
        protocol: String = "vless",
    ) = NodeItem(
        id = name.hashCode().toLong(),
        name = name,
        region = region,
        status = status,
        protocol = protocol,
        tls_mode = "reality",
        latency_ms = latency,
    )

    @Test
    fun picksLowerLatencyInSameRegion() {
        val nodes =
            listOf(
                node("新加坡-A", latency = 200),
                node("新加坡-B", latency = 80),
                node("新加坡-C", latency = 150),
                node("香港-1", region = "hk", latency = 50),
            )
        val backup = NodeFailoverSelector.pickBackup("新加坡-A", "sg", nodes)
        assertEquals("新加坡-B", backup?.name)
    }

    @Test
    fun skipsOfflineAndCurrent() {
        val nodes =
            listOf(
                node("新加坡-A"),
                node("新加坡-B", status = "offline"),
                node("新加坡-C", latency = 90),
            )
        assertEquals("新加坡-C", NodeFailoverSelector.pickBackup("新加坡-A", "sg", nodes)?.name)
    }

    @Test
    fun picksRelayTrojanBackupInSameRegion() {
        val nodes =
            listOf(
                node("杭州-A", region = "cn", protocol = "trojan").copy(access_mode = "relay", tls_mode = "tls"),
                node("杭州-B", region = "cn", protocol = "trojan", latency = 60).copy(
                    access_mode = "relay",
                    tls_mode = "tls",
                ),
                node("新加坡-1", region = "sg", latency = 40),
            )
        val backup = NodeFailoverSelector.pickBackup("杭州-A", "cn", nodes)
        assertEquals("杭州-B", backup?.name)
    }

    @Test
    fun returnsNullWhenNoBackup() {
        val nodes = listOf(node("新加坡-A"))
        assertNull(NodeFailoverSelector.pickBackup("新加坡-A", "sg", nodes))
    }
}
