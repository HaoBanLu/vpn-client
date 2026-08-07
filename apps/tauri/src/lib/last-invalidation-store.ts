/** 对齐 Android LastInvalidationStore：登录页补发会话失效 Banner。 */

const STORAGE_KEY = 'tauri_last_invalidation'

export interface PendingInvalidation {
  title: string
  message: string
  appCode?: string
  timestamp: number
}

export function loginInvalidationTitle(appCode?: string): string {
  if (appCode === 'LOGIN_ON_ANOTHER_DEVICE') return '账号在其他设备登录'
  if (appCode === 'SESSION_REVOKED') return '登录状态已失效'
  return '登录已过期'
}

export function formatLoginBanner(title: string, message: string): string {
  return `${title}：${message}`
}

export function saveLastInvalidation(payload: {
  title?: string
  message: string
  appCode?: string
}) {
  const title = payload.title?.trim() || loginInvalidationTitle(payload.appCode)
  const message = payload.message.trim() || '请重新登录'
  const record: PendingInvalidation = {
    title,
    message,
    appCode: payload.appCode,
    timestamp: Date.now(),
  }
  localStorage.setItem(STORAGE_KEY, JSON.stringify(record))
}

export function peekLastInvalidation(): PendingInvalidation | null {
  const raw = localStorage.getItem(STORAGE_KEY)
  if (!raw) return null
  try {
    const parsed = JSON.parse(raw) as PendingInvalidation
    if (!parsed?.message?.trim() && !parsed?.title?.trim()) return null
    return {
      title: parsed.title?.trim() || loginInvalidationTitle(parsed.appCode),
      message: parsed.message?.trim() || '请重新登录',
      appCode: parsed.appCode,
      timestamp: parsed.timestamp || 0,
    }
  } catch {
    return null
  }
}

export function consumeLastInvalidation(): PendingInvalidation | null {
  const pending = peekLastInvalidation()
  if (pending) {
    localStorage.removeItem(STORAGE_KEY)
  }
  return pending
}

export function clearLastInvalidation() {
  localStorage.removeItem(STORAGE_KEY)
}
