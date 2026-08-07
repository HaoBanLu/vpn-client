package com.vpn.member.vpn

/**
 * 隧道断开时 Kill Switch 决策（鉴权断开 vs 用户主动断开 vs 重连期保持阻断）。
 */
object PrivacyDisconnectPolicy {
    fun shouldEngageKillSwitch(
        userInitiatedDisconnect: Boolean,
        killSwitchEnabled: Boolean,
    ): Boolean = !userInitiatedDisconnect && killSwitchEnabled

    /** 自动重连前是否保持阻断（防真实 IP 裸奔）。 */
    fun shouldHoldKillSwitchDuringReconnect(
        killSwitchEnabled: Boolean,
        reconnectHoldEnabled: Boolean,
    ): Boolean = killSwitchEnabled && reconnectHoldEnabled
}
