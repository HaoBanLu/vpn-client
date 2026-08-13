import SwiftUI

struct RechargeOrdersView: View {
    var embedded = false
    @EnvironmentObject private var auth: AuthStore
    @State private var orders: [RechargeOrderItem] = []
    @State private var loading = false
    @State private var selected: RechargeOrderItem?
    @State private var message: String?

    var body: some View {
        Group {
            if orders.isEmpty && !loading {
                VStack(spacing: 12) {
                    Text("暂无充值记录").foregroundStyle(.secondary)
                    NavigationLink("去充值") { RechargeView() }
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
                                Text(item.orderNo).font(.headline)
                                Spacer()
                                Text(FormatLabels.rechargeStatus(item.status, autoConfirmed: item.chainAutoConfirmed))
                                    .font(.caption)
                                    .foregroundStyle(.secondary)
                            }
                            Text(String(format: "%.2f USDT", item.requestedUsdt))
                            if let credited = item.creditedCny {
                                Text(String(format: "到账约 ¥%.2f", credited))
                                    .font(.caption)
                                    .foregroundStyle(.secondary)
                            }
                            if item.status == "rejected", let reason = item.rejectReason {
                                Text("驳回：\(reason)").font(.caption).foregroundStyle(.red)
                            }
                        }
                    }
                    .buttonStyle(.plain)
                }
            }
        }
        .modifier(EmbeddedNavTitle(title: "充值记录", embedded: embedded))
        .refreshable { await load() }
        .task { await load() }
        .sheet(item: $selected) { order in
            rechargeDetailSheet(order)
        }
        .alert("提示", isPresented: Binding(get: { message != nil }, set: { if !$0 { message = nil } })) {
            Button("确定", role: .cancel) { message = nil }
        } message: {
            Text(message ?? "")
        }
    }

    @ViewBuilder
    private func rechargeDetailSheet(_ order: RechargeOrderItem) -> some View {
        NavigationStack {
            List {
                LabeledContent("状态", value: FormatLabels.rechargeStatus(order.status, autoConfirmed: order.chainAutoConfirmed))
                LabeledContent("申请金额", value: String(format: "%.2f USDT", order.requestedUsdt))
                if let received = order.receivedUsdt {
                    LabeledContent("实收", value: String(format: "%.2f USDT", received))
                }
                LabeledContent("汇率", value: String(format: "1 USDT ≈ ¥%.2f", order.exchangeRate))
                if let credited = order.creditedCny {
                    LabeledContent("到账", value: String(format: "¥%.2f", credited))
                }
                LabeledContent("收款地址", value: order.receiveAddress)
                if let from = order.fromAddress {
                    LabeledContent("付款地址", value: from)
                }
                if let txid = order.txid {
                    LabeledContent("交易哈希", value: txid)
                }
                LabeledContent("创建", value: FormatLabels.formatDateTime(order.createdAt))
                if let paidAt = order.paidAt {
                    LabeledContent("到账时间", value: FormatLabels.formatDateTime(paidAt))
                }
                if order.status == "rejected", let reason = order.rejectReason {
                    Text("驳回原因：\(reason)").foregroundStyle(.red).font(.footnote)
                }
            }
            .navigationTitle(order.orderNo)
            .navigationBarTitleDisplayMode(.inline)
            .toolbar {
                ToolbarItem(placement: .topBarTrailing) {
                    Button("关闭") { selected = nil }
                }
            }
        }
    }

    private func load() async {
        guard let token = auth.token else { return }
        loading = true
        defer { loading = false }
        do {
            orders = try await APIClient.shared.fetchRechargeOrders(token: token).orders
        } catch {
            message = error.localizedDescription
        }
    }
}

extension RechargeOrderItem: Hashable {
    static func == (lhs: RechargeOrderItem, rhs: RechargeOrderItem) -> Bool { lhs.id == rhs.id }
    func hash(into hasher: inout Hasher) { hasher.combine(id) }
}
