/** 会员端会话失效判断，对齐 Android SessionAuth.kt */

const SESSION_APP_CODES = new Set(['SESSION_REVOKED', 'LOGIN_ON_ANOTHER_DEVICE'])

/** 登录/注册等公开接口的 401 表示凭证错误，不应触发全局登出。 */
const PUBLIC_AUTH_PATH_MARKERS = [
  '/auth/login',
  '/auth/register',
  '/auth/forgot-password',
  '/auth/reset-password',
  '/auth/email-code/',
]

export function shouldInvalidateSession(path: string, hadAuth: boolean, appCode?: string): boolean {
  if (appCode && SESSION_APP_CODES.has(appCode)) return true
  if (PUBLIC_AUTH_PATH_MARKERS.some((marker) => path.includes(marker))) return false
  return hadAuth
}

/** 仅 HTTP/业务 401 才登出；超时、断网、5xx 保持登录。 */
export function shouldLogoutOnApiFailure(input: {
  httpStatus?: number
  businessCode?: number
  path: string
  hadAuth: boolean
  appCode?: string
}): boolean {
  if (input.httpStatus !== 401 && input.businessCode !== 401) return false
  return shouldInvalidateSession(input.path, input.hadAuth, input.appCode)
}

export const SESSION_HEARTBEAT_FOREGROUND_MS = 15_000
export const SESSION_HEARTBEAT_BACKGROUND_MS = 60_000

export function sessionHeartbeatIntervalMs(visible: boolean): number {
  return visible ? SESSION_HEARTBEAT_FOREGROUND_MS : SESSION_HEARTBEAT_BACKGROUND_MS
}

export function sessionInvalidationMessage(message?: string, appCode?: string): string {
  const raw = message?.trim()
  if (appCode === 'LOGIN_ON_ANOTHER_DEVICE') {
    return raw || '账号已在其他设备登录，请重新登录'
  }
  if (appCode === 'SESSION_REVOKED') {
    return raw || '登录状态已失效，请重新登录'
  }
  const normalized = raw?.toLowerCase()
  if (
    !normalized ||
    normalized === 'token expired' ||
    normalized === 'invalid token' ||
    normalized === 'invalid session' ||
    normalized === 'unauthorized' ||
    normalized === 'authorization header required' ||
    normalized === 'invalid authorization header format'
  ) {
    return '登录状态已失效，请重新登录'
  }
  return raw!
}
