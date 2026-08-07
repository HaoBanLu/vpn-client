export const APP_ROUTE_MODE = {
  FULL: 'full',
  SPLIT: 'split',
} as const

export type AppRouteMode = (typeof APP_ROUTE_MODE)[keyof typeof APP_ROUTE_MODE]

export type { ClientProfile } from './connection-scenario'
export { CLIENT_PROFILE } from './connection-scenario'

export const ROUTE_MODE_STORAGE_KEY = 'tauri_route_mode'

export function isDomesticDirectEnabled(routeMode: string | null | undefined): boolean {
  return routeMode?.toLowerCase() === APP_ROUTE_MODE.SPLIT
}

export function loadSavedRouteMode(): AppRouteMode {
  const raw = localStorage.getItem(ROUTE_MODE_STORAGE_KEY)
  if (raw === APP_ROUTE_MODE.SPLIT || raw === APP_ROUTE_MODE.FULL) {
    return raw
  }
  return APP_ROUTE_MODE.FULL
}

export function saveRouteMode(mode: AppRouteMode) {
  localStorage.setItem(ROUTE_MODE_STORAGE_KEY, mode)
}

export function defaultRouteModeForProfile(profile: string): AppRouteMode {
  return profile === 'overseas_weak' ? APP_ROUTE_MODE.SPLIT : APP_ROUTE_MODE.FULL
}
