package com.vpn.member.vpn

import org.junit.Assert.assertEquals
import org.junit.Test

class ProtectionLevelResolverTest {
  @Test
  fun baselineReady_whenDisconnectedAndPrivacyBaselineOn() {
    val level =
        ProtectionLevelResolver.resolve(
            connectionState = ConnectionState.DISCONNECTED,
            error = null,
            probeStatus = ProbeStatus.IDLE,
            privacyBaselineReady = true,
        )
    assertEquals(ProtectionLevel.BASELINE_READY, level)
    assertEquals(
        "隐私保护已就绪 · 连接后生效",
        ProtectionLevelResolver.label(level),
    )
  }

  @Test
  fun unprotected_whenDisconnectedAndBaselineOff() {
    val level =
        ProtectionLevelResolver.resolve(
            connectionState = ConnectionState.DISCONNECTED,
            error = null,
            probeStatus = ProbeStatus.IDLE,
            privacyBaselineReady = false,
        )
    assertEquals(ProtectionLevel.UNPROTECTED, level)
  }

  @Test
  fun blocked_whenKillSwitchMessage() {
    val level =
        ProtectionLevelResolver.resolve(
            connectionState = ConnectionState.FAILED,
            error = "Kill Switch 已启用：网络已阻断",
            probeStatus = ProbeStatus.IDLE,
        )
    assertEquals(ProtectionLevel.BLOCKED, level)
  }

  @Test
  fun degraded_whenConnectedButProbeFailed() {
    val level =
        ProtectionLevelResolver.resolve(
            connectionState = ConnectionState.CONNECTED,
            error = null,
            probeStatus = ProbeStatus.FAILED,
        )
    assertEquals(ProtectionLevel.DEGRADED, level)
  }

  @Test
  fun protected_whenConnectedAndProbeOk() {
    val level =
        ProtectionLevelResolver.resolve(
            connectionState = ConnectionState.CONNECTED,
            error = null,
            probeStatus = ProbeStatus.OK,
        )
    assertEquals(ProtectionLevel.PROTECTED, level)
  }
}
