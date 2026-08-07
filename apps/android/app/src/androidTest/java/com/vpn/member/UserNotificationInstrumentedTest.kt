package com.vpn.member

import android.app.NotificationManager
import android.os.Build
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.rule.GrantPermissionRule
import com.vpn.member.data.session.SessionEvents
import com.vpn.member.notification.UserNotificationCoordinator
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class UserNotificationInstrumentedTest {
    @get:Rule
    val notificationPermission =
        GrantPermissionRule.grant(android.Manifest.permission.POST_NOTIFICATIONS)

    @Before
    fun setUp() {
        val context = InstrumentedTestSupport.appContext()
        InstrumentedTestSupport.clearAppState(context)
        val app = context.applicationContext as VpnMemberApp
        UserNotificationCoordinator.start(app, CoroutineScope(Dispatchers.Main))
    }

    @Test
    fun sessionInvalidation_postsAccountSecurityNotification() {
        val context = InstrumentedTestSupport.appContext()

        SessionEvents.publish("登录状态已失效，请重新登录", "SESSION_REVOKED")
        Thread.sleep(800)

        val manager = context.getSystemService(NotificationManager::class.java)
        val posted = manager.activeNotifications.any { it.id == 20_001 }
        assertTrue("会话失效应发出账户安全通知", posted)
    }

    @Test
    fun lastInvalidationStore_persistsForLoginBanner() {
        val store = UserNotificationCoordinator.lastInvalidationStore()
        runBlocking {
            store.save("登录状态已失效", "请重新登录后再次连接", "SESSION_REVOKED")
            val pending = store.consume()
            assertNotNull(pending)
            assertTrue(pending!!.message.contains("重新登录"))
            assertNull(store.peek())
        }
    }

    @Test
    fun sessionInvalidation_persistsForInAppDialogPeek() {
        SessionEvents.publish("账号已在其他设备登录", "SESSION_REVOKED")
        Thread.sleep(800)

        val store = UserNotificationCoordinator.lastInvalidationStore()
        val pending = runBlocking { store.peek() }
        assertNotNull("被挤下线应持久化，供冷启动/回前台弹窗", pending)
        assertTrue(pending!!.message.contains("其他设备") || pending.message.contains("重新登录"))
        // peek 不消费，登录页 banner 与对话框可同时读到
        assertNotNull(runBlocking { store.peek() })
    }
}
