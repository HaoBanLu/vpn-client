import SwiftUI

struct TrafficView: View {
    @EnvironmentObject private var auth: AuthStore
    @State private var summary: TrafficSummary?
    @State private var daily: [DailyTrafficItem] = []
    @State private var error: String?

    var body: some View {
        List {
            if let summary {
                Section("汇总") {
                    LabeledContent("总流量", value: String(format: "%.2f GB", summary.usedGb))
                    LabeledContent("上传", value: String(format: "%.2f GB", summary.uploadGb))
                    LabeledContent("下载", value: String(format: "%.2f GB", summary.downloadGb))
                }
            }
            Section("近 30 天") {
                ForEach(daily) { item in
                    HStack {
                        Text(item.date)
                        Spacer()
                        Text(String(format: "%.2f GB", item.usedGb))
                            .foregroundStyle(.secondary)
                    }
                }
            }
            if let error {
                Section { Text(error).foregroundStyle(.red).font(.footnote) }
            }
        }
        .navigationTitle("流量统计")
        .task { await load() }
    }

    private func load() async {
        guard let token = auth.token else { return }
        do {
            summary = try await APIClient.shared.fetchTrafficSummary(token: token)
            daily = try await APIClient.shared.fetchTrafficDaily(token: token)
        } catch {
            self.error = error.localizedDescription
        }
    }
}
