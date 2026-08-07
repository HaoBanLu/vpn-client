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
                    if let used = summary.usedGb, let total = summary.totalGb {
                        LabeledContent("已用", value: String(format: "%.2f GB", used))
                        LabeledContent("总量", value: String(format: "%.2f GB", total))
                    }
                    if let remaining = summary.remainingGb {
                        LabeledContent("剩余", value: String(format: "%.2f GB", remaining))
                    }
                }
            }
            Section("近 30 天") {
                ForEach(daily) { item in
                    HStack {
                        Text(item.date)
                        Spacer()
                        Text(String(format: "%.2f GB", item.usedGb ?? 0))
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
