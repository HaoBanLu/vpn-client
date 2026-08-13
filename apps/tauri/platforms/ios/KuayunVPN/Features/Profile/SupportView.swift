import SwiftUI

struct SupportView: View {
    @EnvironmentObject private var auth: AuthStore
    @State private var config: SupportConfigData?
    @State private var loading = false
    @State private var message: String?

    private var telegramChannel: SupportChannelItem? {
        config?.channels.first { $0.type == "telegram" }
    }

    private var otherChannels: [SupportChannelItem] {
        guard let channels = config?.channels else { return [] }
        var skippedPrimary = false
        return channels.filter { channel in
            if channel.type == "ticket" { return false }
            if !skippedPrimary, channel.type == "telegram" {
                skippedPrimary = true
                return false
            }
            return true
        }
    }

    private var showTicketEntry: Bool {
        guard let config, config.enabled else { return false }
        return config.ticketEnabled != false
    }

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

                        if let telegramChannel {
                            Section {
                                Button {
                                    openURL(telegramChannel.url)
                                } label: {
                                    VStack(alignment: .leading, spacing: 4) {
                                        Text("Telegram 联系客服")
                                            .font(.headline)
                                        Text(telegramHint(telegramChannel))
                                            .font(.caption)
                                            .foregroundStyle(.secondary)
                                    }
                                    .frame(maxWidth: .infinity, alignment: .leading)
                                }
                            }
                        }

                        if !otherChannels.isEmpty {
                            Section("其它渠道") {
                                ForEach(otherChannels) { channel in
                                    Button {
                                        openURL(channel.url)
                                    } label: {
                                        HStack {
                                            VStack(alignment: .leading) {
                                                Text(shortTitle(channel))
                                                Text(channelHint(channel))
                                                    .font(.caption)
                                                    .foregroundStyle(.secondary)
                                            }
                                            Spacer()
                                            Image(systemName: "arrow.up.right")
                                                .font(.caption)
                                                .foregroundStyle(.secondary)
                                        }
                                    }
                                }
                            }
                        }

                        if showTicketEntry {
                            Section {
                                NavigationLink("提交工单") {
                                    TicketsView()
                                }
                                Text("不方便使用 Telegram 时，可提交工单由客服回复")
                                    .font(.caption)
                                    .foregroundStyle(.secondary)
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

    private func telegramHint(_ channel: SupportChannelItem) -> String {
        let label = channel.label.trimmingCharacters(in: .whitespacesAndNewlines)
        return label.isEmpty ? "将打开 Telegram" : label
    }

    private func shortTitle(_ channel: SupportChannelItem) -> String {
        switch channel.type {
        case "telegram": return "客服"
        case "telegram_group": return "群组"
        case "telegram_channel": return "频道"
        case "email": return "邮箱"
        case "web": return "网页"
        default:
            let label = channel.label.trimmingCharacters(in: .whitespacesAndNewlines)
            return label.isEmpty ? channel.type : label
        }
    }

    private func channelHint(_ channel: SupportChannelItem) -> String {
        switch channel.type {
        case "telegram": return "打开 Telegram 私聊客服"
        case "telegram_group": return "加入官方交流群"
        case "telegram_channel": return "关注官方频道公告"
        case "email": return "发送邮件联系客服"
        case "web": return "打开网页客服"
        default: return "在外部应用中打开"
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
