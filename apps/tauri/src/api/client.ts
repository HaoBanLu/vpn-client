import request from './request'
import type { ClientConfigData } from '@/lib/vpn/types'

export interface ApiResponse<T> {
  code: number
  message: string
  data: T
  app_code?: string
  trace_id?: string
}

export interface AuthData {
  token: string
  user: UserBrief
}

export interface UserBrief {
  id: number
  email: string
  phone?: string
  balance: number
  status?: string
  role?: string
  app_debug_enabled?: boolean
}

export interface RegistrationConfig {
  registration_enabled: boolean
  email_verification_required: boolean
  password_reset_enabled: boolean
  send_cooldown_seconds: number
  code_length: number
  email_configured: boolean
  member_session_permanent?: boolean
  member_session_ttl_hours?: number
}

export function formatMemberSessionHint(
  config: Pick<RegistrationConfig, 'member_session_permanent' | 'member_session_ttl_hours'>,
): string | null {
  if (config.member_session_permanent === true || config.member_session_ttl_hours === 0) {
    return '登录后长期有效，除非在其他设备登录或被管理员下线'
  }
  const hours = config.member_session_ttl_hours
  if (!hours || hours <= 0) return null
  if (hours % 24 === 0) {
    return `登录有效期 ${hours / 24} 天，到期后需重新登录`
  }
  return `登录有效期 ${hours} 小时，到期后需重新登录`
}

export interface SubscriptionActive {
  id: number
  package_id?: number
  status?: string
  expires_at: string
  traffic_total_gb?: number
  traffic_used_gb?: number
  package?: { name: string; devices?: number; level?: number; traffic_gb?: number; duration_days?: number }
}

/** 与 Android ActiveSubscriptionData 一致：/subscription/active 包一层 subscription */
export interface ActiveSubscriptionData {
  subscription?: SubscriptionActive | null
  effective_bandwidth_mbps?: number
  bandwidth_display?: string
}

export interface SubscriptionUsage {
  used: number
  total: number
  remaining: number
  period?: string
}

export interface RegionItem {
  code: string
  name?: string
  count: number
}

export interface NodeItem {
  id: number
  name: string
  region: string
  region_name?: string
  country?: string
  status: string
  protocol?: string
  access_mode?: string
  tls_mode?: string
  scene_tags?: string[]
  latency_endpoint?: string
  latency_ms?: number
}

export interface NodeLatencyDetail {
  latency_ms?: number
  entry_latency_ms?: number
  exit_latency_ms?: number
  test_target?: string
}

export interface BatchLatencyData {
  results: Record<string, number>
  details?: Record<string, NodeLatencyDetail>
}

export interface PackageItem {
  id: number
  name: string
  price: number
  traffic_gb: number
  duration_days: number
  level?: number
  description?: string
}

export interface OrderItem {
  id: number
  status: string
  amount: number
  package_id?: number
  payment_method?: string
  created_at?: string
  paid_at?: string
}

export interface OrderStatusData {
  order_id: number
  status: string
  paid_at?: string
}

export interface TrafficSummary {
  total_up_mb: number
  total_down_mb: number
  total_mb: number
  count: number
}

export interface DailyTrafficItem {
  date: string
  total_up_mb: number
  total_down_mb: number
  total_mb: number
}

export interface USDTConfig {
  network?: string
  exchange_rate: number
  min_recharge_usdt: number
  max_recharge_usdt: number
  order_expire_minutes: number
  confirm_tips?: string
  quick_amounts_usdt?: number[]
  auto_confirm_enabled?: boolean
  confirm_mode?: 'auto' | 'manual'
  scan_interval_seconds?: number
  transfer_hint_optional?: boolean
}

export interface PaymentMethodsData {
  usdt_enabled: boolean
  methods: string[]
  usdt?: USDTConfig
}

export interface RechargeOrderItem {
  id: number
  order_no: string
  status: string
  requested_usdt: number
  received_usdt?: number
  exchange_rate: number
  credited_cny?: number
  receive_address: string
  from_address?: string
  proof_image_url?: string
  txid?: string
  reject_reason?: string
  chain_auto_confirmed?: boolean
  expired_at?: string
  paid_at?: string
  created_at?: string
}

export interface CreateRechargeData {
  order: RechargeOrderItem
  confirm_tips?: string
  estimated_cny?: number
}

export interface TicketReplyItem {
  id: number
  ticket_id: number
  content: string
  created_at?: string
  user_id?: number
  admin_id?: number
}

