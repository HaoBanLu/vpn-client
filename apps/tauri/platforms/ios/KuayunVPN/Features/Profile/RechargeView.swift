import SwiftUI
import PhotosUI

struct RechargeView: View {
    @EnvironmentObject private var auth: AuthStore
    @ObservedObject private var account = AccountStore.shared

    @State private var loading = false
    @State private var submitting = false
    @State private var usdtEnabled = false
    @State private var usdtConfig: USDTConfig?
    @State private var amountUsdt: Double = 50
    @State private var activeOrder: RechargeOrderItem?
    @State private var fromAddress = ""
    @State private var txid = ""
    @State private var proofImageUrl: String?
    @State private var selectedPhoto: PhotosPickerItem?
    @State private var uploadingProof = false
    @State private var message: String?
    @State private var pollTask: Task<Void, Never>?

    private var quickAmounts: [Double] {
        usdtConfig?.quickAmountsUsdt ?? [10, 20, 50, 100, 200]
    }

    private var isAutoMode: Bool {
        guard let cfg = usdtConfig else { return true }
        if cfg.confirmMode == "manual" { return false }
        if cfg.confirmMode == "auto" { return true }
        return cfg.autoConfirmEnabled != false
    }

    var body: some View {
        List {
            Section {
                if let user = auth.user {
                    LabeledContent("当前余额", value: String(format: "¥%.2f", user.balance))
                }
                if let cfg = usdtConfig {
                    Text("汇率 1U ≈ ¥\(String(format: "%.2f", cfg.exchangeRate))")
                        .font(.caption)
                        .foregroundStyle(.secondary)
                    Text(isAutoMode ? "TRC20 转账，自动确认到账" : "TRC20 转账，人工审核入账")
                        .font(.caption)
                        .foregroundStyle(.secondary)
                }
            }

            if !usdtEnabled {
                Section {
                    Text("充值暂未开放").foregroundStyle(.orange)
                }
            } else if activeOrder == nil {
                amountSelectionSection
            } else if let order = activeOrder {
                activeOrderSection(order)
            }

            if let message {
                Section {
                    Text(message).font(.footnote).foregroundStyle(.secondary)
                }
            }
        }
        .navigationTitle("充值")
        .toolbar {
            ToolbarItem(placement: .topBarTrailing) {
                NavigationLink("充值记录") { OrdersView(initialTab: .recharge) }
            }
        }
        .onDisappear { pollTask?.cancel() }
        .task { await load() }
        .onChange(of: selectedPhoto) { item in
            Task { await uploadProof(item) }
        }
    }

    @ViewBuilder
    private var amountSelectionSection: some View {
        Section("选择充值金额") {
            ScrollView(.horizontal, showsIndicators: false) {
                HStack {
                    ForEach(quickAmounts, id: \.self) { amount in
                        Button("\(Int(amount)) U") {
                            amountUsdt = amount
                        }
                        .buttonStyle(.bordered)
                        .tint(abs(amountUsdt - amount) < 0.01 ? .accentColor : .gray)
                    }
                }
            }
            HStack {
                TextField("金额", value: $amountUsdt, format: .number)
                    .keyboardType(.decimalPad)
                Text("USDT")
            }
            Button("创建充值单") {
                Task { await createOrder() }
            }
            .disabled(submitting)
        }
    }

    @ViewBuilder
    private func activeOrderSection(_ order: RechargeOrderItem) -> some View {
        Section("充值进度") {
            Text(FormatLabels.rechargeStatus(order.status, autoConfirmed: order.chainAutoConfirmed))
            Text("单号 \(order.orderNo)").font(.caption).foregroundStyle(.secondary)
        }

        Section("第 1 步 · 转账") {
            Text("向以下 TRC20 地址转账 \(String(format: "%.2f", order.requestedUsdt)) USDT")
                .font(.footnote)
            Text(order.receiveAddress)
                .font(.system(.caption, design: .monospaced))
                .textSelection(.enabled)
            Button("复制收款地址") {
                #if canImport(UIKit)
                UIPasteboard.general.string = order.receiveAddress
                message = "地址已复制"
                #endif
            }
            if let tips = usdtConfig?.confirmTips {
                Text(tips).font(.caption).foregroundStyle(.secondary)
            }
        }

        if order.status == "pending_transfer" {
            transferHintSection(order)
        } else if order.status == "paid" {
            Section {
                if let credited = order.creditedCny {
                    Text("已到账 ¥\(String(format: "%.2f", credited))")
                }
                if let paidAt = order.paidAt {
                    Text("到账时间：\(FormatLabels.formatDateTime(paidAt))")
                        .font(.caption)
                        .foregroundStyle(.secondary)
                }
            }
        } else if order.status == "rejected" {
            Section {
                Text(order.rejectReason ?? "充值被驳回").foregroundStyle(.red)
                Button("重新发起充值") {
                    activeOrder = nil
                    fromAddress = ""
                    txid = ""
                    proofImageUrl = nil
                }
            }
        } else if order.status == "submitted" {
            Section {
                Text(isAutoMode ? "正在确认到账，可在充值记录查看" : "等待人工审核")
            }
        }
    }

