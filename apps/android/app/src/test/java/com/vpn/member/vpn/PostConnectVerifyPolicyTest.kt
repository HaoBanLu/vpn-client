package com.vpn.member.vpn

import org.junit.Assert.assertEquals
import org.junit.Test

class PostConnectVerifyPolicyTest {
    @Test
    fun backgroundVerify_alwaysDefault() {
        val p =
            PostConnectVerifyPolicy.resolve(
                initialConnectPhase = false,
                domesticReturn = true,
                overseasTimezone = true,
            )
        assertEquals(PostConnectVerifyPolicy.DEFAULT, p)
    }

    @Test
    fun chinaDomesticReturn_initial_usesDefaultNotShortTwoAttempts() {
        val p =
            PostConnectVerifyPolicy.resolve(
                initialConnectPhase = true,
                domesticReturn = true,
                overseasTimezone = false,
            )
        assertEquals(3, p.maxAttempts)
        assertEquals(2_000L, p.retryDelayMs)
        assertEquals(400L, p.settleMs)
    }

    @Test
    fun overseasDomesticReturn_initial_getsPatientRetries() {
        val p =
            PostConnectVerifyPolicy.resolve(
                initialConnectPhase = true,
                domesticReturn = true,
                overseasTimezone = true,
            )
        assertEquals(PostConnectVerifyPolicy.OVERSEAS_DOMESTIC_RETURN_INITIAL, p)
        assertEquals(4, p.maxAttempts)
        assertEquals(2_500L, p.retryDelayMs)
        assertEquals(1_200L, p.settleMs)
    }

    @Test
    fun overseasNonDomestic_initial_usesDefault() {
        val p =
            PostConnectVerifyPolicy.resolve(
                initialConnectPhase = true,
                domesticReturn = false,
                overseasTimezone = true,
            )
        assertEquals(PostConnectVerifyPolicy.DEFAULT, p)
    }
}
