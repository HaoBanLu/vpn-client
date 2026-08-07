package com.vpn.member.ui

import android.content.Context
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import androidx.test.uiautomator.By
import androidx.test.uiautomator.UiDevice
import androidx.test.uiautomator.Until
import com.vpn.member.InstrumentedTestSupport
import com.vpn.member.data.local.AppPreferences
import com.vpn.member.notification.UserNotificationCoordinator
import com.vpn.member.vpn.PrivacyOnboardingStore
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith

/**
 * 被挤下线：除通知栏外，回到 App 必须有界面对话框。
 */
@RunWith(AndroidJUnit4::class)
class SessionKickDialogInstrumentedTest {
    private lateinit var context: Context
    private lateinit var device: UiDevice

    @Before
    fun setUp() {
        context = InstrumentedTestSupport.appContext()
        device = UiDevice.getInstance(InstrumentationRegistry.getInstrumentation())
        InstrumentedTestSupport.clearAppState(context)
        AppPreferences(context).setPrivacyAccepted()
        PrivacyOnboardingStore(context).markCompleted(skippedSystemHardening = true)
    }

    @Test
    fun coldStart_showsSessionInvalidationDialogFromPersistedStore() {
        val store = UserNotificationCoordinator.lastInvalidationStore()
        runBlocking {
            store.save(
                title = "登录状态已失效",
                message = "账号已在其他设备登录，请重新登录",
                appCode = "SESSION_REVOKED",
            )
        }

        InstrumentedTestSupport.launchMainActivity(context)

        assertTrue(
            "应弹出「登录状态已失效」对话框（不能只靠通知栏）",
            device.wait(Until.hasObject(By.text("登录状态已失效")), 15_000),
        )
        assertTrue(
            "对话框应展示挤下线原因",
            device.wait(Until.hasObject(By.textContains("其他设备")), 5_000),
        )
        assertTrue(
            "应有确认按钮",
            device.hasObject(By.text("知道了")),
        )
    }
}
