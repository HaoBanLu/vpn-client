package com.vpn.member.vpn

import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class VpnSessionStatsTrackerMihomoTest {
    private val reader = FakeMihomoTrafficReader()

    @After
    fun tearDown() {
        VpnSessionStatsTracker.setMihomoReaderForTest(null)
    }

    @Test
    fun snapshot_subtractsMihomoBaseline() {
        reader.totalUpload = 1_000L
        reader.totalDownload = 2_000L
        VpnSessionStatsTracker.setMihomoReaderForTest(reader)
        VpnSessionStatsTracker.reset()

        reader.totalUpload = 1_600L
        reader.totalDownload = 2_500L
        val stats = VpnSessionStatsTracker.snapshot()

        assertEquals(600L, stats.uploadBytes)
        assertEquals(500L, stats.downloadBytes)
        assertTrue(stats.durationMs >= 0L)
    }

    @Test
    fun tick_marksMihomoSourceWhenReaderAvailable() {
        reader.totalUpload = 10L
        reader.totalDownload = 20L
        VpnSessionStatsTracker.setMihomoReaderForTest(reader)
        VpnSessionStatsTracker.reset()

        val snapshot = VpnSessionStatsTracker.tick()
        assertEquals(TrafficStatsSource.MIHOMO, snapshot.source)
    }

    private class FakeMihomoTrafficReader : MihomoTrafficReader {
        var totalUpload: Long = 0L
        var totalDownload: Long = 0L
        var nowUpload: Long = 0L
        var nowDownload: Long = 0L

        override fun queryTotal(): Long = (totalUpload shl 32) or totalDownload

        override fun queryNow(): Long = (nowUpload shl 32) or nowDownload
    }
}
