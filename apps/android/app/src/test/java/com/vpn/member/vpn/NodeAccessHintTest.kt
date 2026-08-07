package com.vpn.member.vpn

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class NodeAccessHintTest {
    @Test
    fun poolLabel() {
        assertEquals("回国专线", NodeAccessHint.poolLabel("relay"))
        assertEquals("海外直连", NodeAccessHint.poolLabel("direct"))
    }

    @Test
    fun scenarioMismatchHint() {
        assertNull(NodeAccessHint.scenarioMismatchHint(ConnectionScenario.RETURN_HOME, "relay"))
        assertEquals(
            "芜湖/武汉等为「回国专线」，缅甸/海外访问外网请选新加坡、香港等「海外直连」节点。",
            NodeAccessHint.scenarioMismatchHint(ConnectionScenario.OVERSEAS, "relay"),
        )
    }
}
