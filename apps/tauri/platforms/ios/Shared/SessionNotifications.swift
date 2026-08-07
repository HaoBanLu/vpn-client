import Foundation

extension Notification.Name {
    /// API 返回 401 / 业务码 401 时广播，主 App 应断开 VPN 并清理登录态。
    static let kuayunSessionExpired = Notification.Name("com.vpn.kuayun.sessionExpired")
}

enum SessionNotifier {
    static func notifySessionExpired() {
        NotificationCenter.default.post(name: .kuayunSessionExpired, object: nil)
    }
}
