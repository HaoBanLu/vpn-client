import SwiftUI

struct SupportView: View {
    @EnvironmentObject private var auth: AuthStore
    @State private var config: SupportConfigData?
    @State private var loading = false
    @State private var message: String?

    var body: some View {
        Group {
            if loading && config == nil {
                ProgressView()
            } else if let config {
                if !config.enabled {
                    VStack {
                        Text("在线客服暂未开放").foregroundStyle(.secondary)
                    }
                    .frame(maxWidth: .infinity, maxHeight: .infinity)
                } else {
                    List {
                        if let description = config.description, !description.isEmpty {
                            Section {
                                Text(description)
                                if let hours = config.workHours, !hours.isEmpty {
                                    Text("服务时间：\(hours)")
                                        .font(.caption)
                                        .foregroundStyle(.secondary)
                                }
                            }
                        }
                        Section("联系方式") {
                            ForEach(config.channels) { channel in
                                Button {
                                    openURL(channel.url)
                                } label: {
                                    HStack {
                                        VStack(alignment: .leading) {
                                            Text(channel.label)
                                            Text(channel.type).font(.caption).foregroundStyle(.secondary)
                                        }
                                        Spacer()
                                        Image(systemName: "arrow.up.right")
                                            .font(.caption)
                                            .foregroundStyle(.secondary)
                                    }
                                }
                            }
                        }
                        if config.ticketEnabled != false {
                            Section {
                                NavigationLink("提交工单") {
                                    TicketsView()
                                }
                            }
                        }
                    }
                }
            } else {
                VStack {
                    Text("加载失败").foregroundStyle(.secondary)
                }
                .frame(maxWidth: .infinity, maxHeight: .infinity)
            }
        }
        .navigationTitle("在线客服")
        .refreshable { await load() }
        .task { await load() }
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
            config = try await APIClient.shared.fetchSupportConfig(token: token)
        } catch {
            message = error.localizedDescription
        }
    }

    private func openURL(_ raw: String) {
        guard let url = URL(string: raw) else {
            message = "无效链接"
            return
        }
        #if canImport(UIKit)
        UIApplication.shared.open(url)
        #endif
    }
}

#if canImport(UIKit)
import UIKit
#endif
