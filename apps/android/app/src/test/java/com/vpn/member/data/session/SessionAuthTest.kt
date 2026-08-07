package com.vpn.member.data.session

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class SessionAuthTest {
    @Test
    fun shouldInvalidate_whenBearer401OnProtectedEndpoint() {
        assertTrue(
            SessionAuth.shouldInvalidateSession(
                path = "/api/v1/users/me",
                hadAuth = true,
                appCode = null,
            ),
        )
    }

    @Test
    fun shouldNotInvalidate_whenLogin401() {
        assertFalse(
            SessionAuth.shouldInvalidateSession(
                path = "/api/v1/auth/login",
                hadAuth = true,
                appCode = null,
            ),
        )
    }

    @Test
    fun shouldInvalidate_whenSessionRevokedAppCode() {
        assertTrue(
            SessionAuth.shouldInvalidateSession(
                path = "/api/v1/auth/login",
                hadAuth = false,
                appCode = "SESSION_REVOKED",
            ),
        )
    }

    @Test
    fun mapsInvalidTokenMessageToChinese() {
        assertEquals(
            "登录状态已失效，请重新登录",
            SessionAuth.sessionInvalidationMessage("""{"code":401,"message":"Invalid token"}""", null),
        )
    }

    @Test
    fun mapsLoginOnAnotherDeviceMessage() {
        val body = """{"code":401,"message":"账号已在其他设备登录，请重新登录","app_code":"LOGIN_ON_ANOTHER_DEVICE"}"""
        assertEquals(
            "账号已在其他设备登录，请重新登录",
            SessionAuth.sessionInvalidationMessage(body, "LOGIN_ON_ANOTHER_DEVICE"),
        )
    }
}
