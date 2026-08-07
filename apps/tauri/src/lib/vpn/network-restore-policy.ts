/**
 * 桌面断网 / 网卡恢复策略。
 *
 * 桌面 MVP 为**系统代理**（无 TUN），原则与 Android **3.15.7** 对齐：
 * - 断网再连、网卡恢复共用恢复链
 * - 自动重连开启时：**直接完整重连**（禁止先 HEAL 再赌探测）
 * - 没网时不空转重连
 *
 * 见 `apps/tauri/AGENTS.md`「断网 / 网络恢复」。
 */

export type DesktopConnectionState = 'disconnected' | 'connecting' | 'connected' | 'failed'

export type DesktopNetworkRestoreAction = 'heal' | 'schedule_reconnect' | 'none'

export const DESKTOP_NETWORK_RESTORE = {
  /** 合并 online 抖动，避免连扣重连（对齐 Android RECONNECT_DEBOUNCE_MS） */
  reconnectDebounceMs: 1_500,
  /** 关闭自动重连时轻量自愈：重刷系统代理后稍等 */
  settleAfterHealMs: 1_500,
  /** 周期探活连续失败达此次数且 navigator.onLine → 自动重连 */
  healthFailStreakToReconnect: 2,
} as const

/**
 * 物理网从无到有（`online` 事件）时的动作。
 * - 任意未主动断开态 + 自动重连开 → **完整重连**（含已连接；对齐 Android 3.15.7）
 * - 关闭自动重连且仍已连接 → HEAL（仅重刷代理，不完整重连）
 */
export function decideDesktopNetworkRestore(input: {
  connectionState: DesktopConnectionState
  userInitiatedDisconnect: boolean
  autoReconnectEnabled: boolean
}): DesktopNetworkRestoreAction {
  if (input.userInitiatedDisconnect) {
    return 'none'
  }
  if (!input.autoReconnectEnabled) {
    return input.connectionState === 'connected' ? 'heal' : 'none'
  }
  switch (input.connectionState) {
    case 'connected':
    case 'disconnected':
    case 'failed':
    case 'connecting':
      return 'schedule_reconnect'
    default:
      return 'none'
  }
}

/**
 * 轻量自愈后是否仍需完整重连（仅关闭自动重连时的兜底探测用）。
 * `proxyBasicOk` / `proxyOverseasOk` 须来自经系统代理的 `vpn_probe`。
 */
export function shouldReconnectAfterDesktopNetworkRecovery(input: {
  navigatorOnline: boolean
  proxyBasicOk: boolean
  proxyOverseasOk: boolean
}): boolean {
  if (!input.navigatorOnline) return false
  return !input.proxyBasicOk || !input.proxyOverseasOk
}

export function nextDesktopHealthFailStreak(input: {
  navigatorOnline: boolean
  probeFailed: boolean
  previousStreak: number
}): number {
  if (!input.navigatorOnline) return 0
  if (input.probeFailed) return input.previousStreak + 1
  return 0
}

export function shouldReconnectOnDesktopHealthStreak(input: {
  navigatorOnline: boolean
  failStreak: number
}): boolean {
  return (
    input.navigatorOnline &&
    input.failStreak >= DESKTOP_NETWORK_RESTORE.healthFailStreakToReconnect
  )
}

export function shouldProceedDesktopAutoReconnect(navigatorOnline: boolean): boolean {
  return navigatorOnline
}
