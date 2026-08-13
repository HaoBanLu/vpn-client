/** 覆盖安装后是否拆除残留系统 VPN，避免 API 被打进已死隧道。 */

export const APP_VERSION_CODE_KEY = 'tauri_app_version_code'

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
