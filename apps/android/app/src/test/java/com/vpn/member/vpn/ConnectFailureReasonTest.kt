package com.vpn.member.vpn

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class ConnectFailureReasonTest {
    @Test
    fun lineAndNodeIssuesAreDistinct() {
        assertTrue(ConnectFailureReason.LINE_HELD_CONFLICT.isLineIssue())
        assertFalse(ConnectFailureReason.LINE_HELD_CONFLICT.isNodeIssue())
        assertTrue(ConnectFailureReason.NODE_UNREACHABLE.isNodeIssue())
        assertFalse(ConnectFailureReason.NODE_UNREACHABLE.isLineIssue())
    }

    @Test
    fun probeCauseMapsToConnectFailureReason() {
        assertEquals(
            ConnectFailureReason.NODE_UNREACHABLE,
            ProbeFailureCause.PROXY_UNREACHABLE.toConnectFailureReason(),
        )
        assertEquals(
            ConnectFailureReason.PROBE_NO_VPN,
            ProbeFailureCause.VPN_NOT_UP.toConnectFailureReason(),
        )
    }

    @Test
    fun userMessageDoesNotMentionLineForNodeUnreachable() {
        val msg = ConnectFailureReason.NODE_UNREACHABLE.userMessage("贵州")
        assertTrue(msg.contains("节点不可达"))
        assertFalse(msg.contains("占线"))
    }

    @Test
    fun userMessageMentionsUnavailableForLineConflict() {
        val msg = ConnectFailureReason.LINE_HELD_CONFLICT.userMessage()
        assertTrue(msg.contains("不可用"))
    }
}