    @ViewBuilder
    private func transferHintSection(_ order: RechargeOrderItem) -> some View {
        if isAutoMode {
            Section("等待自动确认") {
                Text("转账后系统将自动检测链上到账。")
                    .font(.footnote)
            }
        }
        Section(isAutoMode ? "选填：加速匹配" : "第 2 步 · 提交凭证") {
            TextField("付款钱包地址", text: $fromAddress)
                .textInputAutocapitalization(.never)
            PhotosPicker(selection: $selectedPhoto, matching: .images) {
                HStack {
                    Text(proofImageUrl == nil ? "上传转账截图" : "截图已上传")
                    Spacer()
                    if uploadingProof { ProgressView() }
                }
            }
            TextField("交易哈希（选填）", text: $txid)
                .textInputAutocapitalization(.never)
            if isAutoMode {
                Button("保存加速匹配") {
                    Task { await saveHint(orderId: order.id) }
                }
                .disabled(submitting)
            } else {
                Button("提交审核") {
                    Task { await submitProof(orderId: order.id) }
                }
                .disabled(submitting || proofImageUrl == nil)
            }
            Button("取消充值单", role: .destructive) {
                Task { await cancelOrder(orderId: order.id) }
            }
        }
    }

    private func load() async {
        guard let token = auth.token else { return }
        loading = true
        defer { loading = false }
        do {
            async let methodsTask = APIClient.shared.fetchPaymentMethods(token: token)
            async let ordersTask = APIClient.shared.fetchRechargeOrders(token: token)
            let methods = try await methodsTask
            let orders = try await ordersTask
            await account.refresh(token: token)
            usdtEnabled = methods.usdtEnabled
            usdtConfig = methods.usdt
            if let first = usdtConfig?.quickAmountsUsdt?.first {
                amountUsdt = first
            }
            activeOrder = orders.orders.first { ["pending_transfer", "submitted"].contains($0.status) }
            restartPollingIfNeeded()
        } catch {
            message = error.localizedDescription
        }
    }

    private func createOrder() async {
        guard let token = auth.token else { return }
        submitting = true
        defer { submitting = false }
        do {
            let data = try await APIClient.shared.createRechargeOrder(token: token, amountUsdt: amountUsdt)
            activeOrder = data.order
            message = data.confirmTips
            restartPollingIfNeeded()
        } catch {
            message = error.localizedDescription
        }
    }

    private func uploadProof(_ item: PhotosPickerItem?) async {
        guard let token = auth.token, let item else { return }
        uploadingProof = true
        defer { uploadingProof = false }
        do {
            guard let data = try await item.loadTransferable(type: Data.self) else { return }
            proofImageUrl = try await APIClient.shared.uploadRechargeProof(token: token, imageData: data)
            message = "截图已上传"
        } catch {
            message = error.localizedDescription
        }
    }

    private func submitProof(orderId: UInt64) async {
        guard let token = auth.token else { return }
        submitting = true
        defer { submitting = false }
        do {
            let body = RechargeSubmitBody(
                fromAddress: fromAddress.isEmpty ? nil : fromAddress,
                proofImageUrl: proofImageUrl,
                txid: txid.isEmpty ? nil : txid
            )
            activeOrder = try await APIClient.shared.submitRechargeOrder(token: token, id: orderId, body: body)
            message = "已提交审核"
            restartPollingIfNeeded()
        } catch {
            message = error.localizedDescription
        }
    }

    private func saveHint(orderId: UInt64) async {
        guard let token = auth.token else { return }
        submitting = true
        defer { submitting = false }
        do {
            let body = RechargeSubmitBody(
                fromAddress: fromAddress.isEmpty ? nil : fromAddress,
                proofImageUrl: proofImageUrl,
                txid: txid.isEmpty ? nil : txid
            )
            activeOrder = try await APIClient.shared.saveRechargeTransferHint(token: token, id: orderId, body: body)
            message = "加速信息已保存"
        } catch {
            message = error.localizedDescription
        }
    }

    private func cancelOrder(orderId: UInt64) async {
        guard let token = auth.token else { return }
        do {
            try await APIClient.shared.cancelRechargeOrder(token: token, id: orderId)
            activeOrder = nil
            pollTask?.cancel()
            message = "充值单已取消"
        } catch {
            message = error.localizedDescription
        }
    }

    private func restartPollingIfNeeded() {
        pollTask?.cancel()
        guard let order = activeOrder else { return }
        let shouldPoll = order.status == "submitted" || (isAutoMode && order.status == "pending_transfer")
        guard shouldPoll else { return }
        pollTask = Task {
            let interval = max(5, (usdtConfig?.scanIntervalSeconds ?? 60) / 2)
            while !Task.isCancelled {
                try? await Task.sleep(nanoseconds: UInt64(interval) * 1_000_000_000)
                guard let token = auth.token, let current = activeOrder else { break }
                if let updated = try? await APIClient.shared.fetchRechargeOrder(token: token, id: current.id) {
                    activeOrder = updated
                    await account.refresh(token: token)
                    if ["paid", "rejected", "expired", "cancelled"].contains(updated.status) {
                        break
                    }
                }
            }
        }
    }
}

#if canImport(UIKit)
import UIKit
#endif
