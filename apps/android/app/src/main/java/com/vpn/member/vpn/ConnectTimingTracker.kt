package com.vpn.member.vpn

import com.vpn.member.debug.AppDebugLogger

/**
 * 连接耗时埋点：从用户点击连接到 TUN 就绪（P50/P95 验收用）。
 */
object ConnectTimingTracker {
  private const val CATEGORY = "connect_timing"

  @Volatile private var sessionId: Long = 0L
  @Volatile private var clickAtMs: Long = 0L
  @Volatile private var configDispatchedAtMs: Long = 0L
  @Volatile private var tunReadyAtMs: Long = 0L

  @Volatile private var lastCompletedClickToTunMs: Long? = null

  fun markConnectClick() {
    sessionId = System.currentTimeMillis()
    clickAtMs = sessionId
    configDispatchedAtMs = 0L
    tunReadyAtMs = 0L
  }

  fun markConfigDispatched() {
    if (clickAtMs <= 0L) return
    configDispatchedAtMs = System.currentTimeMillis()
  }

  fun markTunReady(context: android.content.Context? = null) {
    if (clickAtMs <= 0L) return
    tunReadyAtMs = System.currentTimeMillis()
    val clickToTunMs = tunReadyAtMs - clickAtMs
    val configToTunMs =
      if (configDispatchedAtMs > 0L) tunReadyAtMs - configDispatchedAtMs else -1L
    val metKpi = clickToTunMs <= KPI_TARGET_MS
    AppDebugLogger.info(
      category = CATEGORY,
      message = "connect_click_to_tun_ready=${clickToTunMs}ms",
      context =
        buildMap {
          put("session_id", sessionId.toString())
          put("click_to_tun_ms", clickToTunMs.toString())
          if (configToTunMs >= 0) put("config_to_tun_ms", configToTunMs.toString())
          put("kpi_5s_met", metKpi.toString())
        },
    )
    if (!metKpi) {
      AppDebugLogger.warn(
        category = CATEGORY,
        message = "连接耗时超过 KPI（${KPI_TARGET_MS}ms）",
        context = mapOf("click_to_tun_ms" to clickToTunMs.toString()),
      )
    }
    lastCompletedClickToTunMs = clickToTunMs
    context?.let {
      ConnectTimingArchive.record(it, clickToTunMs, configToTunMs.takeIf { v -> v >= 0 })
    }
    reset()
  }

  fun reset() {
    clickAtMs = 0L
    configDispatchedAtMs = 0L
    tunReadyAtMs = 0L
  }

  fun lastClickToTunMsForTest(): Long? = lastCompletedClickToTunMs

  const val KPI_TARGET_MS = 5_000L
}
