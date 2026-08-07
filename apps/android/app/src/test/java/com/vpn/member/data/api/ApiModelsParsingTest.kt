package com.vpn.member.data.api

import com.google.gson.Gson
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class ApiModelsParsingTest {
    private val gson = Gson()

    @Test
    fun packageItemParsesBandwidthFieldFromProductionShape() {
        val json =
            """
            {
              "id": 6,
              "name": "单月VIP会员",
              "description": "测试",
              "price": 110,
              "traffic_gb": 110,
              "devices": 0,
              "duration_days": 30,
              "level": 3,
              "bandwidth_limit_mbps": 5,
              "status": "active"
            }
            """.trimIndent()

        val item = gson.fromJson(json, PackageItem::class.java)

        assertEquals(6L, item.id)
        assertEquals(5, item.bandwidth_limit_mbps)
        assertEquals(110.0, item.traffic_gb, 0.001)
    }

    @Test
    fun packagesDataParsesListWrapper() {
        val json =
            """
            {
              "packages": [
                {
                  "id": 9,
                  "name": "核心访问",
                  "price": 119.99,
                  "traffic_gb": 1200,
                  "duration_days": 90,
                  "level": 3,
                  "bandwidth_limit_mbps": 5
                }
              ]
            }
            """.trimIndent()

        val data = gson.fromJson(json, PackagesData::class.java)

        assertEquals(1, data.packages.size)
        assertEquals("核心访问", data.packages.first().name)
        assertEquals(5, data.packages.first().bandwidth_limit_mbps)
    }

    @Test
    fun clientConfigParsesBandwidthLimit() {
        val json =
            """
            {
              "format": "clash",
              "region": "sg",
              "node": "新加坡-BGP线路",
              "bandwidth_limit_mbps": 10,
              "config": "proxies: []"
            }
            """.trimIndent()

        val data = gson.fromJson(json, ClientConfigData::class.java)

        assertEquals(10, data.bandwidth_limit_mbps)
        assertTrue(data.config.contains("proxies"))
    }
}
