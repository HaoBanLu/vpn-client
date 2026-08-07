package com.vpn.member.vpn

/** Service 侧重连看门狗：与 ViewModel 周期探测解耦。 */
object TunnelWatchdogPolicy {
    const val INTERVAL_MS = 90_000L
    const val FAIL_STREAK_TO_RECONNECT = 2

    fun nextFailStreak(
        vpnNetworkOk: Boolean,
        previousStreak: Int,
        physicalOnline: Boolean,
    ): Int {
        if (!physicalOnline) return 0
        return if (vpnNetworkOk) 0 else previousStreak + 1
    }

    fun shouldRequestReconnect(failStreak: Int): Boolean =
        failStreak >= FAIL_STREAK_TO_RECONNECT
}
