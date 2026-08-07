package com.vpn.member.vpn

import org.junit.Assert.assertEquals
import org.junit.Test

class ClashRouteTargetTest {
    @Test
    fun resolve_prefersConfigNode() {
        val got =
            ClashRouteTarget.resolve(
                configNode = "贵州",
                configYaml = "",
                selectedNode = "新加坡",
            )
        assertEquals("贵州", got)
    }

    @Test
    fun resolve_fallsBackToSelectedNode() {
        val got =
            ClashRouteTarget.resolve(
                configNode = null,
                configYaml = "",
                selectedNode = "新加坡",
            )
        assertEquals("新加坡", got)
    }
}
