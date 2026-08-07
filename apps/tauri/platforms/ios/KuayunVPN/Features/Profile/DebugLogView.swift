import SwiftUI

struct DebugLogView: View {
    @EnvironmentObject private var auth: AuthStore
    @State private var status: String?

    var body: some View {
        Form {
            Section {
                Button("上传测试诊断日志") {
                    Task { await upload() }
                }
            }
            if let status {
                Section { Text(status).font(.footnote) }
            }
        }
        .navigationTitle("诊断日志")
    }

    private func upload() async {
        guard let token = auth.token else { return }
        do {
            let entries = [
                AppDebugLogEntry(level: "info", category: "ios", message: "manual debug upload from iOS app"),
            ]
            try await APIClient.shared.uploadAppDebugLogs(token: token, entries: entries)
            status = "已上传 \(entries.count) 条"
        } catch {
            status = error.localizedDescription
        }
    }
}
