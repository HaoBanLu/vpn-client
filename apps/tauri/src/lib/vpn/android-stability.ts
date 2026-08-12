import { invoke } from '@tauri-apps/api/core'
import { detectClientPlatform } from '@/lib/app-meta'

export interface AndroidStabilityStatus {
  alwaysOnConfigured: boolean
  lockdownConfigured: boolean
  batteryOptimizationIgnored: boolean
  bootAutoConnectEnabled: boolean
  hardeningDoneCount: number
  hardeningTotal: number
}

export function isAndroidStabilitySupported(): boolean {
  return detectClientPlatform() === 'android'
}

export async function getAndroidStabilityStatus(): Promise<AndroidStabilityStatus> {
  return invoke<AndroidStabilityStatus>('vpn_get_stability_status')
}

export async function setBootAutoConnect(enabled: boolean): Promise<boolean> {
  return invoke<boolean>('vpn_set_boot_auto_connect', { options: { enabled } })
}

export async function openVpnSettings(): Promise<void> {
  await invoke<boolean>('vpn_open_vpn_settings')
}

export async function openBatteryOptimizationSettings(): Promise<void> {
  await invoke<boolean>('vpn_open_battery_optimization_settings')
}
