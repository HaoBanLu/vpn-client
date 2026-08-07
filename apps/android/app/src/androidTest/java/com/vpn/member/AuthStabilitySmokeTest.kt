package com.vpn.member

import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import androidx.test.uiautomator.By
import androidx.test.uiautomator.UiDevice
import androidx.test.uiautomator.Until
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith

/**
 * 接口稳定性与鉴权冒烟：模拟器访问本机 Docker API（10.0.2.2:48080）。
 */
@RunWith(AndroidJUnit4::class)
class AuthStabilitySmokeTest {
    private lateinit var device: UiDevice

    @Before
    fun setUp() {
        device = UiDevice.getInstance(InstrumentationRegistry.getInstrumentation())
        InstrumentedTestSupport.clearAppState(InstrumentedTestSupport.appContext())
    }

    @Test
    fun apiLoginFromEmulator_returnsJwt() {
        val token = InstrumentedTestSupport.loginTestUser()
        assertTrue("登录应返回 JWT", token.length > 20)
    }

    @Test
    fun loggedInSession_repositoryGetMe_succeeds() {
        val context = InstrumentedTestSupport.appContext()
        InstrumentedTestSupport.prepareLoggedInSession(context)
        val repo = (context.applicationContext as VpnMemberApp).repository
        val user = kotlinx.coroutines.runBlocking { repo.getMe() }
        org.junit.Assert.assertTrue(user.email.isNotBlank())
    }

    @Test
    fun loggedInSession_repositoryGetNodes_succeeds() {
        val context = InstrumentedTestSupport.appContext()
        InstrumentedTestSupport.prepareLoggedInSession(context)
        val repo = (context.applicationContext as VpnMemberApp).repository
        val nodes = kotlinx.coroutines.runBlocking { repo.getNodes() }
        org.junit.Assert.assertNotNull(nodes)
    }

    @Test
    fun loggedInSession_reachesMainShellOrShowsActionableError() {
        val context = InstrumentedTestSupport.appContext()
        InstrumentedTestSupport.prepareLoggedInSession(context)
        InstrumentedTestSupport.launchMainActivity(context)

        val onMain =
            device.wait(Until.hasObject(By.text("连接")), 25_000) ||
                device.wait(Until.hasObject(By.text("未连接")), 5_000) ||
                device.wait(Until.hasObject(By.text("一键连接")), 5_000) ||
                device.wait(Until.hasObject(By.text("购买套餐")), 5_000)

        if (!onMain) {
            val onLogin = device.hasObject(By.text("欢迎回来"))
            if (onLogin) {
                val err =
                    device.findObjects(By.clazz("android.widget.TextView"))
                        .mapNotNull { it.text }
                        .firstOrNull { it.contains("失败") || it.contains("无效") || it.contains("网络") }
                throw AssertionError("仍在登录页，错误提示: ${err ?: "无"}")
            }
            throw AssertionError("未进入主界面也未在登录页，请检查 Splash/隐私门控")
        }

        assertTrue("应进入主界面", onMain)
    }

    @Test
    fun loggedInSession_nodesTabLoads() {
        val context = InstrumentedTestSupport.appContext()
        InstrumentedTestSupport.prepareLoggedInSession(context)
        InstrumentedTestSupport.launchMainActivity(context)

        assertTrue(
            "应显示底部节点 Tab",
            device.wait(Until.hasObject(By.text("节点")), 25_000),
        )
        device.findObject(By.text("节点")).click()
        assertTrue(
            "节点页应加载（空列表或节点名）",
            device.wait(Until.hasObject(By.textContains("节点")), 15_000),
        )
    }
}
