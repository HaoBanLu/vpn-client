import { invoke } from '@tauri-apps/api/core'
import { listen, type UnlistenFn } from '@tauri-apps/api/event'
import type {
  VpnConnectOptions,
  VpnConnectionStatus,
  VpnDisconnectOptions,
  VpnPlatformInfo,
  VpnSessionStats,
} from './types'
import { effectiveConnectionMode, effectiveKillSwitchEnabled } from './desktop-settings'

const CMD = {
  platformInfo: 'vpn_platform_info',
  connect: 'vpn_connect',
  reconnect: 'vpn_reconnect',
  disconnect: 'vpn_disconnect',
  status: 'vpn_status',
  stats: 'vpn_stats',
  prepare: 'vpn_prepare',
  probe: 'vpn_probe',
  heal: 'vpn_heal',
  killSwitchRelease: 'vpn_kill_switch_release',
  killSwitchStatus: 'vpn_kill_switch_status',
} as const

export const VPN_STATUS_EVENT = 'vpn://status'
export const VPN_STATS_EVENT = 'vpn://stats'

function toRustConnectOptions(options: VpnConnectOptions) {
  return {
    configJson: options.configJson,
    nodeName: options.nodeName,
    connectionMode: options.connectionMode ?? effectiveConnectionMode(),
  }
}

export async function getVpnPlatformInfo(): Promise<VpnPlatformInfo> {
  return invoke<VpnPlatformInfo>(CMD.platformInfo)
}

export async function prepareVpn(): Promise<boolean> {
  return invoke<boolean>(CMD.prepare)
}

export async function connectVpn(options: VpnConnectOptions): Promise<void> {
  await invoke(CMD.connect, { options: toRustConnectOptions(options) })
}

export async function reconnectVpn(options: VpnConnectOptions): Promise<void> {
  await invoke(CMD.reconnect, { options: toRustConnectOptions(options) })
}

export async function disconnectVpn(options?: VpnDisconnectOptions): Promise<void> {
  await invoke(CMD.disconnect, {
    options: {
      userInitiated: options?.userInitiated ?? true,
      killSwitchEnabled: options?.killSwitchEnabled ?? effectiveKillSwitchEnabled(),
    },
  })
}

export async function getVpnStatus(): Promise<VpnConnectionStatus> {
  return invoke<VpnConnectionStatus>(CMD.status, {
    killSwitchEnabled: effectiveKillSwitchEnabled(),
  })
}

export async function releaseKillSwitch(): Promise<void> {
  await invoke(CMD.killSwitchRelease)
}

export async function isKillSwitchEngaged(): Promise<boolean> {
  return invoke<boolean>(CMD.killSwitchStatus)
}

export async function getVpnStats(): Promise<VpnSessionStats> {
  return invoke<VpnSessionStats>(CMD.stats)
}

export interface VpnProbeResult {
  basicOk: boolean
  overseasOk: boolean
  slow?: boolean
  latencyMs?: number
}

export async function probeVpn(): Promise<VpnProbeResult> {
  return invoke<VpnProbeResult>(CMD.probe)
}

/** 断网/网卡恢复轻量自愈：重刷系统代理；调用方须再 probeVpn 验证用户路径。 */
export async function healVpn(): Promise<void> {
  await invoke(CMD.heal)
}

export async function watchVpnStatus(
  handler: (status: VpnConnectionStatus) => void,
): Promise<UnlistenFn> {
  return listen<VpnConnectionStatus>(VPN_STATUS_EVENT, (event) => {
    handler(event.payload)
  })
}

export async function watchVpnStats(handler: (stats: VpnSessionStats) => void): Promise<UnlistenFn> {
  return listen<VpnSessionStats>(VPN_STATS_EVENT, (event) => {
    handler(event.payload)
  })
}
