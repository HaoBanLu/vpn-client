import Foundation
import WebKit
import Tauri

/// iOS VPN Spike：仅暴露命令桩，真正隧道需 PacketTunnelProvider + Network Extension entitlement。
class VpnPlugin: Plugin {
    @objc public func prepare(_ invoke: Invoke) throws {
        invoke.resolve(["ready": false, "reason": "Network Extension not configured"])
    }

    @objc public func connect(_ invoke: Invoke) throws {
        invoke.reject("iOS VPN spike: PacketTunnelProvider not implemented")
    }

    @objc public func disconnect(_ invoke: Invoke) throws {
        invoke.resolve([:])
    }

    @objc public func getStatus(_ invoke: Invoke) throws {
        invoke.resolve(["state": "disconnected"])
    }
}

@_cdecl("init_plugin_vpn")
func initPluginVpn(name: SRString, webview: WKWebView?) {
    Tauri.registerPlugin(webview: webview, name: name.toString(), plugin: VpnPlugin())
}
