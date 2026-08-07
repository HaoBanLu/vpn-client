package com.vpn.member.data.api

import com.google.gson.Gson
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Test

class SubscriptionApiModelsTest {
    private val gson = Gson()

    @Test
    fun activeSubscriptionDataParsesWrappedResponse() {
        val json =
            """
            {
              "subscription": {
                "id": 42,
                "status": "active",
                "expires_at": "2026-09-05T21:20:00Z",
                "traffic_total_gb": 110,
                "traffic_used_gb": 12.5,
                "package_id": 6,
                "package": {
                  "name": "单月VIP会员",
                  "level": 3,
                  "traffic_gb": 110,
                  "duration_days": 30,
                  "bandwidth_limit_mbps": 5
                },
                "bandwidth_limit_mbps": 5
              },
              "effective_bandwidth_mbps": 5,
              "bandwidth_display": "5 Mbps"
            }
            """.trimIndent()

        val data = gson.fromJson(json, ActiveSubscriptionData::class.java)

        assertNotNull(data.subscription)
        assertEquals(42L, data.subscription?.id)
        assertEquals("单月VIP会员", data.subscription?.`package`?.name)
        assertEquals(5, data.effective_bandwidth_mbps)
        assertEquals("5 Mbps", data.bandwidth_display)
    }
}
