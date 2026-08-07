package com.vpn.member.vpn

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class ExitIpProbeTest {
    @Test
    fun parseOwnApiResponse_success() {
        val body =
            """
            {"code":0,"message":"ok","data":{"ip":"61.171.98.25","country":"China","region":"Shanghai","city":"Shanghai"}}
            """.trimIndent()
        val info = ExitIpProbe.parseOwnApiResponse(body)
        assertEquals("61.171.98.25", info?.ip)
        assertEquals("China", info?.country)
        assertEquals("Shanghai", info?.region)
        assertEquals("Shanghai", info?.city)
    }

    @Test
    fun parseOwnApiResponse_nonZeroCode() {
        val body = """{"code":401,"message":"Unauthorized"}"""
        assertNull(ExitIpProbe.parseOwnApiResponse(body))
    }

    @Test
    fun parseIpApiComResponse_success() {
        val body =
            """
            {"status":"success","query":"1.2.3.4","country":"Singapore","regionName":"Central","city":"Singapore"}
            """.trimIndent()
        val info = ExitIpProbe.parseIpApiComResponse(body)
        assertEquals("1.2.3.4", info?.ip)
        assertEquals("Singapore", info?.country)
        assertEquals("Central", info?.region)
        assertEquals("Singapore", info?.city)
    }

    @Test
    fun parseIpSbResponse_success() {
        val body =
            """
            {"ip":"5.6.7.8","country":"Thailand","region":"Bangkok","city":"Bangkok"}
            """.trimIndent()
        val info = ExitIpProbe.parseIpSbResponse(body)
        assertEquals("5.6.7.8", info?.ip)
        assertEquals("Thailand", info?.country)
    }

    @Test
    fun parseCloudflareTrace_success() {
        val body =
            """
            fl=1024f
            h=www.cloudflare.com
            ip=9.9.9.9
            ts=123
            """.trimIndent()
        val info = ExitIpProbe.parseCloudflareTrace(body)
        assertEquals("9.9.9.9", info?.ip)
    }

    @Test
    fun parseIpifyResponse_success() {
        val body = """{"ip":"8.8.8.8"}"""
        val info = ExitIpProbe.parseIpifyResponse(body)
        assertEquals("8.8.8.8", info?.ip)
    }
}
