package com.vpn.member.vpn

/** 持久化的 VPN 会话意图，用于断网恢复、Service 重启与开机自启。 */
data class VpnSessionSnapshot(
    val wasUserConnected: Boolean,
    val nodeName: String?,
    val region: String?,
    val profile: String,
    val routeMode: String,
    val connectionScenario: String,
    val savedAtMs: Long = System.currentTimeMillis(),
)
