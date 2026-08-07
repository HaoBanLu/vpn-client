package com.vpn.member.vpn

/**
 * 自动重连「先备配置、再断隧道/Kill Switch」顺序决策。
 * 防回归：重连期 KS 阻断后不可再拉 API，否则必 30s 超时。
 */
object AutoReconnectPrepPolicy {
    /** 物理网刚恢复后稍等再拉配置/建连，给 DNS/VALIDATED 收敛。 */
    const val NETWORK_SETTLE_MS = 2_000L

    /** 等物理网最长等待（轮询）。 */
    const val PHYSICAL_READY_WAIT_MS = 20_000L

    const val PHYSICAL_POLL_MS = 500L

    /**
     * 是否允许在 API 失败时用本地缓存配置继续重连。
     * Kill Switch 阻断或短暂无网时必须允许，否则卡死在「连接超时」。
     */
    fun allowCachedConfigFallback(
        apiFailed: Boolean,
        hasCachedConfig: Boolean,
    ): Boolean = apiFailed && hasCachedConfig

    /**
     * 是否应在**配置已就绪之后**再拆隧道并保持 KS。
     * 切网/断网恢复/探活失败升级重连时为 true。
     */
    fun shouldHoldKillSwitchAfterPrep(
        holdEnabled: Boolean,
        escalateFromLiveTunnel: Boolean,
        tunnelLive: Boolean,
    ): Boolean = holdEnabled && escalateFromLiveTunnel && tunnelLive
}
