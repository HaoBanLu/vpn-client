import SwiftUI

struct PrivacyView: View {
    var onAccepted: () -> Void

    var body: some View {
        NavigationStack {
            ScrollView {
                VStack(alignment: .leading, spacing: 16) {
                    Text("隐私政策")
                        .font(.title2.bold())
                    Text(
                        "我们仅收集账号、订阅与设备连接所必需的信息，用于提供 VPN 服务。"
                        + "流量日志粒度与会员 Web 端一致，不会向第三方出售你的个人数据。"
                        + "继续使用即表示你同意我们的隐私政策与服务条款。"
                    )
                    .font(.body)
                    .foregroundStyle(.secondary)
                    Button("同意并继续") {
                        PrivacyOnboardingStore.markAccepted()
                        onAccepted()
                    }
                    .buttonStyle(.borderedProminent)
                    .frame(maxWidth: .infinity)
                }
                .padding()
            }
            .navigationBarTitleDisplayMode(.inline)
        }
    }
}
