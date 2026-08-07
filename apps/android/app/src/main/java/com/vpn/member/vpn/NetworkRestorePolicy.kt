package com.vpn.member.vpn

/** 物理网恢复后应对 VPN 采取的动作。 */
enum class NetworkRestoreAction {
    /**
     * 隧道仍在：仅轻量自愈（DNS / underlying）。
     * 注意：默认恢复路径已改为 [SCHEDULE_RECONNECT]；HEAL 仅保留给关闭自动重连时的兜底。
     */
    HEAL,

    /** 完整自动重连（含已连接态切网/断网再恢复）。 */
    SCHEDULE_RECONNECT,

    /** 用户主动断开或关闭自动重连：不干预。 */
    NONE,
}

/**
 * 断网→恢复决策。抽出供单测覆盖各场景，避免 ConnectViewModel 里散落 if。
 *
 * **默认（自动重连开）**：一律 [SCHEDULE_RECONNECT]，禁止 HEAL+探测当恢复。
 * **自动重连关 + 已连接**：[HEAL] 仅刷 DNS/underlying，不完整重连。
 * **用户主动断开**：[NONE]
 */
object NetworkRestorePolicy {
    /** 切网事件风暴合并窗口，避免 network_available+dns+restored 连扣重连次数。 */
    const val RECONNECT_DEBOUNCE_MS = 1_500L

    fun decide(
        connectionState: ConnectionState,
        userInitiatedDisconnect: Boolean,
        autoReconnectEnabled: Boolean,
    ): NetworkRestoreAction {
        if (userInitiatedDisconnect) {
            return NetworkRestoreAction.NONE
        }
        if (!autoReconnectEnabled) {
            // 关自动重连：不断开会话，仅已连接时允许轻量自愈
            return if (connectionState == ConnectionState.CONNECTED) {
                NetworkRestoreAction.HEAL
            } else {
                NetworkRestoreAction.NONE
            }
        }
        // 自动重连开：任何未主动断开态（含已连接）都完整重连
        return NetworkRestoreAction.SCHEDULE_RECONNECT
    }

    /**
     * 物理网切换时是否恢复链路。
     * - CONNECTED：切网须完整重连
     * - FAILED：自动重连曾失败时，后续切网仍可再调度（防卡死）
     * - 不含 CONNECTING：首连过程中的 dns/available 风暴会误触发 KS 重连
     */
    fun shouldRecoverOnTransportChange(
        connectionState: ConnectionState,
    ): Boolean =
        connectionState == ConnectionState.CONNECTED ||
            connectionState == ConnectionState.FAILED

    @Deprecated("改用 shouldRecoverOnTransportChange", ReplaceWith("shouldRecoverOnTransportChange(connectionState)"))
    fun shouldHealOnTransportChange(
        connectionState: ConnectionState,
    ): Boolean = shouldRecoverOnTransportChange(connectionState)
}