export interface TicketItem {
  id: number
  title: string
  content: string
  status: string
  priority: string
  created_at?: string
  updated_at?: string
  replies?: TicketReplyItem[]
}

export interface SupportChannelItem {
  type: string
  label: string
  url: string
  sort_order?: number
}

export interface SupportConfigData {
  enabled: boolean
  ticket_enabled?: boolean
  work_hours?: string
  description?: string
  channels: SupportChannelItem[]
}

export interface ClientVersionData {
  has_update: boolean
  force_update?: boolean
  latest_version_name?: string
  latest_version_code?: number
  min_supported_version_code?: number
  download_url?: string
  sha256?: string
  release_notes?: string
}

export interface SubscriptionTokenData {
  token: string
}

export const clientApi = {
  login: (email: string, password: string) =>
    request.post<AuthData>('/v1/auth/login', {
      email,
      password,
      device_type: 'desktop',
      platform: 'tauri',
    }),

  getRegistrationConfig: () =>
    request.get<RegistrationConfig>('/v1/auth/registration-config'),

  sendEmailCode: (email: string, purpose: 'register' | 'reset_password') =>
    request.post<unknown>('/v1/auth/email-code/send', { email, purpose }),

  register: (body: {
    email: string
    password: string
    email_code?: string
    device_type?: string
    client_platform?: string
  }) =>
    request.post<AuthData>('/v1/auth/register', {
      ...body,
      device_type: body.device_type || 'desktop',
      client_platform: body.client_platform || 'tauri',
    }),

  forgotPassword: (email: string) =>
    request.post<unknown>('/v1/auth/forgot-password', { email }),

  resetPassword: (body: {
    email: string
    email_code: string
    new_password: string
    client_platform?: string
  }) =>
    request.post<unknown>('/v1/auth/reset-password', {
      ...body,
      client_platform: body.client_platform || 'tauri',
    }),

  changePassword: (oldPassword: string, newPassword: string) =>
    request.put<unknown>('/v1/users/me/password', {
      old_password: oldPassword,
      new_password: newPassword,
    }),

  getMe: () => request.get<UserBrief>('/v1/users/me'),

  getActiveSubscription: async () => {
    const res = await request.get<ActiveSubscriptionData | SubscriptionActive | null>(
      '/v1/subscription/active',
    )
    const raw = res.data
    if (!raw) return { ...res, data: null as SubscriptionActive | null }
    if ('subscription' in raw) {
      return { ...res, data: raw.subscription ?? null }
    }
    // 兼容旧版扁平 SubscriptionActive
    return { ...res, data: raw as SubscriptionActive }
  },

  getUsage: () => request.get<SubscriptionUsage>('/v1/subscription/usage'),

  getSubscriptionToken: () => request.get<SubscriptionTokenData>('/v1/subscription/token'),

  getRegions: () => request.get<{ regions: RegionItem[] }>('/v1/subscription/regions'),

  getNodes: () => request.get<{ nodes: NodeItem[] }>('/v1/nodes'),

  getPackages: () => request.get<{ packages: PackageItem[] }>('/v1/packages'),

  createOrder: (packageId: number) =>
    request.post<{ id: number }>('/v1/orders', {
      package_id: packageId,
      payment_method: 'balance',
    }),

  payOrder: (orderId: number) => request.post<unknown>(`/v1/orders/${orderId}/pay`),

  getOrderStatus: (orderId: number) =>
    request.get<OrderStatusData>(`/v1/orders/${orderId}/status`),

  getOrders: () => request.get<{ orders: OrderItem[] }>('/v1/orders'),

  getPaymentMethods: () => request.get<PaymentMethodsData>('/v1/payment-methods'),

  createRechargeOrder: (amountUsdt: number) =>
    request.post<CreateRechargeData>('/v1/recharge-orders', { amount_usdt: amountUsdt }),

  getRechargeOrders: () => request.get<{ orders: RechargeOrderItem[] }>('/v1/recharge-orders'),

  getRechargeOrder: (id: number) => request.get<RechargeOrderItem>(`/v1/recharge-orders/${id}`),

  uploadRechargeProof: (file: File) => {
    const form = new FormData()
    form.append('file', file)
    return request.post<{ url: string }>('/v1/recharge-orders/proof-upload', form)
  },

  submitRechargeOrder: (id: number, body: { from_address?: string; proof_image_url?: string; txid?: string }) =>
    request.post<RechargeOrderItem>(`/v1/recharge-orders/${id}/submit`, body),

  saveRechargeTransferHint: (id: number, body: { from_address?: string; proof_image_url?: string; txid?: string }) =>
    request.post<RechargeOrderItem>(`/v1/recharge-orders/${id}/transfer-hint`, body),

  cancelRechargeOrder: (id: number) =>
    request.post<unknown>(`/v1/recharge-orders/${id}/cancel`),

  getTrafficSummary: () => request.get<TrafficSummary>('/v1/traffic/summary'),

  getTrafficDaily: () => request.get<DailyTrafficItem[]>('/v1/traffic/daily'),

  getClientConfig: (
    region?: string | null,
    node?: string | null,
    routeMode?: string | null,
    profile?: string | null,
  ) =>
    request.get<ClientConfigData>('/v1/client/config', {
      params: {
        region: region || undefined,
        node: node || undefined,
        route_mode: routeMode || undefined,
        profile: profile || undefined,
      },
    }),

  testNodeLatency: (nodeId: number) =>
    request.get<{ latency: number }>(`/v1/nodes/${nodeId}/test/latency`),

  batchTestLatency: (nodeIds: number[]) =>
    request.post<BatchLatencyData>('/v1/nodes/test/batch-latency', {
      node_ids: nodeIds,
    }),

  getTickets: (page = 1, pageSize = 20) =>
    request.get<{ tickets: TicketItem[]; total: number }>('/v1/tickets', {
      params: { page, page_size: pageSize },
    }),

  getTicket: (id: number) => request.get<TicketItem>(`/v1/tickets/${id}`),

  createTicket: (body: { title: string; content: string; priority?: string }) =>
    request.post<TicketItem>('/v1/tickets', {
      priority: 'normal',
      ...body,
    }),

  addTicketReply: (id: number, content: string) =>
    request.post<TicketReplyItem>(`/v1/tickets/${id}/replies`, { content }),

  getSupportConfig: () => request.get<SupportConfigData>('/v1/support-config'),

  getClientVersion: (platform: string, versionCode: number, versionName: string) =>
    request.get<ClientVersionData>('/v1/client/version', {
      params: { platform, version_code: versionCode, version_name: versionName },
    }),

  sendHeartbeat: (payload: {
    vpn_connected: boolean
    probe_status?: string
    connected_node?: string
    probe_latency_ms?: number
    exit_ip?: string
    exit_country?: string
    exit_city?: string
  }) => request.post<unknown>('/v1/session/heartbeat', payload),

  getMySessions: () =>
    request.get<{
      sessions: MemberSessionItem[]
      device_quota: { used: number; max: number }
    }>('/v1/users/me/sessions'),

  revokeMySession: (sessionId: string) =>
    request.post<{
      sessions: MemberSessionItem[]
      device_quota: { used: number; max: number }
    }>(`/v1/users/me/sessions/${sessionId}/revoke`),

  uploadAppDebugLogs: (body: {
    entries: Array<{ level: string; category: string; message: string }>
    device_meta?: Record<string, unknown>
    device_id?: string
  }) => request.post<{ accepted: number }>('/v1/users/me/app-debug-logs', body),

  getConnectDashboard: (selectedNode?: string | null) =>
    request.get<ConnectDashboardData>('/v1/users/me/connect-dashboard', {
      params: { selected_node: selectedNode || undefined },
    }),

  getUserPreferences: () => request.get<UserPreferencesData>('/v1/users/me/preferences'),

  updateUserPreferences: (body: { ip_binding_mode?: string; connection_scenario?: string }) =>
    request.put<UserPreferencesData>('/v1/users/me/preferences', body),
}

export interface MemberSessionItem {
  session_id: string
  device_name?: string
  device_type?: string
  device_model?: string
  is_current?: boolean
  is_online?: boolean
  is_vpn_connected?: boolean
  ip_binding_mode?: string
  vpn_connected_node?: string
  exit_ip?: string
  last_active_at?: string
}

export interface ConnectDashboardData {
  user_id: number
  is_vip: boolean
  expires_at?: string
  ip_binding_mode: string
  ip_binding_mode_label?: string
  scene_tags?: string[]
  probe_latency_ms?: number
  exit_ip?: string
  exit_country?: string
  exit_city?: string
  held_line?: LineLeaseItem | null
  selected_node?: string
}

export interface LineLeaseItem {
  node_name: string
  exit_ip?: string
  exit_country?: string
  exit_city?: string
}

export interface UserPreferencesData {
  ip_binding_mode: string
  ip_binding_mode_label?: string
  connection_scenario?: string
  connection_scenario_label?: string
}
