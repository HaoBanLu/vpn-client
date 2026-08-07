package com.vpn.member.vpn

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class PrivacyDisconnectPolicyTest {
    @Test
    fun engagesKillSwitchOnAuthDisconnect() {
        assertTrue(
            PrivacyDisconnectPolicy.shouldEngageKillSwitch(
                userInitiatedDisconnect = false,
                killSwitchEnabled = true,
            ),
        )
    }

    @Test
    fun skipsKillSwitchOnUserDisconnect() {
        assertFalse(
            PrivacyDisconnectPolicy.shouldEngageKillSwitch(
                userInitiatedDisconnect = true,
                killSwitchEnabled = true,
            ),
        )
    }

    @Test
    fun skipsKillSwitchWhenDisabled() {
        assertFalse(
            PrivacyDisconnectPolicy.shouldEngageKillSwitch(
                userInitiatedDisconnect = false,
                killSwitchEnabled = false,
            ),
        )
    }

    @Test
    fun holdKillSwitchDuringReconnect_requiresBothFlags() {
        assertTrue(
            PrivacyDisconnectPolicy.shouldHoldKillSwitchDuringReconnect(
                killSwitchEnabled = true,
                reconnectHoldEnabled = true,
            ),
        )
        assertFalse(
            PrivacyDisconnectPolicy.shouldHoldKillSwitchDuringReconnect(
                killSwitchEnabled = true,
                reconnectHoldEnabled = false,
            ),
        )
        assertFalse(
            PrivacyDisconnectPolicy.shouldHoldKillSwitchDuringReconnect(
                killSwitchEnabled = false,
                reconnectHoldEnabled = true,
            ),
        )
    }
}
