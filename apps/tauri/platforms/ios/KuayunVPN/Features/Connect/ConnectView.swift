import SwiftUI

struct ConnectView: View {
    @EnvironmentObject private var auth: AuthStore
    @StateObject private var viewModel = ConnectViewModel()
    @ObservedObject private var vpn = VPNController.shared
    @ObservedObject private var routeStore = ConnectRouteStore.shared

    var body: some View {
        NavigationStack {
            List {
                Section("连接状态") {
                    LabeledContent("状态", value: viewModel.connectionLabel)
                    if let dash = viewModel.dashboardText {
                        LabeledContent("出口", value: dash)
                    }
                    if let usage = viewModel.usageText {
                        LabeledContent("流量", value: usage)
                    }
                    if let meta = TunnelMetaStore.read() {
                        if let region = meta.region, !region.isEmpty {
                            LabeledContent("地区", value: region)
                        }
                        if let node = meta.node, !node.isEmpty {
                            LabeledContent("节点", value: node)
                        }
                    }
                }

                Section("选路") {
                    Picker("场景", selection: Binding(
                        get: { viewModel.connectionScenario },
                        set: { viewModel.setScenario($0) }
                    )) {
                        ForEach(ConnectionScenario.allCases) { s in
                            Text(s.label).tag(s)
                        }
                    }
                    .disabled(vpn.isConnected || viewModel.isBusy)

                    if viewModel.regions.isEmpty {
                        Text("暂无可用地区").foregroundStyle(.secondary)
                    } else {
                        Picker("地区", selection: Binding(
                            get: { viewModel.selectedRegion ?? "" },
                            set: { viewModel.selectRegion($0) }
                        )) {
                            ForEach(viewModel.regions) { region in
                                Text(region.name ?? region.code).tag(region.code)
                            }
                        }
                        .disabled(vpn.isConnected || viewModel.isBusy)
                    }

                    if let node = routeStore.selectedNode, !node.isEmpty {
                        LabeledContent("已选节点", value: node)
                    } else {
                        Text("未选节点时使用智能选路").font(.footnote).foregroundStyle(.secondary)
                    }
                }

                Section {
                    if vpn.isConnected {
                        Button("断开 VPN", role: .destructive) { viewModel.disconnect() }
                    } else {
                        Button { Task { await viewModel.connect() } } label: {
                            if viewModel.isBusy {
                                HStack { ProgressView(); Text("连接中…") }
                            } else {
                                Text("连接 VPN")
                            }
                        }
                        .disabled(viewModel.isBusy || viewModel.selectedRegion == nil)
                    }
                }

                if let error = viewModel.lastError {
                    Section { Text(error).foregroundStyle(.red).font(.footnote) }
                }
            }
            .navigationTitle("连接")
            .task { await viewModel.onAppear() }
            .onChange(of: vpn.status) { _, newValue in
                viewModel.syncVpnStatus(newValue)
            }
        }
    }
}
