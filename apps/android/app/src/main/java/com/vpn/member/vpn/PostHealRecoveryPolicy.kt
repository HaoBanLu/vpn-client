package com.vpn.member.vpn

/**
 * 切网 / 断网再恢复后的轻量自愈策略。
 *
 * 两条入口共用同一套决策（[ConnectViewModel.recoverAfterNetworkChange]）：
 * - WiFi↔蜂窝等传输变化（`transportChanged`）
 * - 物理网从无到有（`networkRestored`，含飞行模式关、WiFi 关掉后换移动网）
 *
 * 产品约定：
 * - 手机没网：不拆隧道、不因探活失败重连，等待物理网恢复
 * - 有网时以**系统 VPN 真通**（`vpn_network_ok`）为准，mixed-port 通不算恢复
 * - mixed 明确 FAILED/DEGRADED 作双保险，仍可升级重连
 * - 有网仍连不上节点：才累计失败并最终提示错误
 */
object PostHealRecoveryPolicy {
    /** 自愈（含重绑 underlying）后稍等再探测，给 DNS / 路由 / VALIDATED 收敛时间。 */
    const val SETTLE_AFTER_HEAL_MS = 2_000L

    const val POST_HEAL_PROBE_ATTEMPTS = 2
    const val POST_HEAL_PROBE_TIMEOUT_MS = 5_000
    const val POST_HEAL_RETRY_DELAY_MS = 1_000L

    /** 周期健康探测连续失败（FAILED/DEGRADED）达此次数则自动重连（须物理网可用）。 */
    const val HEALTH_FAIL_STREAK_TO_RECONNECT = 2

    /**
     * 仅看 mixed 探测结果时的旧逻辑（周期健康 streak 等仍用）。
     * 切网/断网恢复请用 [shouldReconnectAfterNetworkRecovery]。
     */
    fun shouldReconnectAfterHeal(
        probeStatus: ProbeStatus,
        physicalOnline: Boolean,
    ): Boolean {
        if (!physicalOnline) return false
        return probeStatus == ProbeStatus.FAILED || probeStatus == ProbeStatus.DEGRADED
    }

    /**
     * 切网或断网再恢复：以系统 VPN 路径为准；mixed 失败作双保险。
     * `vpnNetworkOk=false` 即使 mixed 报 SLOW/OK 也必须重连（避免假恢复）。
     */
    fun shouldReconnectAfterNetworkRecovery(
        physicalOnline: Boolean,
        vpnNetworkOk: Boolean,
        mixedProbeStatus: ProbeStatus,
    ): Boolean {
        if (!physicalOnline) return false
        if (!vpnNetworkOk) return true
        return mixedProbeStatus == ProbeStatus.FAILED || mixedProbeStatus == ProbeStatus.DEGRADED
    }

    fun nextHealthFailStreak(
        probeStatus: ProbeStatus,
        previousStreak: Int,
        physicalOnline: Boolean,
    ): Int {
        // 没物理网：不累计失败，避免离线误拆隧道；恢复后从 0 重新计数
        if (!physicalOnline) return 0
        return when (probeStatus) {
            ProbeStatus.FAILED, ProbeStatus.DEGRADED -> previousStreak + 1
            ProbeStatus.OK,
            ProbeStatus.SLOW,
            ProbeStatus.LIMITED_OVERSEAS,
            ProbeStatus.IDLE,
            ProbeStatus.PROBING,
            -> 0
        }
    }

    fun shouldReconnectOnHealthStreak(
        failStreak: Int,
        physicalOnline: Boolean,
    ): Boolean = physicalOnline && failStreak >= HEALTH_FAIL_STREAK_TO_RECONNECT

    /** 自动重连前：无物理网则等待，不拆隧道。 */
    fun shouldProceedAutoReconnect(physicalOnline: Boolean): Boolean = physicalOnline
}
