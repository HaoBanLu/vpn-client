import Foundation

struct SubscriptionActive: Decodable {
    let id: UInt64
    let packageId: UInt64?
    let status: String?
    let expiresAt: String
    let trafficTotalGb: Double?
    let trafficUsedGb: Double?
    let package: PackageBrief?

    enum CodingKeys: String, CodingKey {
        case id, status, package
        case packageId = "package_id"
        case expiresAt = "expires_at"
        case trafficTotalGb = "traffic_total_gb"
        case trafficUsedGb = "traffic_used_gb"
    }
}

struct PackageBrief: Decodable {
    let name: String
    let devices: Int?
    let level: Int?
    let trafficGb: Double?
    let durationDays: Int?

    enum CodingKeys: String, CodingKey {
        case name, devices, level
        case trafficGb = "traffic_gb"
        case durationDays = "duration_days"
    }
}

struct SubscriptionUsage: Decodable {
    let used: Double
    let total: Double
    let remaining: Double
    let period: String?
}

struct PackageItem: Decodable, Identifiable {
    let id: UInt64
    let name: String
    let price: Double
    let trafficGb: Double
    let durationDays: Int
    let level: Int?
    let description: String?

    enum CodingKeys: String, CodingKey {
        case id, name, price, level, description
        case trafficGb = "traffic_gb"
        case durationDays = "duration_days"
    }
}

struct PackagesData: Decodable {
    let packages: [PackageItem]
}

struct OrderItem: Decodable, Identifiable {
    let id: UInt64
    let status: String
    let amount: Double
    let packageId: UInt64?
    let paymentMethod: String?
    let paidAt: String?
    let createdAt: String?

    enum CodingKeys: String, CodingKey {
        case id, status, amount
        case packageId = "package_id"
        case paymentMethod = "payment_method"
        case paidAt = "paid_at"
        case createdAt = "created_at"
    }
}

struct OrdersData: Decodable {
    let orders: [OrderItem]
}

struct TrafficSummary: Decodable {
    let usedGb: Double?
    let totalGb: Double?
    let remainingGb: Double?

    enum CodingKeys: String, CodingKey {
        case usedGb = "used_gb"
        case totalGb = "total_gb"
        case remainingGb = "remaining_gb"
    }
}

struct DailyTrafficItem: Decodable, Identifiable {
    var id: String { date }
    let date: String
    let usedGb: Double?

    enum CodingKeys: String, CodingKey {
        case date
        case usedGb = "used_gb"
    }
}

struct UserPreferencesData: Decodable {
    let ipBindingMode: String?
    let connectionScenario: String?
    let connectionScenarioLabel: String?

    enum CodingKeys: String, CodingKey {
        case ipBindingMode = "ip_binding_mode"
        case connectionScenario = "connection_scenario"
        case connectionScenarioLabel = "connection_scenario_label"
    }
}

struct ConnectDashboardData: Decodable {
    let isVip: Bool
    let expiresAt: String?
    let exitIp: String?
    let exitCountry: String?
    let exitCity: String?

    enum CodingKeys: String, CodingKey {
        case isVip = "is_vip"
        case expiresAt = "expires_at"
        case exitIp = "exit_ip"
        case exitCountry = "exit_country"
        case exitCity = "exit_city"
    }
}

struct ClientVersionData: Decodable {
    let hasUpdate: Bool
    let latestVersionName: String?
    let releaseNotes: String?

    enum CodingKeys: String, CodingKey {
        case hasUpdate = "has_update"
        case latestVersionName = "latest_version_name"
        case releaseNotes = "release_notes"
    }
}

struct RegisterRequest: Encodable {
    let email: String
    let password: String
    let emailCode: String?
    let deviceType: String
    let clientPlatform: String

    enum CodingKeys: String, CodingKey {
        case email, password
        case emailCode = "email_code"
        case deviceType = "device_type"
        case clientPlatform = "client_platform"
    }
}

struct ForgotPasswordRequest: Encodable {
    let email: String
}

struct ChangePasswordRequest: Encodable {
    let oldPassword: String
    let newPassword: String

    enum CodingKeys: String, CodingKey {
        case oldPassword = "old_password"
        case newPassword = "new_password"
    }
}

struct HeartbeatRequest: Encodable {
    let vpnConnected: Bool
    let probeStatus: String?
    let connectedNode: String?
    let exitIp: String?

    enum CodingKeys: String, CodingKey {
        case vpnConnected = "vpn_connected"
        case probeStatus = "probe_status"
        case connectedNode = "connected_node"
        case exitIp = "exit_ip"
    }
}

struct AppDebugLogEntry: Encodable {
    let level: String
    let category: String
    let message: String
}

struct AppDebugLogsRequest: Encodable {
    let entries: [AppDebugLogEntry]
    let deviceId: String?

    enum CodingKeys: String, CodingKey {
        case entries
        case deviceId = "device_id"
    }
}

// MARK: - 充值

struct USDTConfig: Decodable {
    let network: String?
    let exchangeRate: Double
    let minRechargeUsdt: Double
    let maxRechargeUsdt: Double
    let orderExpireMinutes: Int?
    let confirmTips: String?
    let quickAmountsUsdt: [Double]?
    let autoConfirmEnabled: Bool?
    let confirmMode: String?
    let scanIntervalSeconds: Int?
    let transferHintOptional: Bool?

