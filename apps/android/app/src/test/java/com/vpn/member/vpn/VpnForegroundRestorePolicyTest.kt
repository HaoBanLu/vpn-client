package com.vpn.member.vpn

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class VpnForegroundRestorePolicyTest {
    private val staleSnapshot =
        VpnSessionSnapshot(
            wasUserConnected = true,
            nodeName = "新加坡1",
            region = "sg",
            profile = ConnectionScenario.PROFILE_OVERSEAS_WEAK,
            routeMode = AppRouteMode.FULL,
            connectionScenario = ConnectionScenario.AUTO,
        )

    @Test
    fun staleSnapshotWithoutService_doesNotRestore() {
        assertFalse(
            VpnForegroundRestorePolicy.shouldScheduleForegroundRestore(
                serviceRunning = false,
                snapshot = staleSnapshot,
                autoReconnectEnabled = true,
                userInitiatedDisconnect = false,
                connectionState = ConnectionState.DISCONNECTED,
            ),
        )
    }

    @Test
    fun serviceRunningWithSnapshot_restoresWhenDisconnected() {
        assertTrue(
            VpnForegroundRestorePolicy.shouldScheduleForegroundRestore(
                serviceRunning = true,
                snapshot = staleSnapshot,
                autoReconnectEnabled = true,
                userInitiatedDisconnect = false,
                connectionState = ConnectionState.DISCONNECTED,
            ),
        )
    }

    @Test
    fun noSnapshot_neverRestores() {
        assertFalse(
            VpnForegroundRestorePolicy.shouldScheduleForegroundRestore(
                serviceRunning = true,
                snapshot = null,
                autoReconnectEnabled = true,
                userInitiatedDisconnect = false,
                connectionState = ConnectionState.DISCONNECTED,
            ),
        )
    }

    @Test
    fun userDisconnect_neverRestores() {
        assertFalse(
            VpnForegroundRestorePolicy.shouldScheduleForegroundRestore(
                serviceRunning = true,
                snapshot = staleSnapshot,
                autoReconnectEnabled = true,
                userInitiatedDisconnect = true,
                connectionState = ConnectionState.DISCONNECTED,
            ),
        )
    }

    @Test
    fun autoReconnectDisabled_neverRestores() {
        assertFalse(
            VpnForegroundRestorePolicy.shouldScheduleForegroundRestore(
                serviceRunning = true,
                snapshot = staleSnapshot,
                autoReconnectEnabled = false,
                userInitiatedDisconnect = false,
                connectionState = ConnectionState.DISCONNECTED,
            ),
        )
    }

    @Test
    fun alreadyConnected_neverRestores() {
        assertFalse(
            VpnForegroundRestorePolicy.shouldScheduleForegroundRestore(
                serviceRunning = true,
                snapshot = staleSnapshot,
                autoReconnectEnabled = true,
                userInitiatedDisconnect = false,
                connectionState = ConnectionState.CONNECTED,
            ),
        )
    }

    @Test
    fun failedWithSnapshot_restoresEvenIfServiceStopped() {
        assertTrue(
            VpnForegroundRestorePolicy.shouldScheduleForegroundRestore(
                serviceRunning = false,
                snapshot = staleSnapshot,
                autoReconnectEnabled = true,
                userInitiatedDisconnect = false,
                connectionState = ConnectionState.FAILED,
            ),
        )
    }

    @Test
    fun failedWithoutSnapshot_neverRestores() {
        assertFalse(
            VpnForegroundRestorePolicy.shouldScheduleForegroundRestore(
                serviceRunning = false,
                snapshot = null,
                autoReconnectEnabled = true,
                userInitiatedDisconnect = false,
                connectionState = ConnectionState.FAILED,
            ),
        )
    }
}
