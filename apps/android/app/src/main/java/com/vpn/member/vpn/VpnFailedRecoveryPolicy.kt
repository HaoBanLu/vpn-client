package com.vpn.member.vpn

/**
 * 意外 FAILED（含 dataplane inactive）是否应立即调度自动重连。
 * 与用户主动断开、关闭自动重连区分。
 */
object VpnFailedRecoveryPolicy {
    fun shouldScheduleAfterUnexpectedFailed(
        previousState: ConnectionState,
        newState: ConnectionState,
        userInitiatedDisconnect: Boolean,
        autoReconnectEnabled: Boolean,
        snapshot: VpnSessionSnapshot?,
        physicalOnline: Boolean,
    ): Boolean {
        if (newState != ConnectionState.FAILED) return false
        if (userInitiatedDisconnect || !autoReconnectEnabled) return false
        if (snapshot?.wasUserConnected != true) return false
        if (!physicalOnline) return false
        // 仅从「曾有隧道意图」的态掉到 FAILED，避免首连失败连环空转
        return previousState == ConnectionState.CONNECTED ||
            previousState == ConnectionState.CONNECTING
    }
}
