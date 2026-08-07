package com.vpn.member.data.api

data class ApiResponse<T>(
    val code: Int,
    val message: String,
    val data: T? = null,
    val error: String? = null,
    val app_code: String? = null,
    val retryable: Boolean? = null,
    val trace_id: String? = null,
)

data class RegisterRequest(
    val email: String,
    val password: String,
    val email_code: String? = null,
    val device_id: String? = null,
    val device_name: String? = null,
    val device_type: String? = null,
    val app_version: String? = null,
    val device_brand: String? = null,
    val device_model: String? = null,
    val os_name: String? = null,
    val os_version: String? = null,
    val client_platform: String? = null,
    val login_source: String? = null,
)

data class RegistrationConfigData(
    val registration_enabled: Boolean = true,
    val email_verification_required: Boolean = false,
    val password_reset_enabled: Boolean = true,
    val send_cooldown_seconds: Int = 60,
    val code_length: Int = 6,
    val email_configured: Boolean = false,
    val member_session_permanent: Boolean? = null,
    val member_session_ttl_hours: Int? = null,
) {
    fun sessionHint(): String? {
        if (member_session_permanent == true || member_session_ttl_hours == 0) {
            return "登录后长期有效，除非在其他设备登录或被管理员下线"
        }
        val hours = member_session_ttl_hours ?: return null
        if (hours <= 0) return null
        return if (hours % 24 == 0) {
            "登录有效期 ${hours / 24} 天，到期后需重新登录"
        } else {
            "登录有效期 $hours 小时，到期后需重新登录"
        }
    }
}

data class SessionConfigData(
    val member_session_permanent: Boolean = true,
    val member_session_ttl_hours: Int = 0,
)

data class SendEmailCodeRequest(
    val email: String,
    val purpose: String,
)

data class ForgotPasswordRequest(
    val email: String,
)

data class ResetPasswordRequest(
    val email: String,
    val email_code: String,
    val new_password: String,
    val device_id: String? = null,
    val device_name: String? = null,
    val device_type: String? = null,
    val app_version: String? = null,
    val device_brand: String? = null,
    val device_model: String? = null,
    val os_name: String? = null,
    val os_version: String? = null,
    val client_platform: String? = null,
    val login_source: String? = null,
)

data class LoginRequest(
    val email: String,
    val password: String,
    val device_id: String? = null,
    val device_name: String? = null,
    val device_type: String? = null,
    val app_version: String? = null,
    val device_brand: String? = null,
    val device_model: String? = null,
    val os_name: String? = null,
    val os_version: String? = null,
    val client_platform: String? = null,
    val login_source: String? = null,
)
data class ChangePasswordRequest(val old_password: String, val new_password: String)

data class SessionHeartbeatData(
    val server_time: String? = null,
    val subscription_active: Boolean = true,
    val force_disconnect_reason: String? = null,
)

data class SessionHeartbeatRequest(
    val vpn_connected: Boolean = false,
    val probe_status: String? = null,
    val connected_node: String? = null,
    val probe_latency_ms: Int? = null,
    val exit_ip: String? = null,
    val exit_country: String? = null,
    val exit_region: String? = null,
    val exit_city: String? = null,
)

data class PushTokenRequest(
    val token: String,
    val device_id: String,
    val platform: String = "android",
    val enabled: Boolean? = null,
)

data class AppDebugLogUploadRequest(
    val entries: List<AppDebugLogUploadEntry>,
    val device_id: String? = null,
    val session_id: String? = null,
    /** 本批次设备与环境快照（版本、机型、TUN 栈、VPN 状态等）。 */
    val device_meta: Map<String, String>? = null,
)

data class AppDebugLogUploadEntry(
    val level: String,
    val category: String,
    val message: String,
    val context: Map<String, String>? = null,
    val client_at: String? = null,
)

data class AppDebugLogUploadResult(val accepted: Int = 0)

data class AuthData(
    val token: String,
    val user: UserBrief,
)

data class UserBrief(
    val id: Long,
    val email: String,
    val phone: String? = null,
    val status: String? = null,
    val balance: Double = 0.0,
    val role: String? = null,
    val app_debug_enabled: Boolean = false,
)

data class PackagesData(val packages: List<PackageItem>)
data class PackageItem(
    val id: Long,
    val name: String,
    val price: Double,
    val duration_days: Int,
    val traffic_gb: Double,
    val level: Int = 1,
    val description: String? = null,
    val bandwidth_limit_mbps: Int = 0,
)

data class CreateOrderRequest(
    val package_id: Long,
    val payment_method: String,
)

