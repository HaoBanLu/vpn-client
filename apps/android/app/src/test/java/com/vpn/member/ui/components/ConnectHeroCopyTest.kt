package com.vpn.member.ui.components

import com.vpn.member.vpn.ConnectionState
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class ConnectHeroCopyTest {
    @Test
    fun `connected ignores probe degraded for hero`() {
        val copy =
            resolveConnectHeroCopy(
                connectionState = ConnectionState.CONNECTED,
                connectPending = false,
                isSwitching = false,
                connectedNodeName = "安徽芜湖2",
                selectedNode = "安徽芜湖2",
            )
        assertEquals("已保护", copy.title)
        assertEquals("安徽芜湖2", copy.subtitle)
        assertTrue(copy.connected)
    }

    @Test
    fun `connected ok shows 已保护`() {
        val copy =
            resolveConnectHeroCopy(
                connectionState = ConnectionState.CONNECTED,
                connectPending = false,
                isSwitching = false,
                connectedNodeName = "新加坡-BGP线路",
                selectedNode = "新加坡-BGP线路",
            )
        assertEquals("已保护", copy.title)
        assertTrue(copy.connected)
        assertFalse(copy.subtitle.contains("网络质量"))
    }

    @Test
    fun `connected subtitle shows node only`() {
        val copy =
            resolveConnectHeroCopy(
                connectionState = ConnectionState.CONNECTED,
                connectPending = false,
                isSwitching = false,
                connectedNodeName = "武汉",
                selectedNode = "武汉",
                tunnelLatencyMs = 120,
                entryLatencyMs = 2,
            )
        assertEquals("武汉", copy.subtitle)
        assertFalse(copy.subtitle.contains("ms"))
    }

    @Test
    fun `tunnel start failed shows 连接失败`() {
        val copy =
            resolveConnectHeroCopy(
                connectionState = ConnectionState.FAILED,
                connectPending = false,
                isSwitching = false,
                connectedNodeName = null,
                selectedNode = "武汉",
            )
        assertEquals("连接失败", copy.title)
    }

    @Test
    fun `connecting keeps 连接中 label for tap to interrupt`() {
        val copy =
            resolveConnectHeroCopy(
                connectionState = ConnectionState.CONNECTING,
                connectPending = false,
                isSwitching = false,
                connectedNodeName = null,
                selectedNode = "新加坡5",
            )
        assertEquals("连接中", copy.title)
        assertEquals("连接中", copy.buttonLabel)
        assertTrue(copy.connecting)
        assertFalse(copy.buttonLabel.contains("取消"))
    }
}
