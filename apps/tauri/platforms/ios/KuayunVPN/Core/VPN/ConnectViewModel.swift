import Foundation
import NetworkExtension

@MainActor
final class ConnectViewModel: ObservableObject {
    @Published private(set) var regions: [RegionItem] = []
    @Published private(set) var selectedRegion: String?
    @Published var connectionScenario: ConnectionScenario = ConnectPreferencesStore.connectionScenario
    @Published var routeMode: AppRouteMode = ConnectPreferencesStore.routeMode
    @Published private(set) var connectionLabel: String = "未连接"
    @Published private(set) var isBusy = false
    @Published private(set) var dashboardText: String?
    @Published private(set) var usageText: String?
    @Published var lastError: String?

    private let auth: AuthStore
    private let account = AccountStore.shared
    private let vpn = VPNController.shared

    init(auth: AuthStore = .shared) {
        self.auth = auth
        selectedRegion = ConnectPreferencesStore.selectedRegion
    }

    func onAppear() async {
        await refreshRegions()
        if let token = auth.token {
            await account.refresh(token: token)
            updateUsageText()
            await loadPreferences(token: token)
        }
        updateConnectionLabel(vpn.status)
    }

    func refreshRegions() async {
        guard let token = auth.token else { return }
        do {
            let data = try await APIClient.shared.fetchRegions(token: token)
            regions = data.regions.sorted { ($0.name ?? $0.code) < ($1.name ?? $1.code) }
            if selectedRegion == nil {
                selectedRegion = ConnectPreferencesStore.selectedRegion ?? regions.first?.code
            }
        } catch {
            lastError = error.localizedDescription
        }
    }

    private func loadPreferences(token: String) async {
        do {
            let pref = try await APIClient.shared.fetchUserPreferences(token: token)
            if let raw = pref.connectionScenario {
                connectionScenario = ConnectionScenario.normalize(raw)
                ConnectPreferencesStore.connectionScenario = connectionScenario
            }
        } catch {
            // 非致命
        }
    }

    func selectRegion(_ code: String) {
        selectedRegion = code
        ConnectPreferencesStore.selectedRegion = code
    }

    func setScenario(_ scenario: ConnectionScenario) {
        connectionScenario = scenario
        ConnectPreferencesStore.connectionScenario = scenario
        guard let token = auth.token else { return }
        Task {
            _ = try? await APIClient.shared.updateUserPreferences(token: token, connectionScenario: scenario.rawValue)
        }
    }

    func setRouteMode(_ mode: AppRouteMode) {
        routeMode = mode
        ConnectPreferencesStore.routeMode = mode
    }

    func connect() async {
        guard let token = auth.token else {
            lastError = "请先登录"
            return
        }
        guard !isBusy else { return }
        await account.refresh(token: token)
        if account.lastError != nil && account.subscription == nil {
            lastError = account.lastError ?? "网络异常，请稍后重试"
            return
        }
        guard account.hasActiveSubscription else {
            lastError = "暂无有效套餐，请先购买"
            return
        }

        isBusy = true
        lastError = nil
        defer { isBusy = false }

        let nodeName = ConnectPreferencesStore.selectedNode
        let nodeMeta = await fetchNodeMeta(token: token, nodeName: nodeName)
        let resolved = ConnectionScenarioResolver.resolve(
            scenario: connectionScenario,
            nodeRegion: nodeMeta?.region ?? selectedRegion,
            accessMode: nodeMeta?.accessMode
        )

        do {
            let config = try await APIClient.shared.fetchClientConfig(
                token: token,
                region: selectedRegion,
                node: nodeName,
                routeMode: resolved.routeMode.rawValue,
                profile: resolved.profile.rawValue
            )
            let sanitized = try ClashConfigSanitizer.prepareForTunnel(rawYaml: config.config)
            try VPNConfigStore.writeConfig(sanitized)
            let meta = TunnelMeta(
                region: config.region,
                node: config.node,
                routeMode: resolved.routeMode.rawValue,
                profile: resolved.profile.rawValue,
                updatedAt: Date()
            )
            try TunnelMetaStore.write(meta)
            if let exit = await ExitIpProbe.probe() {
                PrivacyLeakProbe.saveBaselineIp(exit.ip)
            }
            try await vpn.connect(onDemandKillSwitch: true)
            updateConnectionLabel(vpn.status)
            await refreshDashboard(token: token)
            try? await APIClient.shared.sendHeartbeat(
                token: token,
                payload: HeartbeatRequest(
                    vpnConnected: true,
                    probeStatus: "ok",
                    connectedNode: config.node ?? nodeName,
                    exitIp: nil
                )
            )
        } catch {
            lastError = error.localizedDescription
        }
    }

    func disconnect() {
        vpn.disconnect()
        VPNConfigStore.clearConfig()
        updateConnectionLabel(vpn.status)
        if let token = auth.token {
            Task {
                try? await APIClient.shared.sendHeartbeat(
                    token: token,
                    payload: HeartbeatRequest(vpnConnected: false, probeStatus: nil, connectedNode: nil, exitIp: nil)
                )
            }
        }
    }

    func reconnectIfConnected() async {
        guard vpn.isConnected else { return }
        disconnect()
        try? await Task.sleep(nanoseconds: 500_000_000)
        await connect()
    }

    func syncVpnStatus(_ status: NEVPNStatus) {
        updateConnectionLabel(status)
    }

    private func fetchNodeMeta(token: String, nodeName: String?) async -> NodeItem? {
        guard let nodeName, !nodeName.isEmpty else { return nil }
        guard let nodes = try? await APIClient.shared.fetchNodes(token: token).nodes else { return nil }
        return nodes.first { $0.name == nodeName }
    }

    private func refreshDashboard(token: String) async {
        do {
            let dash = try await APIClient.shared.fetchConnectDashboard(
                token: token,
                selectedNode: ConnectPreferencesStore.selectedNode
            )
            if let ip = dash.exitIp {
                dashboardText = "出口 \(ip) \(dash.exitCountry ?? "")"
            } else {
                dashboardText = dash.isVip ? "VIP 已连接" : nil
            }
        } catch {
            dashboardText = nil
        }
    }

    private func updateUsageText() {
        guard let usage = account.usage else {
            usageText = nil
            return
        }
        usageText = String(format: "剩余 %.1f / %.1f GB", usage.remaining, usage.total)
    }

    private func updateConnectionLabel(_ status: NEVPNStatus) {
        switch status {
        case .invalid: connectionLabel = "未配置"
        case .disconnected: connectionLabel = "未连接"
        case .connecting: connectionLabel = "连接中…"
        case .connected: connectionLabel = "已连接"
        case .reasserting: connectionLabel = "重连中…"
        case .disconnecting: connectionLabel = "断开中…"
        @unknown default: connectionLabel = "未知状态"
        }
    }
}
