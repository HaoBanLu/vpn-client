package com.vpn.member.vpn

import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import androidx.test.uiautomator.UiDevice
import com.vpn.member.InstrumentedTestSupport
import com.vpn.member.data.network.NetworkMonitor
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeout
import org.junit.After
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith

/**
 * 模拟器断网/恢复：开关飞行模式，验证 NetworkMonitor 能发出 networkRestored。
 */
@RunWith(AndroidJUnit4::class)
class NetworkFlapInstrumentedTest {
    private lateinit var device: UiDevice
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)

    @Before
    fun setUp() {
        device = UiDevice.getInstance(InstrumentationRegistry.getInstrumentation())
        InstrumentedTestSupport.clearAppState()
        NetworkMonitor.start(InstrumentedTestSupport.appContext())
        // 确保起始非飞行模式
        device.executeShellCommand("cmd connectivity airplane-mode disable")
        Thread.sleep(2_000)
    }

    @After
    fun tearDown() {
        device.executeShellCommand("cmd connectivity airplane-mode disable")
        scope.cancel()
    }

    @Test
    fun airplaneModeOffOnOff_emitsNetworkRestored() =
        runBlocking {
            val restored = CompletableDeferred<Unit>()
            val job =
                scope.launch {
                    NetworkMonitor.networkRestored.collect {
                        if (!restored.isCompleted) restored.complete(Unit)
                    }
                }

            // 断网
            device.executeShellCommand("cmd connectivity airplane-mode enable")
            Thread.sleep(3_000)
            // 恢复
            device.executeShellCommand("cmd connectivity airplane-mode disable")

            withTimeout(25_000) { restored.await() }
            job.cancel()
            assertTrue(restored.isCompleted)
        }

    @Test
    fun rapidAirplaneFlaps_doNotCrashAppProcess() {
        InstrumentedTestSupport.launchMainActivity()
        Thread.sleep(2_000)
        repeat(4) {
            device.executeShellCommand("cmd connectivity airplane-mode enable")
            Thread.sleep(1_200)
            device.executeShellCommand("cmd connectivity airplane-mode disable")
            Thread.sleep(1_800)
        }
        // 进程仍在即可；详细 UI 断言留给 AuthStability
        val pid =
            device.executeShellCommand("pidof com.vpn.member").trim()
        assertTrue("快速断网后 App 进程应仍存活，实际 pid=$pid", pid.isNotBlank())
    }
}
