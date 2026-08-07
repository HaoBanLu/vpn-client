package com.vpn.member.data.network

import com.vpn.member.data.repository.AppException
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.net.UnknownHostException

class ApiErrorTelemetryTest {
    @After
    fun tearDown() {
        ApiErrorTelemetry.resetForTest()
    }

    @Test
    fun recordCountsRetryableFailures() {
        ApiErrorTelemetry.record("get_nodes", UnknownHostException("host"))
        ApiErrorTelemetry.record(
            "login",
            AppException("busy", appCode = "SERVER_BUSY", retryable = true),
        )
        val snap = ApiErrorTelemetry.snapshot()
        assertEquals(2, snap.totalFailures)
        assertEquals(2, snap.retryableFailures)
        assertEquals(2, snap.recent.size)
    }

    @Test
    fun recordIgnoresSuccess() {
        ApiErrorTelemetry.record("ok", IllegalStateException("x"), succeeded = true)
        assertEquals(0, ApiErrorTelemetry.snapshot().totalFailures)
    }

    @Test
    fun recentRingBufferCapsAt50() {
        repeat(55) { i ->
            ApiErrorTelemetry.record("ep_$i", UnknownHostException("h"), succeeded = false)
        }
        assertTrue(ApiErrorTelemetry.snapshot().recent.size <= 50)
    }
}
