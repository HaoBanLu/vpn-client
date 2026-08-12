/** 桌面端连接与隐私偏好（localStorage） */

const PREFIX = 'tauri_desktop_'

/** 桌面 MVP：固定系统代理，Kill Switch 关闭（TUN/Kill Switch 仅 Android 产品范围） */
export const DESKTOP_MVP_PROXY_ONLY = true

export type DesktopConnectionMode = 'proxy' | 'tun'

export const DESKTOP_SETTINGS_KEYS = {
  autoReconnect: `${PREFIX}auto_reconnect`,
  hideOnClose: `${PREFIX}hide_on_close`,
  restoreSession: `${PREFIX}restore_session`,
  bootAutoConnect: `${PREFIX}boot_auto_connect`,
  killSwitch: `${PREFIX}kill_switch`,
  connectionMode: `${PREFIX}connection_mode`,
} as const

export interface DesktopConnectionSettings {
  autoReconnect: boolean
  hideOnClose: boolean
  restoreSession: boolean
  /** Android：开机后按上次会话恢复隧道 */
  bootAutoConnect: boolean
  killSwitch: boolean
  connectionMode: DesktopConnectionMode
}

const DEFAULTS: DesktopConnectionSettings = {
  autoReconnect: true,
  hideOnClose: true,
  restoreSession: false,
  bootAutoConnect: false,
  killSwitch: false,
  connectionMode: 'proxy',
}

function readBool(key: string, fallback: boolean): boolean {
  const raw = localStorage.getItem(key)
  if (raw === null) return fallback
  return raw === '1' || raw === 'true'
}

function writeBool(key: string, value: boolean) {
  localStorage.setItem(key, value ? '1' : '0')
}

function readMode(): DesktopConnectionMode {
  const raw = localStorage.getItem(DESKTOP_SETTINGS_KEYS.connectionMode)
  return raw === 'tun' ? 'tun' : 'proxy'
}

/** 将历史 tun / killSwitch 偏好迁移为 MVP 默认值 */
function migrateLegacySettings(): void {
  if (!DESKTOP_MVP_PROXY_ONLY) return
  const mode = localStorage.getItem(DESKTOP_SETTINGS_KEYS.connectionMode)
  if (mode === 'tun') {
    localStorage.setItem(DESKTOP_SETTINGS_KEYS.connectionMode, 'proxy')
  }
  const ks = localStorage.getItem(DESKTOP_SETTINGS_KEYS.killSwitch)
  if (ks === '1' || ks === 'true') {
    localStorage.setItem(DESKTOP_SETTINGS_KEYS.killSwitch, '0')
  }
}

export function loadDesktopSettings(): DesktopConnectionSettings {
  migrateLegacySettings()
  const loaded: DesktopConnectionSettings = {
    autoReconnect: readBool(DESKTOP_SETTINGS_KEYS.autoReconnect, DEFAULTS.autoReconnect),
    hideOnClose: readBool(DESKTOP_SETTINGS_KEYS.hideOnClose, DEFAULTS.hideOnClose),
    restoreSession: readBool(DESKTOP_SETTINGS_KEYS.restoreSession, DEFAULTS.restoreSession),
    bootAutoConnect: readBool(DESKTOP_SETTINGS_KEYS.bootAutoConnect, DEFAULTS.bootAutoConnect),
    killSwitch: readBool(DESKTOP_SETTINGS_KEYS.killSwitch, DEFAULTS.killSwitch),
    connectionMode: readMode(),
  }
  if (DESKTOP_MVP_PROXY_ONLY) {
    return {
      ...loaded,
      connectionMode: 'proxy',
      killSwitch: false,
    }
  }
  return loaded
}

export function saveDesktopSettings(patch: Partial<DesktopConnectionSettings>) {
  if (patch.autoReconnect !== undefined) {
    writeBool(DESKTOP_SETTINGS_KEYS.autoReconnect, patch.autoReconnect)
  }
  if (patch.hideOnClose !== undefined) {
    writeBool(DESKTOP_SETTINGS_KEYS.hideOnClose, patch.hideOnClose)
  }
  if (patch.restoreSession !== undefined) {
    writeBool(DESKTOP_SETTINGS_KEYS.restoreSession, patch.restoreSession)
  }
  if (patch.bootAutoConnect !== undefined) {
    writeBool(DESKTOP_SETTINGS_KEYS.bootAutoConnect, patch.bootAutoConnect)
  }
  if (!DESKTOP_MVP_PROXY_ONLY) {
    if (patch.killSwitch !== undefined) {
      writeBool(DESKTOP_SETTINGS_KEYS.killSwitch, patch.killSwitch)
    }
    if (patch.connectionMode !== undefined) {
      localStorage.setItem(DESKTOP_SETTINGS_KEYS.connectionMode, patch.connectionMode)
    }
  }
}

export const VPN_SESSION_KEY = `${PREFIX}vpn_was_connected`

export function markVpnSession(active: boolean) {
  if (active) localStorage.setItem(VPN_SESSION_KEY, '1')
  else localStorage.removeItem(VPN_SESSION_KEY)
}

export function hadVpnSession(): boolean {
  return localStorage.getItem(VPN_SESSION_KEY) === '1'
}

export function effectiveConnectionMode(): DesktopConnectionMode {
  return DESKTOP_MVP_PROXY_ONLY ? 'proxy' : loadDesktopSettings().connectionMode
}

export function effectiveKillSwitchEnabled(): boolean {
  return DESKTOP_MVP_PROXY_ONLY ? false : loadDesktopSettings().killSwitch
}
