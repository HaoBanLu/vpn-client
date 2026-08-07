package com.vpn.member.vpn.mihomo

import org.junit.Assert.assertEquals
import org.junit.Test

class MihomoGeoAssetPolicyTest {
    @Test
    fun policyForRouteMode_mapsSplitAndFull() {
        assertEquals(GeoAssetPolicy.FULL_TUNNEL, MihomoGeoAssetManager.policyForRouteMode(routeModeSplit = false))
        assertEquals(GeoAssetPolicy.SPLIT_ROUTING, MihomoGeoAssetManager.policyForRouteMode(routeModeSplit = true))
    }
}