data class PayOrderData(
    val order_id: Long? = null,
    val payment_url: String? = null,
    val qr_code_url: String? = null,
    val payment_method: String? = null,
    val amount: Double? = null,
    val status: String? = null,
)

data class OrderStatusData(
    val order_id: Long,
    val status: String,
    val paid_at: String? = null,
)

data class SubscriptionPackageBrief(
    val name: String,
    val level: Int = 1,
    val traffic_gb: Double = 0.0,
    val duration_days: Int = 0,
    val bandwidth_limit_mbps: Int = 0,
)

data class SubscriptionActive(
    val id: Long,
    val status: String,
    val expires_at: String,
    val traffic_total_gb: Double,
    val traffic_used_gb: Double,
    val package_id: Long? = null,
    val `package`: SubscriptionPackageBrief? = null,
    val bandwidth_limit_mbps: Int? = null,
)

/** 与会员 Web 端一致：/subscription/active 返回订阅 + 生效带宽元数据。 */
data class ActiveSubscriptionData(
    val subscription: SubscriptionActive? = null,
    val effective_bandwidth_mbps: Int = 0,
    val bandwidth_display: String? = null,
)

data class SubscriptionUsage(
    val used: Double,
    val total: Double,
    val remaining: Double,
)

data class SubscriptionTokenData(val token: String)

data class ClientConfigData(
    val format: String,
    val region: String? = null,
    val node: String? = null,
    val bandwidth_limit_mbps: Int = 0,
    val config: String,
)

data class ClientExitIpData(
    val ip: String,
    val country: String? = null,
    val region: String? = null,
    val city: String? = null,
)

data class ClientVersionData(
    val has_update: Boolean = false,
    val force_update: Boolean = false,
    val latest_version_name: String? = null,
    val latest_version_code: Int = 0,
    val min_supported_version_code: Int = 0,
    val download_url: String? = null,
    val sha256: String? = null,
    val release_notes: String? = null,
)

data class RegionsData(val regions: List<RegionItem>)
data class RegionItem(
    val code: String,
    val name: String? = null,
    val count: Int = 0,
)

data class NodesData(val nodes: List<NodeItem>)
data class NodeItem(
    val id: Long,
    val name: String,
    val region: String? = null,
    val region_name: String? = null,
    val status: String? = null,
    val protocol: String? = null,
    val access_mode: String? = null,
    val tls_mode: String? = null,
    val country: String? = null,
    val city: String? = null,
    val latency_ms: Int? = null,
    val scene_tags: List<String>? = null,
    val latency_endpoint: String? = null,
)

data class OrderItem(
    val id: Long,
    val status: String,
    val amount: Double,
    val package_id: Long? = null,
    val payment_method: String? = null,
    val created_at: String? = null,
    val paid_at: String? = null,
)

data class OrdersData(val orders: List<OrderItem>)

data class TrafficSummary(
    val total_up_mb: Double = 0.0,
    val total_down_mb: Double = 0.0,
    val total_mb: Double = 0.0,
    val count: Int = 0,
)

data class DailyTrafficItem(
    val date: String,
    val total_up_mb: Double = 0.0,
    val total_down_mb: Double = 0.0,
    val total_mb: Double = 0.0,
)

data class BatchLatencyRequest(val node_ids: List<Long>)

data class BatchLatencyData(
    val results: Map<String, Int>,
    val details: Map<String, BatchLatencyDetail>? = null,
)

data class BatchLatencyDetail(
    val latency_ms: Int = -1,
    val entry_latency_ms: Int = -1,
    val exit_latency_ms: Int = -1,
    val test_target: String? = null,
)

data class PaymentMethodsData(
    val usdt_enabled: Boolean = false,
    val methods: List<String> = emptyList(),
    val usdt: USDTConfigSummary? = null,
)

data class USDTConfigSummary(
    val network: String? = null,
    val exchange_rate: Double = 0.0,
    val min_recharge_usdt: Double = 0.0,
    val max_recharge_usdt: Double = 0.0,
    val order_expire_minutes: Int = 120,
    val confirm_tips: String? = null,
    val quick_amounts_usdt: List<Double>? = null,
    val auto_confirm_enabled: Boolean? = true,
    val confirm_mode: String? = null,
    val scan_interval_seconds: Int? = null,
    val transfer_hint_optional: Boolean? = null,
)

data class CreateRechargeRequest(val amount_usdt: Double)

data class RechargeOrdersData(val orders: List<RechargeOrderItem>)

