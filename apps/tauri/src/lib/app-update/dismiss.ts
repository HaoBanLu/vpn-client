import type { AppUpdateResult } from '@/lib/desktop/updater'
import { APP_VERSION_CODE, APP_VERSION_NAME } from '@/lib/app-meta'

export const UPDATE_DISMISSED_KEY = 'tauri_update_dismissed_version'
export const UPDATE_ACCEPTED_KEY = 'tauri_update_accepted_version'
export const UPDATE_LAST_CHECK_KEY = 'tauri_update_last_check_at'

/** 24h 前台复检间隔（与 PRD 对齐） */
export const UPDATE_CHECK_INTERVAL_MS = 24 * 60 * 60 * 1000

export function resolveUpdateVersionKey(result: Pick<AppUpdateResult, 'latestVersionCode' | 'latestVersionName'>): string {
  if (result.latestVersionCode != null && result.latestVersionCode > 0) {
    return String(result.latestVersionCode)
  }
  const name = result.latestVersionName?.trim()
  return name || 'unknown'
}

export function markUpdateDismissed(result: Pick<AppUpdateResult, 'latestVersionCode' | 'latestVersionName'>) {
  localStorage.setItem(UPDATE_DISMISSED_KEY, resolveUpdateVersionKey(result))
}

export function markUpdateAccepted(result: Pick<AppUpdateResult, 'latestVersionCode' | 'latestVersionName'>) {
  localStorage.setItem(UPDATE_ACCEPTED_KEY, resolveUpdateVersionKey(result))
}

export function clearUpdateAccepted() {
  localStorage.removeItem(UPDATE_ACCEPTED_KEY)
}

export function wasUpdateDismissed(result: Pick<AppUpdateResult, 'latestVersionCode' | 'latestVersionName'>): boolean {
  return localStorage.getItem(UPDATE_DISMISSED_KEY) === resolveUpdateVersionKey(result)
}

export function wasUpdateAccepted(result: Pick<AppUpdateResult, 'latestVersionCode' | 'latestVersionName'>): boolean {
  return localStorage.getItem(UPDATE_ACCEPTED_KEY) === resolveUpdateVersionKey(result)
}

export function recordUpdateCheckTime() {
  localStorage.setItem(UPDATE_LAST_CHECK_KEY, String(Date.now()))
}

export function shouldRunPeriodicUpdateCheck(now = Date.now()): boolean {
  const raw = localStorage.getItem(UPDATE_LAST_CHECK_KEY)
  if (!raw) return true
  const last = Number(raw)
  if (!Number.isFinite(last)) return true
  return now - last >= UPDATE_CHECK_INTERVAL_MS
}

/** 本地版本已不低于待更新目标时，不再弹发现新版本。 */
export function isLocalVersionUpToDate(result: Pick<AppUpdateResult, 'latestVersionCode' | 'latestVersionName'>): boolean {
  const latestCode = result.latestVersionCode ?? 0
  if (latestCode > 0 && APP_VERSION_CODE >= latestCode) return true
  const latestName = result.latestVersionName?.trim()
  if (latestName && latestName === APP_VERSION_NAME.trim()) return true
  return false
}

export function shouldShowUpdatePrompt(result: AppUpdateResult): boolean {
  if (!result.hasUpdate && !result.forceUpdate) return false
  if (result.forceUpdate) return true
  if (isLocalVersionUpToDate(result)) return false
  if (wasUpdateAccepted(result)) return false
  if (wasUpdateDismissed(result)) return false
  return true
}
