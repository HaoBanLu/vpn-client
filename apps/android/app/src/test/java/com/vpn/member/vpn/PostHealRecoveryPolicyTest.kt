package com.vpn.member.vpn

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class PostHealRecoveryPolicyTest {
    @Test
    fun healProbe_offline_neverEscalatesReconnect() {
        assertFalse(
            PostHealRecoveryPolicy.shouldReconnectAfterHeal(ProbeStatus.FAILED, physicalOnline = false),
        )
        assertFalse(
            PostHealRecoveryPolicy.shouldReconnectAfterHeal(ProbeStatus.DEGRADED, physicalOnline = false),
        )
    }

    @Test
    fun healProbe_online_failedOrDegraded_escalatesReconnect() {
        assertTrue(PostHealRecoveryPolicy.shouldReconnectAfterHeal(ProbeStatus.FAILED, true))
        assertTrue(PostHealRecoveryPolicy.shouldReconnectAfterHeal(ProbeStatus.DEGRADED, true))
        assertFalse(PostHealRecoveryPolicy.shouldReconnectAfterHeal(ProbeStatus.OK, true))
        assertFalse(PostHealRecoveryPolicy.shouldReconnectAfterHeal(ProbeStatus.IDLE, true))
    }

    @Test
    fun healthFailStreak_resetsWhenOffline() {
        assertEquals(
            0,
            PostHealRecoveryPolicy.nextHealthFailStreak(ProbeStatus.FAILED, 5, physicalOnline = false),
        )
    }

    @Test
    fun healthFailStreak_countsOnlyWhenOnline() {
        assertEquals(1, PostHealRecoveryPolicy.nextHealthFailStreak(ProbeStatus.FAILED, 0, true))
        assertEquals(2, PostHealRecoveryPolicy.nextHealthFailStreak(ProbeStatus.DEGRADED, 1, true))
        assertEquals(0, PostHealRecoveryPolicy.nextHealthFailStreak(ProbeStatus.OK, 2, true))
    }

    @Test
    fun healthStreak_triggersReconnectOnlyWhenOnline() {
        assertFalse(PostHealRecoveryPolicy.shouldReconnectOnHealthStreak(2, physicalOnline = false))
        assertTrue(PostHealRecoveryPolicy.shouldReconnectOnHealthStreak(2, physicalOnline = true))
        assertFalse(PostHealRecoveryPolicy.shouldReconnectOnHealthStreak(1, physicalOnline = true))
    }

    @Test
    fun autoReconnect_waitsWithoutPhysicalNet() {
        assertFalse(PostHealRecoveryPolicy.shouldProceedAutoReconnect(physicalOnline = false))
        assertTrue(PostHealRecoveryPolicy.shouldProceedAutoReconnect(physicalOnline = true))
    }

    @Test
    fun networkRecovery_vpnPathGatesReconnect() {
        // mixed SLOW/OK 但系统 VPN 不通 → 必须重连（今日 luban7733 假恢复场景）
        assertTrue(
            PostHealRecoveryPolicy.shouldReconnectAfterNetworkRecovery(
                physicalOnline = true,
                vpnNetworkOk = false,
                mixedProbeStatus = ProbeStatus.SLOW,
            ),
        )
        assertTrue(
            PostHealRecoveryPolicy.shouldReconnectAfterNetworkRecovery(
                physicalOnline = true,
                vpnNetworkOk = false,
                mixedProbeStatus = ProbeStatus.OK,
            ),
        )
        // 系统 VPN 真通 + mixed 正常/慢 → 不重连
        assertFalse(
            PostHealRecoveryPolicy.shouldReconnectAfterNetworkRecovery(
                physicalOnline = true,
                vpnNetworkOk = true,
                mixedProbeStatus = ProbeStatus.SLOW,
            ),
        )
        assertFalse(
            PostHealRecoveryPolicy.shouldReconnectAfterNetworkRecovery(
                physicalOnline = true,
                vpnNetworkOk = true,
                mixedProbeStatus = ProbeStatus.OK,
            ),
        )
        // mixed 明确失败：双保险仍重连
        assertTrue(
            PostHealRecoveryPolicy.shouldReconnectAfterNetworkRecovery(
                physicalOnline = true,
                vpnNetworkOk = true,
                mixedProbeStatus = ProbeStatus.FAILED,
            ),
        )
        // 没物理网：不重连
        assertFalse(
            PostHealRecoveryPolicy.shouldReconnectAfterNetworkRecovery(
                physicalOnline = false,
                vpnNetworkOk = false,
                mixedProbeStatus = ProbeStatus.FAILED,
            ),
        )
    }

    @Test
    fun settleAndProbeConstants_reasonable() {
        assertEquals(2_000L, PostHealRecoveryPolicy.SETTLE_AFTER_HEAL_MS)
        assertEquals(2, PostHealRecoveryPolicy.POST_HEAL_PROBE_ATTEMPTS)
        assertEquals(2, PostHealRecoveryPolicy.HEALTH_FAIL_STREAK_TO_RECONNECT)
    }
}
