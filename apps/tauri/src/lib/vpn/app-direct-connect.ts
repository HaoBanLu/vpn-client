import { invoke } from '@tauri-apps/api/core'
import { detectClientPlatform } from '@/lib/app-meta'

export interface InstalledAppInfo {
  packageName: string
  label: string
}

export interface ListInstalledAppsResult {
  apps: InstalledAppInfo[]
  selectedPackages: string[]
  needsPermission: boolean
}

export interface DirectConnectPackagesResult {
  packages: string[]
  count: number
}

export function isAppDirectConnectSupported(): boolean {
  return detectClientPlatform() === 'android'
}

export async function listInstalledApps(): Promise<ListInstalledAppsResult> {
  return invoke<ListInstalledAppsResult>('vpn_list_installed_apps')
}

export async function getDirectConnectPackages(): Promise<DirectConnectPackagesResult> {
  return invoke<DirectConnectPackagesResult>('vpn_get_direct_connect_packages')
}

export async function setDirectConnectPackages(
  packages: string[],
): Promise<DirectConnectPackagesResult> {
  return invoke<DirectConnectPackagesResult>('vpn_set_direct_connect_packages', {
    options: { packages },
  })
}

export async function requestInstalledAppsPermission(): Promise<boolean> {
  return invoke<boolean>('vpn_request_installed_apps_permission')
}

export function filterInstalledApps(
  apps: InstalledAppInfo[],
  query: string,
): InstalledAppInfo[] {
  const q = query.trim().toLowerCase()
  if (!q) return apps
  return apps.filter(
    (app) =>
      app.label.toLowerCase().includes(q) || app.packageName.toLowerCase().includes(q),
  )
}
