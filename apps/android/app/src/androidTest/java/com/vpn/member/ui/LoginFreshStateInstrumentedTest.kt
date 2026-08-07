package com.vpn.member.ui

import android.content.Context
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import androidx.test.uiautomator.By
import androidx.test.uiautomator.UiDevice
import androidx.test.uiautomator.Until
import com.vpn.member.InstrumentedTestSupport
import com.vpn.member.vpn.VpnTunnelStateSync
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith

/**
 * 覆盖安装后残留会话快照时，登录进主界面不应误显示「已保护」。
 * 依赖本机 Docker API（模拟器 10.0.2.2:48080）。
 */
@RunWith(AndroidJUnit4::class)
class LoginFreshStateInstrumentedTest {
    private lateinit var context: Context
    private lateinit var device: UiDevice

    @Before
    fun setUp() {
        context = InstrumentedTestSupport.appContext()
        device = UiDevice.getInstance(InstrumentationRegistry.getInstrumentation())
        InstrumentedTestSupport.clearAppState(context)
        device.executeShellCommand("appops set com.vpn.member ACTIVATE_VPN allow")
    }

    @Test
    fun staleSnapshotWithoutService_showsDisconnectedAfterLogin() {
        InstrumentedTestSupport.seedStaleVpnSessionSnapshot(context)
        assertFalse(
            "测试前置：VpnTunnelService 不应在运行",
            VpnTunnelStateSync.isServiceRunning(context),
        )

        InstrumentedTestSupport.prepareLoggedInSession(context)
        InstrumentedTestSupport.launchMainActivity(context)

        assertTrue(
            "登录后应进入连接页",
            device.wait(Until.hasObject(By.text("连接")), 25_000),
        )
        assertTrue(
            "应显示未连接状态",
            device.wait(Until.hasObject(By.text("未连接")), 15_000) ||
                device.wait(Until.hasObject(By.text("一键连接")), 5_000),
        )
        assertFalse(
            "不应误显示已保护",
            device.hasObject(By.text("已保护")),
        )
        assertFalse(
            "不应误显示断开按钮",
            device.hasObject(By.text("断开")),
        )
    }
}
