package com.vpn.member

import androidx.test.ext.junit.runners.AndroidJUnit4
import com.vpn.member.ui.components.resolveConnectHeroCopy
import com.vpn.member.vpn.ConnectionState
import com.vpn.member.vpn.ProbeStatus
import com.vpn.member.vpn.VpnConnectionBus
import org.junit.Assert.assertEquals
import org.junit.Test
import org.junit.runner.RunWith

/**
 * 数据面 degraded 时连接页主区仍展示已保护（探测与 UI 解耦）。
 */
@RunWith(AndroidJUnit4::class)
class ConnectDataplaneInstrumentedTest {
    @Test
    fun connectedWithBusDegraded_stillShowsProtected() {
        VpnConnectionBus.updateQuality(probeStatus = ProbeStatus.DEGRADED.name.lowercase())
        val copy =
            resolveConnectHeroCopy(
                connectionState = ConnectionState.CONNECTED,
                connectPending = false,
                isSwitching = false,
                connectedNodeName = "安徽芜湖1",
                selectedNode = "安徽芜湖1",
            )
        assertEquals("已保护", copy.title)
        assertEquals("安徽芜湖1", copy.subtitle)
    }
}
