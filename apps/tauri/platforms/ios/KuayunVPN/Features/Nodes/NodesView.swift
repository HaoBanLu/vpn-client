import SwiftUI

struct NodesView: View {
    @EnvironmentObject private var auth: AuthStore
    @StateObject private var viewModel = NodesViewModel()
    @ObservedObject private var routeStore = ConnectRouteStore.shared
    @ObservedObject private var vpn = VPNController.shared
    @StateObject private var connectVM = ConnectViewModel()

    var body: some View {
        NavigationStack {
            Group {
                if viewModel.isLoading && viewModel.nodes.isEmpty && viewModel.lastError == nil {
                    ProgressView("加载节点…")
                } else if let err = viewModel.lastError, viewModel.nodes.isEmpty {
                    VStack(spacing: 12) {
                        Image(systemName: "wifi.exclamationmark").font(.largeTitle).foregroundStyle(.secondary)
                        Text("网络异常")
                        Text(err).font(.footnote).foregroundStyle(.secondary)
                        Button("重试") {
                            Task { await viewModel.load(token: auth.token) }
                        }
                    }
                } else if viewModel.filteredNodes.isEmpty {
                    VStack(spacing: 12) {
                        Image(systemName: "server.rack").font(.largeTitle).foregroundStyle(.secondary)
                        Text("暂无节点")
                    }
                } else {
                    List {
                        if !viewModel.regions.isEmpty {
                            Section {
                                ScrollView(.horizontal, showsIndicators: false) {
                                    HStack {
                                        FilterChip(title: "全部", selected: viewModel.regionFilter == nil) {
                                            viewModel.regionFilter = nil
                                        }
                                        ForEach(viewModel.regions, id: \.self) { code in
                                            FilterChip(title: code, selected: viewModel.regionFilter == code) {
                                                viewModel.regionFilter = code
                                            }
                                        }
                                    }
                                }
                            }
                        }
                        Section {
                            ForEach(viewModel.filteredNodes) { node in
                                Button {
                                    Task { await selectNode(node) }
                                } label: {
                                    HStack {
                                        VStack(alignment: .leading, spacing: 4) {
                                            Text(node.name)
                                            Text(node.regionName ?? node.region)
                                                .font(.caption).foregroundStyle(.secondary)
                                        }
                                        Spacer()
                                        if routeStore.selectedNode == node.name {
                                            Image(systemName: "checkmark.circle.fill").foregroundStyle(.green)
                                        }
                                        if let ms = viewModel.latencyMap[node.id] ?? node.latencyMs, ms > 0 {
                                            Text("\(ms) ms").font(.caption).foregroundStyle(.secondary)
                                        }
                                    }
                                }
                                .buttonStyle(.plain)
                            }
                        }
                    }
                }
            }
            .navigationTitle("节点")
            .toolbar {
                ToolbarItem(placement: .topBarTrailing) {
                    Button("测速") { Task { await viewModel.batchLatency(token: auth.token) } }
                }
            }
            .refreshable { await viewModel.load(token: auth.token) }
            .task { await viewModel.load(token: auth.token) }
        }
    }

    private func selectNode(_ node: NodeItem) async {
        routeStore.selectedNode = node.name
        if vpn.isConnected {
            await connectVM.reconnectIfConnected()
        }
    }
}

private struct FilterChip: View {
    let title: String
    let selected: Bool
    let action: () -> Void

    var body: some View {
        Button(action: action) {
            Text(title)
                .font(.caption)
                .padding(.horizontal, 10)
                .padding(.vertical, 6)
                .background(selected ? Color.accentColor.opacity(0.2) : Color.gray.opacity(0.15))
                .clipShape(Capsule())
        }
        .buttonStyle(.plain)
    }
}

@MainActor
final class NodesViewModel: ObservableObject {
    @Published private(set) var nodes: [NodeItem] = []
    @Published var regionFilter: String?
    @Published private(set) var isLoading = false
    @Published var lastError: String?
    @Published var latencyMap: [UInt64: Int] = [:]

    var regions: [String] {
        Array(Set(nodes.map(\.region))).sorted()
    }

    var filteredNodes: [NodeItem] {
        guard let regionFilter else { return nodes }
        return nodes.filter { $0.region == regionFilter }
    }

    func load(token: String?) async {
        guard let token else { lastError = "请先登录"; return }
        isLoading = true
        defer { isLoading = false }
        do {
            nodes = try await APIClient.shared.fetchNodes(token: token).nodes
                .sorted { $0.name.localizedCaseInsensitiveCompare($1.name) == .orderedAscending }
            lastError = nil
        } catch {
            lastError = error.localizedDescription
        }
    }

    func batchLatency(token: String?) async {
        guard let token, !nodes.isEmpty else { return }
        let ids = nodes.prefix(20).map(\.id)
        guard let data = try? await APIClient.shared.batchTestLatency(token: token, nodeIds: ids) else { return }
        var map: [UInt64: Int] = [:]
        for node in nodes {
            if let ms = data.results[String(node.id)] {
                map[node.id] = ms
            }
        }
        latencyMap = map
    }
}
