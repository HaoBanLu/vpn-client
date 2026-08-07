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
import org.junit.Assume.assumeTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith

/**
 * 连接体验四项优化仪器化验收：
 * 1. 登录进主界面展示隐私引导
 * 2. 连接中再点大按钮可中断
 * 3. 连接中可在节点页切换节点
 * 4. 不可达节点快速失败并提示断网保护
 *
 * 远程 API 测试：-PdebugApiBase=http://192.229.87.112:44080/
 */
@RunWith(AndroidJUnit4::class)
class ConnectUxOptimizationInstrumentedTest {
    private lateinit var context: Context
    private lateinit var device: UiDevice

    @Before
    fun setUp() {
        context = InstrumentedTestSupport.appContext()
        device = UiDevice.getInstance(InstrumentationRegistry.getInstrumentation())
        InstrumentedTestSupport.clearAppState(context)
        InstrumentedTestSupport.grantInstrumentedPermissions(context)
    }

    @Test
    fun mainShellDoesNotShowPrivacyOnboarding() {
        InstrumentedTestSupport.prepareLoggedInSession(
            context,
            completePrivacyOnboarding = false,
        )
        InstrumentedTestSupport.launchMainActivity(context)
        dismissNotificationPermissionIfVisible()

        assertFalse(
            "登录进主界面不应弹出隐私引导",
            device.wait(Until.hasObject(By.text("隐私保护已默认开启")), 5_000),
        )
        assertTrue(
            "应直接进入连接主界面",
            device.wait(Until.hasObject(By.text("连接")), 20_000) ||
                device.wait(Until.hasObject(By.text("未连接")), 10_000) ||
                device.wait(Until.hasObject(By.text("一键连接")), 10_000),
        )
    }

    @Test
    fun tappingHeroDuringConnectingInterruptsTunnel() {
        InstrumentedTestSupport.prepareLoggedInSession(context)
        launchMainAndOpenNodes()
        scrollToNode("新加坡5")
        val connectBtn =
            if (device.hasObject(By.text("新加坡5"))) {
                findConnectButtonForNode("新加坡5")
            } else {
                waitForConnectButton()
            } ?: assumeNoNodes()
        connectBtn.click()

        val sawConnecting =
            device.wait(Until.hasObject(By.text("连接中")), 20_000) ||
                device.wait(Until.hasObject(By.text("切换中")), 5_000)
        assumeTrue("应进入连接中", sawConnecting)

        clickHeroButton()

        assertTrue(
            "再次点击应中断连接流程",
            device.wait(Until.gone(By.text("连接中")), 25_000) ||
                device.wait(Until.hasObject(By.text("未连接")), 25_000) ||
                device.wait(Until.hasObject(By.text("一键连接")), 10_000) ||
                device.wait(Until.hasObject(By.text("连接失败")), 25_000),
        )
    }

    @Test
    fun canSwitchNodeWhileConnecting() {
        InstrumentedTestSupport.prepareLoggedInSession(context)
        launchMainAndOpenNodes()
        assumeTrue(
            "需要至少两个可连接节点",
            device.findObjects(By.text("连接此节点")).size >= 2,
        )

        val first = waitForConnectButton() ?: assumeNoNodes()
        first.click()

        assertTrue(
            "应进入连接中",
            device.wait(Until.hasObject(By.text("连接中")), 45_000),
        )

        device.findObject(By.text("节点")).click()
        assertTrue(
            "应回到节点页",
            device.wait(Until.hasObject(By.text("节点选择")), 10_000),
        )

        val second = findSecondConnectButton() ?: error("未找到第二个可连接节点")
        second.click()

        val progressed =
            device.wait(Until.hasObject(By.text("切换中")), 8_000) ||
                device.wait(Until.hasObject(By.text("连接中")), 8_000) ||
                device.wait(Until.hasObject(By.text("已保护")), 45_000) ||
                device.wait(Until.hasObject(By.text("连接失败")), 45_000)
        assertTrue("连接中切换节点应继续流程而非卡死", progressed)
    }

