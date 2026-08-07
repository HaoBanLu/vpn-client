package com.vpn.member.vpn

/**
 * 应用回到前台时是否应尝试恢复 VPN 会话。
 *
 * 覆盖安装后可能残留 [VpnSessionSnapshot]，但 [VpnTunnelService] 并未运行；
 * 此时不应自动重连，否则登录后 UI 会误显示「已保护」。
 *
 * [ConnectionState.FAILED]（含 dataplane inactive 后 Service 已 stop）：只要会话快照仍在，
 * 仍应调度重连（不要求 serviceRunning）。
 */
object VpnForegroundRestorePolicy {
    fun shouldScheduleForegroundRestore(
        serviceRunning: Boolean,
        snapshot: VpnSessionSnapshot?,
        autoReconnectEnabled: Boolean,
        userInitiatedDisconnect: Boolean,
        connectionState: ConnectionState,
    ): Boolean {
        if (userInitiatedDisconnect || !autoReconnectEnabled) return false
        if (snapshot?.wasUserConnected != true) return false
        return when (connectionState) {
            ConnectionState.DISCONNECTED -> serviceRunning
            ConnectionState.FAILED -> true
            ConnectionState.CONNECTED,
            ConnectionState.CONNECTING,
            -> false
        }
    }
}
