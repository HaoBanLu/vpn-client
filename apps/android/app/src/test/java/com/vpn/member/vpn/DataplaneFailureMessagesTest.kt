package com.vpn.member.vpn

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class DataplaneFailureMessagesTest {
    @Test
    fun abroadDomesticReturn_degraded_mentionsOverseasMode() {
        val msg =
            DataplaneFailureMessages.degradedDisconnect(
                nodeName = "安徽芜湖1",
                domesticReturn = true,
                tunStackRetried = false,
                overseasTimezone = true,
            )
        assertTrue(msg.contains("海外模式"))
        assertFalse(msg.contains("gvisor"))
    }

    @Test
    fun chinaDomesticReturn_degraded_noGvisorHint() {
        val msg =
            DataplaneFailureMessages.degradedDisconnect(
                nodeName = "安徽芜湖1",
                domesticReturn = true,
                tunStackRetried = false,
                overseasTimezone = false,
            )
        assertTrue(msg.contains("回国节点"))
        assertFalse(msg.contains("gvisor"))
    }

    @Test
    fun overseasWeak_degraded_suggestsGvisorWhenStackNotRetried() {
        val msg =
            DataplaneFailureMessages.degradedDisconnect(
                nodeName = "新加坡",
                domesticReturn = false,
                tunStackRetried = false,
                overseasTimezone = true,
            )
        assertTrue(msg.contains("gvisor"))
    }

    @Test
    fun abroadDomesticReturn_tunnelVerify_mentionsOverseasMode() {
        val msg =
            DataplaneFailureMessages.tunnelVerifyFailed(
                domesticReturn = true,
                overseasTimezone = true,
                nodeName = "芜湖1",
            )
        assertTrue(msg.contains("海外模式"))
    }
}
