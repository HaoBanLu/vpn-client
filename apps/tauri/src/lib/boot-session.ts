/** 覆盖安装后是否拆除残留系统 VPN，避免 API 被打进已死隧道。 */

export const APP_VERSION_CODE_KEY = 'tauri_app_version_code'
/** 升级宽限期截止时间（epoch ms）：期内 API 失败一律拆隧道重试，禁止「隧道仍在」跳过 */
export const UPGRADE_API_GRACE_UNTIL_KEY = 'tauri_upgrade_api_grace_until'
export const UPGRADE_API_GRACE_MS = 90_000
/** 拆隧道 / 原生 disconnect 硬超时，避免 MainShell 一直等 */
export const DROP_TUNNEL_TIMEOUT_MS = 4_000
/** 账户/仪表盘首屏拉取硬超时（WebView 上 axios timeout 偶发不触发） */
export const BOOTSTRAP_FETCH_TIMEOUT_MS = 12_000

export function shouldDropLeftoverTunnelOnLaunch(input: {
  previousVersionCode: string | null
  currentVersionCode: number
  vpnActive: boolean
  systemVpnActive?: boolean
}): boolean {
  const prev = input.previousVersionCode?.trim() || null
  const current = String(input.currentVersionCode)
  if (prev === current) return input.systemVpnActive === true
  if (prev !== null) return true
  return input.vpnActive || input.systemVpnActive === true
}

export function persistAppVersionCode(currentVersionCode: number, storage: Storage = localStorage) {
  storage.setItem(APP_VERSION_CODE_KEY, String(currentVersionCode))
}

export function readPersistedAppVersionCode(storage: Storage = localStorage): string | null {
  return storage.getItem(APP_VERSION_CODE_KEY)
}

export function markUpgradeApiGrace(now = Date.now(), storage: Storage = localStorage) {
  storage.setItem(UPGRADE_API_GRACE_UNTIL_KEY, String(now + UPGRADE_API_GRACE_MS))
}

export function clearUpgradeApiGrace(storage: Storage = localStorage) {
  storage.removeItem(UPGRADE_API_GRACE_UNTIL_KEY)
}

export function isInUpgradeApiGrace(now = Date.now(), storage: Storage = localStorage): boolean {
  const until = Number(storage.getItem(UPGRADE_API_GRACE_UNTIL_KEY) || 0)
  return Number.isFinite(until) && until > 0 && now < until
}
