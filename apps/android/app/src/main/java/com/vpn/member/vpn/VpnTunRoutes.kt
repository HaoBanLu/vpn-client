package com.vpn.member.vpn

import android.net.VpnService

/** TUN / Kill Switch 路由配置（IPv4 全量 + 可选 IPv6 防泄露）。 */
object VpnTunRoutes {
    fun applyFullTunnelRoutes(
        builder: VpnService.Builder,
        ipv6Protection: Boolean,
    ) {
        builder.addRoute("0.0.0.0", 0)
        if (ipv6Protection) {
            builder.addRoute("::", 0)
        }
    }

    fun applyKillSwitchRoutes(
        builder: VpnService.Builder,
        gateway: String,
        prefix: Int,
        ipv6Protection: Boolean,
    ) {
        builder
            .addAddress(gateway, prefix)
            .setBlocking(true)
        applyFullTunnelRoutes(builder, ipv6Protection)
    }
}
