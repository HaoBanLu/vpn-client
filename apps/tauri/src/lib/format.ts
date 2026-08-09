import { formatTraffic as formatTrafficShared } from '@shared/utils/traffic'
import { resolveApiBaseUrl } from '@/lib/api-config'

export function formatTraffic(gb: number): string {
  // 业务 API 以 GB 为单位；shared 以 MB 为单位，此处保持现有语义
  if (gb >= 1024) return `${(gb / 1024).toFixed(2)} TB`
  if (gb >= 1) return `${gb.toFixed(2)} GB`
  return formatTrafficShared(gb * 1024)
}

export function formatMoney(value: number): string {
  return `¥${value.toFixed(2)}`
}

export function formatUsdt(value: number): string {
  return `${value.toFixed(2)} USDT`
}

/** 对齐 Android formatUsdtAmount：整数不带小数 */
export function formatUsdtAmount(amount: number): string {
  return Number.isInteger(amount) ? String(amount) : amount.toFixed(2)
}

/** 对齐 Android formatExpireTime：MM-dd HH:mm */
export function formatExpireShort(raw?: string | null): string {
  if (!raw) return ''
  const date = new Date(raw)
  if (Number.isNaN(date.getTime())) {
    return raw.slice(0, 16).replace('T', ' ')
  }
  const mm = String(date.getMonth() + 1).padStart(2, '0')
  const dd = String(date.getDate()).padStart(2, '0')
  const hh = String(date.getHours()).padStart(2, '0')
  const mi = String(date.getMinutes()).padStart(2, '0')
  return `${mm}-${dd} ${hh}:${mi}`
}

export function orderStatusLabel(status: string): string {
  const map: Record<string, string> = {
    pending: '待支付',
    paid: '已支付',
    cancelled: '已取消',
    expired: '已过期',
    failed: '失败',
  }
  return map[status] || status
}

export function ticketStatusLabel(status: string): string {
  const map: Record<string, string> = {
    open: '待处理',
    pending: '处理中',
    in_progress: '处理中',
    resolved: '已解决',
    closed: '已关闭',
  }
  return map[status] || status
}

export function ticketPriorityLabel(priority: string): string {
  const map: Record<string, string> = {
    low: '低',
    normal: '普通',
    high: '高',
    urgent: '紧急',
  }
  return map[priority] || priority
}

export function formatBytes(bytes: number): string {
  if (bytes < 1024) return `${bytes} B`
  if (bytes < 1024 * 1024) return `${(bytes / 1024).toFixed(1)} KB`
  if (bytes < 1024 * 1024 * 1024) return `${(bytes / 1024 / 1024).toFixed(2)} MB`
  return `${(bytes / 1024 / 1024 / 1024).toFixed(2)} GB`
}

export function rechargeStatusLabel(
  status: string,
  autoConfirmed?: boolean,
  isAutoMode?: boolean,
): string {
  if (status === 'paid' && autoConfirmed) {
    return '自动确认'
  }
  if (status === 'submitted' && isAutoMode) {
    return '确认中'
  }
  if (status === 'pending_transfer' && isAutoMode) {
    return '等待链上确认'
  }
  const map: Record<string, string> = {
    pending_transfer: '待转账',
    submitted: '待审核',
    paid: '已到账',
    rejected: '已驳回',
    expired: '已过期',
    cancelled: '已取消',
  }
  return map[status] || status
}

export function rechargeStatusColor(status: string): string {
  const map: Record<string, string> = {
    paid: 'success',
    rejected: 'error',
    submitted: 'processing',
    pending_transfer: 'warning',
    expired: 'default',
    cancelled: 'default',
  }
  return map[status] || 'default'
}

export function orderStatusColor(status: string): string {
  const map: Record<string, string> = {
    paid: 'success',
    pending: 'warning',
    cancelled: 'default',
    expired: 'default',
    failed: 'error',
    refunded: 'processing',
  }
  return map[status] || 'default'
}

export function formatDateTime(value?: string): string {
  if (!value) return '-'
  const date = new Date(value)
  if (Number.isNaN(date.getTime())) {
    return value.slice(0, 16).replace('T', ' ')
  }
  return date.toLocaleString('zh-CN', {
    year: 'numeric',
    month: '2-digit',
    day: '2-digit',
    hour: '2-digit',
    minute: '2-digit',
    hour12: false,
  })
}

export function resolveAssetUrl(path?: string): string {
  if (!path) return ''
  if (path.startsWith('http://') || path.startsWith('https://')) return path
  const base = resolveApiBaseUrl(import.meta.env.VITE_API_BASE_URL).replace(/\/api\/?$/, '')
  const normalized = path.startsWith('/') ? path : `/${path}`
  if (normalized.startsWith('/api/uploads/')) return `${base}${normalized}`
  if (normalized.startsWith('/uploads/')) return `${base}/api${normalized}`
  return `${base}${normalized}`
}
