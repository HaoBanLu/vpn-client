package com.vpn.member.vpn

import com.vpn.member.data.api.NodeItem

object AppProtocolSupport {
    private val nativeOnlyProtocols = setOf("openvpn", "wireguard")
    /** Mihomo/Clash 出站协议（非 OpenVPN/WireGuard 原生栈）。 */
    private val supportedProxyProtocols =
        setOf(
            "vless",
            "vmess",
            "trojan",
            "shadowsocks",
            "ss",
            "hysteria2",
            "hy2",
        )

    fun normalizeProtocol(protocol: String?): String {
        val raw = protocol?.trim()?.lowercase().orEmpty()
        return when (raw) {
            "", "null" -> "vmess"
            "hy2" -> "hysteria2"
            "ss" -> "shadowsocks"
            else -> raw
        }
    }

    fun isNativeOnly(protocol: String?): Boolean =
        normalizeProtocol(protocol) in nativeOnlyProtocols

    fun usesRelay(node: NodeItem): Boolean =
        node.access_mode?.equals("relay", ignoreCase = true) == true

    fun isRelayCompatible(node: NodeItem): Boolean {
        if (!usesRelay(node)) return true
        val protocol = normalizeProtocol(node.protocol)
        return protocol in supportedProxyProtocols
    }

    fun isAppConnectable(node: NodeItem): Boolean {
        val protocol = normalizeProtocol(node.protocol)
        if (protocol in nativeOnlyProtocols) return false
        if (protocol !in supportedProxyProtocols) return false
        return isRelayCompatible(node)
    }

    fun unsupportedReason(node: NodeItem): String? {
        if (isAppConnectable(node)) return null
        val protocol = normalizeProtocol(node.protocol)
        return when {
            protocol in nativeOnlyProtocols ->
                "需使用 ${protocolLabel(protocol)} 官方客户端，自研 App 不支持"
            !isRelayCompatible(node) ->
                "该节点经中转，自研 App 仅支持 sing-box 族协议（不含 OpenVPN/WireGuard）"
            else -> "自研 App 暂不支持此协议"
        }
    }

    fun protocolLabel(protocol: String): String =
        when (normalizeProtocol(protocol)) {
            "openvpn" -> "OpenVPN"
            "wireguard" -> "WireGuard"
            "shadowsocks" -> "Shadowsocks"
            "hysteria2" -> "Hysteria2"
            "vless" -> "VLESS"
            "vmess" -> "VMess"
            "trojan" -> "Trojan"
            else -> protocol
        }
}
