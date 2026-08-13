import SwiftUI

struct PackagesView: View {
    @EnvironmentObject private var auth: AuthStore
    @State private var packages: [PackageItem] = []
    @State private var loading = false
    @State private var loadError: String?
    @State private var message: String?

    var body: some View {
        List(packages) { pkg in
            VStack(alignment: .leading, spacing: 6) {
                Text(pkg.name).font(.headline)
                Text("\(Int(pkg.trafficGb)) GB · \(pkg.durationDays) 天")
                    .font(.caption).foregroundStyle(.secondary)
                Text(String(format: "%.2f USDT", pkg.price))
                    .font(.subheadline)
                Button("余额购买") {
                    Task { await purchase(pkg) }
                }
                .buttonStyle(.borderedProminent)
                .disabled(loading)
            }
            .padding(.vertical, 4)
        }
        .navigationTitle("购买套餐")
        .overlay {
            if loading && packages.isEmpty {
                ProgressView()
            } else if packages.isEmpty {
                VStack(spacing: 12) {
                    if let loadError {
                        Text("网络异常").foregroundStyle(.red)
                        Text(loadError).font(.footnote).foregroundStyle(.secondary)
                        Button("重试") { Task { await load() } }
                    } else {
                        Text(message ?? "暂无套餐").foregroundStyle(.secondary)
                    }
                }
            }
        }
        .task { await load() }
    }

    private func load() async {
        guard let token = auth.token else { return }
        loading = true
        loadError = nil
        defer { loading = false }
        do {
            packages = try await APIClient.shared.fetchPackages(token: token).packages
        } catch {
            loadError = error.localizedDescription
        }
    }

    private func purchase(_ pkg: PackageItem) async {
        guard let token = auth.token else { return }
        loading = true
        defer { loading = false }
        do {
            let created = try await APIClient.shared.createOrder(token: token, packageId: pkg.id)
            try await APIClient.shared.payOrder(token: token, orderId: created.id)
            await AccountStore.shared.refresh(token: token)
            message = "购买成功"
        } catch {
            message = error.localizedDescription
        }
    }
}
