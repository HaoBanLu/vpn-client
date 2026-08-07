package com.vpn.member.vpn.mihomo

import com.vpn.member.vpn.ConnectionState
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class MihomoSuspendPolicyTest {
    @Test
    fun screenOn_neverSuspend() {
        assertFalse(
            MihomoSuspendPolicy.shouldSuspendCore(
                screenOff = false,
                state = ConnectionState.CONNECTED,
            ),
        )
        assertFalse(
            MihomoSuspendPolicy.shouldSuspendCore(
                screenOff = false,
                state = ConnectionState.DISCONNECTED,
            ),
        )
    }

    @Test
    fun screenOff_whileConnected_keepsCoreRunning() {
        assertFalse(
            MihomoSuspendPolicy.shouldSuspendCore(
                screenOff = true,
                state = ConnectionState.CONNECTED,
            ),
        )
        assertFalse(
            MihomoSuspendPolicy.shouldSuspendCore(
                screenOff = true,
                state = ConnectionState.CONNECTING,
            ),
        )
    }

    @Test
    fun screenOff_whileIdle_allowsSuspend() {
        assertTrue(
            MihomoSuspendPolicy.shouldSuspendCore(
                screenOff = true,
                state = ConnectionState.DISCONNECTED,
            ),
        )
        assertTrue(
            MihomoSuspendPolicy.shouldSuspendCore(
                screenOff = true,
                state = ConnectionState.FAILED,
            ),
        )
    }
}
