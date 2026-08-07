package com.vpn.member.vpn

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class NetworkRestorePolicyTest {
    @Test
    fun connected_networkRestored_schedulesReconnect() {
        // 已连接也完整重连：避免 HEAL+探测假恢复（luban7733 多次踩坑）
        assertEquals(
            NetworkRestoreAction.SCHEDULE_RECONNECT,
            NetworkRestorePolicy.decide(
                connectionState = ConnectionState.CONNECTED,
                userInitiatedDisconnect = false,
                autoReconnectEnabled = true,
            ),
        )
    }

    @Test
    fun disconnected_networkRestored_schedulesReconnect() {
        assertEquals(
            NetworkRestoreAction.SCHEDULE_RECONNECT,
            NetworkRestorePolicy.decide(
                connectionState = ConnectionState.DISCONNECTED,
                userInitiatedDisconnect = false,
                autoReconnectEnabled = true,
            ),
        )
    }

    @Test
    fun failed_networkRestored_schedulesReconnect() {
        assertEquals(
            NetworkRestoreAction.SCHEDULE_RECONNECT,
            NetworkRestorePolicy.decide(
                connectionState = ConnectionState.FAILED,
                userInitiatedDisconnect = false,
                autoReconnectEnabled = true,
            ),
        )
    }

    @Test
    fun connecting_networkRestored_schedulesReconnect_notStuck() {
        // 断网发生在连接过程中：恢复后应重试，不能空等 CONNECTING 直到超时
        assertEquals(
            NetworkRestoreAction.SCHEDULE_RECONNECT,
            NetworkRestorePolicy.decide(
                connectionState = ConnectionState.CONNECTING,
                userInitiatedDisconnect = false,
                autoReconnectEnabled = true,
            ),
        )
    }

    @Test
    fun userDisconnect_neverAutoReconnect() {
        for (state in ConnectionState.entries) {
            assertEquals(
                NetworkRestoreAction.NONE,
                NetworkRestorePolicy.decide(
                    connectionState = state,
                    userInitiatedDisconnect = true,
                    autoReconnectEnabled = true,
                ),
            )
        }
    }

    @Test
    fun autoReconnectDisabled_connected_healsOnly() {
        assertEquals(
            NetworkRestoreAction.HEAL,
            NetworkRestorePolicy.decide(
                connectionState = ConnectionState.CONNECTED,
                userInitiatedDisconnect = false,
                autoReconnectEnabled = false,
            ),
        )
        for (state in listOf(
            ConnectionState.DISCONNECTED,
            ConnectionState.FAILED,
            ConnectionState.CONNECTING,
        )) {
            assertEquals(
                NetworkRestoreAction.NONE,
                NetworkRestorePolicy.decide(
                    connectionState = state,
                    userInitiatedDisconnect = false,
                    autoReconnectEnabled = false,
                ),
            )
        }
    }

    @Test
    fun autoReconnectEnabled_neverReturnsHeal() {
        // 防回归：自动重连开时禁止 HEAL（否则又会「探测 OK 不重连」）
        for (state in ConnectionState.entries) {
            val action =
                NetworkRestorePolicy.decide(
                    connectionState = state,
                    userInitiatedDisconnect = false,
                    autoReconnectEnabled = true,
                )
            assertTrue(
                "state=$state must not HEAL when autoReconnect on, got $action",
                action == NetworkRestoreAction.SCHEDULE_RECONNECT,
            )
        }
    }

    @Test
    fun autoReconnectDisabled_disconnected_neverReconnectOrHealFromMonitor() {
        assertEquals(
            NetworkRestoreAction.NONE,
            NetworkRestorePolicy.decide(
                connectionState = ConnectionState.DISCONNECTED,
                userInitiatedDisconnect = false,
                autoReconnectEnabled = false,
            ),
        )
    }

    @Test
    fun transportChange_recoversWhenConnectedOrFailed() {
        assertTrue(NetworkRestorePolicy.shouldRecoverOnTransportChange(ConnectionState.CONNECTED))
        assertTrue(NetworkRestorePolicy.shouldRecoverOnTransportChange(ConnectionState.FAILED))
        assertFalse(NetworkRestorePolicy.shouldRecoverOnTransportChange(ConnectionState.CONNECTING))
        assertFalse(NetworkRestorePolicy.shouldRecoverOnTransportChange(ConnectionState.DISCONNECTED))
    }

    @Test
    fun reconnectDebounce_reasonable() {
        assertEquals(1_500L, NetworkRestorePolicy.RECONNECT_DEBOUNCE_MS)
    }

    @Test
    fun reconnectBackoff_matrix() {
        assertEquals(3_000L, VpnAutoReconnectPolicy.backoffDelayMs(0))
        assertEquals(6_000L, VpnAutoReconnectPolicy.backoffDelayMs(1))
        assertEquals(10_000L, VpnAutoReconnectPolicy.backoffDelayMs(2))
        assertEquals(3, VpnAutoReconnectPolicy.MAX_ATTEMPTS)
    }
}
