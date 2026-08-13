import SwiftUI

struct OrdersView: View {
    enum Tab: String, CaseIterable {
        case recharge
        case purchase

        var title: String {
            switch self {
            case .recharge: return "充值"
            case .purchase: return "套餐"
            }
        }
    }

    var initialTab: Tab = .recharge
    @State private var tab: Tab

    init(initialTab: Tab = .recharge) {
        self.initialTab = initialTab
        _tab = State(initialValue: initialTab)
    }

    var body: some View {
        VStack(spacing: 0) {
            Picker("订单类型", selection: $tab) {
                ForEach(Tab.allCases, id: \.self) { item in
                    Text(item.title).tag(item)
                }
            }
            .pickerStyle(.segmented)
            .padding(.horizontal)
            .padding(.vertical, 8)

            Group {
                if tab == .recharge {
                    RechargeOrdersView(embedded: true)
                } else {
                    PurchaseOrdersView(embedded: true)
                }
            }
            .frame(maxWidth: .infinity, maxHeight: .infinity)
        }
        .navigationTitle("订单")
        .navigationBarTitleDisplayMode(.inline)
    }
}

struct EmbeddedNavTitle: ViewModifier {
    let title: String
    var embedded = false

    @ViewBuilder
    func body(content: Content) -> some View {
        if embedded {
            content
        } else {
            content.navigationTitle(title)
        }
    }
}
