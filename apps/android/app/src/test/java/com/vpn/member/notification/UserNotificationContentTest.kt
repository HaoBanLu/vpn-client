package com.vpn.member.notification

import com.vpn.member.data.session.RechargeStatusChange
import com.vpn.member.data.session.SessionInvalidation
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class UserNotificationContentTest {
    @Test
    fun sessionRevoked_mapsToAccountSecurityChannel() {
        val payload =
            UserNotificationContent.forSessionInvalidation(
                SessionInvalidation("请重新登录", "SESSION_REVOKED"),
            )
        assertEquals(UserNotificationChannels.ACCOUNT_SECURITY, payload.channelId)
        assertEquals("登录状态已失效", payload.title)
        assertTrue(payload.persistForLoginBanner)
        assertTrue(payload.killSwitchSubtitle!!.contains("登录状态"))
    }

    @Test
    fun loginOnAnotherDevice_mapsCorrectCopy() {
        val payload =
            UserNotificationContent.forSessionInvalidation(
                SessionInvalidation("账号已在其他设备登录", "LOGIN_ON_ANOTHER_DEVICE"),
            )
        assertEquals("账号在其他设备登录", payload.title)
        assertEquals("auth:LOGIN_ON_ANOTHER_DEVICE", payload.dedupeKey)
    }

    @Test
    fun forceDisconnect_subscriptionExpired() {
        val payload = UserNotificationContent.forForceDisconnect("subscription_expired")
        assertEquals(UserNotificationChannels.ACCOUNT_STATUS, payload.channelId)
        assertEquals("套餐已到期", payload.title)
    }

    @Test
    fun rechargePaid_includesOrderNo() {
        val payload =
            UserNotificationContent.forRechargeChange(
                RechargeStatusChange(orderId = 42L, orderNo = "RO-001", status = "paid"),
            )
        assertEquals(UserNotificationChannels.ORDER_FINANCE, payload.channelId)
        assertTrue(payload.body.contains("RO-001"))
    }
}
