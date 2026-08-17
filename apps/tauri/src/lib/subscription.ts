import type { PackageItem, SubscriptionActive, SubscriptionUsage } from '@/api/client'

export type PurchaseActionLabel =
  | '立即购买'
  | '续费'
  | '升级'
  | '更换套餐'
  | '当前套餐'
  | '余额不足，去充值'

export interface PurchaseButtonState {
  label: PurchaseActionLabel
  enabled: boolean
  insufficientBalance: boolean
}

export function isCurrentPackage(sub: SubscriptionActive | null, pkg: PackageItem): boolean {
  if (!sub?.package_id) return false
  return sub.package_id === pkg.id
}

export function purchaseButtonState(
  sub: SubscriptionActive | null,
  pkg: PackageItem,
  userBalance: number,
  paying: boolean,
): PurchaseButtonState {
  if (paying) {
    return { label: '立即购买', enabled: false, insufficientBalance: false }
  }
  if (isCurrentPackage(sub, pkg)) {
    if (userBalance < pkg.price) {
      return { label: '余额不足，去充值', enabled: true, insufficientBalance: true }
    }
    return { label: '续费', enabled: true, insufficientBalance: false }
  }
  if (userBalance < pkg.price) {
    return { label: '余额不足，去充值', enabled: true, insufficientBalance: true }
  }
  if (!sub) {
    return { label: '立即购买', enabled: true, insufficientBalance: false }
  }
  const currentLevel = sub.package?.level ?? 1
  if ((pkg.level ?? 1) > currentLevel) {
    return { label: '升级', enabled: true, insufficientBalance: false }
  }
  return { label: '更换套餐', enabled: true, insufficientBalance: false }
}

export function purchaseSuccessMessage(sub: SubscriptionActive | null, pkg: PackageItem): string {
  if (!sub) return '购买成功，请返回连接页'
  if (isCurrentPackage(sub, pkg)) return '续费成功，权益已延长'
  const currentLevel = sub.package?.level ?? 1
  if ((pkg.level ?? 1) > currentLevel) return '升级成功，请返回连接页'
  return '套餐已更换，请返回连接页'
}

export function subscriptionStatusLabel(
  sub: SubscriptionActive | null,
  usage: SubscriptionUsage | null,
): '使用中' | '即将到期' | '流量不足' | null {
  if (!sub) return null
  const total = usage?.total ?? sub.traffic_total_gb ?? 0
  const used = usage?.used ?? sub.traffic_used_gb ?? 0
  const remaining = usage?.remaining ?? total - used
  if (total > 0 && remaining <= total * 0.1) return '流量不足'
  const daysLeft = daysUntilExpiry(sub.expires_at)
  if (daysLeft !== null && daysLeft >= 0 && daysLeft <= 7) return '即将到期'
  return '使用中'
}

export function daysUntilExpiry(expiresAt?: string | null): number | null {
  if (!expiresAt) return null
  const date = new Date(expiresAt.slice(0, 10))
  if (Number.isNaN(date.getTime())) return null
  const today = new Date()
  today.setHours(0, 0, 0, 0)
  date.setHours(0, 0, 0, 0)
  return Math.round((date.getTime() - today.getTime()) / 86400000)
}

export function trafficProgress(usage: SubscriptionUsage | null): number {
  if (!usage || usage.total <= 0) return 0
  return Math.min(1, Math.max(0, usage.used / usage.total))
}

export function regionDisplayLabel(region: { code: string; name?: string }): string {
  return region.name?.trim() || region.code.toUpperCase()
}

export function regionDisplayName(
  code: string | null,
  regions: Array<{ code: string; name?: string }>,
): string {
  if (!code) return '智能（全部地区）'
  const found = regions.find((r) => r.code === code)
  return found ? regionDisplayLabel(found) : code.toUpperCase()
}

export function buildRenewalHint(expiresAt?: string | null): string | null {
  const days = daysUntilExpiry(expiresAt)
  if (days === null) return null
  if (days < 0) return '套餐已过期，请立即续费'
  if (days === 0) return '套餐今天到期，建议尽快续费'
  if (days <= 7) return `套餐将在 ${days} 天后到期，建议提前续费`
  return null
}

export function nodeRegionLabel(region?: string, regionName?: string): string {
  return regionName?.trim() || region?.trim()?.toUpperCase() || '未知'
}

const LATENCY_GOOD_MAX_MS = 600
const LATENCY_WARN_MAX_MS = 1200

export function latencyColor(ms: number): string {
  if (ms <= LATENCY_GOOD_MAX_MS) return '#4CAF50'
  if (ms <= LATENCY_WARN_MAX_MS) return '#FFC107'
  return '#FF6B6B'
}
