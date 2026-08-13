/** VPN Bridge 跨端统一类型，对齐 Android ConnectionState / VpnConnectionStatus */

export type VpnConnectionState = 'disconnected' | 'connecting' | 'connected' | 'failed'

export type VpnProbeStatus =
  | 'idle'
  | 'probing'
  | 'ok'
  | 'slow'
  | 'limited_overseas'
  | 'degraded'
  | 'failed'

export interface VpnConnectionStatus {
  state: VpnConnectionState
  error?: string | null
  nodeName?: string | null
}

export interface VpnSessionStats {
  uploadBytes: number
  downloadBytes: number
  durationMs: number
  /** 展示用上传 bytes/s（Android 为 tracker EMA，可选） */
  uploadBps?: number
  /** 展示用下载 bytes/s（Android 为 tracker EMA，可选） */
  downloadBps?: number
}

export interface VpnConnectOptions {
  configJson: string
  nodeName?: string
  /** 桌面端：proxy | tun */
  connectionMode?: 'proxy' | 'tun'
}

export interface VpnDisconnectOptions {
  userInitiated?: boolean
  killSwitchEnabled?: boolean
}

export interface VpnPlatformInfo {
  platform: 'android' | 'ios' | 'windows' | 'macos' | 'linux' | 'web'
  vpnSupported: boolean
  implementation: 'android-vpn' | 'desktop-vpn' | 'network-extension' | 'none'
  notes?: string
}

export interface ClientConfigData {
  config: string
  format?: string
  region?: string
  node?: string
}
