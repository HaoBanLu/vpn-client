import SwiftUI

struct PurchaseOrdersView: View {
    var embedded = false
    @EnvironmentObject private var auth: AuthStore
    @State private var orders: [OrderItem] = []
    @State private var loading = false
    @State private var message: String?
    @State private var selected: OrderItem?

    var body: some View {
        Group {
            if orders.isEmpty && !loading {
                VStack(spacing: 12) {
                    Text("暂无套餐订单").foregroundStyle(.secondary)
                    NavigationLink("去购买套餐") { PackagesView() }
                        .buttonStyle(.borderedProminent)
                }
                .frame(maxWidth: .infinity, maxHeight: .infinity)
            } else {
                List(orders) { item in
                    Button {
                        selected = item
                    } label: {
                        VStack(alignment: .leading, spacing: 4) {
                            HStack {
                                Text("订单 #\(item.id)").font(.headline)
                                Spacer()
                                Text(FormatLabels.orderStatus(item.status))
                                    .font(.caption)
                                    .foregroundStyle(.secondary)
                            }
                            Text(String(format: "%.2f USDT", item.amount))
                                .font(.subheadline)
                            if let createdAt = item.createdAt {
                                Text(FormatLabels.formatDateTime(createdAt))
                                    .font(.caption)
                                    .foregroundStyle(.secondary)
                            }
                        }
                    }
                    .buttonStyle(.plain)
                }
            }
        }
        .modifier(EmbeddedNavTitle(title: "购买记录", embedded: embedded))
        .refreshable { await load() }
        .task { await load() }
        .overlay {
            if loading && orders.isEmpty {
                ProgressView()
            }
        }
        .alert("订单详情", isPresented: Binding(
            get: { selected != nil },
            set: { if !$0 { selected = nil } }
        )) {
            Button("关闭", role: .cancel) { selected = nil }
        } message: {
            if let item = selected {
                Text(detailText(for: item))
            }
        }
        .alert("提示", isPresented: Binding(get: { message != nil }, set: { if !$0 { message = nil } })) {
            Button("确定", role: .cancel) { message = nil }
        } message: {
            Text(message ?? "")
        }
    }

    private func detailText(for item: OrderItem) -> String {
        var lines = [
            "状态：\(FormatLabels.orderStatus(item.status))",
            "金额：\(String(format: "%.2f", item.amount)) USDT",
            "支付方式：\(item.paymentMethod ?? "-")",
            "创建：\(FormatLabels.formatDateTime(item.createdAt))",
            "支付：\(FormatLabels.formatDateTime(item.paidAt))",
        ]
        if let packageId = item.packageId {
            lines.append("套餐 ID：\(packageId)")
        }
        return lines.joined(separator: "\n")
    }

    private func load() async {
        guard let token = auth.token else { return }
        loading = true
        defer { loading = false }
        do {
            orders = try await APIClient.shared.fetchOrders(token: token).orders
        } catch {
            message = error.localizedDescription
        }
    }
}
