package com.vpn.member.vpn

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class VpnFailedRecoveryPolicyTest {
    private val snapshot =
        VpnSessionSnapshot(
            wasUserConnected = true,
            nodeName = "新加坡1",
            region = "sg",
            profile = ConnectionScenario.PROFILE_OVERSEAS_WEAK,
            routeMode = AppRouteMode.FULL,
            connectionScenario = ConnectionScenario.AUTO,
        )

    @Test
    fun connectedToFailed_schedulesWhenOnline() {
        assertTrue(
            VpnFailedRecoveryPolicy.shouldScheduleAfterUnexpectedFailed(
                previousState = ConnectionState.CONNECTED,
                newState = ConnectionState.FAILED,
                userInitiatedDisconnect = false,
                autoReconnectEnabled = true,
                snapshot = snapshot,
                physicalOnline = true,
            ),
        )
    }

    @Test
    fun connectingToFailed_schedules() {
        assertTrue(
            VpnFailedRecoveryPolicy.shouldScheduleAfterUnexpectedFailed(
                previousState = ConnectionState.CONNECTING,
                newState = ConnectionState.FAILED,
                userInitiatedDisconnect = false,
                autoReconnectEnabled = true,
                snapshot = snapshot,
                physicalOnline = true,
            ),
        )
    }

    @Test
    fun userDisconnect_neverSchedules() {
        assertFalse(
            VpnFailedRecoveryPolicy.shouldScheduleAfterUnexpectedFailed(
                previousState = ConnectionState.CONNECTED,
                newState = ConnectionState.FAILED,
                userInitiatedDisconnect = true,
                autoReconnectEnabled = true,
                snapshot = snapshot,
                physicalOnline = true,
            ),
        )
    }

    @Test
    fun offline_neverSchedules() {
        assertFalse(
            VpnFailedRecoveryPolicy.shouldScheduleAfterUnexpectedFailed(
                previousState = ConnectionState.CONNECTED,
                newState = ConnectionState.FAILED,
                userInitiatedDisconnect = false,
                autoReconnectEnabled = true,
                snapshot = snapshot,
                physicalOnline = false,
            ),
        )
    }

    @Test
    fun noSnapshot_neverSchedules() {
        assertFalse(
            VpnFailedRecoveryPolicy.shouldScheduleAfterUnexpectedFailed(
                previousState = ConnectionState.CONNECTED,
                newState = ConnectionState.FAILED,
                userInitiatedDisconnect = false,
                autoReconnectEnabled = true,
                snapshot = null,
                physicalOnline = true,
            ),
        )
    }

    @Test
    fun disconnectedToFailed_doesNotSchedule() {
        assertFalse(
            VpnFailedRecoveryPolicy.shouldScheduleAfterUnexpectedFailed(
                previousState = ConnectionState.DISCONNECTED,
                newState = ConnectionState.FAILED,
                userInitiatedDisconnect = false,
                autoReconnectEnabled = true,
                snapshot = snapshot,
                physicalOnline = true,
            ),
        )
    }
}