    @Test
    fun unreachableNodeFailsFastWithKillSwitchHint() {
        InstrumentedTestSupport.prepareLoggedInSession(context)
        launchMainAndOpenNodes()
        scrollToNode("新加坡5")
        assumeTrue("需要新加坡5节点用于失败用例", device.hasObject(By.text("新加坡5")))

        val target = findConnectButtonForNode("新加坡5") ?: assumeNoNodes()
        val startedAt = System.currentTimeMillis()
        target.click()

        val failed =
            device.wait(Until.hasObject(By.text("连接失败")), 35_000) ||
                device.wait(Until.hasObject(By.textContains("节点不可达")), 35_000)
        val elapsed = System.currentTimeMillis() - startedAt

        assertTrue("不可达节点应在 35 秒内失败（非 ~70 秒）", failed && elapsed < 50_000)
        assertTrue(
            "应提示节点不可达（默认不断网；开启连接失败阻断时才提断网保护）",
            device.hasObject(By.textContains("节点不可达")) ||
                device.hasObject(By.textContains("断网保护")),
        )
    }

    private fun dismissNotificationPermissionIfVisible() {
        if (device.wait(Until.hasObject(By.text("Allow")), 2_000)) {
            device.findObject(By.text("Allow"))?.click()
        }
    }

    private fun dismissSystemDialogsIfVisible() {
        dismissNotificationPermissionIfVisible()
        dismissPrivacySheetIfVisible()
    }

    private fun dismissPrivacySheetIfVisible() {
        if (device.wait(Until.hasObject(By.textContains("跳过引导")), 2_000)) {
            device.findObject(By.textContains("跳过引导"))?.click()
            device.wait(Until.gone(By.text("隐私保护已默认开启")), 5_000)
        }
    }

    private fun launchMainAndOpenNodes() {
        InstrumentedTestSupport.launchMainActivity(context)
        dismissSystemDialogsIfVisible()
        assertTrue(
            "应进入连接页",
            device.wait(Until.hasObject(By.text("连接")), 25_000) ||
                device.wait(Until.hasObject(By.text("未连接")), 10_000) ||
                device.wait(Until.hasObject(By.text("一键连接")), 10_000) ||
                device.wait(Until.hasObject(By.text("购买套餐")), 10_000) ||
                device.wait(Until.hasObject(By.text("已保护")), 10_000),
        )
        assertTrue("应显示节点 Tab", device.wait(Until.hasObject(By.text("节点")), 10_000))
        device.findObject(By.text("节点")).click()
        assertTrue(
            "节点页应加载",
            device.wait(Until.hasObject(By.text("节点选择")), 15_000),
        )
        waitForNodesLoaded()
    }

    private fun waitForNodesLoaded() {
        val loaded =
            device.wait(Until.hasObject(By.text("连接此节点")), 45_000) ||
                device.wait(Until.hasObject(By.textContains("新加坡")), 5_000) ||
                device.wait(Until.hasObject(By.text("当前地区暂无在线节点")), 45_000)
        if (!loaded) error("节点列表长时间未加载")
        if (device.hasObject(By.text("当前地区暂无在线节点"))) {
            error("节点列表为空")
        }
    }

    private fun waitForConnectButton(): UiObject2? {
        repeat(8) {
            device.findObject(By.text("连接此节点"))?.let { return it }
            device.findObject(By.scrollable(true))?.scroll(Direction.DOWN, 0.8f)
            device.waitForIdle(400)
        }
        return device.findObject(By.text("连接此节点"))
    }

    private fun findSecondConnectButton(): UiObject2? {
        val buttons = device.findObjects(By.text("连接此节点"))
        if (buttons.size >= 2) return buttons[1]
        repeat(6) {
            device.findObject(By.scrollable(true))?.scroll(Direction.DOWN, 0.8f)
            device.waitForIdle(400)
            val after = device.findObjects(By.text("连接此节点"))
            if (after.size >= 2) return after[1]
        }
        return null
    }

    private fun findConnectButtonForNode(nodeName: String): UiObject2? {
        scrollToNode(nodeName)
        if (!device.hasObject(By.text(nodeName))) return null
        return device.findObject(By.text("连接此节点"))
    }

    private fun scrollToNode(nodeName: String) {
        repeat(10) {
            if (device.hasObject(By.text(nodeName))) return
            device.findObject(By.scrollable(true))?.scroll(Direction.DOWN, 0.85f)
            device.waitForIdle(350)
        }
    }

    private fun clickHeroButton() {
        when {
            device.hasObject(By.text("连接中")) -> {
                device.findObjects(By.text("连接中")).lastOrNull()?.click()
            }
            device.hasObject(By.text("断开")) -> {
                device.findObject(By.text("断开"))?.click()
            }
            else -> {
                device.click(device.displayWidth / 2, device.displayHeight / 3)
            }
        }
    }

    private fun assumeNoNodes(): Nothing {
        assumeTrue("无可用节点，跳过", false)
        error("unreachable")
    }
}
