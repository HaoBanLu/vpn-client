package com.vpn.member

import android.content.Context
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import androidx.test.uiautomator.By
import androidx.test.uiautomator.Direction
import androidx.test.uiautomator.UiDevice
import androidx.test.uiautomator.UiObject2
import androidx.test.uiautomator.Until
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith

/**
 * VPN 连接专项冒烟：登录 → 选节点 → 发起连接 → 断开 → 杀进程恢复。
 * 依赖本机 Docker API（模拟器 10.0.2.2:48080）及至少一个在线可连接节点。
 */
@RunWith(AndroidJUnit4::class)
class VpnConnectSmokeInstrumentedTest {
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
    fun loginSelectNodeConnectAndDisconnect_withoutCrash() {
        InstrumentedTestSupport.prepareLoggedInSession(context)
        InstrumentedTestSupport.launchMainActivity(context)

        assertTrue(
            "应进入连接页",
            device.wait(Until.hasObject(By.text("未连接")), 25_000) ||
                device.wait(Until.hasObject(By.text("一键连接")), 5_000),
        )

        assertTrue("应显示节点 Tab", device.wait(Until.hasObject(By.text("节点")), 10_000))
        device.findObject(By.text("节点")).click()

        assertTrue(
            "节点页应加载",
            device.wait(Until.hasObject(By.text("节点选择")), 15_000),
        )

        waitForNodesLoaded()

        val connectBtn =
            waitForConnectButton()
                ?: error("未找到可连接节点，请确认测试账号已分配套餐且后台有在线节点")

        connectBtn.click()

        val reachedTunnelPhase =
            device.wait(Until.hasObject(By.text("连接中")), 30_000) ||
                device.wait(Until.hasObject(By.text("已连接")), 60_000) ||
                device.wait(Until.hasObject(By.text("保护中")), 10_000) ||
                device.wait(Until.hasObject(By.textContains("连接失败")), 60_000) ||
                device.wait(Until.hasObject(By.textContains("未连接")), 5_000)

        assertTrue("连接流程应进入连接中/已连接/失败提示之一，而非无响应", reachedTunnelPhase)

        if (device.hasObject(By.text("断开"))) {
            device.findObject(By.text("断开")).click()
            assertTrue(
                "断开后应回到未连接",
                device.wait(Until.hasObject(By.text("未连接")), 30_000) ||
                    device.wait(Until.hasObject(By.text("一键连接")), 10_000),
            )
        }

        device.executeShellCommand("am force-stop com.vpn.member")
        InstrumentedTestSupport.launchMainActivity(context)
        assertTrue(
            "杀进程后应能回到主界面",
            device.wait(Until.hasObject(By.text("连接")), 30_000) ||
                device.wait(Until.hasObject(By.text("未连接")), 10_000) ||
                device.wait(Until.hasObject(By.text("一键连接")), 10_000),
        )

        assertFalse(
            "不应出现应用崩溃对话框",
            device.hasObject(By.textContains(" keeps stopping")),
        )
    }

    private fun waitForNodesLoaded() {
        val loaded =
            device.wait(Until.hasObject(By.text("连接此节点")), 45_000) ||
                device.wait(Until.hasObject(By.textContains("新加坡")), 5_000) ||
                device.wait(Until.hasObject(By.text("当前地区暂无在线节点")), 45_000)
        if (!loaded) {
            error("节点列表长时间未加载")
        }
        if (device.hasObject(By.text("当前地区暂无在线节点"))) {
            error("节点列表为空，请确认后台有在线节点且已绑定给测试用户")
        }
    }

    private fun waitForConnectButton(): UiObject2? {
        repeat(6) {
            device.findObject(By.text("连接此节点"))?.let { return it }
            device.findObject(By.scrollable(true))?.scroll(Direction.DOWN, 0.8f)
            device.waitForIdle(500)
        }
        return device.findObject(By.text("连接此节点"))
    }
}