data class RechargeOrderItem(
    val id: Long,
    val order_no: String,
    val status: String,
    val requested_usdt: Double,
    val received_usdt: Double? = null,
    val exchange_rate: Double,
    val credited_cny: Double? = null,
    val receive_address: String,
    val from_address: String? = null,
    val proof_image_url: String? = null,
    val txid: String? = null,
    val reject_reason: String? = null,
    val chain_auto_confirmed: Boolean? = null,
    val expired_at: String? = null,
    val paid_at: String? = null,
    val created_at: String? = null,
)

data class CreateRechargeData(
    val order: RechargeOrderItem,
    val confirm_tips: String? = null,
    val estimated_cny: Double? = null,
)

data class SubmitRechargeRequest(
    val from_address: String? = null,
    val proof_image_url: String? = null,
    val txid: String? = null,
)

data class TransferHintRequest(
    val from_address: String? = null,
    val proof_image_url: String? = null,
    val txid: String? = null,
)

data class ProofUploadData(
    val url: String,
)

data class NodeLatencyData(
    val node_id: Long,
    val latency: Int,
    val status: Boolean = false,
)

data class CreateTicketRequest(
    val title: String,
    val content: String,
    val priority: String = "normal",
)

data class AddTicketReplyRequest(
    val content: String,
)

data class TicketsData(
    val tickets: List<TicketItem>,
    val total: Long = 0,
    val page: Int = 1,
    val page_size: Int = 20,
)

data class TicketItem(
    val id: Long,
    val user_id: Long? = null,
    val title: String,
    val content: String,
    val status: String,
    val priority: String,
    val admin_id: Long? = null,
    val created_at: String? = null,
    val updated_at: String? = null,
    val replies: List<TicketReplyItem>? = null,
)

data class TicketReplyItem(
    val id: Long,
    val ticket_id: Long,
    val user_id: Long? = null,
    val admin_id: Long? = null,
    val content: String,
    val created_at: String? = null,
)

data class SupportChannelItem(
    val type: String,
    val label: String,
    val url: String,
    val sort_order: Int = 0,
)

data class SupportConfigData(
    val enabled: Boolean = false,
    val ticket_enabled: Boolean = true,
    val work_hours: String? = null,
    val description: String? = null,
    val channels: List<SupportChannelItem> = emptyList(),
)

data class DeviceQuota(
    val used: Int = 0,
    val max: Int = 1,
)

data class MemberSessionItem(
    val session_id: String,
    val device_name: String? = null,
    val device_type: String? = null,
    val device_model: String? = null,
    val os_name: String? = null,
    val is_current: Boolean = false,
    val is_online: Boolean = false,
    val is_vpn_connected: Boolean = false,
    val ip_binding_mode: String? = null,
    val vpn_connected_node: String? = null,
    val vpn_probe_latency_ms: Int? = null,
    val exit_ip: String? = null,
    val exit_country: String? = null,
    val exit_city: String? = null,
    val last_active_at: String? = null,
)

data class MemberSessionsData(
    val sessions: List<MemberSessionItem> = emptyList(),
    val device_quota: DeviceQuota = DeviceQuota(),
)

data class LineLeaseItem(
    val id: Long = 0,
    val node_id: Long = 0,
    val node_name: String = "",
    val status: String = "",
    val exit_ip: String? = null,
    val exit_country: String? = null,
    val exit_city: String? = null,
    val ip_binding_mode: String? = null,
)

data class ConnectDashboardData(
    val user_id: Long = 0,
    val is_vip: Boolean = false,
    val expires_at: String? = null,
    val package_name: String? = null,
    val ip_binding_mode: String = "multi",
    val ip_binding_mode_label: String? = null,
    val device_quota: DeviceQuota = DeviceQuota(),
    val held_line: LineLeaseItem? = null,
    val selected_node: String? = null,
    val scene_tags: List<String> = emptyList(),
    val probe_latency_ms: Int? = null,
    val exit_ip: String? = null,
    val exit_country: String? = null,
    val exit_city: String? = null,
)

data class UserPreferencesData(
    val ip_binding_mode: String? = null,
    val ip_binding_mode_label: String? = null,
    val connection_scenario: String? = null,
    val connection_scenario_label: String? = null,
)

data class UserPreferencesUpdate(
    val ip_binding_mode: String? = null,
    val connection_scenario: String? = null,
)

data class AcquireLineRequest(
    val node_id: Long = 0,
    val node_name: String = "",
)

data class AcquireLineResponse(
    val line: LineLeaseItem? = null,
)

data class LineStatusData(
    val held: Boolean = false,
    val line: LineLeaseItem? = null,
)
