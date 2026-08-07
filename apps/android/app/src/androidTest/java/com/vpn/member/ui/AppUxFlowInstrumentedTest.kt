package com.vpn.member.ui

import android.content.Context
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import androidx.test.uiautomator.By
import androidx.test.uiautomator.UiDevice
import androidx.test.uiautomator.Until
import com.vpn.member.InstrumentedTestSupport
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Assume.assumeTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith

/**
 * 连接主流程与子页头部一致性验收。
 * 依赖本机 Docker API（模拟器访问 10.0.2.2:48080）。
 */
@RunWith(AndroidJUnit4::class)
class AppUxFlowInstrumentedTest {
    private lateinit var context: Context
    private lateinit var device: UiDevice

    @Before
    fun setUp() {
        context = InstrumentedTestSupport.appContext()
        device = UiDevice.getInstance(InstrumentationRegistry.getInstrumentation())
        InstrumentedTestSupport.clearAppState(context)
    }

    @Test
    fun connectWithoutNodeNavigatesToNodesTabWithHint() {
        InstrumentedTestSupport.prepareLoggedInSession(context)
        InstrumentedTestSupport.launchMainActivity(context)

        assertTrue(
            "应进入连接页",
            device.wait(Until.hasObject(By.text("未连接")), 20_000),
        )
        assumeTrue(
            "当前测试账号无有效套餐，跳过本用例（需有套餐账号验证选节点跳转）",
            device.hasObject(By.text("一键连接")),
        )
        device.findObject(By.text("一键连接")).click()

        assertTrue(
            "应跳转到节点页",
            device.wait(Until.hasObject(By.textContains("连接此节点")), 8_000),
        )
        assertTrue(
            "应提示选择节点",
            device.wait(Until.hasObject(By.textContains("请选择要连接的节点")), 5_000),
        )
    }

    @Test
    fun connectWithoutSubscriptionNavigatesToPackagesTab() {
        InstrumentedTestSupport.prepareLoggedInSession(context)
        InstrumentedTestSupport.launchMainActivity(context)

        assertTrue(
            "应进入连接页",
            device.wait(Until.hasObject(By.text("未连接")), 20_000),
        )
        assumeTrue(
            "当前测试账号已有套餐，跳过无套餐跳转用例",
            device.hasObject(By.text("购买套餐")),
        )
        device.findObject(By.text("购买套餐")).click()

        assertTrue(
            "应跳转到套餐页",
            device.wait(Until.hasObject(By.textContains("套餐")), 8_000),
        )
    }

    @Test
    fun accountBarDoesNotShowIpBindingLabels() {
        InstrumentedTestSupport.prepareLoggedInSession(context)
        InstrumentedTestSupport.launchMainActivity(context)

        assertTrue(
            "应进入主界面",
            device.wait(Until.hasObject(By.text("连接")), 15_000),
        )
        assertFalse(
            "不应显示多 IP 标签",
            device.hasObject(By.textContains("多IP")),
        )
        assertFalse(
            "不应显示单 IP 标签",
            device.hasObject(By.textContains("单IP")),
        )
        device.findObject(By.desc("菜单")).click()
        assertFalse(
            "菜单中不应有 IP 设置",
            device.hasObject(By.textContains("IP设置")),
        )
        device.pressBack()
    }

    @Test
    fun connectionSettingsSubPagesShareConsistentBackHeader() {
        InstrumentedTestSupport.prepareLoggedInSession(context)
        InstrumentedTestSupport.launchMainActivity(context)

        assertTrue(
            "应进入主界面",
            device.wait(Until.hasObject(By.text("我的")), 15_000),
        )
        openProfileTab()

        val pages =
            listOf(
                PageCase("连接与隐私", "连接与隐私", "断网保护"),
                PageCase("应用直连", "应用直连", "默认全部走 VPN"),
                PageCase("规则直连", "规则直连", "条规则；下次连接 VPN 后生效"),
            )
        for (page in pages) {
            device.findObject(By.text(page.menuTitle)).click()
            assertSubPageHeader(page.pageTitle, page.pageMarker)
            openProfileTab()
        }
    }

    @Test
    fun devicesScreenUsesConsistentBackHeader() {
        InstrumentedTestSupport.prepareLoggedInSession(context)
        InstrumentedTestSupport.launchMainActivity(context)

        assertTrue(
            "应进入主界面",
            device.wait(Until.hasObject(By.desc("菜单")), 15_000),
        )
        device.findObject(By.desc("菜单")).click()
        assertTrue(
            "菜单应包含查看设备",
            device.wait(Until.hasObject(By.text("查看设备")), 5_000),
        )
        device.findObject(By.text("查看设备")).click()
        assertSubPageHeader("我的设备", "管理已登录设备")
    }

    private fun openProfileTab() {
        assertTrue(
            "应显示我的 Tab",
            device.wait(Until.hasObject(By.text("我的")), 10_000),
        )
        device.findObject(By.text("我的")).click()
        assertTrue(
            "应进入我的页",
            device.wait(Until.hasObject(By.textContains("连接设置")), 8_000),
        )
    }

    private fun assertSubPageHeader(
        expectedTitle: String,
        uniquePageContent: String,
    ) {
        assertTrue(
            "应进入 $expectedTitle 详情页",
            device.wait(Until.hasObject(By.desc("返回")), 10_000),
        )
        assertTrue(
            "应显示标题：$expectedTitle",
            device.hasObject(By.text(expectedTitle)),
        )
        assertTrue(
            "$expectedTitle 页应展示：$uniquePageContent",
            device.wait(Until.hasObject(By.textContains(uniquePageContent)), 15_000),
        )
        device.findObject(By.desc("返回")).click()
        assertTrue(
            "返回后应离开 $expectedTitle 详情页",
            device.wait(Until.gone(By.textContains(uniquePageContent)), 5_000),
        )
    }

    private data class PageCase(
        val menuTitle: String,
        val pageTitle: String,
        val pageMarker: String,
    )
}
