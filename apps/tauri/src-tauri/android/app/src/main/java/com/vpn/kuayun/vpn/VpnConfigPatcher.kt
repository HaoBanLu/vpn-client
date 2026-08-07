package com.vpn.kuayun.vpn

object VpnConfigPatcher {
    /** Clash YAML 直接透传；App 自身 bypass 在 VpnTunnelService.openTun 中处理。 */
    fun prepareClashConfig(config: String): String = config.trim()
}
