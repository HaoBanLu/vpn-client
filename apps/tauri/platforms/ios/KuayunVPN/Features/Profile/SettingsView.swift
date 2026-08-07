import SwiftUI

struct SettingsView: View {
    @EnvironmentObject private var auth: AuthStore
    @State private var scenario = ConnectPreferencesStore.connectionScenario
    @State private var routeMode = ConnectPreferencesStore.routeMode
    @State private var probeRunning = false
    @State private var probeMessage: String?
    @State private var probeHistory: [PrivacyProbeHistoryEntry] = []

    var body: some View {
        List {
            Section("连接场景") {
                Picker("场景", selection: $scenario) {
                    ForEach(ConnectionScenario.allCases) { s in
                        Text(s.label).tag(s)
                    }
                }
                .onChange(of: scenario) { _, newValue in
                    ConnectPreferencesStore.connectionScenario = newValue
                    syncScenarioToServer(newValue)
                }
                Picker("路由模式", selection: $routeMode) {
                    ForEach(AppRouteMode.allCases) { m in
                        Text(m.label).tag(m)
                    }
                }
                .onChange(of: routeMode) { _, newValue in
                    ConnectPreferencesStore.routeMode = newValue
                }
            }
            Section("隐私自检") {
                Button(probeRunning ? "自检中…" : "运行泄露自检") {
                    Task { await runProbe() }
                }
                .disabled(probeRunning || !VPNController.shared.isConnected)
                if let probeMessage {
                    Text(probeMessage).font(.footnote).foregroundStyle(.secondary)
                }
                if !probeHistory.isEmpty {
                    ForEach(probeHistory.prefix(5)) { entry in
                        HStack(alignment: .top, spacing: 8) {
                            Circle()
                                .fill(entry.passed ? Color.green : Color.orange)
                                .frame(width: 8, height: 8)
                                .padding(.top, 5)
                            VStack(alignment: .leading, spacing: 2) {
                                Text(entry.summary).font(.footnote)
                                Text(formatProbeTime(entry.atMillis))
                                    .font(.caption2)
                                    .foregroundStyle(.secondary)
                            }
                        }
                    }
                    Button("清空记录", role: .destructive) {
                        PrivacyProbeHistoryStore.clear()
                        probeHistory = []
                    }
                    .font(.footnote)
                }
            }
            Section("Kill Switch") {
                Text("iOS 使用 On-Demand VPN，意外断线后系统可自动重连隧道。")
                    .font(.footnote).foregroundStyle(.secondary)
            }
        }
        .navigationTitle("连接与隐私")
        .onAppear { probeHistory = PrivacyProbeHistoryStore.load() }
    }

    private func syncScenarioToServer(_ value: ConnectionScenario) {
        guard let token = auth.token else { return }
        Task {
            _ = try? await APIClient.shared.updateUserPreferences(token: token, connectionScenario: value.rawValue)
        }
    }

    private func runProbe() async {
        probeRunning = true
        defer { probeRunning = false }
        let result = await PrivacyLeakProbe.run()
        PrivacyProbeHistoryStore.append(result: result)
        probeHistory = PrivacyProbeHistoryStore.load()
        probeMessage = PrivacyLeakProbe.formatMessage(result)
    }

    private func formatProbeTime(_ millis: TimeInterval) -> String {
        let date = Date(timeIntervalSince1970: millis / 1000)
        let formatter = DateFormatter()
        formatter.dateFormat = "MM-dd HH:mm"
        return formatter.string(from: date)
    }
}
