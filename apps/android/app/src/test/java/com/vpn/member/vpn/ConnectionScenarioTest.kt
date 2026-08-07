package com.vpn.member.vpn

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class ConnectionScenarioTest {
    @Test
    fun auto_withoutCnNode_usesOverseasWeak() {
        val got = ConnectionScenario.resolve(ConnectionScenario.AUTO, nodeRegion = "sg", accessMode = "direct")
        assertEquals(ConnectionScenario.PROFILE_OVERSEAS_WEAK, got.profile)
        assertEquals(AppRouteMode.FULL, got.routeMode)
    }

    @Test
    fun auto_withCnRegion_usesDomesticReturn() {
        val got = ConnectionScenario.resolve(ConnectionScenario.AUTO, nodeRegion = "cn", accessMode = "direct")
        assertEquals(ConnectionScenario.PROFILE_DOMESTIC_RETURN, got.profile)
    }

    @Test
    fun auto_withRelayNode_usesDomesticReturn() {
        val got = ConnectionScenario.resolve(ConnectionScenario.AUTO, nodeRegion = "sg", accessMode = "relay")
        assertEquals(ConnectionScenario.PROFILE_DOMESTIC_RETURN, got.profile)
    }

    @Test
    fun returnHome_usesDomesticReturn() {
        val got = ConnectionScenario.resolve(ConnectionScenario.RETURN_HOME)
        assertEquals(ConnectionScenario.PROFILE_DOMESTIC_RETURN, got.profile)
        assertEquals(AppRouteMode.FULL, got.routeMode)
    }

    @Test
    fun overseas_usesOverseasWeak() {
        val got = ConnectionScenario.resolve(ConnectionScenario.OVERSEAS)
        assertEquals(ConnectionScenario.PROFILE_OVERSEAS_WEAK, got.profile)
    }

    @Test
    fun inferDomesticReturnFromNode() {
        assertTrue(ConnectionScenario.inferDomesticReturnFromNode("cn", "direct"))
        assertTrue(ConnectionScenario.inferDomesticReturnFromNode(null, "relay"))
        assertFalse(ConnectionScenario.inferDomesticReturnFromNode("sg", "direct"))
    }
}
