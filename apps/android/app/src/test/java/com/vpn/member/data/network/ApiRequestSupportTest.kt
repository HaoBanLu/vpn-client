package com.vpn.member.data.network

import com.vpn.member.data.repository.AppException
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.IOException
import java.net.ConnectException
import java.net.SocketTimeoutException
import java.net.UnknownHostException
import javax.net.ssl.SSLException

class ApiRequestSupportTest {
    @Test
    fun isRetryable_networkErrorsAreRetryable() {
        assertTrue(ApiRequestSupport.isRetryable(UnknownHostException("host")))
        assertTrue(ApiRequestSupport.isRetryable(ConnectException("connect")))
        assertTrue(ApiRequestSupport.isRetryable(SocketTimeoutException("timeout")))
        assertTrue(ApiRequestSupport.isRetryable(IOException("reset")))
    }

    @Test
    fun isRetryable_sslAndBusinessErrorsAreNotRetryable() {
        assertFalse(ApiRequestSupport.isRetryable(SSLException("cert")))
        assertFalse(
            ApiRequestSupport.isRetryable(
                AppException(userMessage = "邮箱或密码错误", appCode = "INVALID_CREDENTIALS"),
            ),
        )
    }

    @Test
    fun isRetryable_respectsBackendRetryableFlag() {
        assertTrue(
            ApiRequestSupport.isRetryable(
                AppException(userMessage = "服务繁忙", appCode = "SERVER_BUSY", retryable = true),
            ),
        )
    }

    @Test
    fun mapError_appendsTraceId() {
        val message =
            ApiRequestSupport.mapError(
                AppException(
                    userMessage = "加载失败",
                    appCode = "UPSTREAM_UNAVAILABLE",
                    traceId = "trace-abc",
                ),
                fallback = "失败",
            )
        assertTrue(message.contains("追踪ID: trace-abc"))
        assertTrue(message.contains("加载失败"))
    }

    @Test
    fun mapError_unknownHostUsesSpecificMessage() {
        val message = ApiRequestSupport.mapError(UnknownHostException("vpn.example.com"), fallback = "失败")
        assertEquals("无法解析服务器地址，请检查网络或接口域名配置", message)
    }

    @Test
    fun mapError_connectExceptionIncludesServerHint() {
        val message =
            ApiRequestSupport.mapError(
                ConnectException("refused"),
                fallback = "登录失败",
                serverBaseUrl = "http://192.229.87.112:44080/api/v1/",
            )
        assertTrue(message.contains("192.229.87.112:44080"))
        assertTrue(message.contains("无法连接服务器"))
    }

    @Test
    fun mapError_cleartextBlockedUsesExplicitMessage() {
        val message =
            ApiRequestSupport.mapError(
                IOException("Cleartext HTTP traffic to 192.229.87.112 not permitted by network security policy"),
                fallback = "登录失败",
                serverBaseUrl = "http://192.229.87.112:44080/api/v1/",
            )
        assertTrue(message.contains("HTTP 明文"))
        assertTrue(message.contains("192.229.87.112"))
    }

    @Test
    fun defaultRetryDelayMs_followsBackoffBaseline() {
        assertTrue(ApiRequestSupport.defaultRetryDelayMs(1) in 300L..500L)
        assertTrue(ApiRequestSupport.defaultRetryDelayMs(2) in 900L..1100L)
    }

    @Test
    fun withRetry_retriesOnceOnTransientFailure() = runBlocking {
        var calls = 0
        val result =
            ApiRequestSupport.withRetry(
                maxAttempts = 2,
                retryDelayMs = { 1L },
            ) {
                calls += 1
                if (calls == 1) throw SocketTimeoutException("timeout")
                "ok"
            }
        assertEquals("ok", result)
        assertEquals(2, calls)
    }

    @Test
    fun withRetry_doesNotRetryBusinessError() = runBlocking {
        var calls = 0
        val err =
            runCatching {
                ApiRequestSupport.withRetry(maxAttempts = 2, retryDelayMs = { 1L }) {
                    calls += 1
                    throw AppException(userMessage = "验证码错误", appCode = "INVALID_CODE")
                }
            }.exceptionOrNull()
        assertEquals(1, calls)
        assertTrue(err is AppException)
    }
}
