package com.vpn.member.vpn

import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class VpnConnectionBusTest {
    @After
    fun tearDown() {
        VpnConnectionBus.resetForSessionEnd()
    }

    @Test
    fun resetForSessionEnd_clearsFailedState() {
        VpnConnectionBus.update(ConnectionState.FAILED, error = "隧道数据面长时间不可用")

        VpnConnectionBus.resetForSessionEnd()

        val status = VpnConnectionBus.status.value
        assertEquals(ConnectionState.DISCONNECTED, status.state)
        assertNull(status.error)
        assertNull(status.connectedNode)
    }
}
