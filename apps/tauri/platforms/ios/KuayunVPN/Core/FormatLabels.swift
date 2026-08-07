import Foundation

enum FormatLabels {
    static func orderStatus(_ status: String) -> String {
        switch status {
        case "pending": return "待支付"
        case "paid": return "已支付"
        case "cancelled": return "已取消"
        case "expired": return "已过期"
        default: return status
        }
    }

    static func rechargeStatus(_ status: String, autoConfirmed: Bool?) -> String {
        switch status {
        case "pending_transfer": return "待转账"
        case "submitted": return autoConfirmed == true ? "确认中" : "待审核"
        case "paid": return autoConfirmed == true ? "已自动到账" : "已到账"
        case "rejected": return "已驳回"
        case "expired": return "已过期"
        case "cancelled": return "已取消"
        default: return status
        }
    }

    static func ticketStatus(_ status: String) -> String {
        switch status {
        case "open": return "待处理"
        case "in_progress": return "处理中"
        case "resolved": return "已解决"
        case "closed": return "已关闭"
        default: return status
        }
    }

    static func ticketPriority(_ priority: String) -> String {
        switch priority {
        case "low": return "低"
        case "normal": return "普通"
        case "high": return "高"
        case "urgent": return "紧急"
        default: return priority
        }
    }

    static func formatDateTime(_ raw: String?) -> String {
        guard let raw, !raw.isEmpty else { return "-" }
        return String(raw.prefix(16)).replacingOccurrences(of: "T", with: " ")
    }
}
