package com.vpn.member.vpn

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class VpnSessionStatsTrackerTest {
  @Test
  fun formatBytes_formatsMegabytes() {
    assertEquals("1.0 MB", VpnSessionStatsTracker.formatBytes(1024 * 1024))
  }

    @Test
    fun formatSpeed_usesMbpsWhenFast() {
        // 2 MiB/s = 16.777… Mbps → 一位小数 16.8
        assertEquals("16.8 Mbps", VpnSessionStatsTracker.formatSpeed(2 * 1024 * 1024))
    }

  @Test
  fun formatSpeed_usesKbpsWhenSlow() {
    assertEquals("5.0 KB/s", VpnSessionStatsTracker.formatSpeed(5 * 1024))
  }

  @Test
  fun formatSpeed_idleAndLowTraffic() {
    assertEquals("0.0 KB/s", VpnSessionStatsTracker.formatSpeed(0))
    assertEquals("0.2 KB/s", VpnSessionStatsTracker.formatSpeed(163))
  }

  @Test
  fun formatDuration_formatsMinutesAndSeconds() {
    assertEquals("02:05", VpnSessionStatsTracker.formatDuration(125_000L))
  }

  @Test
  fun formatDuration_includesHoursWhenNeeded() {
    val text = VpnSessionStatsTracker.formatDuration(3_661_000L)
    assertTrue(text.startsWith("01:"))
  }
}
