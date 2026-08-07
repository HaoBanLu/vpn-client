package com.vpn.member.vpn

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class VpnTunnelStateSyncTest {
    @Test
    fun killSwitchShell_doesNotPromoteDisconnectedToConnected() {
        assertNull(
            VpnTunnelStateSync.reconcileBusState(
                busState = ConnectionState.DISCONNECTED,
                serviceUp = true,
                vpnUp = true,
                tunnelRunning = false,
            ),
        )
    }

    @Test
    fun realTunnel_promotesDisconnectedToConnected() {
        assertEquals(
            ConnectionState.CONNECTED,
            VpnTunnelStateSync.reconcileBusState(
                busState = ConnectionState.DISCONNECTED,
                serviceUp = true,
                vpnUp = true,
                tunnelRunning = true,
            ),
        )
    }

    @Test
    fun staleBusConnected_demotesWhenSystemDown() {
        assertEquals(
            ConnectionState.DISCONNECTED,
            VpnTunnelStateSync.reconcileBusState(
                busState = ConnectionState.CONNECTED,
                serviceUp = false,
                vpnUp = false,
                tunnelRunning = false,
            ),
        )
    }

    @Test
    fun busConnectedWithKillSwitch_doesNotDemote() {
        assertNull(
            VpnTunnelStateSync.reconcileBusState(
                busState = ConnectionState.CONNECTED,
                serviceUp = true,
                vpnUp = true,
                tunnelRunning = false,
            ),
        )
    }

    @Test
    fun alreadyAlignedStates_noChange() {
        assertNull(
            VpnTunnelStateSync.reconcileBusState(
                busState = ConnectionState.DISCONNECTED,
                serviceUp = false,
                vpnUp = false,
                tunnelRunning = false,
            ),
        )
    }
}
