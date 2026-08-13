import SwiftUI

struct HelpView: View {
    @EnvironmentObject private var auth: AuthStore
    @State private var subscriptionUrl: String?
    @State private var loading = false
    @State private var message: String?
    @State private var isSuccess = false

    var body: some View {
        List {
            Section {
                Text("若 App 无法连接，可导出 Clash 订阅链接在第三方客户端使用。")
                    .font(.footnote)
                    .foregroundStyle(.secondary)
            }

            Section {
                Button {
                    Task { await loadSubscriptionUrl() }
                } label: {
                    HStack {
                        Text("生成 Clash 订阅链接")
                        Spacer()
                        if loading { ProgressView() }
                    }
                }
                .disabled(loading)
            }

            if let subscriptionUrl {
                Section("订阅链接") {
                    Text(subscriptionUrl)
                        .font(.system(.caption, design: .monospaced))
                        .textSelection(.enabled)
                    Button("复制订阅链接") {
                        #if canImport(UIKit)
                        UIPasteboard.general.string = subscriptionUrl
                        isSuccess = true
                        message = "订阅链接已复制"
                        #endif
                    }
                }
            }

            if let message {
                Section {
                    Text(message)
                        .foregroundStyle(isSuccess ? .green : .red)
                        .font(.footnote)
                }
            }
        }
        .navigationTitle("导出订阅")
    }

    private func loadSubscriptionUrl() async {
        guard let token = auth.token else { return }
        loading = true
        isSuccess = false
        message = nil
        defer { loading = false }
        do {
            let data = try await APIClient.shared.fetchSubscriptionToken(token: token)
            let clashURL = APIConfig.endpoint("subscription/clash")
            var clashComponents = URLComponents(url: clashURL, resolvingAgainstBaseURL: false)
            clashComponents?.queryItems = [URLQueryItem(name: "token", value: data.token)]
            subscriptionUrl = clashComponents?.url?.absoluteString
        } catch {
            message = error.localizedDescription
        }
    }
}

#if canImport(UIKit)
import UIKit
#endif
