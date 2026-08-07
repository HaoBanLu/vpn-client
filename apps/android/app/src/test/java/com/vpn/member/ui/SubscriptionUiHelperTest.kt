package com.vpn.member.ui

import com.google.gson.Gson
import com.vpn.member.data.api.SubscriptionActive
import com.vpn.member.data.api.SubscriptionUsage
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class SubscriptionUiHelperTest {
    private val gson = Gson()

    @Test
    fun formatExpiryDate_nullOrBlank_returnsPlaceholder() {
        assertEquals("—", formatExpiryDate(null))
        assertEquals("—", formatExpiryDate(""))
        assertEquals("—", formatExpiryDate("   "))
    }

    @Test
    fun formatExpiryDate_validIso_returnsDatePrefix() {
        assertEquals("2026-09-05", formatExpiryDate("2026-09-05T21:20:00Z"))
    }

    @Test
    fun trafficProgress_invalidNumbers_returnsZero() {
        assertEquals(0f, trafficProgress(SubscriptionUsage(1.0, 0.0, 0.0)))
        assertEquals(0f, trafficProgress(SubscriptionUsage(Double.NaN, 10.0, 0.0)))
        assertEquals(0f, trafficProgress(null))
    }

    @Test
    fun trafficProgress_validUsage_isClamped() {
        assertEquals(0.5f, trafficProgress(SubscriptionUsage(5.0, 10.0, 5.0)))
        assertEquals(1f, trafficProgress(SubscriptionUsage(20.0, 10.0, 0.0)))
    }

    @Test
    fun legacyWrongTopLevelParse_expiresAtMissing_formattersStaySafe() {
        // 回归：旧版把 /subscription/active 整包当 SubscriptionActive 解析时顶层无 expires_at
        val json =
            """
            {
              "subscription": {
                "id": 42,
                "status": "active",
                "expires_at": "2026-09-05T21:20:00Z",
                "traffic_total_gb": 110,
                "traffic_used_gb": 12.5
              },
              "effective_bandwidth_mbps": 5
            }
            """.trimIndent()
        @Suppress("UNCHECKED_CAST")
        val legacy = gson.fromJson(json, SubscriptionActive::class.java)
        val expiresAt: String? = legacy.expires_at
        assertEquals("—", formatExpiryDate(expiresAt))
        assertEquals(null, daysUntilExpiry(expiresAt))
    }
}
