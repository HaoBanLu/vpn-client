package com.vpn.member.vpn.mihomo

import com.vpn.member.vpn.ConnectionState

/** 息屏挂起策略：VPN 已连接/连接中时不要 suspendCore，否则会像「莫名掉线」。 */
object MihomoSuspendPolicy {
    fun shouldSuspendCore(
        screenOff: Boolean,
        state: ConnectionState,
    ): Boolean {
        if (!screenOff) return false
        return state != ConnectionState.CONNECTED && state != ConnectionState.CONNECTING
    }
}
