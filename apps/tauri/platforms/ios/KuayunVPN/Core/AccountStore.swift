import Foundation

struct AppNotification: Identifiable, Equatable {
    let id: UInt64
    let orderNo: String
    let message: String
    let type: String
}

@MainActor
final class AccountStore: ObservableObject {
    static let shared = AccountStore()

    @Published private(set) var subscription: SubscriptionActive?
    @Published private(set) var usage: SubscriptionUsage?
    @Published private(set) var isLoading = false
    @Published private(set) var didFetch = false
    @Published var lastError: String?
    @Published private(set) var notifications: [AppNotification] = []
    @Published private(set) var unreadNotificationCount = 0

    private var knownRechargeStatuses: [UInt64: String] = [:]
    private var pollingInitialized = false
    private var notificationTimer: Task<Void, Never>?
    private var inFlightRefresh: Task<Void, Never>?

    private init() {}

    func refresh(token: String) async {
        if let inFlightRefresh {
            await inFlightRefresh.value
            return
        }
        let task = Task { @MainActor in
            await self.performRefresh(token: token)
        }
        inFlightRefresh = task
        await task.value
        inFlightRefresh = nil
    }

    private func performRefresh(token: String) async {
        isLoading = true
        defer { isLoading = false }
        do {
            subscription = try await APIClient.shared.fetchActiveSubscription(token: token)
            if subscription != nil {
                usage = try await APIClient.shared.fetchUsage(token: token)
            } else {
                usage = nil
            }
            let recharge = try await APIClient.shared.fetchRechargeOrders(token: token)
            seedRechargeStatusesIfNeeded(recharge.orders)
            lastError = nil
            didFetch = true
        } catch {
            if (error as? APIClientError)?.isUnauthorized == true { return }
            lastError = error.localizedDescription
        }
    }

    func refreshUser(token: String) async {
        do {
            let user = try await APIClient.shared.fetchCurrentUser(token: token)
            AuthStore.shared.applySession(token: token, user: user)
        } catch {
            if (error as? APIClientError)?.isUnauthorized == true { return }
            lastError = error.localizedDescription
        }
    }

    func reset() {
        inFlightRefresh?.cancel()
        inFlightRefresh = nil
        stopNotificationPolling()
        subscription = nil
        usage = nil
        isLoading = false
        didFetch = false
        lastError = nil
        notifications = []
        unreadNotificationCount = 0
        knownRechargeStatuses = [:]
        pollingInitialized = false
    }

    var hasActiveSubscription: Bool {
        guard let subscription else { return false }
        return !subscription.expiresAt.isEmpty
    }

    func startNotificationPolling() {
        guard notificationTimer == nil else { return }
        notificationTimer = Task {
            await pollRechargeNotifications()
            while !Task.isCancelled {
                try? await Task.sleep(nanoseconds: 30_000_000_000)
                await pollRechargeNotifications()
            }
        }
    }

    func stopNotificationPolling() {
        notificationTimer?.cancel()
        notificationTimer = nil
    }

    func clearUnreadNotifications() {
        unreadNotificationCount = 0
    }

    private func seedRechargeStatusesIfNeeded(_ orders: [RechargeOrderItem]) {
        if !pollingInitialized {
            knownRechargeStatuses = Dictionary(uniqueKeysWithValues: orders.map { ($0.id, $0.status) })
            pollingInitialized = true
            return
        }
        for order in orders {
            if knownRechargeStatuses[order.id] == nil {
                knownRechargeStatuses[order.id] = order.status
            }
        }
    }

    private func notificationMessage(for status: String) -> String {
        switch status {
        case "paid": return "USDT 充值已到账，余额已更新"
        case "rejected": return "USDT 充值被驳回，请查看原因"
        default: return "充值订单状态已更新"
        }
    }

    private func pollRechargeNotifications() async {
        guard let token = AuthStore.shared.token else { return }
        do {
            let orders = try await APIClient.shared.fetchRechargeOrders(token: token).orders
            guard pollingInitialized else {
                seedRechargeStatusesIfNeeded(orders)
                return
            }
            var fresh: [AppNotification] = []
            for order in orders {
                let previous = knownRechargeStatuses[order.id]
                if previous == "submitted", order.status == "paid" || order.status == "rejected" {
                    fresh.append(AppNotification(
                        id: order.id,
                        orderNo: order.orderNo,
                        message: notificationMessage(for: order.status),
                        type: order.status
                    ))
                }
                knownRechargeStatuses[order.id] = order.status
            }
            if !fresh.isEmpty {
                let existing = Set(notifications.map { "\($0.id):\($0.type)" })
                let unique = fresh.filter { !existing.contains("\($0.id):\($0.type)") }
                notifications.append(contentsOf: unique)
                notifications = Array(notifications.suffix(10))
                unreadNotificationCount += unique.count
                await refreshUser(token: token)
            }
        } catch {
            // 轮询失败不打断主流程
        }
    }
}
