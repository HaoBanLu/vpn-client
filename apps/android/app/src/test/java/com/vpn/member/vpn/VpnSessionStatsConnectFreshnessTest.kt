package com.vpn.member.vpn

import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * 刚连上不应把「上一次内核残余」算进速率，也不应显示历史累计；
 * 速率必须来自会话累计差，不能被 TrafficNow 短间隔放大成数 Gbps。
 */
class VpnSessionStatsConnectFreshnessTest {
    private val reader = FakeMihomoTrafficReader()

    @After
    fun tearDown() {
        VpnSessionStatsTracker.setMihomoReaderForTest(null)
        VpnTrafficBus.clear()
    }

    @Test
    fun reset_flushesResidualTrafficNow_andRatesUseTotalDelta() {
        reader.totalUpload = 100L
        reader.totalDownload = 200L
        reader.nowUpload = 300L * 1024 * 1024
        reader.nowDownload = 200L * 1024 * 1024
        VpnSessionStatsTracker.setMihomoReaderForTest(reader)

        VpnSessionStatsTracker.reset()
        assertTrue("reset 应至少调用一次 queryNow 冲掉残余", reader.queryNowCallCount >= 1)

        val first = VpnSessionStatsTracker.tick()
        assertEquals(0L, first.stats.uploadBytes)
        assertEquals(0L, first.stats.downloadBytes)

        Thread.sleep(450)
        // 0.45s 内真实增量约 200KB 上下行 → 远低于 Gbps
        reader.totalUpload = 100L + 200L * 1024
        reader.totalDownload = 200L + 200L * 1024
        val second = VpnSessionStatsTracker.tick()

        assertTrue(
            "上传速率异常偏大: ${second.rates.uploadBps}",
            second.rates.uploadBps < 5L * 1024 * 1024,
        )
        assertTrue(
            "下载速率异常偏大: ${second.rates.downloadBps}",
            second.rates.downloadBps < 5L * 1024 * 1024,
        )
    }

    @Test
    fun reset_sessionCountersStartAtZero_evenIfCoreHadHistory() {
        reader.totalUpload = 80L * 1024 * 1024
        reader.totalDownload = 120L * 1024 * 1024
        VpnSessionStatsTracker.setMihomoReaderForTest(reader)
        VpnSessionStatsTracker.reset()

        val snap = VpnSessionStatsTracker.snapshot()
        assertEquals(0L, snap.uploadBytes)
        assertEquals(0L, snap.downloadBytes)
    }

    @Test
    fun notificationAndUi_shareSameSpeedFormatter() {
        assertEquals("16.8 Mbps", VpnSessionStatsTracker.formatSpeed(2 * 1024 * 1024))
        assertEquals("5.0 KB/s", VpnSessionStatsTracker.formatSpeed(5 * 1024))
    }

    @Test
    fun ratesAreZeroDuringWarmup() {
        reader.totalUpload = 0L
        reader.totalDownload = 0L
        VpnSessionStatsTracker.setMihomoReaderForTest(reader)
        VpnSessionStatsTracker.reset()

        Thread.sleep(450)
        reader.totalUpload = 50L * 1024 * 1024
        reader.totalDownload = 50L * 1024 * 1024
        val snap = VpnSessionStatsTracker.tick()
        assertEquals(0L, snap.rates.uploadBps)
        assertEquals(0L, snap.rates.downloadBps)
    }

    @Test
    fun ratesAreCappedAt200Mbps_evenIfTotalJumpsUnrealistically() {
        reader.totalUpload = 0L
        reader.totalDownload = 0L
        VpnSessionStatsTracker.setMihomoReaderForTest(reader)
        VpnSessionStatsTracker.reset()

        Thread.sleep(3_100)
        VpnSessionStatsTracker.tick()

        Thread.sleep(450)
        // 模拟脏数据：0.45s 内「涨」2GB → 旧算法可到数 Gbps
        reader.totalUpload = 2L * 1024 * 1024 * 1024
        reader.totalDownload = 2L * 1024 * 1024 * 1024
        val snap = VpnSessionStatsTracker.tick()

        val maxBps = 25_000_000L
        assertTrue(
            "上传仍虚高: ${snap.rates.uploadBps} -> ${VpnSessionStatsTracker.formatSpeed(snap.rates.uploadBps)}",
            snap.rates.uploadBps <= maxBps,
        )
        assertTrue(
            "下载仍虚高: ${snap.rates.downloadBps} -> ${VpnSessionStatsTracker.formatSpeed(snap.rates.downloadBps)}",
            snap.rates.downloadBps <= maxBps,
        )
        // 展示文案也不该再出现上万 Mbps
        val shown = VpnSessionStatsTracker.formatSpeed(snap.rates.downloadBps)
        assertTrue(shown, !shown.contains("14686") && !shown.startsWith("16284"))
        if (shown.endsWith("Mbps")) {
            val num = shown.removeSuffix(" Mbps").toDouble()
            assertTrue("展示仍吓人: $shown", num <= 200.1)
        }
    }

    @Test
    fun shortSampleInterval_doesNotAmplifyRate() {
        reader.totalUpload = 0L
        reader.totalDownload = 0L
        VpnSessionStatsTracker.setMihomoReaderForTest(reader)
        VpnSessionStatsTracker.reset()
        Thread.sleep(3_100)
        VpnSessionStatsTracker.tick()

        reader.totalUpload = 5L * 1024 * 1024
        reader.totalDownload = 5L * 1024 * 1024
        // 故意短间隔连 tick：旧 TrafficNow/短 delta 路径会爆表；新逻辑应忽略短间隔
        val a = VpnSessionStatsTracker.tick()
        val b = VpnSessionStatsTracker.tick()
        assertTrue(a.rates.downloadBps <= 25_000_000L)
        assertTrue(b.rates.downloadBps <= 25_000_000L)
    }

    private class FakeMihomoTrafficReader : MihomoTrafficReader {
        var totalUpload: Long = 0L
        var totalDownload: Long = 0L
        var nowUpload: Long = 0L
        var nowDownload: Long = 0L
        var queryNowCallCount: Int = 0

        override fun queryTotal(): Long = (totalUpload shl 32) or (totalDownload and 0xFFFFFFFFL)

        override fun queryNow(): Long {
            queryNowCallCount++
            val packed = (nowUpload shl 32) or (nowDownload and 0xFFFFFFFFL)
            nowUpload = 0L
            nowDownload = 0L
            return packed
        }
    }
}
