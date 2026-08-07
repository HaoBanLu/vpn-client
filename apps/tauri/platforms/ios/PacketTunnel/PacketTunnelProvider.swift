import NetworkExtension
import os.log

/// Phase B：App Group Clash 配置 → 本机 mixed-port Mihomo + NE 代理设置（对齐桌面系统代理 MVP）。
final class PacketTunnelProvider: NEPacketTunnelProvider {
    private let log = Logger(subsystem: "com.vpn.kuayun.tunnel", category: "PacketTunnel")
    private let mixedPort: Int = 17890

    override func startTunnel(options: [String: NSObject]?, completionHandler: @escaping (Error?) -> Void) {
        log.info("PacketTunnel start requested")

        guard let yaml = VPNConfigStore.readConfig() else {
            log.error("Missing clash config in App Group")
            completionHandler(NSError(
                domain: "KuayunVPN",
                code: 1002,
                userInfo: [NSLocalizedDescriptionKey: "缺少 VPN 配置，请先在主 App 连接"]
            ))
            return
        }

        let settings = NEPacketTunnelNetworkSettings(tunnelRemoteAddress: "127.0.0.1")
        settings.ipv4Settings = NEIPv4Settings(addresses: ["198.18.0.1"], subnetMasks: ["255.255.255.0"])
        settings.ipv4Settings?.includedRoutes = [NEIPv4Route.default()]
        settings.dnsSettings = NEDNSSettings(servers: ["8.8.8.8", "1.1.1.1"])
        settings.mtu = 1500

        // 对齐桌面：流量经隧道内 HTTP/HTTPS 代理打到 Extension 内 Mihomo mixed-port
        let proxy = NEProxySettings()
        let server = NEProxyServer(host: "127.0.0.1", port: mixedPort)
        proxy.httpServer = server
        proxy.httpsServer = server
        proxy.httpEnabled = true
        proxy.httpsEnabled = true
        proxy.excludeSimpleHostnames = true
        proxy.matchDomains = [""] // 空串表示匹配全部域名（Apple 文档约定）
        settings.proxySettings = proxy

        setTunnelNetworkSettings(settings) { [weak self] error in
            guard let self else { return }
            if let error {
                self.log.error("Failed to apply tunnel settings: \(error.localizedDescription)")
                completionHandler(error)
                return
            }
            MihomoRunner.shared.start(configYAML: yaml) { mihomoError in
                if let mihomoError {
                    self.log.error("Mihomo start failed: \(mihomoError.localizedDescription)")
                    completionHandler(mihomoError)
                    return
                }
                if let meta = TunnelMetaStore.read() {
                    self.log.info("Tunnel up region=\(meta.region ?? "-", privacy: .public)")
                }
                completionHandler(nil)
            }
        }
    }

    override func stopTunnel(with reason: NEProviderStopReason, completionHandler: @escaping () -> Void) {
        log.info("PacketTunnel stop: \(String(describing: reason))")
        MihomoRunner.shared.stop()
        completionHandler()
    }
}
