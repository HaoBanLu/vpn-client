import Foundation
import NetworkExtension

enum VPNControllerError: LocalizedError {
    case managerUnavailable
    case saveFailed(String)
    case startFailed(String)

    var errorDescription: String? {
        switch self {
        case .managerUnavailable:
            return "VPN 配置不可用"
        case let .saveFailed(reason):
            return "保存 VPN 配置失败: \(reason)"
        case let .startFailed(reason):
            return "启动 VPN 失败: \(reason)"
        }
    }
}

/// 封装 NETunnelProviderManager，对齐 Android VpnController 职责。
@MainActor
final class VPNController: ObservableObject {
    static let shared = VPNController()

    @Published private(set) var status: NEVPNStatus = .invalid
    @Published var lastError: String?

    private var manager: NETunnelProviderManager?
    private var statusObserver: NSObjectProtocol?

    private init() {}

    deinit {
        if let statusObserver {
            NotificationCenter.default.removeObserver(statusObserver)
        }
    }

    func prepare() async throws {
        if manager != nil { return }
        let managers = try await NETunnelProviderManager.loadAllFromPreferences()
        if let existing = managers.first(where: { $0.localizedDescription == Self.profileName }) {
            manager = existing
        } else {
            let created = NETunnelProviderManager()
            created.localizedDescription = Self.profileName
            let proto = NETunnelProviderProtocol()
            proto.providerBundleIdentifier = Self.tunnelBundleId
            proto.serverAddress = Self.profileName
            created.protocolConfiguration = proto
            created.isEnabled = true
            manager = created
        }
        bindStatusObserver()
        status = manager?.connection.status ?? .invalid
    }

    func connect(onDemandKillSwitch: Bool = true) async throws {
        try await prepare()
        guard let manager else { throw VPNControllerError.managerUnavailable }
        manager.isEnabled = true
        applyOnDemandKillSwitch(onDemandKillSwitch, manager: manager)
        do {
            try await manager.saveToPreferences()
            try await manager.loadFromPreferences()
        } catch {
            throw VPNControllerError.saveFailed(error.localizedDescription)
        }
        do {
            try manager.connection.startVPNTunnel()
        } catch {
            throw VPNControllerError.startFailed(error.localizedDescription)
        }
    }

    /// On-Demand VPN：断线后系统可自动重连，对齐 Android Kill Switch 意图（iOS 无 pf 级阻断）。
    private func applyOnDemandKillSwitch(_ enabled: Bool, manager: NETunnelProviderManager) {
        if enabled {
            let rule = NEOnDemandRuleConnect()
            rule.interfaceTypeMatch = .any
            manager.onDemandRules = [rule]
            manager.isOnDemandEnabled = true
        } else {
            manager.isOnDemandEnabled = false
            manager.onDemandRules = []
        }
    }

    func disconnect() {
        manager?.connection.stopVPNTunnel()
    }

    var isConnected: Bool {
        status == .connected || status == .connecting || status == .reasserting
    }

    private func bindStatusObserver() {
        if let statusObserver {
            NotificationCenter.default.removeObserver(statusObserver)
        }
        statusObserver = NotificationCenter.default.addObserver(
            forName: .NEVPNStatusDidChange,
            object: manager?.connection,
            queue: .main
        ) { [weak self] _ in
            Task { @MainActor in
                self?.status = self?.manager?.connection.status ?? .invalid
            }
        }
    }

    private static let profileName = "跨云 VPN"
    private static let tunnelBundleId = "com.vpn.kuayun.tunnel"
}
