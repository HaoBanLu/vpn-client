package com.vpn.member.vpn

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class AutoReconnectPrepPolicyTest {
    @Test
    fun allowCache_whenApiFailedAndHasYaml() {
        assertTrue(AutoReconnectPrepPolicy.allowCachedConfigFallback(apiFailed = true, hasCachedConfig = true))
        assertFalse(AutoReconnectPrepPolicy.allowCachedConfigFallback(apiFailed = true, hasCachedConfig = false))
        assertFalse(AutoReconnectPrepPolicy.allowCachedConfigFallback(apiFailed = false, hasCachedConfig = true))
    }

    @Test
    fun holdKs_onlyAfterPrepWhenTunnelLive() {
        assertTrue(
            AutoReconnectPrepPolicy.shouldHoldKillSwitchAfterPrep(
                holdEnabled = true,
                escalateFromLiveTunnel = true,
                tunnelLive = true,
            ),
        )
        assertFalse(
            AutoReconnectPrepPolicy.shouldHoldKillSwitchAfterPrep(
                holdEnabled = true,
                escalateFromLiveTunnel = true,
                tunnelLive = false,
            ),
        )
        assertFalse(
            AutoReconnectPrepPolicy.shouldHoldKillSwitchAfterPrep(
                holdEnabled = false,
                escalateFromLiveTunnel = true,
                tunnelLive = true,
            ),
        )
    }
}
