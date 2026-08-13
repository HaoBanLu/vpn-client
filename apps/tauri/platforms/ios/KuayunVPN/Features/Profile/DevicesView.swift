import SwiftUI

struct DevicesView: View {
    @EnvironmentObject private var auth: AuthStore
    @State private var sessions: [MemberSessionItem] = []
    @State private var quotaUsed = 0
    @State private var quotaMax = 1
    @State private var loading = false
    @State private var revokingId: String?
    @State private var message: String?

    var body: some View {
        Group {
            if sessions.isEmpty && !loading {
                VStack {
                    Text("暂无登录设备").foregroundStyle(.secondary)
                }
                .frame(maxWidth: .infinity, maxHeight: .infinity)
            } else {
                List {
                    Section {
                        Text("设备配额 \(quotaUsed)/\(quotaMax)")
                            .font(.footnote)
                            .foregroundStyle(.secondary)
                    }
                    ForEach(sessions) { item in
                        HStack(alignment: .top) {
                            VStack(alignment: .leading, spacing: 4) {
                                Text(item.deviceModel ?? item.deviceName ?? "未知设备")
                                    .font(.headline)
                                HStack(spacing: 8) {
                                    if let type = item.deviceType {
                                        Text(type).font(.caption2).padding(.horizontal, 6).padding(.vertical, 2)
                                            .background(Color.blue.opacity(0.15)).clipShape(Capsule())
                                    }
                                    if item.isCurrent == true {
                                        Text("当前设备").font(.caption2).padding(.horizontal, 6).padding(.vertical, 2)
                                            .background(Color.orange.opacity(0.15)).clipShape(Capsule())
                                    }
                                    if item.isOnline == true {
                                        Text("在线").font(.caption2).padding(.horizontal, 6).padding(.vertical, 2)
                                            .background(Color.green.opacity(0.15)).clipShape(Capsule())
                                    }
                                }
                                if let node = item.vpnConnectedNode, !node.isEmpty {
                                    Text(node).font(.caption).foregroundStyle(.secondary)
                                }
                                if let last = item.lastActiveAt {
                                    Text("最后活跃：\(FormatLabels.formatDateTime(last))")
                                        .font(.caption)
                                        .foregroundStyle(.secondary)
                                }
                            }
                            Spacer()
                            if item.isCurrent != true {
                                Button("踢下线") {
                                    Task { await revoke(item.sessionId) }
                                }
                                .font(.caption)
                                .disabled(revokingId == item.sessionId)
                            }
                        }
                        .padding(.vertical, 4)
                    }
                }
            }
        }
        .navigationTitle("登录设备")
        .refreshable { await load() }
        .task { await load() }
        .overlay {
            if loading && sessions.isEmpty { ProgressView() }
        }
        .alert("提示", isPresented: Binding(get: { message != nil }, set: { if !$0 { message = nil } })) {
            Button("确定", role: .cancel) { message = nil }
        } message: {
            Text(message ?? "")
        }
    }

    private func load() async {
        guard let token = auth.token else { return }
        loading = true
        defer { loading = false }
        do {
            let data = try await APIClient.shared.fetchMySessions(token: token)
            sessions = data.sessions
            quotaUsed = data.deviceQuota?.used ?? 0
            quotaMax = data.deviceQuota?.max ?? 1
        } catch {
            message = error.localizedDescription
        }
    }

    private func revoke(_ sessionId: String) async {
        guard let token = auth.token else { return }
        revokingId = sessionId
        defer { revokingId = nil }
        do {
            let data = try await APIClient.shared.revokeMySession(token: token, sessionId: sessionId)
            sessions = data.sessions
            quotaUsed = data.deviceQuota?.used ?? quotaUsed
            message = "设备已踢下线"
        } catch {
            message = error.localizedDescription
        }
    }
}
