import SwiftUI

struct AboutView: View {
    @State private var versionInfo: String = "0.1.0 (1)"
    @State private var updateHint: String?

    var body: some View {
        List {
            Section("应用") {
                LabeledContent("版本", value: versionInfo)
            }
            if let updateHint {
                Section("更新") {
                    Text(updateHint).font(.footnote)
                }
            }
        }
        .navigationTitle("关于")
        .task { await checkUpdate() }
    }

    private func checkUpdate() async {
        do {
            let data = try await APIClient.shared.fetchClientVersion(platform: "ios", versionCode: 1, versionName: "0.1.0")
            if data.hasUpdate {
                updateHint = "新版本 \(data.latestVersionName ?? "-") 可用\n\(data.releaseNotes ?? "")"
            } else {
                updateHint = "当前已是最新版本"
            }
        } catch {
            updateHint = nil
        }
    }
}
