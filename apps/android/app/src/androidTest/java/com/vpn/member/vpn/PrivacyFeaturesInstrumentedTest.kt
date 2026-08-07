package com.vpn.member.vpn

import android.content.Context
import android.content.Intent
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import androidx.test.uiautomator.By
import androidx.test.uiautomator.UiDevice
import androidx.test.uiautomator.Until
import com.vpn.member.InstrumentedTestSupport
import com.vpn.member.MainActivity
import com.vpn.member.data.local.AppPreferences
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith

/**
 * 模拟器/真机仪器化验收：隐私基线、登录后 UI、鉴权门控。
 * 依赖本机 Docker API（模拟器访问 10.0.2.2:48080）。
 */
@RunWith(AndroidJUnit4::class)
class PrivacyFeaturesInstrumentedTest {
    private lateinit var context: Context
    private lateinit var device: UiDevice

    @Before
    fun setUp() {
        context = ApplicationProvider.getApplicationContext()
        device = UiDevice.getInstance(InstrumentationRegistry.getInstrumentation())
        InstrumentedTestSupport.clearAppState(context)
    }

    @Test
    fun privacyBaselineDefaultsAfterMigration() {
        val prefs = AppPreferences(context)
        assertTrue("Kill Switch 应默认开启", prefs.isKillSwitchEnabled())
        assertTrue("IPv6 防护应默认开启", prefs.isIpv6LeakProtectionEnabled())
        assertTrue("重连期阻断应默认开启", prefs.isReconnectKillSwitchHoldEnabled())
        assertFalse("连接失败阻断应默认关闭", prefs.isBlockOnConnectFailureEnabled())
        assertTrue("自动重连应默认开启", prefs.isAutoReconnectEnabled())
        assertEquals(
            PrivacyBaselineMigrator.CURRENT_VERSION,
            prefs.getPrivacyBaselineVersion(),
        )
    }

    @Test
    fun vpnAuthGateBlocksWithoutLogin() {
        assertFalse(VpnAuthGate.isLoggedIn(context))
        // 崩溃恢复入口：未登录时不应拉起 VPN Service
        VpnCrashRecovery.scheduleRestoreIfNeeded(context)
        assertFalse(VpnAuthGate.isLoggedIn(context))
    }

    @Test
    fun privacyOnboardingDisabledByDefault() {
        val store = PrivacyOnboardingStore(context)
        assertFalse("隐私引导应默认关闭，基线静默开启", store.shouldShowOnboarding())
        store.markCompleted(skippedSystemHardening = true)
        assertFalse(store.shouldShowOnboarding())
        assertTrue(store.hasSkippedSystemHardening())
    }

    @Test
    fun loggedInUserSeesPrivacySettingsScreen() {
        InstrumentedTestSupport.prepareLoggedInSession(context)

        val intent =
            Intent(context, MainActivity::class.java).apply {
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            }
        context.startActivity(intent)

        assertTrue(
            "应进入主界面",
            device.wait(Until.hasObject(By.text("我的")), 15_000),
        )
        device.findObject(By.text("我的")).click()
        assertTrue(
            "我的页应显示连接与隐私",
            device.wait(Until.hasObject(By.textContains("连接与隐私")), 8_000),
        )
        device.findObject(By.textContains("连接与隐私")).click()
        assertTrue(
            "未连接时应显示隐私保护已就绪",
            device.wait(Until.hasObject(By.textContains("隐私保护已就绪")), 8_000),
        )
        assertTrue(
            "应有默认保护卡片",
            device.hasObject(By.textContains("默认保护")),
        )
        assertTrue(
            "应有系统级加固区块",
            device.hasObject(By.textContains("系统级加固")),
        )
        assertTrue(
            "连接与隐私页应展示断网保护",
            device.hasObject(By.textContains("断网保护")),
        )
        assertTrue(
            device.hasObject(By.textContains("IPv6 防泄露")),
        )
        assertTrue(
            "未连接时隐私检测应不可用",
            device.hasObject(By.textContains("请先连接 VPN 后再检测")),
        )
    }
}