    enum CodingKeys: String, CodingKey {
        case network
        case exchangeRate = "exchange_rate"
        case minRechargeUsdt = "min_recharge_usdt"
        case maxRechargeUsdt = "max_recharge_usdt"
        case orderExpireMinutes = "order_expire_minutes"
        case confirmTips = "confirm_tips"
        case quickAmountsUsdt = "quick_amounts_usdt"
        case autoConfirmEnabled = "auto_confirm_enabled"
        case confirmMode = "confirm_mode"
        case scanIntervalSeconds = "scan_interval_seconds"
        case transferHintOptional = "transfer_hint_optional"
    }
}

struct PaymentMethodsData: Decodable {
    let usdtEnabled: Bool
    let methods: [String]?
    let usdt: USDTConfig?

    enum CodingKeys: String, CodingKey {
        case usdtEnabled = "usdt_enabled"
        case methods, usdt
    }
}

struct RechargeOrderItem: Decodable, Identifiable {
    let id: UInt64
    let orderNo: String
    let status: String
    let requestedUsdt: Double
    let receivedUsdt: Double?
    let exchangeRate: Double
    let creditedCny: Double?
    let receiveAddress: String
    let fromAddress: String?
    let proofImageUrl: String?
    let txid: String?
    let rejectReason: String?
    let chainAutoConfirmed: Bool?
    let expiredAt: String?
    let paidAt: String?
    let createdAt: String?

    enum CodingKeys: String, CodingKey {
        case id, status, txid
        case orderNo = "order_no"
        case requestedUsdt = "requested_usdt"
        case receivedUsdt = "received_usdt"
        case exchangeRate = "exchange_rate"
        case creditedCny = "credited_cny"
        case receiveAddress = "receive_address"
        case fromAddress = "from_address"
        case proofImageUrl = "proof_image_url"
        case rejectReason = "reject_reason"
        case chainAutoConfirmed = "chain_auto_confirmed"
        case expiredAt = "expired_at"
        case paidAt = "paid_at"
        case createdAt = "created_at"
    }
}

struct RechargeOrdersData: Decodable {
    let orders: [RechargeOrderItem]
}

struct CreateRechargeData: Decodable {
    let order: RechargeOrderItem
    let confirmTips: String?
    let estimatedCny: Double?

    enum CodingKeys: String, CodingKey {
        case order
        case confirmTips = "confirm_tips"
        case estimatedCny = "estimated_cny"
    }
}

struct ProofUploadData: Decodable {
    let url: String
}

struct RechargeSubmitBody: Encodable {
    let fromAddress: String?
    let proofImageUrl: String?
    let txid: String?

    enum CodingKeys: String, CodingKey {
        case fromAddress = "from_address"
        case proofImageUrl = "proof_image_url"
        case txid
    }
}

struct CreateRechargeRequest: Encodable {
    let amountUsdt: Double

    enum CodingKeys: String, CodingKey {
        case amountUsdt = "amount_usdt"
    }
}

// MARK: - 工单 / 客服

struct TicketReplyItem: Decodable, Identifiable {
    let id: UInt64
    let ticketId: UInt64?
    let content: String
    let createdAt: String?

    enum CodingKeys: String, CodingKey {
        case id, content
        case ticketId = "ticket_id"
        case createdAt = "created_at"
    }
}

struct TicketItem: Decodable, Identifiable {
    let id: UInt64
    let title: String
    let content: String
    let status: String
    let priority: String
    let createdAt: String?
    let updatedAt: String?
    let replies: [TicketReplyItem]?

    enum CodingKeys: String, CodingKey {
        case id, title, content, status, priority, replies
        case createdAt = "created_at"
        case updatedAt = "updated_at"
    }
}

struct TicketsData: Decodable {
    let tickets: [TicketItem]
    let total: Int?
}

struct CreateTicketRequest: Encodable {
    let title: String
    let content: String
    let priority: String
}

struct TicketReplyRequest: Encodable {
    let content: String
}

struct SupportChannelItem: Decodable, Identifiable {
    var id: String { "\(type)-\(label)-\(url)" }
    let type: String
    let label: String
    let url: String
    let sortOrder: Int?

    enum CodingKeys: String, CodingKey {
        case type, label, url
        case sortOrder = "sort_order"
    }
}

struct SupportConfigData: Decodable {
    let enabled: Bool
    let ticketEnabled: Bool?
    let workHours: String?
    let description: String?
    let channels: [SupportChannelItem]

    enum CodingKeys: String, CodingKey {
        case enabled, description, channels
        case ticketEnabled = "ticket_enabled"
        case workHours = "work_hours"
    }
}

// MARK: - 设备会话

struct MemberSessionItem: Decodable, Identifiable {
    var id: String { sessionId }
    let sessionId: String
    let deviceName: String?
    let deviceType: String?
    let deviceModel: String?
    let isCurrent: Bool?
    let isOnline: Bool?
    let isVpnConnected: Bool?
    let ipBindingMode: String?
    let vpnConnectedNode: String?
    let exitIp: String?
    let lastActiveAt: String?

    enum CodingKeys: String, CodingKey {
        case sessionId = "session_id"
        case deviceName = "device_name"
        case deviceType = "device_type"
        case deviceModel = "device_model"
        case isCurrent = "is_current"
        case isOnline = "is_online"
        case isVpnConnected = "is_vpn_connected"
        case ipBindingMode = "ip_binding_mode"
        case vpnConnectedNode = "vpn_connected_node"
        case exitIp = "exit_ip"
        case lastActiveAt = "last_active_at"
    }
}

struct DeviceQuota: Decodable {
    let used: Int
    let max: Int
}

struct MemberSessionsData: Decodable {
    let sessions: [MemberSessionItem]
    let deviceQuota: DeviceQuota?

    enum CodingKeys: String, CodingKey {
        case sessions
        case deviceQuota = "device_quota"
    }
}

struct SubscriptionTokenData: Decodable {
    let token: String
}
