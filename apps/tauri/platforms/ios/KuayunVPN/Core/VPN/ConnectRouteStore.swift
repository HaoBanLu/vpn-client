import Foundation

/// 连接页与节点页共享的选路状态（持久化）。
@MainActor
final class ConnectRouteStore: ObservableObject {
    static let shared = ConnectRouteStore()

    @Published var selectedNode: String? {
        didSet { ConnectPreferencesStore.selectedNode = selectedNode }
    }

    private init() {
        selectedNode = ConnectPreferencesStore.selectedNode
    }
}
